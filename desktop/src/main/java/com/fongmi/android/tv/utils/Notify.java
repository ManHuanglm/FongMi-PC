package com.fongmi.android.tv.utils;

import android.content.Context;

/**
 * 桌面端 com.fongmi.android.tv.utils.Notify 替换实现：M1 阶段输出到控制台，后续接入 Compose 提示层
 */
public class Notify {

    private Notify() {
    }

    public static void createChannel() {
    }

    public static String getError(int resId, Throwable e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.toString() : message;
    }

    public static void show(String text) {
        if (text == null || text.isEmpty()) return;
        System.out.println("[Notify] " + text);
    }

    public static void show(int resId) {
        show(ResUtil.getString(resId));
    }

    public static void progress(Context context) {
    }

    public static void dismiss() {
    }
}
