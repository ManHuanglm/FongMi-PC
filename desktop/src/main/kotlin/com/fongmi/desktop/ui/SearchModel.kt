package com.fongmi.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fongmi.android.tv.Constant
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.api.SiteApi
import com.fongmi.android.tv.bean.Result
import com.fongmi.android.tv.bean.Site
import com.fongmi.android.tv.bean.Vod
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Prefers
import com.github.catvod.utils.Trans
import com.google.common.net.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 桌面端聚合搜索中枢：对齐安卓 SiteViewModel/ViewModelSearchRunner 语义
 * （isSearchable 源并行搜索、单源超时、结果上限、热词缓存）
 */
class SearchModel {

    companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val RESULT_LIMIT = 50
        private const val HISTORY_LIMIT = 10
        private const val HISTORY_KEY = "desktop_search_history"
        private const val HOT_URL = "https://api.web.360kan.com/v1/rank?cat=1"
    }

    var keyword by mutableStateOf("")
    var searching by mutableStateOf(false)
    var finished by mutableStateOf(0)
    var totalSites by mutableStateOf(0)
    var results by mutableStateOf<List<Vod>>(emptyList())
    var hotWords by mutableStateOf<List<String>>(emptyList())
    var suggestWords by mutableStateOf<List<String>>(emptyList())
    var history by mutableStateOf<List<String>>(emptyList())
    var error by mutableStateOf("")

    private val jobs = mutableListOf<Job>()

    init {
        history = loadHistory()
        loadHotWords()
    }

    /** 可搜索站点（与安卓 CollectFragment 一致：isSearchable 过滤） */
    fun searchableSites(): List<Site> = VodConfig.get().getSites().filter { it.isSearchable }

    /** 聚合搜索：全部可搜索源并行，单源限时，结果上限 50 */
    fun search(text: String) {
        val word = text.trim()
        if (word.isEmpty()) return
        stopSearch()
        keyword = word
        searching = true
        finished = 0
        results = emptyList()
        error = ""
        suggestWords = emptyList()
        pushHistory(word)
        val sites = searchableSites()
        totalSites = sites.size
        if (sites.isEmpty()) {
            searching = false
            error = "当前订阅无可搜索站点"
            return
        }
        val simplified = Trans.t2s(word)
        sites.forEach { site ->
            jobs += scope.launch {
                val result = withTimeoutOrNull(Constant.TIMEOUT_SEARCH) {
                    runCatching { SiteApi.searchContent(site, simplified, false, "1") }.getOrElse { Result.empty() }
                } ?: Result.empty()
                onSiteResult(result)
            }
        }
    }

    private fun onSiteResult(result: Result) {
        finished += 1
        if (results.size >= RESULT_LIMIT) {
            if (finished >= totalSites) searching = false
            return
        }
        val list = result.getList()
        if (list.isEmpty()) {
            if (finished >= totalSites) searching = false
            return
        }
        results = (results + list).take(RESULT_LIMIT)
        if (finished >= totalSites) searching = false
    }

    /** 停止全部站点搜索（离开页面或重新搜索时调用） */
    fun stopSearch() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    /** 热词：360kan 榜单，失败回退本地缓存 */
    private fun loadHotWords() {
        scope.launch {
            val cached = WordCache.decode(Prefers.getString("hot"))
            if (cached.isNotEmpty()) hotWords = cached
            try {
                val body = OkHttp.string(HOT_URL, mapOf(HttpHeaders.REFERER to "https://www.360kan.com/rank/general"))
                val words = WordCache.decode(body)
                if (words.isNotEmpty()) {
                    hotWords = words
                    Prefers.put("hot", body)
                }
            } catch (e: Exception) {
                println("[SearchModel] 热词拉取失败，回退缓存: ${e.message}")
            }
        }
    }

    /** 输入联想（iqiyi suggest 接口，失败静默） */
    fun suggest(text: String) {
        suggestWords = emptyList()
        if (text.trim().isEmpty()) return
        scope.launch {
            try {
                val body = OkHttp.string("https://suggest.video.iqiyi.com/?if=mobile&key=" + java.net.URLEncoder.encode(text.trim(), "UTF-8"))
                suggestWords = WordCache.decode(body)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun loadHistory(): List<String> = WordCache.decode(Prefers.getString(HISTORY_KEY))

    private fun pushHistory(word: String) {
        val next = (listOf(word) + history.filter { it != word }).take(HISTORY_LIMIT)
        history = next
        Prefers.put(HISTORY_KEY, WordCache.encode(next))
    }

    fun clearHistory() {
        history = emptyList()
        Prefers.put(HISTORY_KEY, "[]")
    }
}

/** 词表 JSON 编解码（安卓端为 Word bean 的 data[].title/name 结构） */
private object WordCache {

    private val gson = com.google.gson.Gson()
    private val type = com.google.gson.reflect.TypeToken.getParameterized(
        List::class.java,
        com.google.gson.reflect.TypeToken.getParameterized(Map::class.java, String::class.java, Any::class.java).type
    ).type

    fun encode(words: List<String>): String = gson.toJson(words)

    fun decode(json: String): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val raw = if (json.trim().startsWith("[")) {
                gson.fromJson<MutableList<MutableMap<String, Any>>>(json, type).mapNotNull { entry -> entry["title"] ?: entry["name"] }
            } else {
                // 安卓端缓存的是完整 Word JSON（{"data":[...]} 结构）
                val word = com.fongmi.android.tv.bean.Word.objectFrom(json)
                word.data.map { it.title }
            }
            raw.map { it.toString() }.filter { it.isNotBlank() }.distinct()
        }.getOrDefault(emptyList())
    }
}
