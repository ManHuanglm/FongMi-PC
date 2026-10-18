package com.fongmi.desktop.compat

import com.github.catvod.net.OkHttp
import com.google.gson.JsonParser

/**
 * M5 自动更新检查：对照 FongMi/Release 官方发布库最新版本，仅提示+跳转下载（不自动安装）
 */
object DesktopUpdater {

    private const val RELEASE_API = "https://api.github.com/repos/FongMi/Release/releases/latest"
    private const val RELEASE_PAGE = "https://github.com/FongMi/Release/releases/latest"

    data class Info(val version: String, val page: String, val notes: String) {
        fun hasUpdate(current: String): Boolean = version != current
    }

    fun check(): Info {
        return try {
            val body = OkHttp.string(RELEASE_API)
            val root = JsonParser.parseString(body).asJsonObject
            Info(
                version = root.get("tag_name")?.asString ?: "",
                page = root.get("html_url")?.asString ?: RELEASE_PAGE,
                notes = root.get("body")?.asString?.take(200) ?: ""
            )
        } catch (e: Exception) {
            Info(version = "", page = RELEASE_PAGE, notes = "检查失败: ${e.message}")
        }
    }

    fun open(page: String) {
        runCatching {
            java.awt.Desktop.getDesktop().browse(java.net.URI(page))
        }.onFailure { println("[Updater] 打开下载页失败: ${it.message}") }
    }
}
