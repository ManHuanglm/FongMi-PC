package com.fongmi.desktop.compat

import java.io.File

/**
 * 桌面端路径中枢：全部数据落地 %APPDATA%/FongMiTV/
 */
object DesktopPaths {

    private const val DIR_NAME = "FongMiTV"

    @JvmStatic
    fun configDir(): File {
        val appdata = System.getenv("APPDATA")
        val base = if (appdata.isNullOrEmpty()) System.getProperty("user.home") else appdata
        return File(File(base, DIR_NAME), "config").apply { mkdirs() }
    }

    @JvmStatic
    fun rootDir(): File {
        val appdata = System.getenv("APPDATA")
        val base = if (appdata.isNullOrEmpty()) System.getProperty("user.home") else appdata
        return File(base, DIR_NAME).apply { mkdirs() }
    }

    @JvmStatic
    fun filesDir(): File = File(rootDir(), "files").apply { mkdirs() }

    @JvmStatic
    fun cacheDir(): File = File(rootDir(), "cache").apply { mkdirs() }

    @JvmStatic
    fun assetsDir(): File = File(rootDir(), "assets").apply { mkdirs() }

    @JvmStatic
    fun databaseFile(): File = File(rootDir(), "database/tv.db").apply { parentFile.mkdirs() }
}
