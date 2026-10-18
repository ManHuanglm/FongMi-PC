package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.fongmi.android.tv.bean.Config
import com.fongmi.desktop.compat.DesktopConfig
import com.fongmi.desktop.compat.DesktopUpdater
import kotlinx.coroutines.launch
import javax.swing.JFileChooser

/**
 * 配置管理页：订阅列表增删改查 + 本地文件导入 + 一键加载
 * type=0 点播订阅 / type=1 直播订阅（与安卓 Config 表复用同一份数据）
 */
@Composable
fun ConfigScreen(appModel: AppModel, liveModel: LiveModel, onLoadVod: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Config?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(UiTheme.Bg)) {
        ConfigHeader(
            onRefresh = { version += 1 },
            onImport = { version += 1 }
        )
        val vodConfigs = remember(version) { Config.getAll(0) }
        val liveConfigs = remember(version) { Config.getAll(1) }
        if (vodConfigs.isEmpty() && liveConfigs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无配置记录\n可在上方添加订阅或导入本地文件", color = UiTheme.TextSub, fontSize = 14.sp, lineHeight = 22.sp)
            }
            return
        }
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ConfigSection("直播订阅", liveConfigs, onChanged = { version += 1 }, onRename = { editing = it }, onLoad = { liveModel.loadUrl(it.getUrl()) })
            ConfigSection("点播订阅", vodConfigs, onChanged = { version += 1 }, onRename = { editing = it }, onLoad = {
                appModel.loadConfig(it.getUrl())
                onLoadVod()
            })
        }
    }
    editing?.let { config ->
        RenameDialog(
            config = config,
            onDismiss = { editing = null },
            onConfirm = { version += 1 }
        )
    }
}

/** 头部：添加输入行 + 导入按钮 */
@Composable
private fun ConfigHeader(onRefresh: () -> Unit, onImport: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(0) }
    var updateText by remember { mutableStateOf("") }
    var updatePage by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().background(UiTheme.Panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "配置管理", color = UiTheme.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            if (updateText.isNotEmpty()) {
                Text(text = updateText, color = UiTheme.TextSub, fontSize = 12.sp, modifier = Modifier.clickable(enabled = updatePage.isNotEmpty()) { DesktopUpdater.open(updatePage) })
                Spacer(modifier = Modifier.width(10.dp))
            }
            ChipButton(text = "检查更新") {
                updateText = "检查中..."
                AppModel.scope.launch {
                    val info = DesktopUpdater.check()
                    updateText = if (info.version.isEmpty()) info.notes else "官方最新: ${info.version}（点击下载）"
                    updatePage = info.page
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ChipButton(text = "导入本地文件") { importLocalFile(onImport) }
            Spacer(modifier = Modifier.width(8.dp))
            ChipButton(text = "刷新") { onRefresh() }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(text = "输入订阅 URL（JSON/TXT/M3U）或 AppleCMS 采集地址", color = UiTheme.TextSub, fontSize = 13.sp) },
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
                modifier = Modifier.weight(1f).height(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            ChipButton(text = "直播", selected = type == 1) { type = 1 }
            Spacer(modifier = Modifier.width(6.dp))
            ChipButton(text = "点播", selected = type == 0) { type = 0 }
            Spacer(modifier = Modifier.width(8.dp))
            ChipButton(
                text = "添加",
                selected = true,
                enabled = url.trim().isNotEmpty()
            ) {
                addConfig(url.trim(), type)
                url = ""
                onRefresh()
            }
        }
    }
}

/** 添加订阅（AppleCMS 采集地址走 DesktopConfig 包装为单站点点播配置） */
private fun addConfig(url: String, type: Int) {
    if (url.isEmpty()) return
    if (type == 0 && url.contains("api.php/provide/vod")) {
        DesktopConfig.resolve(url)
        return
    }
    Config.find(url, displayName(url), type).save()
}

/** 本地文件导入：同一文件同时登记为点播与直播两类配置，按需加载 */
private fun importLocalFile(onImport: () -> Unit) {
    val chooser = JFileChooser()
    chooser.dialogTitle = "选择本地配置文件"
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    val file = chooser.selectedFile ?: return
    val fileUrl = "file://" + file.absolutePath.replace('\\', '/')
    Config.find(fileUrl, file.nameWithoutExtension, 0).save()
    Config.find(fileUrl, file.nameWithoutExtension, 1).save()
    onImport()
}

private fun displayName(url: String): String {
    return runCatching { url.removePrefix("http://").removePrefix("https://").substringBefore('/') }
        .getOrDefault("订阅源")
        .ifEmpty { "订阅源" }
}

/** 分区列表（点播/直播各自成组） */
private fun LazyListScope.ConfigSection(
    title: String,
    items: List<Config>,
    onChanged: () -> Unit,
    onRename: (Config) -> Unit,
    onLoad: (Config) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "$title-header") {
        Text(text = title, color = UiTheme.TextSub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
    items(items, key = { "${title}-${it.getId()}-${it.getType()}" }) { config ->
        ConfigRow(config, onChanged, onRename, onLoad)
    }
}

@Composable
private fun ConfigRow(config: Config, onChanged: () -> Unit, onRename: (Config) -> Unit, onLoad: (Config) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiTheme.Panel, RoundedCornerShape(6.dp))
            .border(1.dp, UiTheme.ChipBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = config.getName().ifEmpty { "未命名" }, color = UiTheme.TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = config.getUrl(), color = UiTheme.TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        ChipButton(text = "加载", selected = true) { onLoad(config) }
        Spacer(modifier = Modifier.width(6.dp))
        ChipButton(text = "重命名") { onRename(config) }
        Spacer(modifier = Modifier.width(6.dp))
        ChipButton(text = "删除") {
            Config.delete(config.getUrl(), config.getType())
            onChanged()
        }
    }
}

@Composable
private fun RenameDialog(config: Config, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var name by remember(config.getId()) { mutableStateOf(config.getName()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(UiTheme.Panel, RoundedCornerShape(10.dp))
                .border(1.dp, UiTheme.ChipBorder, RoundedCornerShape(10.dp))
                .padding(20.dp)
        ) {
            Text(text = "重命名订阅", color = UiTheme.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
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
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ChipButton(text = "取消") { onDismiss() }
                Spacer(modifier = Modifier.width(8.dp))
                ChipButton(
                    text = "保存",
                    selected = true,
                    enabled = name.trim().isNotEmpty()
                ) {
                    config.setName(name.trim())
                    config.save()
                    onConfirm()
                    onDismiss()
                }
            }
        }
    }
}
