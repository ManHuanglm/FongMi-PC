package com.fongmi.desktop.player

import com.fongmi.android.tv.api.DanmakuApi
import com.fongmi.android.tv.bean.Danmaku
import com.fongmi.android.tv.setting.DanmakuSetting
import com.github.catvod.net.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * 桌面端弹幕管线：DanmakuApi 搜索 → 拉取评论（XML/JSON 双格式）→ 转换 ASS（车道分配 + \move 滚动）
 * → 经 mpv sub-add 挂载（libass 渲染，滚动/顶/底模式，速度/字号/透明度/屏蔽词由 DanmakuSetting 驱动）
 */
object DesktopDanmaku {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = HashMap<String, List<DanmakuComment>>()
    private val currentFile = AtomicReference<File?>(null)
    private val currentKey = AtomicReference<String>("")

    /** 弹幕评论（时间秒/文本/模式/颜色） */
    data class DanmakuComment(val time: Double, val text: String, val mode: Int, val color: Int)

    /** 自动挂载入口（canSearch 语义与安卓一致） */
    fun autoAttach(player: MpvPlayer, name: String, episode: String, onStatus: (String) -> Unit) {
        if (!DanmakuSetting.isLoad() || !DanmakuApi.canSearch()) return
        attach(player, name, episode, onStatus)
    }

    /** 搜索并挂载弹幕（重复调用先卸载旧轨） */
    fun attach(player: MpvPlayer, name: String, episode: String, onStatus: (String) -> Unit) {
        detach(player)
        onStatus("弹幕搜索中...")
        DanmakuApi.search(name, episode, { items ->
            val target = items.firstOrNull { !it.isEmpty() }
            if (target == null) {
                onStatus("未找到匹配弹幕")
                return@search
            }
            scope.launch {
                try {
                    val comments = loadComments(target.getUrl())
                    if (comments.isEmpty()) {
                        onStatus("弹幕数据为空")
                        return@launch
                    }
                    val file = buildAssFile(comments, "${name}_${episode}".hashCode().toString())
                    if (player.loadSubFile(file.absolutePath, "弹幕")) {
                        onStatus("弹幕已加载（${comments.size} 条）")
                    } else {
                        onStatus("弹幕挂载失败")
                    }
                } catch (e: Exception) {
                    onStatus("弹幕加载失败: ${e.message}")
                    println("[Danmaku] 加载失败: ${e.message}")
                }
            }
        }, { e ->
            onStatus("弹幕搜索失败: ${e.message ?: "未知错误"}")
        })
    }

    /** 卸载弹幕轨 */
    fun detach(player: MpvPlayer) {
        player.removeSub()
        currentFile.getAndSet(null)?.delete()
        currentKey.getAndSet("")
    }

    /** 设置变更后重建（保持当前弹幕源） */
    fun refresh(player: MpvPlayer, name: String, episode: String, onStatus: (String) -> Unit) {
        val key = currentKey.get()
        if (key.isEmpty()) return
        val comments = cache[key] ?: return
        detach(player)
        scope.launch {
            runCatching {
                val file = buildAssFile(comments, key)
                if (player.loadSubFile(file.absolutePath, "弹幕")) onStatus("弹幕设置已应用") else onStatus("弹幕重建失败")
            }.onFailure { onStatus("弹幕重建失败: ${it.message}") }
        }
    }

    /** 拉取评论数据（按 url 缓存；自动识别 bilibili XML 与 dandanplay JSON） */
    private fun loadComments(url: String): List<DanmakuComment> {
        cache[url]?.let { return it }
        val body = OkHttp.string(url)
        val comments = if (body.trimStart().startsWith("<")) parseXml(body) else parseJson(body)
        cache[url] = comments
        return comments
    }

    /** bilibili 格式：<d p="time,mode,size,color,...">text</d> */
    private fun parseXml(body: String): List<DanmakuComment> {
        val result = ArrayList<DanmakuComment>()
        val regex = Regex("""<d\s[^>]*p="([^"]*)"[^>]*>(.*?)</d>""", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(body).forEach { match ->
            val parts = match.groupValues[1].split(",")
            val text = unescape(match.groupValues[2]).trim()
            if (parts.size < 2 || text.isEmpty()) return@forEach
            val time = parts[0].toDoubleOrNull() ?: return@forEach
            val mode = parts[1].toIntOrNull() ?: 1
            // bilibili: 下标 3 为颜色；4 列以下按 dandanplay（下标 2）
            val colorIndex = if (parts.size >= 5) 3 else 2
            val color = parts.getOrNull(colorIndex)?.toIntOrNull()?.and(0xFFFFFF) ?: 0xFFFFFF
            result.add(DanmakuComment(time, text, mode, color))
        }
        return result
    }

    /** dandanplay 格式：{"comments":[{"p":"time,mode,color,uid","m":"text"}]} */
    private fun parseJson(body: String): List<DanmakuComment> {
        val result = ArrayList<DanmakuComment>()
        runCatching {
            val root = com.google.gson.JsonParser.parseString(body)
            val array = if (root.isJsonObject) root.asJsonObject.getAsJsonArray("comments") else root.asJsonArray
            array?.forEach { element ->
                val item = element.asJsonObject
                val parts = item.get("p")?.asString?.split(",") ?: return@forEach
                if (parts.size < 3) return@forEach
                val time = parts[0].toDoubleOrNull() ?: return@forEach
                val mode = parts[1].toIntOrNull() ?: 1
                val color = parts.getOrNull(2)?.toLongOrNull()?.and(0xFFFFFF)?.toInt() ?: 0xFFFFFF
                val text = item.get("m")?.asString?.trim() ?: return@forEach
                if (text.isNotEmpty()) result.add(DanmakuComment(time, text, mode, color))
            }
        }
        return result
    }

    /** 转换为 ASS 字幕文件（滚动 \move + 顶/底定位，车道防重叠） */
    private fun buildAssFile(comments: List<DanmakuComment>, key: String): File {
        val setting = DanmakuSettingSettings()
        val ass = AssBuilder.build(comments, setting)
        val file = File.createTempFile("fongmi-danmaku-$key-", ".ass")
        file.writeText(ass, Charsets.UTF_8)
        currentFile.getAndSet(file)?.delete()
        currentKey.set(key)
        return file
    }

    /** 当前设置快照（隔离 DanmakuSetting 静态读取，便于 ASS 构建统一取参） */
    class DanmakuSettingSettings {
        val durationSec: Double = DanmakuSetting.getDurationMs() / 1000.0
        val fixedDurationSec: Double = 5.0
        val timeOffsetSec: Double = DanmakuSetting.getTimeOffsetMs() / 1000.0
        val fontSize: Double = 44.0 * DanmakuSetting.getTextScale()
        val alpha: Int = ((DanmakuSetting.getTransparency()) * 255).toInt().coerceIn(0, 230)
        val bold: Boolean = DanmakuSetting.isTextBold()
        val showScroll: Boolean = DanmakuSetting.isShow()
        val shield: List<String> = DanmakuSetting.getShield().split("、", ",", "\n", "，").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

private fun unescape(text: String): String = text
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&#39;", "'")

/**
 * ASS 生成器：1280x720 画布；滚动弹幕车道分配（同车道按尾距间隔），顶/底弹幕固定定位
 */
private object AssBuilder {

    private const val RES_X = 1280.0
    private const val RES_Y = 720.0

    fun build(comments: List<DesktopDanmaku.DanmakuComment>, setting: DesktopDanmaku.DanmakuSettingSettings): String {
        val shielded = comments
            .filter { it.text.isNotEmpty() && setting.shield.none { word -> it.text.contains(word, ignoreCase = true) } }
            .sortedBy { it.time }
        val header = buildHeader(setting)
        val events = ArrayList<String>()
        val scrollLanes = ArrayList<ScrollLane>()
        val topLanes = ArrayList<FixedLane>()
        val bottomLanes = ArrayList<FixedLane>()

        val scrollArea = RES_Y * 0.75
        val lineHeight = setting.fontSize * 1.35
        val maxScrollLanes = (scrollArea / lineHeight).toInt().coerceAtLeast(1)
        val maxFixedLanes = 5

        for (comment in shielded) {
            val start = (comment.time + setting.timeOffsetSec).coerceAtLeast(0.0)
            val text = escape(comment.text)
            when (comment.mode) {
                1, 2, 3, 6 -> {
                    if (!setting.showScroll) continue
                    val width = textWidth(comment.text, setting.fontSize)
                    val lane = allocateScroll(scrollLanes, start, width, setting, maxScrollLanes) ?: continue
                    events.add(scrollEvent(comment, setting, start, lane * lineHeight + 4.0, width, text))
                }
                5 -> {
                    val lane = allocateFixed(topLanes, start, setting.fixedDurationSec, maxFixedLanes) ?: continue
                    events.add(fixedEvent(comment, setting, start, setting.fixedDurationSec, 8, lane * lineHeight + 4.0, text))
                }
                4 -> {
                    val lane = allocateFixed(bottomLanes, start, setting.fixedDurationSec, maxFixedLanes) ?: continue
                    events.add(fixedEvent(comment, setting, start, setting.fixedDurationSec, 2, RES_Y - lane * lineHeight - setting.fontSize - 8.0, text))
                }
                else -> continue
            }
        }
        return header + events.joinToString("\n") + "\n"
    }

    private class ScrollLane(var lastStart: Double = -1.0, var lastWidth: Double = 0.0)
    private class FixedLane(var until: Double = -1.0)

    /** 同车道复用条件：新弹幕入场时，上一条尾部已完全进入屏幕并留出间距 */
    private fun allocateScroll(lanes: MutableList<ScrollLane>, start: Double, width: Double, setting: DesktopDanmaku.DanmakuSettingSettings, maxLanes: Int): Int? {
        val gap = setting.fontSize * 0.6
        for (index in 0 until maxLanes) {
            if (index == lanes.size) {
                lanes.add(ScrollLane(start, width))
                return index
            }
            val lane = lanes[index]
            val freeTime = lane.lastStart + setting.durationSec * (lane.lastWidth + gap) / (RES_X + lane.lastWidth)
            if (start >= freeTime) {
                lane.lastStart = start
                lane.lastWidth = width
                return index
            }
        }
        return null
    }

    private fun allocateFixed(lanes: MutableList<FixedLane>, start: Double, durationSec: Double, maxLanes: Int): Int? {
        for (index in 0 until maxLanes) {
            if (index == lanes.size) {
                lanes.add(FixedLane(start + durationSec))
                return index
            }
            if (start >= lanes[index].until) {
                lanes[index].until = start + durationSec
                return index
            }
        }
        return null
    }

    private fun scrollEvent(comment: DesktopDanmaku.DanmakuComment, setting: DesktopDanmaku.DanmakuSettingSettings, start: Double, y: Double, width: Double, text: String): String {
        val end = start + setting.durationSec
        val fromX = RES_X + width
        val toX = -width
        val tags = "\\move(${"%.0f".format(fromX)},${"%.0f".format(y)},${"%.0f".format(toX)},${"%.0f".format(y)})"
        return dialogue(start, end, tags + colorTags(comment, setting) + text)
    }

    private fun fixedEvent(comment: DesktopDanmaku.DanmakuComment, setting: DesktopDanmaku.DanmakuSettingSettings, start: Double, durationSec: Double, alignment: Int, y: Double, text: String): String {
        val tags = "\\an$alignment\\pos(${"%.0f".format(RES_X / 2)},${"%.0f".format(y)})"
        return dialogue(start, start + durationSec, tags + colorTags(comment, setting) + text)
    }

    private fun colorTags(comment: DesktopDanmaku.DanmakuComment, setting: DesktopDanmaku.DanmakuSettingSettings): String {
        val rgb = "%06X".format(comment.color)
        // ASS 颜色为 &HBBGGRR&
        val assColor = rgb.substring(4, 6) + rgb.substring(2, 4) + rgb.substring(0, 2)
        val alpha = "%02X".format(setting.alpha)
        return "\\c&H$assColor&\\alpha&H$alpha&"
    }

    private fun dialogue(start: Double, end: Double, content: String): String {
        return "Dialogue: 0,${assTime(start)},${assTime(end)},Danmaku,,0,0,0,,{$content}"
    }

    private fun assTime(seconds: Double): String {
        val total = (seconds * 100).toInt().coerceAtLeast(0)
        val h = total / 360000
        val m = total % 360000 / 6000
        val s = total % 6000 / 100
        val cs = total % 100
        return "%d:%02d:%02d.%02d".format(h, m, s, cs)
    }

    private fun textWidth(text: String, fontSize: Double): Double {
        // 全角按 1 字宽、半角按 0.55 估算（libass 实际渲染略有出入，车道分配允许近似）
        var units = 0.0
        for (ch in text) units += if (ch.code > 0x2E80) 1.0 else 0.55
        return units * fontSize
    }

    private fun escape(text: String): String = text.replace("\\", "＼").replace("{", "｛").replace("}", "｝").replace("\n", " ")

    private fun buildHeader(setting: DesktopDanmaku.DanmakuSettingSettings): String {
        val bold = if (setting.bold) 1 else 0
        val fontSize = "%.0f".format(setting.fontSize)
        return """[Script Info]
ScriptType: v4.00+
PlayResX: ${RES_X.toInt()}
PlayResY: ${RES_Y.toInt()}
WrapStyle: 2
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Danmaku,Microsoft YaHei,$fontSize,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,$bold,0,0,0,100,100,0,0,1,1.6,0,7,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    }
}
