package com.fongmi.desktop.compat

import com.fongmi.android.tv.bean.Config
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 桌面端配置入口适配：
 * - 常规订阅 URL 直传安卓端 Config/VodConfig 链路
 * - 裸 Apple CMS 采集地址（以 /api.php/provide/vod 结尾）自动包装为单站点 type:1 配置，无需手写 JSON
 */
object DesktopConfig {

    private const val CMS_MARK = "/api.php/provide/vod"

    fun resolve(url: String): Config {
        val value = url.trim()
        return if (value.contains(CMS_MARK)) wrapCms(value) else Config.find(value, displayName(value), 0).save()
    }

    private fun wrapCms(url: String): Config {
        val api = normalizeCms(url)
        val name = displayName(api)
        val json = """{"spider":"","sites":[{"key":"$name","name":"$name","type":1,"api":"$api","searchable":1,"changeable":1}],"parses":[],"flags":[],"lives":[]}"""
        val file = DesktopConfigStore.write(name, json)
        return Config.find("file://" + file.absolutePath.replace('\\', '/'), name, 0).save()
    }

    /** 兼容传参带 /vod/ 后带路径或查询参数的情况，截取到标准采集根 */
    private fun normalizeCms(url: String): String {
        val index = url.indexOf(CMS_MARK)
        val root = url.substring(0, index + CMS_MARK.length)
        val suffix = if (url.endsWith("/")) "/" else ""
        return root + suffix
    }

    private fun displayName(url: String): String {
        return runCatching {
            val host = url.removePrefix("http://").removePrefix("https://").substringBefore('/')
            URLDecoder.decode(host, StandardCharsets.UTF_8)
        }.getOrDefault("CMS 源")
    }
}
