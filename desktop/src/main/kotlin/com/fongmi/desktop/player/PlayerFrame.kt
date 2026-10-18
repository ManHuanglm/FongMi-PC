package com.fongmi.desktop.player

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

/**
 * 播放窗口：普通可缩放 JFrame（owner 为主窗口），内嵌 PlayerPanel（Canvas + 控制条）
 * 全屏按钮等价于最大化切换；窗口关闭等价于"返回"
 */
class PlayerFrame(
    owner: Window,
    onBack: () -> Unit,
    onToggleDanmaku: () -> Unit = {},
    onOpenDanmakuSettings: () -> Unit = {},
    onPrevChannel: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    onSwitchLine: () -> Unit = {},
) : JFrame("FongMiPlayer") {

    private val panel = PlayerPanel(
        onBack = { dispatchClose() },
        onToggleFullscreen = { toggleFullscreen() },
        onToggleDanmaku = onToggleDanmaku,
        onOpenDanmakuSettings = onOpenDanmakuSettings,
        onPrevChannel = onPrevChannel,
        onNextChannel = onNextChannel,
        onSwitchLine = onSwitchLine,
    )

    private val onCloseCallback: (() -> Unit) = onBack

    /** 全屏切换 = 最大化（最大化边界限定在当前屏幕） */
    private fun toggleFullscreen() {
        maximizedBounds = graphicsConfiguration?.bounds
        extendedState = if (extendedState and MAXIMIZED_BOTH != 0) NORMAL else MAXIMIZED_BOTH
    }

    init {
        background = Color(0xFF05080C.toInt())
        layout = BorderLayout()
        add(panel, BorderLayout.CENTER)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        setSize(1280, 720)
        setLocationRelativeTo(owner)
        isResizable = true
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                dispatchClose()
            }
        })
    }

    private fun dispatchClose() {
        panel.releasePlayer()
        onCloseCallback.invoke()
    }

    /** 播放器就绪后由外部接入：面板接管状态并切到视频层 */
    fun attachPlayer(player: MpvPlayer) {
        panel.attachPlayer(player)
        panel.showCanvas()
    }

    /** 控制面板透出（弹幕状态/开关由 Compose 层同步） */
    fun panel(): PlayerPanel = panel

    fun showCanvas() {
        panel.showCanvas()
    }

    companion object {

        /** 最近一次创建的 Canvas 原生句柄（供 mpv wid 使用） */
        @Volatile
        private var latestHwnd: Long? = null

        fun latestCanvasHwnd(): Long? = latestHwnd

        fun registerCanvasHwnd(hwnd: Long) {
            latestHwnd = hwnd
        }
    }
}
