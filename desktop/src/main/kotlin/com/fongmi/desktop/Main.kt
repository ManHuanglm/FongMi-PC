package com.fongmi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.fongmi.android.tv.App
import com.fongmi.desktop.ui.AppModel
import com.fongmi.desktop.ui.DetailScreen
import com.fongmi.desktop.ui.HomeScreen
import com.fongmi.desktop.ui.KeepScreen
import com.fongmi.desktop.ui.LiveModel
import com.fongmi.desktop.ui.LiveScreen
import com.fongmi.desktop.ui.SearchModel
import com.fongmi.desktop.ui.SearchScreen
import com.fongmi.desktop.ui.PlayerScreen
import com.fongmi.desktop.ui.PlayRequest
import com.fongmi.desktop.ui.SettingsScreen
import com.fongmi.desktop.ui.UiTheme

/** 主导航页签（详情/播放为覆盖层，不占页签位） */
private enum class Tab(val label: String) {
    HOME("首页"),
    LIVE("直播"),
    SEARCH("搜索"),
    KEEP("收藏"),
    CONFIG("设置"),
}

fun main(args: Array<String>) = application {
    App.init()
    UiTheme.applyThemeColor()
    val configUrl = args.firstOrNull { it.startsWith("http") || it.startsWith("file") } ?: System.getProperty("fongmi.config", "")
    val demoPlay = args.firstOrNull { it.startsWith("demo://") }?.let { demo ->
        if (demo == "demo://testsrc") "av://lavfi:testsrc=duration=120:size=1280x720:rate=30" else demo.removePrefix("demo://")
    }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = {
            com.fongmi.desktop.bridge.SnifferService.shutdown()
            exitApplication()
        },
        state = windowState,
        title = "FongMi TV PC"
    ) {
        AppRoot(configUrl, demoPlay, windowState)
    }
}

/** 应用根：主导航（首页/直播/搜索/收藏/配置）+ 详情/播放覆盖层 */
@Composable
private fun AppRoot(configUrl: String, demoPlay: String?, windowState: androidx.compose.ui.window.WindowState) {
    val appModel = remember { AppModel() }
    val liveModel = remember { LiveModel() }
    val searchModel = remember { SearchModel() }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var detailVod by remember { mutableStateOf<Pair<String, String>?>(null) }
    var playRequest by remember { mutableStateOf<PlayRequest?>(null) }
    // 从 AWT 根窗口列表定位 ComposeWindow（播放窗口需要它做 owner 与边界同步）
    var composeWindow by remember { mutableStateOf<androidx.compose.ui.awt.ComposeWindow?>(null) }
    LaunchedEffect(Unit) {
        composeWindow = java.awt.Window.getOwnerlessWindows().filterIsInstance<androidx.compose.ui.awt.ComposeWindow>().firstOrNull()
    }

    LaunchedEffect(configUrl) {
        if (configUrl.isNotEmpty()) appModel.loadConfig(configUrl)
    }

    LaunchedEffect(demoPlay) {
        if (demoPlay != null) {
            playRequest = PlayRequest(
                siteKey = "demo",
                vodId = "demo",
                vodName = "MPV Demo",
                vodPic = "",
                flagFlag = "",
                episodeName = "testsrc",
                episodeUrl = "",
                directUrl = demoPlay
            )
        }
    }

    // 直播播放请求由 LiveModel 统一产出（频道点击/换线路/回看）
    LaunchedEffect(liveModel.playRequest) {
        liveModel.playRequest?.let { playRequest = it }
    }

    // 局域网推送桥：手机端经 /action 推配置/设置/搜索到 PC（server 在配置加载时自启）
    LaunchedEffect(appModel, liveModel, searchModel) {
        com.fongmi.android.tv.server.Server.get().setListener(object : com.fongmi.android.tv.server.Server.Listener {
            override fun onPushConfig(url: String) {
                appModel.loadConfig(url)
            }

            override fun onSettingConfig(text: String, name: String) {
                val file = com.fongmi.desktop.compat.DesktopConfigStore.write(name.ifEmpty { "推送配置" }, text)
                appModel.loadConfig("file:///" + file.absolutePath.replace('\\', '/'))
            }

            override fun onSearch(word: String) {
                searchModel.keyword = word
                searchModel.search(word)
                tab = Tab.SEARCH
            }
        })
    }

    val current = playRequest
    val awtWindow = composeWindow
    when {
        current != null && awtWindow != null -> PlayerScreen(request = current, composeWindow = awtWindow, onBack = {
            playRequest = null
            liveModel.clearPlay()
        })
        detailVod != null -> DetailScreen(
            siteKey = detailVod!!.first,
            vodId = detailVod!!.second,
            onBack = { detailVod = null },
            onPlay = { flagFlag, episodeName, episodeUrl ->
                val home = appModel.homeSite()
                val vodId = detailVod!!.second
                playRequest = PlayRequest(
                    siteKey = home.getKey(),
                    vodId = vodId,
                    vodName = appModel.currentVodName ?: "",
                    vodPic = appModel.currentVodPic ?: "",
                    flagFlag = flagFlag,
                    episodeName = episodeName,
                    episodeUrl = episodeUrl
                )
            }
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            MainNavBar(tab) { tab = it }
            when (tab) {
                Tab.HOME -> HomeScreen(
                    model = appModel,
                    onOpenVod = { vod ->
                        appModel.currentVodName = vod.getName()
                        appModel.currentVodPic = vod.getPic()
                        // 首页/分类结果不绑定站点，统一归属首页源；搜索结果自带 siteKey
                        detailVod = appModel.homeSite().getKey() to vod.getId()
                    },
                    onOpenSearch = { tab = Tab.SEARCH },
                    onOpenKeep = { tab = Tab.KEEP }
                )
                Tab.LIVE -> LiveScreen(liveModel)
                Tab.SEARCH -> SearchScreen(searchModel) { vod ->
                    appModel.currentVodName = vod.getName()
                    appModel.currentVodPic = vod.getPic()
                    detailVod = vod.getSiteKey() to vod.getId()
                }
                Tab.KEEP -> KeepScreen { siteKey, vodId -> detailVod = siteKey to vodId }
                Tab.CONFIG -> SettingsScreen(appModel, liveModel) { tab = Tab.HOME }
            }
        }
    }
}

/** 顶部主导航栏 */
@Composable
private fun MainNavBar(active: Tab, onTab: (Tab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(UiTheme.Panel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "FongMi TV PC", color = UiTheme.Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(32.dp))
        Tab.entries.forEach { entry ->
            val selected = entry == active
            Box(
                modifier = Modifier
                    .background(if (selected) UiTheme.Accent else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onTab(entry) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = entry.label,
                    color = if (selected) Color.White else UiTheme.TextSub,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
