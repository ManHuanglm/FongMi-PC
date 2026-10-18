package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.fongmi.android.tv.api.config.LiveConfig
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.api.config.WallConfig
import com.fongmi.android.tv.setting.PlayerSetting
import com.fongmi.android.tv.setting.Setting
import com.fongmi.desktop.compat.DesktopUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VERSION = "1.0.0"

/** 布局常量：与原版设置页一致（左标题右值，配置行带双图标按钮） */
private val RowShape = RoundedCornerShape(6.dp)

/**
 * 设置页：布局对齐原版 FongMi 设置（点播/直播/壁纸 + 功能项），
 * 全部功能桌面端落地：配置选择/首页源切换/壁纸预设、播放与弹幕设置、
 * 无痕模式、图片尺寸、主题色彩、DoH、缓存清理、备份恢复、版本更新
 */
@Composable
fun SettingsScreen(appModel: AppModel, liveModel: LiveModel, onLoadVod: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var cacheSize by remember { mutableStateOf("计算中...") }
    var configDialog by remember { mutableStateOf<Int?>(null) }
    var siteDialog by remember { mutableStateOf(false) }
    var liveDialog by remember { mutableStateOf(false) }
    var playerDialog by remember { mutableStateOf(false) }
    var danmakuDialog by remember { mutableStateOf(false) }
    var sizeDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var dohDialog by remember { mutableStateOf(false) }
    var backupDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun show(text: String) {
        message = text
    }

    // 壁纸自定义地址（type=2 配置 JSON 中的 wallpaper 字段）
    LaunchedEffect(version) {
        runCatching {
            appModel.wallUrl = parseWallpaper(WallConfig.get().getConfig().getJson())
            cacheSize = withContext(Dispatchers.IO) { cacheSizeText() }
        }.onFailure { it.printStackTrace() }
    }

    Column(modifier = Modifier.fillMaxSize().background(UiTheme.Bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(text = "设置", color = UiTheme.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        if (message.isNotEmpty()) {
            Text(text = message, color = Color(0xFFD9A44A), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }

        // ===== 配置区（点播/直播/壁纸，对齐原版：URL + 主页/历史按钮） =====
        ConfigRow(
            title = "点播",
            url = VodConfig.getDesc(),
            homeIcon = "🏠",
            historyIcon = "🕘",
            onHome = { siteDialog = true },
            onHistory = { configDialog = 0 },
            onClick = { configDialog = 0 }
        )
        ConfigRow(
            title = "直播",
            url = LiveConfig.getDesc(),
            homeIcon = "🏠",
            historyIcon = "🕘",
            onHome = { liveDialog = true },
            onHistory = { configDialog = 1 },
            onClick = { configDialog = 1 }
        )
        ConfigRow(
            title = "壁纸",
            url = WallConfig.getDesc().ifEmpty { "默认壁纸" },
            homeIcon = "🖼",
            historyIcon = "🔄",
            onHome = {
                // 原版语义：内置壁纸轮换（1→4 循环）
                val next = if (Setting.getWall() >= 4 || Setting.getWall() == 0) 1 else Setting.getWall() + 1
                Setting.putWall(next)
                Setting.putWallType(0)
                appModel.wall = next
            },
            onHistory = {
                // 原版语义：恢复自定义壁纸配置
                Setting.putWall(0)
                appModel.wall = 0
                configDialog = 2
            },
            onClick = { configDialog = 2 }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ===== 功能区 =====
        SettingRow(title = "播放设置") { playerDialog = true }
        SettingRow(title = "弹幕设置") { danmakuDialog = true }
        SettingRow(title = "无痕模式", value = if (Setting.isIncognito()) "开" else "关") {
            Setting.putIncognito(!Setting.isIncognito())
            version += 1
        }
        SettingRow(title = "图片尺寸", value = sizeLabel(PlayerSetting.getSize())) { sizeDialog = true }
        SettingRow(title = "主题色彩", value = themeLabel()) { themeDialog = true }
        SettingRow(title = "DoH", value = dohLabel()) { dohDialog = true }
        SettingRow(title = "缓存", value = cacheSize) {
            AppModel.scope.launch {
                runCatching {
                    com.fongmi.desktop.compat.DesktopPaths.cacheDir().listFiles()?.forEach { it.deleteRecursively() }
                }
                cacheSize = withContext(Dispatchers.IO) { cacheSizeText() }
                message = "缓存已清理"
            }
        }
        SettingRow(title = "备份", value = "恢复") { backupDialog = true }
        SettingRow(title = "版本", value = VERSION) {
            message = "正在检查更新..."
            AppModel.scope.launch {
                val info = DesktopUpdater.check()
                if (info.version.isEmpty()) message = info.notes
                else {
                    message = "官方最新: ${info.version}，正在打开下载页"
                    DesktopUpdater.open(info.page)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ===== 对话框 =====
    configDialog?.let { type ->
        ConfigPickerDialog(
            type = type,
            appModel = appModel,
            liveModel = liveModel,
            onLoadVod = onLoadVod,
            onDismiss = { configDialog = null },
            onChanged = { version += 1 }
        )
    }
    if (siteDialog) SitePickerDialog(appModel, onDismiss = { siteDialog = false })
    if (liveDialog) LivePickerDialog(liveModel, onDismiss = { liveDialog = false })
    if (playerDialog) PlayerSettingsDialog(onDismiss = { playerDialog = false })
    if (danmakuDialog) DanmakuPrefsDialog(onDismiss = { danmakuDialog = false })
    if (sizeDialog) ChoiceDialog(title = "图片尺寸", options = listOf("小", "中", "大", "特大"), selected = PlayerSetting.getSize(), onDismiss = { sizeDialog = false }) { which ->
        PlayerSetting.putSize(which)
        appModel.posterSize = which
        sizeDialog = false
    }
    if (themeDialog) ThemePrefsDialog(onDismiss = { themeDialog = false }, onPicked = { version += 1 })
    if (dohDialog) DohPrefsDialog(onDismiss = { dohDialog = false })
    if (backupDialog) BackupRestoreDialog(onDismiss = { backupDialog = false }, onMessage = { show(it); version += 1 })
}

@Composable
private fun ConfigRow(title: String, url: String, homeIcon: String, historyIcon: String, onHome: () -> Unit, onHistory: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(UiTheme.Panel, RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = UiTheme.TextMain, fontSize = 14.sp, modifier = Modifier.width(44.dp))
        Text(text = url.ifEmpty { "未配置" }, color = UiTheme.TextSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        IconChip(homeIcon, onHome)
        Spacer(modifier = Modifier.width(6.dp))
        IconChip(historyIcon, onHistory)
    }
}

@Composable
private fun IconChip(icon: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(UiTheme.Chip, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, fontSize = 14.sp)
    }
}

@Composable
private fun SettingRow(title: String, value: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(UiTheme.Panel, RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = UiTheme.TextMain, fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(text = value, color = UiTheme.TextSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ==================== 取值辅助 ====================

fun sizeLabel(size: Int): String = listOf("小", "中", "大", "特大").getOrElse(size) { "大" }

fun themeLabel(): String {
    val color = Setting.getThemeColor()
    if (color == -1) return "关"
    return UiTheme.ThemePresets.entries.firstOrNull { it.value == color }?.key ?: "自定义"
}

fun dohLabel(): String {
    val saved = Setting.getDoh()
    if (saved.isEmpty()) return "系统"
    return com.github.catvod.bean.Doh.objectFrom(saved).getName().ifEmpty { "系统" }
}

/** 壁纸配置 JSON 中的 wallpaper 字段（getJson 可能为 null，须可空入参） */
fun parseWallpaper(json: String?): String {
    if (json.isNullOrEmpty()) return ""
    return runCatching {
        val root = com.google.gson.JsonParser.parseString(json)
        if (root.isJsonObject) root.asJsonObject.get("wallpaper")?.asString ?: "" else ""
    }.getOrDefault("")
}

fun cacheSizeText(): String {
    val dir = com.fongmi.desktop.compat.DesktopPaths.cacheDir()
    val bytes = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    return "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
