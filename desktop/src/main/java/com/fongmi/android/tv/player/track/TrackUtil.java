package com.fongmi.android.tv.player.track;

import android.text.TextUtils;

import androidx.media3.common.MimeTypes;

import java.util.Locale;

/**
 * 桌面端 com.fongmi.android.tv.player.track.TrackUtil 替换实现：
 * 仅保留挂载代码（bean/Sub）引用的字幕格式推断方法，其余轨道选择逻辑属播放域不挂载
 */
public class TrackUtil {

    private TrackUtil() {
    }

    public static String getSubtitleMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (lower.endsWith(".ssa") || lower.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (lower.endsWith(".ttml") || lower.endsWith(".xml") || lower.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }
}
