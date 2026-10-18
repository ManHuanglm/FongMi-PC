package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.R;

/**
 * 桌面端 com.fongmi.android.tv.utils.ResUtil 替换实现：
 * 按桌面 R 桩 ID 返回中文文案（与安卓 values-zh 文案对齐）
 */
public class ResUtil {

    private ResUtil() {
    }

    public static String getString(int resId) {
        return getString(resId, new Object[0]);
    }

    public static String getString(int resId, Object... formatArgs) {
        String value = resolve(resId);
        return formatArgs.length == 0 ? value : String.format(value, formatArgs);
    }

    private static String resolve(int resId) {
        if (resId == R.string.all) return "全部";
        if (resId == R.string.copied) return "已复制";
        if (resId == R.string.error_config_get) return "获取配置失败，请检查网络或配置地址";
        if (resId == R.string.error_empty) return "数据为空";
        if (resId == R.string.error_file_open) return "文件打开失败";
        if (resId == R.string.keep) return "收藏";
        if (resId == R.string.live_line) return "线路";
        if (resId == R.string.none) return "无";
        if (resId == R.string.parse_god) return "嗅探";
        if (resId == R.string.play_backward) return "倒放";
        if (resId == R.string.play_backward_hint) return "倒放提示";
        if (resId == R.string.play_forward) return "顺播";
        if (resId == R.string.play_forward_hint) return "顺播提示";
        if (resId == R.string.push) return "推送";
        if (resId == R.string.push_image) return "推送图片";
        if (resId == R.string.setting_live) return "直播";
        if (resId == R.string.setting_off) return "关闭";
        if (resId == R.string.setting_on) return "开启";
        return "";
    }
}
