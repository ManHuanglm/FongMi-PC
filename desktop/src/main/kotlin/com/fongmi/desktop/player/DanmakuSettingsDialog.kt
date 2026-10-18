package com.fongmi.desktop.player

import com.fongmi.android.tv.setting.DanmakuSetting
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextArea
import javax.swing.JTextField

/**
 * 弹幕设置对话框：开关/自动匹配/弹幕接口/字号/透明度/滚动时长/时移/屏蔽词
 * 偏好键与安卓一致（danmaku_*），设置保存后由调用方触发弹幕轨重建
 */
class DanmakuSettingsDialog(owner: java.awt.Window, onSaved: () -> Unit) : JDialog(owner, "弹幕设置", ModalityType.APPLICATION_MODAL) {

    private val apiUrlField = JTextField(DanmakuSetting.getApiUrl(), 24)
    private val autoBox = JCheckBox("自动匹配弹幕", DanmakuSetting.isAuto())
    private val scaleSlider = JSlider(50, 300, (DanmakuSetting.getTextScale() * 100).toInt())
    private val alphaSlider = JSlider(0, 90, (DanmakuSetting.getTransparency() * 100).toInt())
    private val durationSlider = JSlider(3, 15, (DanmakuSetting.getDurationMs() / 1000).toInt())
    private val offsetSlider = JSlider(-60, 60, (DanmakuSetting.getTimeOffsetMs() / 1000).toInt())
    private val shieldArea = JTextArea(DanmakuSetting.getShield(), 3, 30)

    init {
        layout = BorderLayout(8, 8)
        add(buildCenter(), BorderLayout.CENTER)
        add(buildBottom(onSaved), BorderLayout.SOUTH)
        setSize(520, 420)
        setLocationRelativeTo(owner)
    }

    private fun buildCenter(): JPanel {
        val grid = JPanel(GridLayout(0, 1, 4, 2))

        val apiRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        apiRow.add(JLabel("接口地址"))
        apiRow.add(apiUrlField)
        grid.add(apiRow)
        grid.add(autoBox)

        grid.add(labeledSlider(scaleSlider, "字号", "%") { value -> (value / 100.0).toString() + "x" })
        grid.add(labeledSlider(alphaSlider, "透明度", "%") { value -> (100 - value).toString() + "%" })
        grid.add(labeledSlider(durationSlider, "滚动时长", "s") { value -> "${value}s" })
        grid.add(labeledSlider(offsetSlider, "时间偏移", "s") { value -> "${value}s" })

        val shieldRow = JPanel(BorderLayout())
        shieldRow.add(JLabel("屏蔽词（顿号/逗号分隔）"), BorderLayout.NORTH)
        shieldArea.lineWrap = true
        shieldRow.add(shieldArea, BorderLayout.CENTER)
        grid.add(shieldRow)
        return grid
    }

    private fun labeledSlider(slider: JSlider, label: String, unit: String, format: (Int) -> String): JPanel {
        val row = JPanel(BorderLayout(6, 0))
        val title = JLabel("$label ($unit)")
        title.preferredSize = Dimension(80, 22)
        val value = JLabel(format(slider.value))
        value.preferredSize = Dimension(70, 22)
        slider.addChangeListener { value.text = format(slider.value) }
        row.add(title, BorderLayout.WEST)
        row.add(slider, BorderLayout.CENTER)
        row.add(value, BorderLayout.EAST)
        return row
    }

    private fun buildBottom(onSaved: () -> Unit): JPanel {
        val bottom = JPanel(FlowLayout(FlowLayout.RIGHT))
        bottom.add(JButton("取消").apply { addActionListener { dispose() } })
        bottom.add(JButton("保存").apply {
            addActionListener {
                DanmakuSetting.putApiUrl(apiUrlField.text.trim())
                DanmakuSetting.putAuto(autoBox.isSelected)
                DanmakuSetting.putTextScale(scaleSlider.value / 100f)
                DanmakuSetting.putTransparency(alphaSlider.value / 100f)
                DanmakuSetting.putDurationMs(durationSlider.value * 1000L)
                DanmakuSetting.putTimeOffsetMs(offsetSlider.value * 1000L)
                DanmakuSetting.putShield(shieldArea.text.trim())
                onSaved()
                dispose()
            }
        })
        return bottom
    }
}
