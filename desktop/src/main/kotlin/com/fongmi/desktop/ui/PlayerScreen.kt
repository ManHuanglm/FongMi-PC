package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.desktop.player.DanmakuSettingsDialog
import com.fongmi.desktop.player.DesktopDanmaku
import com.fongmi.desktop.player.HwndResolver
import com.fongmi.desktop.player.MpvPlayer
import com.fongmi.desktop.player.PlayerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Window

private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 播放页：视频与控制条承载于独立 Swing 窗口（PlayerFrame，owner 为主窗口）
 * 说明：CMP 的 SwingPanel interop 在本机存在渲染遮挡问题（Compose surface 盖住 AWT 层），独立窗口方案绕开该限制
 * Compose 侧仅显示加载/错误状态；PlayerFrame 的 bounds 跟随主窗口
 * 弹幕：ASS 轨道挂载（libass 渲染），由 DanmakuSetting 开关/自动匹配驱动
 */
@Composable
fun PlayerScreen(request: PlayRequest, composeWindow: Window, onBack: () -> Unit) {
    val model = remember { PlayerModel() }
    var player by remember { mutableStateOf<MpvPlayer?>(null) }
    var frame by remember { mutableStateOf<PlayerFrame?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var danmakuOn by remember { mutableStateOf(false) }

    fun danmakuStatus(text: String) {
        javax.swing.SwingUtilities.invokeLater { frame?.panel()?.setDanmakuStatus(text) }
    }

    fun toggleDanmaku() {
        val mpv = player ?: return
        danmakuOn = !danmakuOn
        frame?.panel()?.setDanmakuEnabled(danmakuOn)
        if (danmakuOn) DesktopDanmaku.attach(mpv, request.vodName, request.episodeName, ::danmakuStatus)
        else DesktopDanmaku.detach(mpv)
    }

    fun openDanmakuSettings() {
        val currentFrame = frame ?: return
        val mpv = player ?: return
        DanmakuSettingsDialog(currentFrame) {
            if (danmakuOn) DesktopDanmaku.refresh(mpv, request.vodName, request.episodeName, ::danmakuStatus)
        }.isVisible = true
    }

    DisposableEffect(request) {
        val playerFrame = PlayerFrame(
            owner = composeWindow,
            onBack = {
                model.saveProgress(request, player?.position()?.toLong() ?: 0L, player?.duration()?.toLong() ?: 0L)
                player?.release()
                player = null
                onBack()
            },
            onToggleDanmaku = ::toggleDanmaku,
            onOpenDanmakuSettings = ::openDanmakuSettings
        )
        frame = playerFrame
        playerFrame.setVisible(true)
        onDispose {
            model.saveProgress(request, player?.position()?.toLong() ?: 0L, player?.duration()?.toLong() ?: 0L)
            player?.release()
            player = null
            playerFrame.dispose()
        }
    }

    LaunchedEffect(request) {
        loading = true
        error = ""
        model.resolve(request) { url, start, headers ->
            playerScope.launch {
                val hwnd = awaitCanvasHwnd()
                val created = try {
                    MpvPlayer.create(hwnd, playerListener())
                } catch (e: Exception) {
                    error = e.message ?: "播放器创建失败"
                    null
                }
                if (created == null) {
                    loading = false
                    if (hwnd == 0L) error = "视频画布未就绪"
                    return@launch
                }
                player = created
                javax.swing.SwingUtilities.invokeLater {
                    frame?.attachPlayer(created)
                    created.load(url, headers, start, model.title)
                }
                loading = false
                // 直播无弹幕；点播按设置自动挂载
                if (!request.isLive) DesktopDanmaku.autoAttach(created, request.vodName, request.episodeName, ::danmakuStatus)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF10141A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(color = Color(0xFF3D8BFF))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "正在准备播放...", color = Color(0xFF98A2B0), fontSize = 14.sp)
            }
            if (error.isNotEmpty()) {
                Text(text = error, color = Color(0xFFE06A6A), fontSize = 14.sp)
            }
            if (model.notice.isNotEmpty()) {
                Text(text = model.notice, color = Color(0xFF98A2B0), fontSize = 14.sp)
            }
        }
    }
}

private fun playerListener() = object : MpvPlayer.Listener {
    override fun onFileLoaded() {
        println("[Player] mpv 文件装载完成，开始渲染")
    }

    override fun onEndFile(reason: Int, error: Int) {
        println("[Player] 播放结束 reason=$reason error=$error")
    }

    override fun onError(message: String) {
        println("[Player] $message")
    }
}

/** 轮询等待播放窗口的 Canvas 原生句柄（PlayerFrame 显示后才有），最多 6 秒 */
private suspend fun awaitCanvasHwnd(): Long {
    repeat(30) {
        HwndResolver.findCanvasHwnd("FongMiPlayer")?.let { return it }
        delay(200)
    }
    return 0L
}
