package android.content;

/**
 * 桌面端 android.content.PackageManager 编译桩（桌面端无包管理器语义）
 */
public class PackageManager {

    public String getPackageInfoName(String packageName) {
        return packageName;
    }
}
