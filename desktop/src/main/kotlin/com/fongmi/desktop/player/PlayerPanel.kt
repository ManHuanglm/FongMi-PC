package com.fongmi.desktop.player

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Swing 播放面板：CENTER=视频 Canvas（mpv wid 直绘），SOUTH=控制条
 * 键盘：Space 暂停 / ←→ 快退快进 / F 全屏 / ESC 退出全屏或返回
 */
class PlayerPanel(
    private val onBack: () -> Unit,
    private val onToggleFullscreen: () -> Unit,
) : JPanel(BorderLayout()) {

    val canvas = Canvas().apply { background = Color(0xFF05080C.toInt()) }

    private val cardLayout = CardLayout()
    private val centerPanel = JPanel(cardLayout)
    private val messageLabel = JLabel(" ", JLabel.CENTER)
    private val playButton = JButton("播放")
    private val slider = JSlider(0, 1000, 0)
    private val timeLabel = JLabel("00:00 / 00:00")
    private val volumeSlider = JSlider(0, 100, 100)
    private val speedBox = JComboBox(arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "3.0x"))
    private val fullscreenButton = JButton("全屏")
    private val backButton = JButton("← 返回")
    private val seekBackButton = JButton("«10s")
    private val seekForwardButton = JButton("10s»")

    private var mpv: MpvPlayer? = null
    private var dragging = false
    private var fullscreen = false

    private val progressTimer = Timer(250) { updateProgress() }

    init {
        background = Color(0xFF05080C.toInt())
        messageLabel.foreground = Color(0xFF98A2B0.toInt())
        messageLabel.font = Font("Microsoft YaHei UI", Font.PLAIN, 16)
        centerPanel.add(canvas, "canvas")
        centerPanel.add(messageLabel, "message")
        add(centerPanel, BorderLayout.CENTER)
        add(buildControlBar(), BorderLayout.SOUTH)
        bindKeys()
        installResizeWorkaround()
        progressTimer.start()
        showMessage("正在准备播放...")
    }

    fun showMessage(text: String) {
        messageLabel.text = text
        cardLayout.show(centerPanel, "message")
    }

    fun showCanvas() {
        cardLayout.show(centerPanel, "canvas")
    }

    /** 由 Compose 层同步全屏状态，供 ESC 键决定退出全屏还是返回 */
    fun setFullscreen(value: Boolean) {
        fullscreen = value
        fullscreenButton.text = if (value) "退出全屏" else "全屏"
    }

    private fun buildControlBar(): JPanel {
        val bar = JPanel(BorderLayout())
        bar.background = Color(0xFF10141A.toInt())
        bar.border = BorderFactory.createEmptyBorder(6, 12, 8, 12)

        val top = JPanel()
        top.isOpaque = false
        top.layout = BoxLayout(top, BoxLayout.X_AXIS)
        slider.isOpaque = false
        top.add(slider)
        top.add(Box.createHorizontalStrut(8))
        timeLabel.foreground = Color(0xFF98A2B0.toInt())
        top.add(timeLabel)

        val bottom = JPanel()
        bottom.isOpaque = false
        bottom.layout = BoxLayout(bottom, BoxLayout.X_AXIS)
        styleButton(backButton)
        styleButton(playButton)
        styleButton(seekBackButton)
        styleButton(seekForwardButton)
        styleButton(fullscreenButton)
        bottom.add(backButton)
        bottom.add(Box.createHorizontalStrut(8))
        bottom.add(playButton)
        bottom.add(Box.createHorizontalStrut(8))
        bottom.add(seekBackButton)
        bottom.add(seekForwardButton)
        bottom.add(Box.createHorizontalGlue())
        val volumeLabel = JLabel("音量")
        volumeLabel.foreground = Color(0xFF98A2B0.toInt())
        bottom.add(volumeLabel)
        volumeSlider.preferredSize = Dimension(110, 26)
        volumeSlider.isOpaque = false
        bottom.add(volumeSlider)
        bottom.add(Box.createHorizontalStrut(12))
        val speedLabel = JLabel("倍速")
        speedLabel.foreground = Color(0xFF98A2B0.toInt())
        bottom.add(speedLabel)
        speedBox.preferredSize = Dimension(90, 26)
        bottom.add(speedBox)
        bottom.add(Box.createHorizontalStrut(12))
        bottom.add(fullscreenButton)

        bar.add(top, BorderLayout.NORTH)
        bar.add(bottom, BorderLayout.SOUTH)
        bindActions()
        return bar
    }

    private fun styleButton(button: JButton) {
        button.isFocusPainted = false
        button.isContentAreaFilled = false
        button.foreground = Color(0xFFE8ECF2.toInt())
        button.font = Font("Microsoft YaHei UI", Font.PLAIN, 13)
    }

    private fun bindActions() {
        backButton.addActionListener { onBack() }
        fullscreenButton.addActionListener { onToggleFullscreen() }
        playButton.addActionListener { mpv?.togglePause() }
        seekBackButton.addActionListener { mpv?.seekBy(-10.0) }
        seekForwardButton.addActionListener { mpv?.seekBy(10.0) }
        slider.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                dragging = true
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                dragging = false
                mpv?.seek(slider.value.toLong().toDouble())
            }
        })
        volumeSlider.addChangeListener { mpv?.setVolume(volumeSlider.value.toDouble()) }
        speedBox.addActionListener {
            val text = speedBox.selectedItem?.toString() ?: return@addActionListener
            mpv?.setSpeed(text.removeSuffix("x").toDoubleOrNull() ?: 1.0)
        }
    }

    private fun bindKeys() {
        val actionMap = mutableMapOf(
            KeyEvent.VK_SPACE to { mpv?.togglePause() },
            KeyEvent.VK_LEFT to { mpv?.seekBy(-10.0) },
            KeyEvent.VK_RIGHT to { mpv?.seekBy(10.0) },
            KeyEvent.VK_UP to { volumeSlider.value = (volumeSlider.value + 5).coerceAtMost(100) },
            KeyEvent.VK_DOWN to { volumeSlider.value = (volumeSlider.value - 5).coerceAtLeast(0) },
            KeyEvent.VK_ESCAPE to { if (fullscreen) onToggleFullscreen() else onBack() },
            KeyEvent.VK_F to { onToggleFullscreen() },
        )
        val inputs = getInputMap(WHEN_IN_FOCUSED_WINDOW)
        val actions = getActionMap()
        actionMap.forEach { (keyCode, action) ->
            val key = "fm-key-$keyCode"
            inputs.put(javax.swing.KeyStroke.getKeyStroke(keyCode, 0), key)
            actions.put(key, object : javax.swing.AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent) {
                    action()
                }
            })
        }
    }

    /** 规避 mpv 子窗口在父容器 resize 后黑边/错位：触发 native repaint */
    private fun installResizeWorkaround() {
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                canvas.repaint()
            }
        })
    }

    fun attachPlayer(player: MpvPlayer) {
        mpv = player
        updateProgress()
    }

    private fun updateProgress() {
        val player = mpv ?: return
        val position = player.position()
        val duration = player.duration()
        if (!dragging && duration > 0) slider.value = ((position / duration) * 1000).toInt().coerceIn(0, 1000)
        timeLabel.text = "${format(position)} / ${format(duration)}"
        playButton.text = if (player.paused()) "播放" else "暂停"
    }

    fun currentSliderPositionSec(): Double {
        val player = mpv ?: return 0.0
        val duration = player.duration()
        if (duration <= 0) return player.position()
        return slider.value / 1000.0 * duration
    }

    fun setSpeedSilently(value: Double) {
        val index = when {
            value <= 0.5 -> 0
            value <= 0.75 -> 1
            value <= 1.0 -> 2
            value <= 1.25 -> 3
            value <= 1.5 -> 4
            value <= 2.0 -> 5
            else -> 6
        }
        speedBox.selectedIndex = index
    }

    fun releasePlayer() {
        progressTimer.stop()
        mpv?.release()
        mpv = null
    }

    private fun format(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
    }
}

