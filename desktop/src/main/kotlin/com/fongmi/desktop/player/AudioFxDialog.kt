package com.fongmi.desktop.player

import com.github.catvod.utils.Prefers
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * 音效设置对话框：左右平衡 / 响度均衡 / 十段均衡器 → mpv af 链
 * 偏好键（desktop_ 前缀）：desktop_af_balance / desktop_af_loudness / desktop_af_eq
 */
class AudioFxDialog(
    private val player: MpvPlayer?,
    owner: java.awt.Window,
    onChanged: (String) -> Unit
) : JDialog(owner, "音效设置", ModalityType.APPLICATION_MODAL) {

    companion object {

        private val FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        fun loadBalance(): Int = (Prefers.getFloat("desktop_af_balance", 0f) * 100).toInt()

        fun loadLoudness(): Boolean = Prefers.getBoolean("desktop_af_loudness", false)

        /** 十段增益（dB，-12..12），逗号分隔存储 */
        fun loadEq(): List<Int> {
            val saved = Prefers.getString("desktop_af_eq", "")
            val values = saved.split(",").mapNotNull { it.trim().toFloatOrNull()?.toInt() }
            return List(FREQS.size) { i -> values.getOrElse(i) { 0 } }
        }

        /** 由持久化偏好构建 mpv af 链（空串=无滤镜） */
        fun buildChain(balance: Float, loudness: Boolean, eq: List<Int>): String {
            val filters = ArrayList<String>()
            if (loudness) filters.add("lavfi=[loudnorm=I=-16:TP=-1.5:LRA=11]")
            if (kotlin.math.abs(balance) > 0.01f) {
                // 恒功率左右平衡：b=-1 全左，b=1 全右
                val angle = ((balance + 1f) / 2f) * (Math.PI / 2).toFloat()
                val left = cos(angle.toDouble()).toFloat()
                val right = sin(angle.toDouble()).toFloat()
                filters.add("lavfi=[pan=stereo|c0=c0*${"%.3f".format(left)}|c1=c1*${"%.3f".format(right)}]")
            }
            val active = eq.withIndex().filter { it.value != 0 }
            if (active.isNotEmpty()) {
                val chain = active.joinToString(",") { (index, gain) ->
                    "equalizer=f=${FREQS[index]}:t=q:w=o:g=$gain"
                }
                filters.add("lavfi=[$chain]")
            }
            return filters.joinToString(",")
        }
    }

    private val balanceSlider = JSlider(-100, 100, loadBalance())
    private val loudnessBox = JCheckBox("响度均衡（夜间模式）", loadLoudness())
    private val eqSliders = loadEq().map { JSlider(-12, 12, it) }

    init {
        layout = BorderLayout()
        add(buildCenter(), BorderLayout.CENTER)
        add(buildBottom(onChanged), BorderLayout.SOUTH)
        setSize(460, 520)
        setLocationRelativeTo(owner)
    }

    private fun buildCenter(): JPanel {
        val center = JPanel(BorderLayout())
        val balancePanel = JPanel(BorderLayout())
        val balanceLabel = JLabel(balanceText(balanceSlider.value))
        balanceSlider.addChangeListener { balanceLabel.text = balanceText(balanceSlider.value) }
        balancePanel.add(JLabel("左右平衡"), BorderLayout.WEST)
        balancePanel.add(balanceSlider, BorderLayout.CENTER)
        balancePanel.add(balanceLabel, BorderLayout.EAST)

        val eqPanel = JPanel(GridLayout(FREQS.size, 1, 2, 0))
        FREQS.forEachIndexed { index, freq ->
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
            val label = JLabel(eqLabel(freq))
            label.preferredSize = Dimension(52, 22)
            val value = JLabel(eqSliders[index].value.toString() + " dB")
            value.preferredSize = Dimension(56, 22)
            eqSliders[index].preferredSize = Dimension(260, 24)
            eqSliders[index].addChangeListener { value.text = eqSliders[index].value.toString() + " dB" }
            row.add(label)
            row.add(eqSliders[index])
            row.add(value)
            eqPanel.add(row)
        }
        center.add(balancePanel, BorderLayout.NORTH)
        center.add(loudnessBox, BorderLayout.CENTER)
        center.add(eqPanel, BorderLayout.SOUTH)
        return center
    }

    private fun buildBottom(onChanged: (String) -> Unit): JPanel {
        val bottom = JPanel(FlowLayout(FlowLayout.RIGHT))
        val apply = JButton("应用")
        val reset = JButton("重置")
        apply.addActionListener {
            val balance = balanceSlider.value / 100f
            val eq = eqSliders.map { it.value }
            Prefers.put("desktop_af_balance", balance)
            Prefers.put("desktop_af_loudness", loudnessBox.isSelected)
            Prefers.put("desktop_af_eq", eq.joinToString(","))
            val chain = buildChain(balance, loudnessBox.isSelected, eq)
            player?.setAudioFilter(chain)
            onChanged(chain)
            dispose()
        }
        reset.addActionListener {
            balanceSlider.value = 0
            loudnessBox.isSelected = false
            eqSliders.forEach { it.value = 0 }
            Prefers.put("desktop_af_balance", 0f)
            Prefers.put("desktop_af_loudness", false)
            Prefers.put("desktop_af_eq", "")
            player?.setAudioFilter("")
            onChanged("音效已重置")
            dispose()
        }
        bottom.add(reset)
        bottom.add(apply)
        return bottom
    }

    private fun balanceText(value: Int): String = when {
        value == 0 -> "居中"
        value < 0 -> "左 ${-value}%"
        else -> "右 $value%"
    }

    private fun eqLabel(freq: Int): String = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz"
}
