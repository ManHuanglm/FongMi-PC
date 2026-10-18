package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fongmi.android.tv.api.config.LiveConfig
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.api.config.WallConfig
import com.fongmi.android.tv.bean.Config
import com.fongmi.android.tv.setting.Setting
import com.github.catvod.net.OkHttp
import com.fongmi.desktop.compat.DesktopConfig
import com.fongmi.desktop.compat.DesktopPaths
import com.fongmi.desktop.compat.DesktopUpdater
import kotlinx.coroutines.launch
import javax.swing.JFileChooser

/** 对话框外壳：统一宽度/底色/标题/圆角 */
@Composable
private fun PrefsDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(UiTheme.Panel, RoundedCornerShape(10.dp))
                .border(1.dp, UiTheme.ChipBorder, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Text(text = title, color = UiTheme.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PrefsTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(text = placeholder, color = UiTheme.TextSub, fontSize = 13.sp) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedTextColor = UiTheme.TextMain,
            unfocusedTextColor = UiTheme.TextMain,
            focusedContainerColor = UiTheme.Chip,
            unfocusedContainerColor = UiTheme.Chip,
            cursorColor = UiTheme.Accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth().height(48.dp)
    )
}

/** 通用单选对话框（图片尺寸/DoH 等复用） */
@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    PrefsDialog(title = title, onDismiss = onDismiss) {
        Column {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(if (index == selected) UiTheme.Accent.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { onPick(index) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = option, color = if (index == selected) UiTheme.Accent else UiTheme.TextMain, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * 配置选择对话框（对齐原版 ConfigDialog/HistoryDialog）：
 * 历史列表加载/重命名/删除 + 添加订阅 + 本地导入；type 0 点播 / 1 直播 / 2 壁纸
 */
@Composable
fun ConfigPickerDialog(type: Int, appModel: AppModel, liveModel: LiveModel, onLoadVod: () -> Unit, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var url by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Config?>(null) }
    val title = when (type) {
        1 -> "直播配置"
        2 -> "壁纸配置"
        else -> "点播配置"
    }

    fun loadConfig(config: Config) {
        when (type) {
            1 -> liveModel.loadUrl(config.getUrl().orEmpty())
            2 -> {
                Setting.putWall(0)
                appModel.wall = 0
                WallConfig.get().config(config).load(object : com.fongmi.android.tv.impl.Callback() {
                    override fun success() {
                        onChanged()
                    }
                })
            }
            else -> {
                appModel.loadConfig(config.getUrl().orEmpty())
                onLoadVod()
            }
        }
        onDismiss()
    }

    PrefsDialog(title = title, onDismiss = onDismiss) {
        val configs = remember(version) { Config.getAll(type) }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrefsTextField(value = url, onValueChange = { url = it }, placeholder = "输入订阅 URL 或 AppleCMS 采集地址", modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                ChipButton(text = "导入") { importConfigFile(type) { version += 1 } }
                Spacer(modifier = Modifier.width(6.dp))
                ChipButton(text = "添加", selected = true, enabled = url.trim().isNotEmpty()) {
                    addConfig(url.trim(), type)
                    url = ""
                    version += 1
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (configs.isEmpty()) {
                Text(text = "暂无历史配置", color = UiTheme.TextSub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(configs, key = { "${it.getId()}-${it.getType()}" }) { config ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UiTheme.Bg, RoundedCornerShape(6.dp))
                                .border(1.dp, UiTheme.ChipBorder, RoundedCornerShape(6.dp))
                                .clickable { loadConfig(config) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = config.getName().orEmpty().ifEmpty { "未命名" }, color = if (config.getUrl() == currentConfigUrl(type)) UiTheme.Accent else UiTheme.TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = config.getUrl().orEmpty(), color = UiTheme.TextSub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ChipButton(text = "重命名") { editing = config }
                            Spacer(modifier = Modifier.width(6.dp))
                            ChipButton(text = "删除") {
                                Config.delete(config.getUrl(), config.getType())
                                version += 1
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { config ->
        RenameConfigDialog(config, onDismiss = { editing = null }) { version += 1; onChanged() }
    }
}

private fun currentConfigUrl(type: Int): String = when (type) {
    1 -> LiveConfig.getUrl()
    2 -> WallConfig.getUrl()
    else -> VodConfig.getUrl()
}

private fun addConfig(url: String, type: Int) {
    if (url.isEmpty()) return
    if (type == 0 && url.contains("api.php/provide/vod")) {
        DesktopConfig.resolve(url)
        return
    }
    Config.find(url, url.removePrefix("http://").removePrefix("https://").substringBefore('/').ifEmpty { "订阅源" }, type).save()
}

private fun importConfigFile(type: Int, onImported: () -> Unit) {
    val chooser = JFileChooser()
    chooser.dialogTitle = "选择本地配置文件"
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    val file = chooser.selectedFile ?: return
    val fileUrl = "file://" + file.absolutePath.replace('\\', '/')
    Config.find(fileUrl, file.nameWithoutExtension, type).save()
    onImported()
}

@Composable
private fun RenameConfigDialog(config: Config, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var name by remember(config.getId()) { mutableStateOf(config.getName().orEmpty()) }
    PrefsDialog(title = "重命名配置", onDismiss = onDismiss) {
        Column {
            PrefsTextField(value = name, onValueChange = { name = it }, placeholder = "配置名称")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ChipButton(text = "取消") { onDismiss() }
                Spacer(modifier = Modifier.width(8.dp))
                ChipButton(text = "保存", selected = true, enabled = name.trim().isNotEmpty()) {
                    config.setName(name.trim())
                    config.save()
                    onConfirm()
                    onDismiss()
                }
            }
        }
    }
}

/** 首页源选择（原版 SiteDialog 语义：切换点播首页源） */
@Composable
fun SitePickerDialog(appModel: AppModel, onDismiss: () -> Unit) {
    PrefsDialog(title = "选择首页源", onDismiss = onDismiss) {
        val sites = remember { VodConfig.get().getSites() }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sites, key = { it.getKey() }) { site ->
                val active = site.getKey() == appModel.homeSite().getKey()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) UiTheme.Accent.copy(alpha = 0.15f) else UiTheme.Bg, RoundedCornerShape(6.dp))
                        .clickable {
                            appModel.changeSite(site)
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(text = site.getName(), color = if (active) UiTheme.Accent else UiTheme.TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 首页直播选择（原版 LiveDialog 语义） */
@Composable
fun LivePickerDialog(liveModel: LiveModel, onDismiss: () -> Unit) {
    PrefsDialog(title = "选择直播源", onDismiss = onDismiss) {
        val lives = remember { LiveConfig.get().getLives() }
        if (lives.isEmpty()) {
            Text(text = "当前订阅未包含多直播源", color = UiTheme.TextSub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            return@PrefsDialog
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lives, key = { it.getName() }) { live ->
                val active = live.isSelected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) UiTheme.Accent.copy(alpha = 0.15f) else UiTheme.Bg, RoundedCornerShape(6.dp))
                        .clickable {
                            LiveConfig.get().setHome(live)
                            liveModel.refreshHome()
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(text = live.getName(), color = if (active) UiTheme.Accent else UiTheme.TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 播放设置（桌面 mpv 对应项：硬解 / 变速不变调） */
@Composable
fun PlayerSettingsDialog(onDismiss: () -> Unit) {
    var hwdec by remember { mutableStateOf(com.github.catvod.utils.Prefers.getBoolean("desktop_hwdec", true)) }
    var pitch by remember { mutableStateOf(com.github.catvod.utils.Prefers.getBoolean("desktop_pitch", true)) }
    PrefsDialog(title = "播放设置", onDismiss = onDismiss) {
        Column {
            SwitchRow(title = "硬件解码（重启播放生效）", value = hwdec) {
                com.github.catvod.utils.Prefers.put("desktop_hwdec", it)
                hwdec = it
            }
            SwitchRow(title = "倍速不变调", value = pitch) {
                com.github.catvod.utils.Prefers.put("desktop_pitch", it)
                pitch = it
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "提示：桌面端播放内核为 libmpv，与安卓 mpv 引擎同源", color = UiTheme.TextSub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SwitchRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = UiTheme.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .background(if (value) UiTheme.Accent else UiTheme.Chip, RoundedCornerShape(14.dp))
                .clickable { onChange(!value) }
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(text = if (value) "开" else "关", color = Color.White, fontSize = 13.sp)
        }
    }
}

/** 弹幕设置（Compose 版，偏好键与安卓一致） */
@Composable
fun DanmakuPrefsDialog(onDismiss: () -> Unit) {
    var apiUrl by remember { mutableStateOf(com.fongmi.android.tv.setting.DanmakuSetting.getApiUrl()) }
    var auto by remember { mutableStateOf(com.fongmi.android.tv.setting.DanmakuSetting.isAuto()) }
    var load by remember { mutableStateOf(com.fongmi.android.tv.setting.DanmakuSetting.isLoad()) }
    var scale by remember { mutableStateOf((com.fongmi.android.tv.setting.DanmakuSetting.getTextScale() * 100).toInt()) }
    var alpha by remember { mutableStateOf((com.fongmi.android.tv.setting.DanmakuSetting.getTransparency() * 100).toInt()) }
    var duration by remember { mutableStateOf((com.fongmi.android.tv.setting.DanmakuSetting.getDurationMs() / 1000).toInt()) }
    var offset by remember { mutableStateOf((com.fongmi.android.tv.setting.DanmakuSetting.getTimeOffsetMs() / 1000).toInt()) }
    var shield by remember { mutableStateOf(com.fongmi.android.tv.setting.DanmakuSetting.getShield()) }

    PrefsDialog(title = "弹幕设置", onDismiss = onDismiss) {
        Column {
            SwitchRow(title = "播放时加载弹幕", value = load) {
                com.fongmi.android.tv.setting.DanmakuSetting.putLoad(it)
                load = it
            }
            SwitchRow(title = "自动匹配弹幕", value = auto) {
                com.fongmi.android.tv.setting.DanmakuSetting.putAuto(it)
                auto = it
            }
            Spacer(modifier = Modifier.height(6.dp))
            PrefsTextField(value = apiUrl, onValueChange = { apiUrl = it }, placeholder = "弹幕接口地址（含 {name}/{episode}）")
            Spacer(modifier = Modifier.height(8.dp))
            ValueSlider("字号", "${scale / 100.0}x", scale, 50, 300) { scale = it }
            ValueSlider("不透明度", "${100 - alpha}%", alpha, 0, 90) { alpha = it }
            ValueSlider("滚动时长", "${duration}s", duration, 3, 15) { duration = it }
            ValueSlider("时间偏移", "${offset}s", offset, -60, 60) { offset = it }
            Spacer(modifier = Modifier.height(6.dp))
            PrefsTextField(value = shield, onValueChange = { shield = it }, placeholder = "屏蔽词（顿号/逗号分隔）")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ChipButton(text = "取消") { onDismiss() }
                Spacer(modifier = Modifier.width(8.dp))
                ChipButton(text = "保存", selected = true) {
                    com.fongmi.android.tv.setting.DanmakuSetting.putApiUrl(apiUrl.trim())
                    com.fongmi.android.tv.setting.DanmakuSetting.putAuto(auto)
                    com.fongmi.android.tv.setting.DanmakuSetting.putTextScale(scale / 100f)
                    com.fongmi.android.tv.setting.DanmakuSetting.putTransparency(alpha / 100f)
                    com.fongmi.android.tv.setting.DanmakuSetting.putDurationMs(duration * 1000L)
                    com.fongmi.android.tv.setting.DanmakuSetting.putTimeOffsetMs(offset * 1000L)
                    com.fongmi.android.tv.setting.DanmakuSetting.putShield(shield.trim())
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ValueSlider(label: String, valueText: String, value: Int, from: Int, to: Int, onChange: (Int) -> Unit) {
    var current by remember(value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = UiTheme.TextSub, fontSize = 12.sp, modifier = Modifier.width(70.dp))
        androidx.compose.material3.Slider(
            value = current.toFloat(),
            onValueChange = { current = it.toInt() },
            onValueChangeFinished = { onChange(current) },
            valueRange = from.toFloat()..to.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = UiTheme.Accent, activeTrackColor = UiTheme.Accent, inactiveTrackColor = UiTheme.Chip),
            modifier = Modifier.weight(1f).height(26.dp)
        )
        Text(text = valueText, color = UiTheme.TextMain, fontSize = 12.sp, modifier = Modifier.width(56.dp))
    }
}

/** 主题色彩（关 + 预设色板，持久化 theme_color） */
@Composable
fun ThemePrefsDialog(onDismiss: () -> Unit, onPicked: () -> Unit) {
    val current = Setting.getThemeColor()
    PrefsDialog(title = "主题色彩", onDismiss = onDismiss) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        com.github.catvod.utils.Prefers.put("theme_color", -1)
                        UiTheme.accentState = Color(0xFF3D8BFF)
                        onPicked()
                        onDismiss()
                    }
                    .padding(vertical = 10.dp)
            ) {
                Text(text = "关（默认蓝）", color = if (current == -1) UiTheme.Accent else UiTheme.TextMain, fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                UiTheme.ThemePresets.forEach { (name, argb) ->
                    val active = argb == current
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .background(if (active) UiTheme.Chip else Color.Transparent, RoundedCornerShape(8.dp))
                            .border(1.dp, if (active) UiTheme.Accent else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable {
                                UiTheme.setThemeColor(argb)
                                onPicked()
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.width(24.dp).height(24.dp).background(Color(argb), CircleShape))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = name, color = UiTheme.TextSub, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** DoH 选择（系统 + 预设公共 DNS，经 OkHttp.dns().setDoh 生效） */
@Composable
fun DohPrefsDialog(onDismiss: () -> Unit) {
    data class DohOption(val name: String, val url: String)

    val options = remember {
        listOf(
            DohOption("系统", ""),
            DohOption("Cloudflare", "https://cloudflare-dns.com/dns-query"),
            DohOption("Google", "https://dns.google/dns-query"),
            DohOption("阿里", "https://dns.alidns.com/dns-query"),
        )
    }
    val saved = Setting.getDoh()
    val selected = options.indexOfFirst { it.url.isNotEmpty() && saved.contains(it.url) }.let { if (saved.isEmpty()) 0 else it.coerceAtLeast(0) }
    PrefsDialog(title = "DoH（DNS over HTTPS）", onDismiss = onDismiss) {
        Column {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(if (index == selected) UiTheme.Accent.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable {
                            val doh = com.github.catvod.bean.Doh().name(option.name).url(option.url)
                            OkHttp.dns().setDoh(doh)
                            Setting.putDoh(if (option.url.isEmpty()) "" else doh.toString())
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = option.name, color = if (index == selected) UiTheme.Accent else UiTheme.TextMain, fontSize = 14.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = option.url.ifEmpty { "跟随系统 DNS" }, color = UiTheme.TextSub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 备份/恢复（Backup bean 全表 + 偏好导出导入） */
@Composable
fun BackupRestoreDialog(onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    PrefsDialog(title = "备份 / 恢复", onDismiss = onDismiss) {
        Column {
            Text(
                text = "备份包含：站点/直播/收藏/配置/历史与全部偏好设置；恢复后自动重新加载配置。",
                color = UiTheme.TextSub,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ChipButton(text = "导出备份") {
                    val chooser = javax.swing.JFileChooser()
                    chooser.dialogTitle = "选择备份保存位置"
                    chooser.selectedFile = java.io.File("fongmi-backup.json")
                    if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val file = chooser.selectedFile
                        runCatching {
                            file.writeText(com.fongmi.android.tv.bean.Backup.create().toString(), Charsets.UTF_8)
                            onMessage("备份已导出: ${file.absolutePath}")
                            onDismiss()
                        }.onFailure { onMessage("备份失败: ${it.message}") }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                ChipButton(text = "导入恢复", selected = true) {
                    val chooser = javax.swing.JFileChooser()
                    chooser.dialogTitle = "选择备份文件"
                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val file = chooser.selectedFile
                        AppModel.scope.launch {
                            runCatching {
                                val json = file.readText(Charsets.UTF_8)
                                com.fongmi.android.tv.bean.Backup.objectFrom(json).restore()
                                // 恢复后重载配置链路（与原版 initConfig 一致）
                                VodConfig.get().init().load(object : com.fongmi.android.tv.impl.Callback() {
                                    override fun success() {}
                                })
                                LiveConfig.get().init().load()
                            }.onSuccess {
                                onMessage("恢复完成，配置已重新加载")
                                onDismiss()
                            }.onFailure { onMessage("恢复失败: ${it.message}") }
                        }
                    }
                }
            }
        }
    }
}
