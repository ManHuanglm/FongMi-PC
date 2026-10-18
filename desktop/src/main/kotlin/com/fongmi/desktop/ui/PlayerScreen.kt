package com.fongmi.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.fongmi.desktop.player.HwndResolver
import com.fongmi.desktop.player.MpvPlayer
import com.fongmi.desktop.player.PlayerPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import javax.swing.SwingUtilities

private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val WINDOW_TITLE = "FongMi TV PC"

/**
 * 播放页：SwingPanel 承载 PlayerPanel（Canvas + 控制条）
 * 挂载时定位 Canvas HWND → 创建 libmpv（wid 嵌入）→ 加载播放地址；卸载时保存进度并释放
 */
@Composable
fun PlayerScreen(request: PlayRequest, windowState: WindowState, onBack: () -> Unit) {
    val model = remember { PlayerModel() }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var player by remember { mutableStateOf<MpvPlayer?>(null) }
    var fullscreen by remember { mutableStateOf(windowState.placement == WindowPlacement.Fullscreen) }

    LaunchedEffect(request) {
        model.resolve(request) { url, start, headers ->
            println("[Player] 地址就绪，开始 attach mpv: $url")
            playerScope.launch {
                val created = attachPlayer(panel) { hwnd ->
                    println("[Player] Canvas HWND=$hwnd，创建 libmpv 实例")
                    try {
                        MpvPlayer.create(hwnd, panelListener(panel)).also { println("[Player] libmpv 实例创建成功") }
                    } catch (e: Exception) {
                        println("[Player] libmpv 创建失败: ${e.message}")
                        SwingUtilities.invokeLater { panel?.showMessage(e.message ?: "播放器创建失败") }
                        null
                    }
                }
                if (created == null) {
                    println("[Player] attach 失败（HWND 未定位）")
                    SwingUtilities.invokeLater { panel?.showMessage(MpvPlayer.runtimeHint()) }
                    return@launch
                }
                player = created
                SwingUtilities.invokeLater {
                    panel?.attachPlayer(created)
                    created.load(url, headers, start, model.title)
                    println("[Player] loadfile 已提交: $url")
                }
            }
        }
    }

    LaunchedEffect(windowState.placement) {
        fullscreen = windowState.placement == WindowPlacement.Fullscreen
        panel?.setFullscreen(fullscreen)
    }

    DisposableEffect(request) {
        onDispose {
            saveNow(model, request, player)
            player?.release()
            player = null
        }
    }

    SwingPanel(
        factory = {
            PlayerPanel(
                onBack = {
                    saveNow(model, request, player)
                    player?.release()
                    player = null
                    onBack()
                },
                onToggleFullscreen = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                }
            ).also { panel = it }
        },
        modifier = Modifier
    )
}

private fun saveNow(model: PlayerModel, request: PlayRequest, player: MpvPlayer?) {
    val position = player?.position()?.toLong() ?: return
    val duration = player.duration().toLong()
    model.saveProgress(request, position, duration)
}

private fun panelListener(panel: PlayerPanel?) = object : MpvPlayer.Listener {
    override fun onFileLoaded() {
        println("[Player] mpv 文件装载完成，开始渲染")
        SwingUtilities.invokeLater { panel?.showCanvas() }
    }

    override fun onEndFile(reason: Int, error: Int) {
        println("[Player] 播放结束 reason=$reason error=$error")
        SwingUtilities.invokeLater { panel?.showMessage("播放结束") }
    }

    override fun onError(message: String) {
        SwingUtilities.invokeLater { panel?.showMessage(message) }
        println("[Player] $message")
    }

    override fun onLog(level: String, text: String) {
        if (level.equals("error", ignoreCase = true)) println("[mpv] $text")
    }
}

/** 轮询等待 Canvas 原生句柄就绪（SwingPanel addNotify 后才有），随后创建播放器 */
private suspend fun attachPlayer(panel: PlayerPanel?, create: (Long) -> MpvPlayer?): MpvPlayer? {
    if (panel == null) return null
    val hwnd = withContext(Dispatchers.IO) {
        var found: Long? = null
        repeat(30) {
            if (found == null) {
                runCatching {
                    SwingUtilities.invokeAndWait(Runnable { found = HwndResolver.findCanvasHwnd(WINDOW_TITLE) })
                }.getOrNull()
            }
            if (found == null) delay(200)
        }
        found
    }
    println("[Player] HWND 定位结果: $hwnd")
    if (hwnd == null || hwnd == 0L) return null
    return create(hwnd)
}
