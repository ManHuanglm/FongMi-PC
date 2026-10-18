package com.fongmi.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fongmi.android.tv.api.SiteApi
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.bean.Class
import com.fongmi.android.tv.bean.Config
import com.fongmi.android.tv.bean.Filter
import com.fongmi.android.tv.bean.Site
import com.fongmi.android.tv.bean.Vod
import com.fongmi.android.tv.impl.Callback
import com.fongmi.desktop.compat.DesktopConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面端点播状态中枢：对齐安卓 VodFragment/FolderFragment/TypeFragment 的数据流语义
 * （homeContent 装载分类 → categoryContent 分页加载 → extend 筛选重载）
 */
class AppModel {

    companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    var loading by mutableStateOf(false)
    var message by mutableStateOf("未指定订阅配置\n启动示例: gradlew run --args=<订阅URL 或 AppleCMS 采集地址>")
    var loaded by mutableStateOf(false)
    var sites by mutableStateOf<List<Site>>(emptyList())
    var siteName by mutableStateOf("")
    var types by mutableStateOf<List<Class>>(emptyList())
    var activeIndex by mutableStateOf(0)
    var vods by mutableStateOf<List<Vod>>(emptyList())
    var page by mutableStateOf("1")
    var end by mutableStateOf(false)
    var error by mutableStateOf("")

    private var extend: MutableMap<String, String> = HashMap()

    fun activeType(): Class? = types.getOrNull(activeIndex)

    fun currentFilters(): List<Filter> = activeType()?.getFilters() ?: emptyList()

    /** 当前筛选键的选中值（含默认 init 值） */
    fun selectedValue(key: String): String = extend[key] ?: currentFilters().find { it.getKey() == key }?.getInit() ?: ""

    fun homeSite(): Site = VodConfig.get().getHome()

    /** 加载订阅配置（常规 URL 或裸 AppleCMS 采集地址） */
    fun loadConfig(url: String) {
        loading = true
        error = ""
        message = "正在加载配置..."
        scope.launch {
            try {
                val config: Config = DesktopConfig.resolve(url)
                VodConfig.load(config, object : Callback() {
                    override fun success() {
                        onConfigReady()
                    }

                    override fun error(msg: String) {
                        loading = false
                        error = msg.ifEmpty { "配置加载失败" }
                        println("[AppModel] 配置加载失败: $error")
                    }
                })
            } catch (e: Exception) {
                loading = false
                error = e.message ?: e.toString()
                e.printStackTrace()
            }
        }
    }

    private fun onConfigReady() {
        sites = VodConfig.get().getSites()
        // 桌面端插件源（dex/jar，type=3）由 SpiderNull 回退为空数据，默认首页自动切到首个 JSON CMS 源
        val home = homeSite()
        if (home.getType() == 3 || home.getType() == 0) {
            val candidate = sites.firstOrNull { it.getType() == 1 && it.getApi().startsWith("http") }
                ?: sites.firstOrNull { it.getType() != 3 && it.getApi().startsWith("http") }
            candidate?.let {
                VodConfig.get().setHome(it)
                println("[AppModel] 首页源不可直接浏览，已自动切换: ${it.getName()} (type=${it.getType()})")
            }
        }
        siteName = listOf(homeSite().getName(), VodConfig.get().getConfig().getName()).firstOrNull { it.isNotEmpty() } ?: "FongMi TV PC"
        println("[AppModel] 配置就绪: sites=${sites.size}, home=${homeSite().getName()}")
        homeContent()
    }

    /** 首页内容：分类与筛选装载 */
    fun homeContent() {
        loading = true
        error = ""
        scope.launch {
            try {
                val result = SiteApi.homeContent(homeSite())
                types = result.getTypes()
                activeIndex = 0
                selectType(0)
            } catch (e: Exception) {
                loading = false
                error = e.message ?: e.toString()
                e.printStackTrace()
            }
        }
    }

    /** 切换站点（首页源） */
    fun changeSite(site: Site) {
        if (site.getKey() == homeSite().getKey()) return
        VodConfig.get().setHome(site)
        siteName = site.getName()
        homeContent()
    }

    /** 切换分类并加载第一页 */
    fun selectType(index: Int) {
        activeIndex = index
        extend = HashMap()
        currentFilters().forEach { filter -> filter.getInit()?.let { extend[filter.getKey()] = filter.setSelected(filter.getInit()) } }
        page = "1"
        end = false
        vods = emptyList()
        loadPage("1")
    }

    /** 筛选值选项（对齐安卓 Filter.getValue()） */
    fun filterValues(filter: Filter) = filter.getValue()

    /** 筛选变更重载 */
    fun applyFilter(key: String, value: String) {
        extend[key] = value
        page = "1"
        end = false
        vods = emptyList()
        loadPage("1")
    }

    /** 清除筛选 */
    fun clearFilters() {
        extend = HashMap()
        selectType(activeIndex)
    }

    /** 加载下一页（滚动到底触发） */
    fun loadMore() {
        if (loading || end) return
        loadPage((page.toIntOrNull() ?: 1).plus(1).toString())
    }

    private fun loadPage(pageNo: String) {
        val type = activeType() ?: run { loading = false; return }
        loading = true
        error = ""
        scope.launch {
            try {
                val result = SiteApi.categoryContent(homeSite().getKey(), type.getTypeId(), pageNo, true, HashMap(extend))
                page = pageNo
                if (pageNo == "1") vods = result.getList() else vods = vods + result.getList()
                end = result.getList().isEmpty()
                loading = false
                println("[AppModel] 分类[${type.getTypeName()}] 第${pageNo}页加载成功, 本页=${result.getList().size}, 累计=${vods.size}")
            } catch (e: Exception) {
                loading = false
                if (pageNo == "1") error = e.message ?: e.toString() else end = true
                e.printStackTrace()
            }
        }
    }
}
