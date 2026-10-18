package com.fongmi.android.tv.server;

import com.github.catvod.Proxy;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 桌面端 com.fongmi.android.tv.server.Server 替换实现：
 * NanoHTTPD 承载 /file/（本地资源）+ /action（手机推送配置/设置/搜索）+ /device（设备信息）
 * 推送事件经 Listener 桥接至桌面 UI 层（Main.kt 订阅后驱动 AppModel/LiveModel）
 */
public class Server {

    private static final int PORT_MIN = 9978;
    private static final int PORT_MAX = 9999;

    private volatile Nano nano;
    private volatile Listener listener;

    /** 桌面 UI 层事件桥（推送/设置/搜索） */
    public interface Listener {

        void onPushConfig(String url);

        void onSettingConfig(String text, String name);

        void onSearch(String word);
    }

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    public synchronized void start() {
        if (nano != null) return;
        for (int port = PORT_MIN; port < PORT_MAX; port++) {
            try {
                nano = new Nano(port);
                nano.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Proxy.set(port);
                System.out.println("[Server] 本地服务已启动: " + getAddress());
                return;
            } catch (Throwable e) {
                nano = null;
            }
        }
        System.err.println("[Server] 本地服务启动失败（端口占用）");
    }

    public synchronized void stop() {
        if (nano != null) nano.stop();
        nano = null;
    }

    private void dispatchPush(String url) {
        Listener target = listener;
        if (target != null && url != null && !url.isEmpty()) target.onPushConfig(url);
    }

    private void dispatchSetting(String text, String name) {
        Listener target = listener;
        if (target != null && text != null && !text.isEmpty()) target.onSettingConfig(text, name == null ? "" : name);
    }

    private void dispatchSearch(String word) {
        Listener target = listener;
        if (target != null && word != null && !word.isEmpty()) target.onSearch(word);
    }

    private static class Nano extends NanoHTTPD {

        Nano(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if (uri.startsWith("/file/")) return serveFile(uri.substring("/file/".length()));
            if (uri.startsWith("/action")) return serveAction(session);
            if (uri.startsWith("/device")) return newFixedLengthResponse(Response.Status.OK, "application/json", deviceInfo());
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }

        /** 手机端推送：/action?do=push&url=<订阅地址> ｜ do=setting&text=<配置JSON>&name= ｜ do=search&word= */
        private Response serveAction(IHTTPSession session) {
            Map<String, String> params = session.getParms();
            String action = params.get("do");
            if (action == null) return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
            switch (action) {
                case "push" -> Server.get().dispatchPush(params.get("url"));
                case "setting" -> Server.get().dispatchSetting(params.get("text"), params.get("name"));
                case "search" -> Server.get().dispatchSearch(params.get("word"));
            }
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
        }

        /** 设备信息（供手机端识别接收端） */
        private String deviceInfo() {
            return "{\"device\":\"FongMi TV PC\",\"type\":2,\"name\":\"FongMi TV PC\"}";
        }

        private Response serveFile(String path) {
            try {
                String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
                File file = Path.local("file:/" + decoded);
                if (!file.exists() || !file.isFile()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File Not Found");
                InputStream is = new FileInputStream(file);
                return newChunkedResponse(Response.Status.OK, guessMime(file), is);
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }

        private String guessMime(File file) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".json")) return "application/json";
            if (name.endsWith(".txt") || name.endsWith(".m3u")) return "text/plain";
            if (name.endsWith(".xml")) return "application/xml";
            if (name.endsWith(".jar")) return "application/java-archive";
            if (name.endsWith(".mp4")) return "video/mp4";
            return "application/octet-stream";
        }
    }
}
