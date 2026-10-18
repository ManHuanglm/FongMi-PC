package com.fongmi.desktop.player

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File

/**
 * 桌面端 libmpv 播放器封装（对齐安卓 MpvUtil/MpvPlayer 语义）：
 * - wid 子窗口嵌入（传入 Canvas 原生句柄，d3d11 直绘）
 * - 事件模型：onFileLoaded（对齐 onPrepared）/ onEndFile（对齐 onCompletion/onError）/ onError
 * - 硬解 auto-safe；HTTP 头（UA/Referer）经 http-header-fields 注入
 * - libmpv-2.dll 缺失时 create() 抛 MpvUnavailableException，UI 给出明确提示（禁止无提示失败）
 */
class MpvPlayer private constructor(private val lib: MpvNative.MpvLibrary, private val ctx: Pointer, hwnd: Long, private val listener: Listener) {

    interface Listener {
        fun onFileLoaded()
        fun onEndFile(reason: Int, error: Int)
        fun onError(message: String)
        fun onLog(level: String, text: String) {}
    }

    class MpvUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {

        /** 运行时目录：开发期 desktop/runtime/mpv/；打包后随包 resources/mpv；安装目录 mpv；最后回退 %APPDATA%/FongMiTV/mpv */
        fun runtimeDirectory(): File {
            val fromWorkspace = File("runtime/mpv")
            if (fromWorkspace.resolve("libmpv-2.dll").isFile) return fromWorkspace
            // CMP 打包后资源目录（app/resources，由 appResourcesRootDir 注入 libmpv-2.dll）
            System.getProperty("compose.application.resources.dir")?.let { dir ->
                val fromPackage = File(dir, "mpv")
                if (fromPackage.resolve("libmpv-2.dll").isFile) return fromPackage
            }
            val fromInstall = File(System.getProperty("user.dir"), "mpv")
            if (fromInstall.resolve("libmpv-2.dll").isFile) return fromInstall
            return File(com.fongmi.desktop.compat.DesktopPaths.rootDir(), "mpv")
        }

        fun isRuntimeAvailable(): Boolean = runtimeDirectory().resolve("libmpv-2.dll").isFile

        fun runtimeHint(): String = "缺少 libmpv 运行时：请将 libmpv-2.dll 放入 ${runtimeDirectory().absolutePath}"

        fun create(hwnd: Long, listener: Listener): MpvPlayer {
            val dir = runtimeDirectory()
            if (!dir.resolve("libmpv-2.dll").isFile) throw MpvUnavailableException(runtimeHint())
            NativeLibrary.addSearchPath("libmpv-2", dir.absolutePath)
            val lib = try {
                MpvNative.load("libmpv-2")
            } catch (e: Throwable) {
                throw MpvUnavailableException("加载 libmpv-2.dll 失败（目录: ${dir.absolutePath}）：${e.message}", e)
            }
            return MpvPlayer(lib, lib.mpv_create(), hwnd, listener)
        }
    }

    @Volatile
    private var released = false

    private val eventThread: Thread

    init {
        with(lib) {
            // 渲染与解码（对齐安卓 mpvplayer：wid 子窗口 d3d11 直绘 + 硬解可由设置页关闭）
            // 注意：wid 嵌入不可设 vo=libmpv（那是 render API 模式，与 wid 互斥），走默认 gpu 即可
            // d3d11-flip=no：flip model 交换链会破坏 AWT/Compose 的窗口合成（整窗白屏），必须关闭
            mpv_set_option_string(ctx, "gpu-context", "d3d11")
            mpv_set_option_string(ctx, "d3d11-flip", "no")
            val hwdec = if (com.github.catvod.utils.Prefers.getBoolean("desktop_hwdec", true)) "auto-safe" else "no"
            mpv_set_option_string(ctx, "hwdec", hwdec)
            mpv_set_option_string(ctx, "keep-open", "no")
            // wid 必须在 initialize 前设置
            if (hwnd != 0L) mpv_set_option_string(ctx, "wid", hwnd.toString())
            mpv_request_log_messages(ctx, "warn")
            val code = mpv_initialize(ctx)
            if (code < 0) throw MpvUnavailableException("libmpv 初始化失败: ${MpvNative.errorString(lib, code)}")
        }
        // 倍速不变调（设置页可关）
        setPitchPreserving(com.github.catvod.utils.Prefers.getBoolean("desktop_pitch", true))
        eventThread = Thread({ eventLoop() }, "FongMi-Mpv-Events").apply { start() }
    }

    private fun eventLoop() {
        while (!released) {
            val event = lib.mpv_wait_event(ctx, 0.25)
            when (event.eventId) {
                MpvNative.EVENT_FILE_LOADED -> runCatching { listener.onFileLoaded() }
                MpvNative.EVENT_END_FILE -> handleEndFile(event)
                MpvNative.EVENT_SHUTDOWN -> return
                MpvNative.EVENT_LOG_MESSAGE -> runCatching {
                    event.data?.let { pointer ->
                        val log = MpvNative.MpvEventLogMessage(pointer)
                        listener.onLog(log.level, log.text)
                    }
                }
            }
        }
    }

    private fun handleEndFile(event: MpvNative.MpvEvent) {
        val pointer = event.data
        if (pointer == null) {
            runCatching { listener.onEndFile(MpvNative.END_FILE_EOF, 0) }
            return
        }
        runCatching {
            val end = MpvNative.MpvEventEndFile(pointer)
            if (end.reason == MpvNative.END_FILE_ERROR || end.error < 0) {
                listener.onError("播放失败: ${MpvNative.errorString(lib, end.error)}")
            } else {
                listener.onEndFile(end.reason, end.error)
            }
        }
    }

    // ==================== 播放控制（对齐 media3 Player 语义） ====================

    /** 加载媒体：headers 注入 UA/Referer（无逗号头安全）；startPosition 秒级断点续播 */
    fun load(url: String, headers: Map<String, String> = emptyMap(), startPosition: Long = 0, title: String? = null) {
        val headerText = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
        if (headerText.isNotEmpty()) lib.mpv_set_property_string(ctx, "http-header-fields", headerText)
        if (!title.isNullOrEmpty()) lib.mpv_set_property_string(ctx, "force-media-title", title)
        val options = if (startPosition > 0) "start=${startPosition}" else ""
        if (options.isEmpty()) lib.mpv_command(ctx, arrayOf("loadfile", url, "replace"))
        else lib.mpv_command(ctx, arrayOf("loadfile", url, "replace", "0", options))
    }

    fun seek(positionSec: Double) {
        lib.mpv_command(ctx, arrayOf("seek", positionSec.toString(), "absolute"))
    }

    fun seekBy(deltaSec: Double) {
        lib.mpv_command(ctx, arrayOf("seek", deltaSec.toString(), "relative"))
    }

    fun setPaused(paused: Boolean) {
        MpvNative.setFlag(lib, ctx, "pause", paused)
    }

    fun togglePause() {
        setPaused(!paused())
    }

    fun paused(): Boolean = MpvNative.getFlag(lib, ctx, "pause") ?: true

    fun position(): Double = MpvNative.getDouble(lib, ctx, "time-pos") ?: 0.0

    fun duration(): Double = MpvNative.getDouble(lib, ctx, "duration") ?: 0.0

    fun speed(): Double = MpvNative.getDouble(lib, ctx, "speed") ?: 1.0

    fun setSpeed(value: Double) {
        MpvNative.setDouble(lib, ctx, "speed", value)
    }

    fun volume(): Double = MpvNative.getDouble(lib, ctx, "volume") ?: 100.0

    fun setVolume(value: Double) {
        MpvNative.setDouble(lib, ctx, "volume", value)
    }

    fun muted(): Boolean = MpvNative.getFlag(lib, ctx, "mute") ?: false

    fun setMuted(muted: Boolean) {
        MpvNative.setFlag(lib, ctx, "mute", muted)
    }

    /** 轨道切换：property = aid / vid / sid，value = 轨道 id 字符串（"auto"/"no"/"1"...） */
    fun setTrack(property: String, value: String) {
        lib.mpv_set_property_string(ctx, property, value)
    }

    /** 加载外挂字幕/弹幕 ASS 文件并选中（占用主字幕轨；title 用于轨道列表显示） */
    fun loadSubFile(path: String, title: String, lang: String = "danmaku"): Boolean {
        val code = lib.mpv_command(ctx, arrayOf("sub-add", path, "select", title, lang))
        return code >= 0
    }

    /** 移除字幕轨（弹幕卸载/重建用；不传 id 时移除当前选中轨） */
    fun removeSub(id: Int? = null) {
        if (id == null) lib.mpv_command(ctx, arrayOf("sub-remove"))
        else lib.mpv_command(ctx, arrayOf("sub-remove", id.toString()))
    }

    /** 字幕时间偏移（秒，正数延后；弹幕时移用 sub-delay 实现） */
    fun setSubDelay(seconds: Double) {
        MpvNative.setDouble(lib, ctx, "sub-delay", seconds)
    }

    fun subDelay(): Double = MpvNative.getDouble(lib, ctx, "sub-delay") ?: 0.0

    /** 音频滤镜链（af 属性整体替换；空串恢复默认） */
    fun setAudioFilter(chain: String) {
        lib.mpv_set_property_string(ctx, "af", chain)
    }

    fun audioFilter(): String? {
        val pointer = lib.mpv_get_property_string(ctx, "af") ?: return null
        val text = pointer.getString(0)
        lib.mpv_free(pointer)
        return text
    }

    /** 倍速时是否保持音调（scaletempo2 变速不变调） */
    fun setPitchPreserving(enabled: Boolean) {
        lib.mpv_set_property_string(ctx, "audio-pitch", if (enabled) "yes" else "no")
    }

    /** track-list JSON（含 id/type/lang/title/codec），UI 直接解析 */
    fun trackListJson(): String? {
        val pointer = lib.mpv_get_property_string(ctx, "track-list") ?: return null
        val text = pointer.getString(0)
        lib.mpv_free(pointer)
        return text
    }

    fun stop() {
        lib.mpv_command(ctx, arrayOf("stop"))
    }

    fun release() {
        if (released) return
        released = true
        try {
            lib.mpv_command(ctx, arrayOf("quit"))
            eventThread.join(2000)
        } catch (ignored: Throwable) {
        }
        try {
            lib.mpv_terminate_destroy(ctx)
        } catch (ignored: Throwable) {
        }
    }
}
