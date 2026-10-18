package com.fongmi.android.tv;

import android.content.Context;

import com.fongmi.desktop.compat.DesktopPaths;
import com.github.catvod.Init;
import com.google.gson.Gson;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

/**
 * 桌面端 com.fongmi.android.tv.App 替换实现：
 * 保持安卓版静态 API（post/gson/time/get/activity），主线程回调映射到 Swing EDT
 */
public class App extends Context {

    private static volatile App instance;

    private final Gson gson;
    private final long time;
    private final ScheduledExecutorService scheduler;
    private final Map<Runnable, ScheduledFuture<?>> delayed;

    private App() {
        super("com.fongmi.android.tv", DesktopPaths.filesDir(), DesktopPaths.cacheDir(), DesktopPaths.assetsDir());
        this.gson = new Gson();
        this.time = System.currentTimeMillis();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "FongMi-App-Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.delayed = new ConcurrentHashMap<>();
    }

    public static App get() {
        return instance;
    }

    /** 桌面端启动入口：初始化全局实例与 catvod 上下文 */
    public static void init() {
        if (instance == null) {
            synchronized (App.class) {
                if (instance == null) {
                    instance = new App();
                    Init.set(instance);
                }
            }
        }
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return null;
    }

    public static void post(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().delayed.remove(runnable);
        if (delayMillis < 0) return;
        ScheduledFuture<?> future = get().scheduler.schedule(() -> SwingUtilities.invokeLater(runnable), delayMillis, TimeUnit.MILLISECONDS);
        get().delayed.put(runnable, future);
    }

    public static void removeCallbacks(Runnable runnable) {
        ScheduledFuture<?> future = get().delayed.remove(runnable);
        if (future != null) future.cancel(false);
    }

    public static void removeCallbacks(Runnable... runnables) {
        for (Runnable runnable : runnables) removeCallbacks(runnable);
    }

    /** 桌面端无需 Activity 生命周期类型，占位保持 API 形状 */
    public static final class Activity {
    }
}
