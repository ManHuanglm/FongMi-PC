package com.fongmi.desktop.player

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.util.concurrent.Executors
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
 * 键盘：Space 暂停 / ←→ 快退快进 / ↑↓ 音量 / F 全屏 / ESC 退出全屏或返回 / D 弹幕 / M 静音
 * 线程模型：全部 mpv JNA 调用走单线程执行器（网络流阻塞核心锁时不得卡死 EDT），结果经 invokeLater 回刷
 */
class PlayerPanel(
    private val onBack: () -> Unit,
    private val onToggleFullscreen: () -> Unit,
    private val onToggleDanmaku: () -> Unit = {},
    private val onOpenDanmakuSettings: () -> Unit = {},
    private val onPrevChannel: () -> Unit = {},
    private val onNextChannel: () -> Unit = {},
    private val onSwitchLine: () -> Unit = {},
) : JPanel(BorderLayout()) {

    val canvas = Canvas().apply { background = Color(0xFF05080C.toInt()) }

    private val cardLayout = CardLayout()
    private val centerPanel = JPanel(cardLayout)
    private val messageLabel = JLabel(" ", JLabel.CENTER)
    private val playButton = JButton("播放")
    private val slider = JSlider(0, 1000, 0)
    private val timeLabel = JLabel("00:00 / 00:00")
    private val titleLabel = JLabel(" ")
    private val volumeSlider = JSlider(0, 100, 100)
    private val speedBox = JComboBox(arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "3.0x"))
    private val fullscreenButton = JButton("全屏")
    private val backButton = JButton("← 返回")
    private val seekBackButton = JButton("«10s")
    private val seekForwardButton = JButton("10s»")
    private val prevChannelButton = JButton("⏮")
    private val nextChannelButton = JButton("⏭")
    private val danmakuButton = JButton("弹幕")
    private val danmakuSettingButton = JButton("弹幕设置")
    private val fxButton = JButton("音效")
    private val switchLineButton = JButton("换线")
    private val hwdecButton = JButton("硬解")
    private val reloadButton = JButton("刷新")
    private val replayButton = JButton("重播")
    private val loopButton = JButton("循环")
    private val resetScaleButton = JButton("原始")
    private val subButton = JButton("字幕")
    private val audioButton = JButton("音轨")
    private val videoButton = JButton("视轨")
    private val skipIntroButton = JButton("片头")
    private val skipOutroButton = JButton("片尾")
    private val danmakuStatusLabel = JLabel(" ")
    private val liveInfoLabel = JLabel(" ")
    private lateinit var vodRow: JPanel

    private var mpv: MpvPlayer? = null
    private var dragging = false
    private var fullscreen = false
    private var danmakuEnabled = false
    private var hwdecOn = com.github.catvod.utils.Prefers.getBoolean("desktop_hwdec", true)
    private var loopOn = false

    /** 最近一次装载的媒体（刷新按钮重载用） */
    private var lastUrl: String = ""
    private var lastHeaders: Map<String, String> = emptyMap()

    /** mpv JNA 专用单线程执行器：网络流阻塞 mpv 核心锁时 EDT 保持响应 */
    private val mpvExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "FongMi-Mpv-Commands") }

    private val progressTimer = Timer(250) { pollProgress() }

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

    /** 在 mpv 命令线程执行（全部 JNA 调用的唯一入口） */
    private fun mpvExec(block: () -> Unit) {
        mpvExecutor.execute {
            runCatching { block() }
        }
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

    /** 由 Compose 层同步弹幕开关状态 */
    fun setDanmakuEnabled(value: Boolean) {
        danmakuEnabled = value
        danmakuButton.text = if (value) "弹幕:开" else "弹幕:关"
    }

    /** 弹幕状态提示（搜索中/加载条数/失败原因） */
    fun setDanmakuStatus(text: String) {
        danmakuStatusLabel.text = text.ifEmpty { " " }
        danmakuStatusLabel.toolTipText = text
    }

    /** 媒体标题（顶栏左侧，原版播放器标题位） */
    fun setMediaTitle(text: String) {
        titleLabel.text = text.ifEmpty { " " }
        titleLabel.toolTipText = text
    }

    /** 直播信息行（配置名 + 线路），非直播隐藏 */
    fun setLiveInfo(text: String) {
        liveInfoLabel.text = text
        liveInfoLabel.isVisible = text.isNotEmpty()
        switchLineButton.isVisible = text.isNotEmpty()
    }

    /** 上一个/下一个频道按钮（仅直播显示，对齐原版播放器中置 ⏮⏭） */
    fun setChannelSwitchVisible(visible: Boolean) {
        prevChannelButton.isVisible = visible
        nextChannelButton.isVisible = visible
    }

    /** 记录当前媒体（刷新按钮重载用） */
    fun setMedia(url: String, headers: Map<String, String>) {
        lastUrl = url
        lastHeaders = headers
    }

    /** 点播功能行（原版底部：硬解 刷新 重播 循环 原始 | 字幕 音轨 视轨 片头 片尾） */
    fun setVodRowVisible(visible: Boolean) {
        listOf(hwdecButton, reloadButton, replayButton, loopButton, resetScaleButton, subButton, audioButton, videoButton, skipIntroButton, skipOutroButton).forEach { it.isVisible = visible }
        vodRow.isVisible = visible
    }

    /** VOD 功能行（与原版一致：左解码/播放操作，右轨道/跳转） */
    private fun buildVodRow(): JPanel {
        val row = JPanel()
        row.isOpaque = false
        row.layout = BoxLayout(row, BoxLayout.X_AXIS)
        listOf(hwdecButton, reloadButton, replayButton, loopButton, resetScaleButton, subButton, audioButton, videoButton, skipIntroButton, skipOutroButton).forEach { styleButton(it) }
        hwdecButton.foreground = if (hwdecOn) Color(0xFF6FA8DC.toInt()) else Color(0xFF98A2B0.toInt())
        row.add(hwdecButton)
        row.add(reloadButton)
        row.add(replayButton)
        row.add(loopButton)
        row.add(resetScaleButton)
        row.add(Box.createHorizontalGlue())
        row.add(subButton)
        row.add(audioButton)
        row.add(videoButton)
        row.add(skipIntroButton)
        row.add(skipOutroButton)
        row.isVisible = false
        return row
    }

    private fun buildControlBar(): JPanel {
        val bar = JPanel(BorderLayout())
        bar.background = Color(0xFF10141A.toInt())
        bar.border = BorderFactory.createEmptyBorder(6, 12, 8, 12)

        // 顶行：标题（原版左上标题位）+ 弹幕状态
        val top = JPanel()
        top.isOpaque = false
        top.layout = BoxLayout(top, BoxLayout.X_AXIS)
        titleLabel.foreground = Color(0xFFE8ECF2.toInt())
        titleLabel.font = Font("Microsoft YaHei UI", Font.BOLD, 15)
        top.add(titleLabel)
        top.add(Box.createHorizontalGlue())
        danmakuStatusLabel.foreground = Color(0xFF6FA8DC.toInt())
        danmakuStatusLabel.font = Font("Microsoft YaHei UI", Font.PLAIN, 12)
        top.add(danmakuStatusLabel)

        // 中行：进度条 + 时间
        val mid = JPanel()
        mid.isOpaque = false
        mid.layout = BoxLayout(mid, BoxLayout.X_AXIS)
        slider.isOpaque = false
        mid.add(slider)
        mid.add(Box.createHorizontalStrut(8))
        timeLabel.foreground = Color(0xFF98A2B0.toInt())
        mid.add(timeLabel)

        // 底行：返回 ⏮ 播放 ⏭ 快进快退 … 音量 倍速 弹幕 音效 换线 全屏
        val bottom = JPanel()
        bottom.isOpaque = false
        bottom.layout = BoxLayout(bottom, BoxLayout.X_AXIS)
        listOf(backButton, prevChannelButton, playButton, nextChannelButton, seekBackButton, seekForwardButton, fullscreenButton, danmakuButton, danmakuSettingButton, fxButton, switchLineButton).forEach { styleButton(it) }
        bottom.add(backButton)
        bottom.add(Box.createHorizontalStrut(8))
        bottom.add(prevChannelButton)
        bottom.add(playButton)
        bottom.add(nextChannelButton)
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
        bottom.add(danmakuButton)
        bottom.add(danmakuSettingButton)
        bottom.add(Box.createHorizontalStrut(8))
        bottom.add(fxButton)
        bottom.add(Box.createHorizontalStrut(8))
        bottom.add(switchLineButton)
        bottom.add(Box.createHorizontalStrut(8))
        bottom.add(fullscreenButton)

        // 直播信息行（原版底部左：配置/线路）
        val liveRow = JPanel()
        liveRow.isOpaque = false
        liveRow.layout = BoxLayout(liveRow, BoxLayout.X_AXIS)
        liveInfoLabel.foreground = Color(0xFF98A2B0.toInt())
        liveInfoLabel.font = Font("Microsoft YaHei UI", Font.PLAIN, 12)
        liveRow.add(liveInfoLabel)
        liveRow.add(Box.createHorizontalGlue())
        liveRow.isVisible = false

        // 点播功能行（原版底部：硬解 刷新 重播 循环 原始 | 字幕 音轨 视轨 片头 片尾）
        vodRow = buildVodRow()

        val south = JPanel()
        south.isOpaque = false
        south.layout = BoxLayout(south, BoxLayout.Y_AXIS)
        south.add(mid)
        south.add(bottom)
        south.add(liveRow)
        south.add(vodRow)

        bar.add(top, BorderLayout.NORTH)
        bar.add(south, BorderLayout.SOUTH)
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
        playButton.addActionListener { mpvExec { mpv?.togglePause() } }
        seekBackButton.addActionListener { mpvExec { mpv?.seekBy(-10.0) } }
        seekForwardButton.addActionListener { mpvExec { mpv?.seekBy(10.0) } }
        slider.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                dragging = true
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                dragging = false
                val target = slider.value.toLong().toDouble()
                mpvExec { mpv?.seek(target) }
            }
        })
        volumeSlider.addChangeListener {
            val volume = volumeSlider.value.toDouble()
            mpvExec { mpv?.setVolume(volume) }
        }
        speedBox.addActionListener {
            val text = speedBox.selectedItem?.toString() ?: return@addActionListener
            val speed = text.removeSuffix("x").toDoubleOrNull() ?: 1.0
            mpvExec { mpv?.setSpeed(speed) }
        }
        danmakuButton.addActionListener { onToggleDanmaku() }
        danmakuSettingButton.addActionListener { onOpenDanmakuSettings() }
        fxButton.addActionListener { showAudioFx() }
        prevChannelButton.addActionListener { onPrevChannel() }
        nextChannelButton.addActionListener { onNextChannel() }
        switchLineButton.addActionListener { onSwitchLine() }
        hwdecButton.addActionListener { toggleHwdec() }
        reloadButton.addActionListener { reloadMedia() }
        replayButton.addActionListener { mpvExec { mpv?.seek(0.0); mpv?.setPaused(false) } }
        loopButton.addActionListener { toggleLoop() }
        resetScaleButton.addActionListener { mpvExec { mpv?.resetVideoScale() } }
        subButton.addActionListener { cycleTrack("sid", "sub", "字幕") }
        audioButton.addActionListener { cycleTrack("aid", "audio", "音轨") }
        videoButton.addActionListener { cycleTrack("vid", "video", "视轨") }
        skipIntroButton.addActionListener { mpvExec { mpv?.seekBy(90.0) } }
        skipOutroButton.addActionListener {
            mpvExec {
                val duration = mpv?.duration() ?: 0.0
                if (duration > 30) mpv?.seek(duration - 15)
            }
        }
    }

    /** 硬解开关：运行时切换并持久化（与设置页共享 desktop_hwdec 键） */
    private fun toggleHwdec() {
        hwdecOn = !hwdecOn
        com.github.catvod.utils.Prefers.put("desktop_hwdec", hwdecOn)
        hwdecButton.foreground = if (hwdecOn) Color(0xFF6FA8DC.toInt()) else Color(0xFF98A2B0.toInt())
        mpvExec { mpv?.setHwdec(if (hwdecOn) "auto-safe" else "no") }
    }

    /** 刷新：重载当前媒体并尽量保持进度 */
    private fun reloadMedia() {
        val url = lastUrl
        if (url.isEmpty()) return
        mpvExec {
            val player = mpv ?: return@mpvExec
            val position = player.position().toLong()
            player.load(url, lastHeaders, position, null)
        }
    }

    /** 单文件循环开关 */
    private fun toggleLoop() {
        loopOn = !loopOn
        loopButton.foreground = if (loopOn) Color(0xFF6FA8DC.toInt()) else Color(0xFF98A2B0.toInt())
        mpvExec { mpv?.setLoop(loopOn) }
    }

    /** 轨道循环切换，结果提示复用弹幕状态标签 */
    private fun cycleTrack(property: String, type: String, label: String) {
        mpvExec {
            val player = mpv ?: return@mpvExec
            val desc = player.cycleTrack(property, type)
            SwingUtilities.invokeLater {
                setDanmakuStatus(if (desc == null) "$label：无多轨道可切换" else "$label → $desc")
            }
        }
    }

    /** 音效设置对话框（挂 owner 为主播放窗口，应用后即时生效） */
    private fun showAudioFx() {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        AudioFxDialog(mpv, owner) { }.isVisible = true
    }

    private fun bindKeys() {
        val actionMap = mutableMapOf(
            KeyEvent.VK_SPACE to { mpvExec { mpv?.togglePause() } },
            KeyEvent.VK_LEFT to { mpvExec { mpv?.seekBy(-10.0) } },
            KeyEvent.VK_RIGHT to { mpvExec { mpv?.seekBy(10.0) } },
            KeyEvent.VK_UP to { volumeSlider.value = (volumeSlider.value + 5).coerceAtMost(100) },
            KeyEvent.VK_DOWN to { volumeSlider.value = (volumeSlider.value - 5).coerceAtLeast(0) },
            KeyEvent.VK_ESCAPE to { if (fullscreen) onToggleFullscreen() else onBack() },
            KeyEvent.VK_F to { onToggleFullscreen() },
            KeyEvent.VK_D to { onToggleDanmaku() },
            KeyEvent.VK_M to { mpvExec { mpv?.let { player -> player.setMuted(!player.muted()) } } },
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

    fun attachPlayer(player: MpvPlayer?) {
        mpv = player
        if (player == null) {
            showMessage("（实验模式）Swing 面板渲染测试 — 若见此文字与深色控制条，说明 SwingPanel 正常")
            return
        }
        pollProgress()
    }

    /** 后台线程轮询 mpv 状态（绝不阻塞 EDT），EDT 内回刷 UI */
    private fun pollProgress() {
        val player = mpv ?: return
        mpvExecutor.execute {
            if (player.isReleased()) return@execute
            val position = player.position()
            val duration = player.duration()
            val paused = player.paused()
            SwingUtilities.invokeLater { applyProgress(position, duration, paused) }
        }
    }

    private fun applyProgress(position: Double, duration: Double, paused: Boolean) {
        if (mpv == null) return
        if (!dragging && duration > 0) slider.value = ((position / duration) * 1000).toInt().coerceIn(0, 1000)
        timeLabel.text = "${format(position)} / ${format(duration)}"
        playButton.text = if (paused) "播放" else "暂停"
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
        val player = mpv
        mpv = null
        // 释放走命令线程（避免与排队的 mpv 调用竞态；release 内部有重入保护）
        if (player != null) mpvExecutor.execute { player.release() }
        mpvExecutor.shutdown()
    }

    private fun format(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
    }
}

