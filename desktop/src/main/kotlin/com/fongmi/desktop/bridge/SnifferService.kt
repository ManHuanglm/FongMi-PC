package com.fongmi.desktop.bridge

import com.fongmi.android.tv.utils.Sniffer
import com.fongmi.desktop.compat.DesktopPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.IProgressHandler
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * M5 网页嗅探桥（对照安卓 utils/Sniffer + WebView 拦截）：
 * JCEF（jcefmaven）加载 parse=1 网页，onResourceLoadComplete 拦截视频地址
 * （Sniffer.isVideoFormat URL 正则 / video 类型 MIME），命中后交 mpv 播放
 * natives 首次运行自动下载（约 120MB，缓存于 %APPDATA%/FongMiTV/jcef）
 */
object SnifferService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var app: CefApp? = null

    @Volatile
    private var builder: CefAppBuilder? = null

    @Volatile
    private var frame: JFrame? = null

    @Volatile
    private var browser: CefBrowser? = null

    @Volatile
    private var statusLabel: JLabel? = null

    private val hit = AtomicBoolean(false)
    private var timeoutTimer: Timer? = null

    /** 当前嗅探状态文本（下载 natives/加载页面/已命中） */
    @Volatile
    var lastStatus: String = ""
        private set

    /**
     * 打开嗅探窗口加载页面
     * @param pageUrl parse 页地址（playerContent 返回的网页 URL）
     * @param onFound 命中视频直链回调（CEF 线程，调用方自行切线程）
     * @param onError 失败回调（natives 下载失败/超时/窗口关闭）
     */
    @Synchronized
    fun sniff(pageUrl: String, onFound: (String) -> Unit, onError: (String) -> Unit) {
        if (frame != null) {
            SwingUtilities.invokeLater {
                frame?.toFront()
            }
            return
        }
        hit.set(false)
        lastStatus = "正在初始化浏览器内核..."
        openWindow(pageUrl)
        scope.launch {
            try {
                val cef = ensureApp(onError) ?: return@launch
                SwingUtilities.invokeLater {
                    try {
                        val client = cef.createClient()
                        client.addRequestHandler(object : CefRequestHandlerAdapter() {
                            override fun getResourceRequestHandler(
                                browser: org.cef.browser.CefBrowser?,
                                frame: CefFrame?,
                                request: CefRequest?,
                                isNavigation: Boolean,
                                isDownload: Boolean,
                                requestInitiator: String?,
                                callback: org.cef.misc.BoolRef?
                            ): org.cef.handler.CefResourceRequestHandler {
                                return object : CefResourceRequestHandlerAdapter() {
                                    override fun onResourceLoadComplete(
                                        browser: CefBrowser?,
                                        frame: CefFrame?,
                                        request: CefRequest?,
                                        response: CefResponse?,
                                        status: org.cef.network.CefURLRequest.Status?,
                                        downloadBytes: Long
                                    ) {
                                        val url = request?.getURL() ?: return
                                        val mime = response?.getMimeType() ?: ""
                                        if (url == pageUrl) return
                                        if (isSniffHit(url, mime)) onHit(url, onFound)
                                    }
                                }
                            }
                        })
                        val target = client.createBrowser(pageUrl, false, false)
                        browser = target
                        (frame?.getContentPane() ?: return@invokeLater).apply {
                            removeAll()
                            add(target.uiComponent, BorderLayout.CENTER)
                            revalidate()
                            repaint()
                        }
                        setStatus("页面加载中，正在嗅探视频地址...")
                        startTimeout(onError)
                    } catch (e: Exception) {
                        onError("嗅探浏览器创建失败: ${e.message}")
                        closeWindow()
                    }
                }
            } catch (e: Exception) {
                onError(if (e.message.isNullOrEmpty()) "浏览器内核初始化失败" else e.message!!)
                closeWindow()
            }
        }
    }

    /** 命中判定：对齐安卓 Sniffer.isVideoFormat + video 类型 MIME 兜底 */
    private fun isSniffHit(url: String, mime: String): Boolean {
        if (mime.startsWith("video/") || mime.contains("mpegurl")) return true
        return runCatching { Sniffer.isVideoFormat(url) }.getOrDefault(false)
    }

    private fun onHit(url: String, onFound: (String) -> Unit) {
        if (!hit.compareAndSet(false, true)) return
        setStatus("已嗅探到视频地址")
        stopTimeout()
        SwingUtilities.invokeLater { closeWindow() }
        onFound(url)
    }

    private fun startTimeout(onError: (String) -> Unit) {
        stopTimeout()
        timeoutTimer = Timer(120_000) {
            if (!hit.get()) onError("嗅探超时（2 分钟内未发现视频地址）")
            closeWindow()
        }.apply { isRepeats = false; start() }
    }

    private fun stopTimeout() {
        timeoutTimer?.stop()
        timeoutTimer = null
    }

    /** 懒加载 CEF（进程内只初始化一次；natives 下载进度回传窗口标题） */
    private fun ensureApp(onError: (String) -> Unit): CefApp? {
        app?.let { return it }
        synchronized(this) {
            app?.let { return it }
            val localBuilder = CefAppBuilder()
            val installDir = File(DesktopPaths.cacheDir(), "jcef")
            installDir.mkdirs()
            localBuilder.setInstallDir(installDir)
            localBuilder.setProgressHandler(object : IProgressHandler {
                override fun handleProgress(state: EnumProgress, value: Float) {
                    when (state) {
                        EnumProgress.INITIALIZING -> setStatus("浏览器内核初始化中...")
                        EnumProgress.DOWNLOADING -> setStatus("浏览器内核下载中 ${(value * 100).toInt()}%（首次运行约 120MB）")
                        EnumProgress.EXTRACTING -> setStatus("浏览器内核解压中...")
                        EnumProgress.INITIALIZED -> setStatus("浏览器内核就绪")
                        else -> setStatus("浏览器内核准备中 $state")
                    }
                }
            })
            localBuilder.getCefSettings().windowless_rendering_enabled = false
            builder = localBuilder
            try {
                val cef = localBuilder.build()
                app = cef
                return cef
            } catch (e: Exception) {
                onError("浏览器内核安装失败（需联网下载 natives）: ${e.message}")
                return null
            }
        }
    }

    private fun openWindow(pageUrl: String) {
        SwingUtilities.invokeLater {
            val window = JFrame("FongMi Sniffer")
            window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            window.setSize(1120, 760)
            window.setLocationRelativeTo(null)
            val label = JLabel(lastStatus, JLabel.CENTER)
            statusLabel = label
            val closeButton = JButton("取消嗅探")
            closeButton.addActionListener { closeWindow() }
            val top = java.awt.Panel(FlowLayout(FlowLayout.LEFT)).apply {
                add(closeButton)
                add(label)
            }
            window.contentPane.add(top, BorderLayout.NORTH)
            window.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    closeWindow()
                }
            })
            frame = window
            window.isVisible = true
        }
    }

    private fun setStatus(text: String) {
        lastStatus = text
        SwingUtilities.invokeLater {
            frame?.title = "FongMi Sniffer - $text"
            statusLabel?.text = text
        }
    }

    @Synchronized
    fun closeWindow() {
        stopTimeout()
        SwingUtilities.invokeLater {
            val window = frame ?: return@invokeLater
            frame = null
            statusLabel = null
            runCatching {
                browser?.close(true)
            }
            browser = null
            window.dispose()
        }
    }

    /** 进程退出前释放 CEF（避免 JVM 挂起） */
    fun shutdown() {
        runCatching {
            closeWindow()
            app?.dispose()
        }
        app = null
    }
}
