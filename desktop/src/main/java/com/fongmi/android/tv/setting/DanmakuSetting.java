package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.github.catvod.utils.Prefers;

/**
 * 桌面端 com.fongmi.android.tv.setting.DanmakuSetting 替换实现：
 * 键名与安卓端保持一致（跨端共享偏好），渲染参数由桌面 ASS 转换管线消费
 * （安卓端的 DanmakuConfig/media3 依赖在桌面不存在，故整体重写）
 */
public class DanmakuSetting {

    private static final long MIN_DURATION_MS = 3000L;
    private static final long MAX_DURATION_MS = 15000L;
    private static final long MIN_TIME_OFFSET_MS = -300000L;
    private static final long MAX_TIME_OFFSET_MS = 300000L;
    private static final float MIN_TEXT_SCALE = 0.5f;
    private static final float MAX_TEXT_SCALE = 3.0f;
    private static final float MIN_TRANSPARENCY = 0.0f;
    private static final float MAX_TRANSPARENCY = 0.9f;

    public static boolean isLoad() {
        return Prefers.getBoolean("danmaku_load");
    }

    public static void putLoad(boolean danmakuLoad) {
        Prefers.put("danmaku_load", danmakuLoad);
        if (danmakuLoad) putShow(true);
    }

    public static boolean isAuto() {
        return Prefers.getBoolean("danmaku_auto", true);
    }

    public static void putAuto(boolean auto) {
        Prefers.put("danmaku_auto", auto);
    }

    public static boolean isShow() {
        return Prefers.getBoolean("danmaku_show", true);
    }

    public static void putShow(boolean danmakuShow) {
        Prefers.put("danmaku_show", danmakuShow);
    }

    public static String getApiUrl() {
        return Prefers.getString("danmaku_api_url", "");
    }

    public static void putApiUrl(String url) {
        Prefers.put("danmaku_api_url", url);
    }

    public static String getEffectiveApiUrl() {
        String userUrl = getApiUrl();
        if (!TextUtils.isEmpty(userUrl)) return userUrl;
        return VodConfig.get().getConfig().getDanmaku();
    }

    public static float getTextScale() {
        return clamp(Prefers.getFloat("danmaku_text_scale", 1f), MIN_TEXT_SCALE, MAX_TEXT_SCALE);
    }

    public static void putTextScale(float value) {
        Prefers.put("danmaku_text_scale", clamp(value, MIN_TEXT_SCALE, MAX_TEXT_SCALE));
    }

    public static float getTransparency() {
        return clamp(Prefers.getFloat("danmaku_transparency", 0f), MIN_TRANSPARENCY, MAX_TRANSPARENCY);
    }

    public static void putTransparency(float value) {
        Prefers.put("danmaku_transparency", clamp(value, MIN_TRANSPARENCY, MAX_TRANSPARENCY));
    }

    public static boolean isTextBold() {
        return Prefers.getBoolean("danmaku_text_bold");
    }

    public static void putTextBold(boolean value) {
        Prefers.put("danmaku_text_bold", value);
    }

    public static long getDurationMs() {
        return clampLong(Prefers.getLong("danmaku_duration", 8000L), MIN_DURATION_MS, MAX_DURATION_MS);
    }

    public static void putDurationMs(long value) {
        Prefers.put("danmaku_duration", clampLong(value, MIN_DURATION_MS, MAX_DURATION_MS));
    }

    public static long getTimeOffsetMs() {
        return clampLong(Prefers.getLong("danmaku_time_offset", 0L), MIN_TIME_OFFSET_MS, MAX_TIME_OFFSET_MS);
    }

    public static void putTimeOffsetMs(long value) {
        Prefers.put("danmaku_time_offset", clampLong(value, MIN_TIME_OFFSET_MS, MAX_TIME_OFFSET_MS));
    }

    /** 桌面端扩展：关键词屏蔽（顿号分隔），安卓端由 UI 层黑名单实现 */
    public static String getShield() {
        return Prefers.getString("danmaku_shield", "");
    }

    public static void putShield(String value) {
        Prefers.put("danmaku_shield", value == null ? "" : value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
