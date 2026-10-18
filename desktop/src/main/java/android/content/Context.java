package android.content;

import java.io.File;

import android.content.res.AssetManager;
import android.content.res.Resources;

/**
 * 桌面端 android.content.Context 等价实现：提供文件目录/资源/系统服务的桌面语义
 */
public class Context {

    public static final String WIFI_SERVICE = "wifi";

    private final String packageName;
    private final File filesDir;
    private final File cacheDir;
    private final File assetsDir;
    private final Resources resources;
    private final AssetManager assets;

    public Context(String packageName, File filesDir, File cacheDir, File assetsDir) {
        this.packageName = packageName;
        this.filesDir = filesDir;
        this.cacheDir = cacheDir;
        this.assetsDir = assetsDir;
        this.assets = new AssetManager(assetsDir);
        this.resources = new Resources();
    }

    public String getPackageName() {
        return packageName;
    }

    public File getFilesDir() {
        return filesDir;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public Context getApplicationContext() {
        return this;
    }

    public Resources getResources() {
        return resources;
    }

    public AssetManager getAssets() {
        return assets;
    }

    public Object getSystemService(String name) {
        if (WIFI_SERVICE.equals(name)) return new android.net.wifi.WifiManager();
        return null;
    }

    public PackageManager getPackageManager() {
        return new PackageManager();
    }
}
