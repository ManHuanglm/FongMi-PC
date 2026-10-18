package com.fongmi.desktop

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.fongmi.android.tv.App
import com.fongmi.desktop.ui.AppModel
import com.fongmi.desktop.ui.DetailScreen
import com.fongmi.desktop.ui.HomeScreen

fun main(args: Array<String>) = application {
    App.init()
    val configUrl = args.firstOrNull { it.startsWith("http") || it.startsWith("file") } ?: System.getProperty("fongmi.config", "")
    Window(
        onCloseRequest = ::exitApplication,
        title = "FongMi TV PC",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        AppRoot(configUrl)
    }
}

/** 应用根：首页 ↔ 详情 两级导航 */
@Composable
private fun AppRoot(configUrl: String) {
    val model = remember { AppModel() }
    var detailVod by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedConfigLoader(configUrl) { model.loadConfig(it) }

    if (detailVod == null) {
        HomeScreen(model) { vod -> detailVod = model.homeSite().getKey() to vod.getId() }
    } else {
        DetailScreen(siteKey = detailVod!!.first, vodId = detailVod!!.second, onBack = { detailVod = null })
    }
}

@Composable
private fun LaunchedConfigLoader(configUrl: String, load: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(configUrl) {
        if (configUrl.isNotEmpty()) load(configUrl)
    }
}
