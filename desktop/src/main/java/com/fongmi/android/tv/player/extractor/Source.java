package com.fongmi.android.tv.player.extractor;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;

/**
 * 桌面端 com.fongmi.android.tv.player.extractor.Source 替换实现：
 * M1 阶段不做磁力/网盘等特殊链接解析，保持 API 形状
 */
public class Source {

    private static class Loader {
        static volatile Source INSTANCE = new Source();
    }

    public static Source get() {
        return Loader.INSTANCE;
    }

    public void parse(Vod vod) {
    }

    public void stop() {
    }

    public String fetch(Result result) {
        return result.getUrl().v();
    }
}
