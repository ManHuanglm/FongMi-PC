package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

/**
 * 桌面端 com.fongmi.android.tv.setting.DanmakuSetting 替换实现：
 * M1 无弹幕播放链路，isLoad 恒为 false；开关状态仍按安卓 key 存取以便后续接入
 */
public class DanmakuSetting {

    private static final String LOAD = "danmaku_load";

    public static boolean isLoad() {
        return Prefers.getBoolean(LOAD);
    }

    public static DanmakuSetting load(boolean load) {
        Prefers.put(LOAD, load);
        return new DanmakuSetting();
    }
}
