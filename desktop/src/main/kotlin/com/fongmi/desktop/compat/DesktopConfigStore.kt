package com.fongmi.desktop.compat

import java.io.File

/**
 * 桌面端配置文件存取：包装生成的单站配置落盘于 %APPDATA%/FongMiTV/config/sites/
 */
object DesktopConfigStore {

    @JvmStatic
    fun write(name: String, json: String): File {
        val dir = File(DesktopPaths.configDir(), "sites").apply { mkdirs() }
        return File(dir, sanitize(name) + ".json").apply { writeText(json, Charsets.UTF_8) }
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifEmpty { "source" }
    }
}
