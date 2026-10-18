package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;

/**
 * 桌面端 com.fongmi.android.tv.api.config.WallConfig 替换实现：
 * 桌面端无壁纸特性，仅保持 API 形状（needSync/config 等继承 BaseConfig）
 */
public class WallConfig extends BaseConfig {

    private static final String TAG = WallConfig.class.getSimpleName();

    private static class Loader {
        static volatile WallConfig INSTANCE = new WallConfig();
    }

    public static WallConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public WallConfig config(Config config) {
        this.config = config;
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.wall();
    }

    @Override
    protected void load(Config config) {
    }

    @Override
    protected boolean isLoaded() {
        return false;
    }
}
