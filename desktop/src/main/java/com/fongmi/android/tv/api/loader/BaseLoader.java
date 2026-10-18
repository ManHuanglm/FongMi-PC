package com.fongmi.android.tv.api.loader;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

/**
 * 桌面端 com.fongmi.android.tv.api.loader.BaseLoader 替换实现：
 * M1 不加载 dex/jar/py/js 插件，Spider 源一律回退 SpiderNull（空数据，不崩溃）
 */
public class BaseLoader {

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    public void clear() {
    }

    public void parseJar(String jar, boolean force) {
        if (jar == null || jar.isEmpty()) return;
        System.out.println("[BaseLoader] 桌面端暂不支持插件源（dex/jar），已忽略: " + jar);
    }

    public void setRecent(String key, String api, String jar) {
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return new SpiderNull();
    }
}
