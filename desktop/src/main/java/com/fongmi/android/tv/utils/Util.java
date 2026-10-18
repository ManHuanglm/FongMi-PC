package com.fongmi.android.tv.utils;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Formatter;

/**
 * 桌面端 com.fongmi.android.tv.utils.Util 替换实现：
 * 仅保留挂载代码引用的纯逻辑方法（系统 UI/剪贴板/输入法方法不提供）
 */
public class Util {

    private static final String SERIAL = "desktop-" + Long.toHexString(System.currentTimeMillis());

    private Util() {
    }

    public static int getNumber(String text) {
        try {
            return Integer.parseInt(clean(text));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String clean(String text) {
        return text == null ? "" : text.replaceAll("[^0-9]", "");
    }

    public static String getAndroidId() {
        return SERIAL;
    }

    public static String getSerial() {
        return SERIAL;
    }

    public static String getMac(String name) {
        try {
            for (Enumeration<NetworkInterface> items = NetworkInterface.getNetworkInterfaces(); items.hasMoreElements(); ) {
                NetworkInterface item = items.nextElement();
                if (item.getHardwareAddress() == null) continue;
                Formatter formatter = new Formatter();
                for (byte b : item.getHardwareAddress()) formatter.format("%02X:", b);
                String mac = formatter.toString().replaceAll(":$", "");
                return mac;
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    public static String getDeviceName() {
        return System.getProperty("os.name", "PC") + " " + System.getProperty("os.arch", "");
    }

    public static boolean isLeanback() {
        return false;
    }

    public static boolean isMobile() {
        return false;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }

    public static String timeMs(long timeMs) {
        return format(new StringBuilder(), new Formatter(), timeMs);
    }

    public static String format(StringBuilder builder, Formatter formatter, long timeMs) {
        try {
            long seconds = Math.abs(timeMs) / 1000;
            builder.setLength(0);
            formatter.format("%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
            return formatter.toString();
        } finally {
            if (formatter != null) formatter.close();
        }
    }
}
