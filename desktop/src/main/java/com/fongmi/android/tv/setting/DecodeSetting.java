package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

/**
 * 桌面端 com.fongmi.android.tv.setting.DecodeSetting 替换实现：
 * 桌面端无 ExoPlayer 隧道渲染概念，仅保持 PlayerSetting 引用的 API（状态按安卓 key 存取）
 */
public class DecodeSetting {

    private static final String TUNNEL = "tunnel";

    public static boolean isTunnel() {
        return Prefers.getBoolean(TUNNEL);
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put(TUNNEL, tunnel);
    }
}
