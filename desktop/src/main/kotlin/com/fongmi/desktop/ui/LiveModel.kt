package com.fongmi.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fongmi.android.tv.api.LiveApi
import com.fongmi.android.tv.api.config.LiveConfig
import com.fongmi.android.tv.bean.Channel
import com.fongmi.android.tv.bean.Config
import com.fongmi.android.tv.bean.Epg
import com.fongmi.android.tv.bean.EpgData
import com.fongmi.android.tv.bean.Group
import com.fongmi.android.tv.bean.Live
import com.fongmi.android.tv.bean.Keep
import com.fongmi.android.tv.impl.Callback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 桌面端直播状态中枢：对齐安卓 LiveActivity/LiveViewModel 数据流语义
 * （LiveConfig 装载 → 分组/频道列表 → LiveApi.getUrl 取流 → Keep 收藏记忆）
 */
class LiveModel {

    companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf("")
    var message by mutableStateOf("未配置直播源\n可在配置页添加直播订阅，或使用点播订阅内嵌的直播源")
    var liveName by mutableStateOf("")
    var groups by mutableStateOf<List<Group>>(emptyList())
    var groupIndex by mutableStateOf(0)
    var epg by mutableStateOf<Epg?>(null)
    var currentChannel by mutableStateOf<Channel?>(null)

    /** 当前播放请求（频道切换/换线路/回看共用，由 Main 层观察并送入播放器） */
    var playRequest by mutableStateOf<PlayRequest?>(null)
    var playError by mutableStateOf("")

    private var zoneId: ZoneId = ZoneId.systemDefault()

    fun activeGroup(): Group? = groups.getOrNull(groupIndex)

    fun channels(groupIndex: Int): List<Channel> = groups.getOrNull(groupIndex)?.getChannel() ?: emptyList()

    /** 进入直播页时调用：优先复用订阅内嵌直播，其次加载本地保存的直播配置 */
    fun ensureReady() {
        if (loading) return
        if (loaded && groups.isNotEmpty()) return
        val saved = Config.live()
        if (saved.getUrl().isNotEmpty()) loadUrl(saved.getUrl()) else refreshHome()
    }

    /** 加载直播源地址（M3U/TXT/JSON 订阅，或点播订阅同步内嵌） */
    fun loadUrl(url: String) {
        loading = true
        error = ""
        message = "正在加载直播源..."
        scope.launch {
            try {
                val config = Config.find(url.trim(), "直播源", 1).save()
                LiveConfig.load(config, object : Callback() {
                    override fun success() {
                        refreshHome()
                    }

                    override fun error(msg: String) {
                        loading = false
                        error = msg.ifEmpty { "直播源加载失败" }
                        println("[LiveModel] 直播源加载失败: $error")
                    }
                })
            } catch (e: Exception) {
                loading = false
                error = e.message ?: e.toString()
                e.printStackTrace()
            }
        }
    }

    /** 加载当前订阅链路同步下来的直播配置（VodConfig.load 内部已同步 LiveConfig） */
    fun refreshHome() {
        scope.launch {
            try {
                LiveConfig.get().ensureLoaded()
                val home: Live = LiveConfig.get().getHome()
                if (home.isEmpty() || home.getGroups().isEmpty()) {
                    loading = false
                    return@launch
                }
                zoneId = home.getZoneId()
                liveName = home.getName().ifEmpty { LiveConfig.get().getConfig().getName() }
                groups = home.getGroups()
                loaded = true
                loading = false
                error = ""
                // 恢复上次观看位置（Keep 落地记忆），仅定位不自动播放
                val position = LiveConfig.get().findKeepPosition(groups)
                if (position[1] > 0 || position[0] > 0) {
                    groupIndex = position[0]
                    currentChannel = groups.getOrNull(position[0])?.getChannel()?.getOrNull(position[1])
                    currentChannel?.let { loadEpg(it) }
                } else {
                    groupIndex = 0
                }
                println("[LiveModel] 直播就绪: live=$liveName, groups=${groups.size}")
            } catch (e: Exception) {
                loading = false
                error = e.message ?: e.toString()
                e.printStackTrace()
            }
        }
    }

    fun selectGroup(index: Int) {
        groupIndex = index
    }

    /** 选择频道：刷新 EPG 并直连播放 */
    fun play(channel: Channel) {
        currentChannel = channel
        loadEpg(channel)
        LiveConfig.get().setKeep(channel)
        playError = ""
        scope.launch {
            try {
                val result = LiveApi.getUrl(channel)
                val url = result.getUrl().v()
                if (url.isEmpty()) {
                    playError = "未获取到播放地址"
                    return@launch
                }
                playRequest = PlayRequest(
                    siteKey = "live",
                    vodId = channel.getName(),
                    vodName = channel.getShow(),
                    vodPic = channel.getLogo(),
                    flagFlag = "",
                    episodeName = channel.getLine().ifEmpty { "直播" },
                    episodeUrl = "",
                    directUrl = url,
                    headers = result.getHeader(),
                    isLive = true
                )
                println("[LiveModel] 播放频道: ${channel.getShow()} -> $url")
            } catch (e: Exception) {
                playError = e.message ?: e.toString()
                e.printStackTrace()
            }
        }
    }

    /** 切换线路后重播当前频道 */
    fun switchLine(next: Boolean) {
        val channel = currentChannel ?: return
        if (channel.isOnly()) return
        channel.switchLine(next)
        play(channel)
    }

    /** 节目单回看（时移）；未播出的节目不可回看 */
    fun catchup(data: EpgData) {
        val channel = currentChannel ?: return
        if (data.isFuture() || !channel.hasCatchup()) {
            playError = if (data.isFuture()) "该节目尚未播出，暂不支持预约" else "该频道不支持时移回看"
            return
        }
        playError = ""
        scope.launch {
            try {
                val result = LiveApi.getUrl(channel, data)
                val url = result.getUrl().v()
                if (url.isEmpty()) {
                    playError = "回看地址获取失败"
                    return@launch
                }
                playRequest = PlayRequest(
                    siteKey = "live",
                    vodId = channel.getName(),
                    vodName = channel.getShow(),
                    vodPic = channel.getLogo(),
                    flagFlag = "",
                    episodeName = data.getTitle(),
                    episodeUrl = "",
                    directUrl = url,
                    headers = result.getHeader(),
                    isLive = true
                )
                println("[LiveModel] 回看: ${channel.getShow()} ${data.format()}")
            } catch (e: Exception) {
                playError = e.message ?: e.toString()
                e.printStackTrace()
            }
        }
    }

    /** 装载频道节目单（含昨日/明日跨界，删除线数据由 LiveApi.getEpg 聚合） */
    private fun loadEpg(channel: Channel) {
        scope.launch {
            try {
                epg = LiveApi.getEpg(channel, zoneId)
            } catch (e: Exception) {
                epg = Epg()
                e.printStackTrace()
            }
        }
    }

    /** 频道收藏状态（Keep 表 type=1，key=频道名，与安卓直播收藏互通） */
    fun isKeep(channel: Channel): Boolean = Keep.exist(channel.getName())

    fun toggleKeep(channel: Channel) {
        val added = !Keep.exist(channel.getName())
        if (added) {
            val keep = Keep()
            keep.setKey(channel.getName())
            keep.setVodName(channel.getShow())
            keep.setSiteName(liveName)
            keep.setType(1)
            keep.setCreateTime(System.currentTimeMillis())
            keep.save()
        } else {
            Keep.delete(channel.getName())
        }
        // 本地同步收藏分组（下标 0，由 LiveApi.parse 创建），与安卓 LiveActivity 长按收藏行为一致
        val keepGroup = groups.firstOrNull { it.isKeep() }
        if (added && keepGroup != null) keepGroup.add(channel)
        if (!added && keepGroup != null) keepGroup.getChannel().removeIf { it.getName() == channel.getName() }
        groups = ArrayList(groups)
    }

    /** 清空播放请求（播放页返回后复位） */
    fun clearPlay() {
        playRequest = null
        playError = ""
    }
}
