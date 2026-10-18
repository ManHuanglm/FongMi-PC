package com.fongmi.desktop.player

import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

/**
 * 播放窗口：独立 JFrame（无装饰，owner 为主窗口），内嵌 PlayerPanel（Canvas + 控制条）
 * bounds 持续跟随主窗口客户区；窗口关闭等价于"返回"
 */
class PlayerFrame(owner: Window, onBack: () -> Unit) : JFrame("FongMiPlayer") {

    private val panel = PlayerPanel(
        onBack = { dispatchClose() },
        onToggleFullscreen = { toggleFullscreen() }
    )

    private val onCloseCallback: (() -> Unit) = onBack

    private fun toggleFullscreen() {
        // owner 在窗口关闭瞬间可能为 null，避免全屏切换竞态崩溃
        val owner = getOwner() ?: return
        maximizedBounds = owner.bounds
        extendedState = if (extendedState and MAXIMIZED_BOTH != 0) NORMAL else MAXIMIZED_BOTH
    }

    init {
        isUndecorated = true
        background = Color(0xFF05080C.toInt())
        layout = BorderLayout()
        add(panel, BorderLayout.CENTER)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                dispatchClose()
            }
        })
        owner.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = syncBounds()
            override fun componentMoved(e: ComponentEvent) = syncBounds()
        })
        syncBounds()
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

    fun showCanvas() {
        panel.showCanvas()
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (visible) syncBounds()
    }

    private fun syncBounds() {
        // 播放窗口直接铺满所在屏幕（undecorated 全屏）；ComposeWindow 的 AWT bounds 在 CMP 下不可靠
        val config = graphicsConfiguration ?: return
        setBounds(config.bounds)
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
