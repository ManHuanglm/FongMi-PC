package com.fongmi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.fongmi.android.tv.App
import com.fongmi.desktop.ui.AppModel
import com.fongmi.desktop.ui.DetailScreen
import com.fongmi.desktop.ui.HomeScreen
import com.fongmi.desktop.ui.PlayerScreen
import com.fongmi.desktop.ui.PlayRequest

fun main(args: Array<String>) = application {
    App.init()
    val configUrl = args.firstOrNull { it.startsWith("http") || it.startsWith("file") } ?: System.getProperty("fongmi.config", "")
    val demoPlay = args.firstOrNull { it.startsWith("demo://") }?.let { demo ->
        if (demo == "demo://testsrc") "av://lavfi:testsrc=duration=15:size=1280x720:rate=30" else demo.removePrefix("demo://")
    }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "FongMi TV PC",
        state = windowState
    ) {
        AppRoot(configUrl, demoPlay, windowState)
    }
}

/** 应用根：首页 ↔ 详情 ↔ 播放 三级导航 */
@Composable
private fun AppRoot(configUrl: String, demoPlay: String?, windowState: androidx.compose.ui.window.WindowState) {
    val model = remember { AppModel() }
    var detailVod by remember { mutableStateOf<Pair<String, String>?>(null) }
    var playRequest by remember { mutableStateOf<PlayRequest?>(null) }

    LaunchedEffect(configUrl) {
        if (configUrl.isNotEmpty()) model.loadConfig(configUrl)
    }

    LaunchedEffect(demoPlay) {
        if (demoPlay != null) {
            playRequest = PlayRequest(siteKey = "demo", vodId = "demo", vodName = "MPV Demo", vodPic = "", flagFlag = "", episodeName = "testsrc", episodeUrl = "", directUrl = demoPlay)
        }
    }

    val current = playRequest
    when {
        current != null -> PlayerScreen(request = current, windowState = windowState, onBack = { playRequest = null })
        detailVod != null -> DetailScreen(
            siteKey = detailVod!!.first,
            vodId = detailVod!!.second,
            onBack = { detailVod = null },
            onPlay = { flagFlag, episodeName, episodeUrl ->
                val home = model.homeSite()
                val vodId = detailVod!!.second
                playRequest = PlayRequest(
                    siteKey = home.getKey(),
                    vodId = vodId,
                    vodName = model.currentVodName ?: "",
                    vodPic = model.currentVodPic ?: "",
                    flagFlag = flagFlag,
                    episodeName = episodeName,
                    episodeUrl = episodeUrl
                )
            }
        )
        else -> HomeScreen(model) { vod ->
            model.currentVodName = vod.getName()
            model.currentVodPic = vod.getPic()
            detailVod = model.homeSite().getKey() to vod.getId()
        }
    }
}
