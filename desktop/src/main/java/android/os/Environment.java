package android.os;

import java.io.File;

/**
 * 桌面端 android.os.Environment 等价实现：外部存储根映射到用户数据目录
 */
public class Environment {

    private static File root;

    private Environment() {
    }

    /** 由桌面端启动时注入根目录（%APPDATA%/FongMiTV） */
    public static void setRoot(File directory) {
        root = directory;
    }

    public static File getExternalStorageDirectory() {
        if (root != null) return root;
        String appdata = System.getenv("APPDATA");
        return new File(appdata != null && !appdata.isEmpty() ? appdata : System.getProperty("user.home"), "FongMiTV");
    }

    public static boolean isExternalStorageManager() {
        return true;
    }
}
