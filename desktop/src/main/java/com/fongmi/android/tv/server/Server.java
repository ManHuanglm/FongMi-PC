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
 * 内嵌最小 NanoHTTPD（仅 /file/ 本地文件服务，满足本地配置与资源加载）；代理/推送随 M4 接入
 */
public class Server {

    private static final int PORT_MIN = 9978;
    private static final int PORT_MAX = 9999;

    private volatile Nano nano;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
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

    private static class Nano extends NanoHTTPD {

        Nano(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if (uri.startsWith("/file/")) return serveFile(uri.substring("/file/".length()));
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
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
