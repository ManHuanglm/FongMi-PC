package com.fongmi.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fongmi.android.tv.api.SiteApi
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.bean.History
import com.fongmi.android.tv.bean.Result
import com.fongmi.android.tv.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 播放请求（详情页选集 / 直播频道 / demo 直连共用；directUrl 跳过 playerContent） */
data class PlayRequest(
    val siteKey: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val flagFlag: String,
    val episodeName: String,
    val episodeUrl: String,
    val directUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isLive: Boolean = false,
)

/**
 * 播放链路中枢：playerContent 解析 → 直接播放 / 明确提示（parse=1 属 M5 嗅探域）
 * 进度记忆复用安卓 History 表（key = siteKey@@@vodId@@@cid）
 */
class PlayerModel {

    companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    var loading by mutableStateOf(true)
    var error by mutableStateOf("")
    var notice by mutableStateOf("")
    var playUrl by mutableStateOf("")
    var startSeconds by mutableStateOf(0L)
    var headers by mutableStateOf<Map<String, String>>(emptyMap())
    var title by mutableStateOf("")
    var pendingSniff by mutableStateOf<String?>(null)

    private var history: History? = null
    private var historyCid: Int = 0

    /** 解析选集播放地址并恢复断点 */
    fun resolve(request: PlayRequest, onReady: (String, Long, Map<String, String>) -> Unit) {
        loading = true
        error = ""
        notice = ""
        title = "${request.vodName} ${request.episodeName}"
        scope.launch {
            try {
                // 直播无断点记忆
                var resume = 0L
                if (!request.isLive) {
                    val cid = VodConfig.getCid()
                    historyCid = cid
                    val key = request.siteKey + AppDatabase.SYMBOL + request.vodId + AppDatabase.SYMBOL + cid
                    history = History.find(key)
                    val start = history?.getPosition()?.takeIf { it > 0 } ?: 0L
                    val duration = history?.getDuration()?.takeIf { it > 0 } ?: 0L
                    resume = if (duration > 0 && start < duration - 15) start else 0L
                }

                // demo/直连/直播模式：跳过 playerContent
                request.directUrl?.let { direct ->
                    playUrl = direct
                    startSeconds = resume
                    loading = false
                    println("[Player] 直连播放: $direct, 断点=$resume 秒")
                    onReady(direct, resume, request.headers)
                    return@launch
                }

                val result: Result = SiteApi.playerContent(request.siteKey, request.flagFlag, request.episodeUrl)
                val directUrl = result.getUrl().v()
                val parse = result.getParse()
                val fallbackUrl = result.getPlayUrl()
                val responseHeaders = result.getHeader()

                when {
                    parse == 1 -> {
                        loading = false
                        headers = responseHeaders
                        // M5：网页解析走 JCEF 嗅探（PlayerScreen 观察 pendingSniff 打开嗅探窗口）
                        val page = if (directUrl.isNotEmpty()) directUrl else fallbackUrl
                        if (page.startsWith("http")) {
                            pendingSniff = page
                            println("[Player] parse=1 进入嗅探: $page")
                        } else {
                            notice = "网页解析地址无效，暂无法播放"
                            println("[Player] parse=1 无效地址: $page")
                        }
                    }
                    directUrl.isNotEmpty() -> {
                        playUrl = directUrl
                        startSeconds = resume
                        headers = responseHeaders
                        loading = false
                        println("[Player] 播放地址就绪: $directUrl, 断点=$resume 秒")
                        onReady(directUrl, resume, responseHeaders)
                    }
                    fallbackUrl.isNotEmpty() -> {
                        playUrl = fallbackUrl
                        startSeconds = resume
                        headers = responseHeaders
                        loading = false
                        println("[Player] playUrl 就绪: $fallbackUrl")
                        onReady(fallbackUrl, resume, responseHeaders)
                    }
                    else -> {
                        loading = false
                        error = result.getMsg().ifEmpty { "未获取到播放地址" }
                    }
                }
            } catch (e: Exception) {
                loading = false
                error = e.message ?: e.toString()
                e.printStackTrace()
            }
        }
    }

    /** 保存进度（播放中周期调用与退出时调用；直播与无痕模式不落 History） */
    fun saveProgress(request: PlayRequest, positionSec: Long, durationSec: Long) {
        if (request.isLive) return
        if (com.fongmi.android.tv.setting.Setting.isIncognito()) return
        if (positionSec <= 0 || durationSec <= 0) return
        scope.launch {
            runCatching {
                val target = history ?: History().also { created ->
                    history = created
                }
                target.setKey(request.siteKey + AppDatabase.SYMBOL + request.vodId + AppDatabase.SYMBOL + historyCid)
                target.setVodName(request.vodName)
                target.setVodPic(request.vodPic)
                target.setVodFlag(request.flagFlag)
                target.setEpisodeUrl(request.episodeUrl)
                target.setCid(historyCid)
                target.setPosition(positionSec)
                target.setDuration(durationSec)
                target.setCreateTime(System.currentTimeMillis())
                AppDatabase.get().getHistoryDao().insertOrUpdate(target)
            }.onFailure { it.printStackTrace() }
        }
    }
}
