package android.content.res;

/**
 * 桌面端 android.content.res.Resources 编译桩：资源 ID 无桌面语义，返回空值
 */
public class Resources {

    public String getString(int resId) {
        return "";
    }

    public String[] getStringArray(int resId) {
        return new String[0];
    }

    public Configuration getConfiguration() {
        return new Configuration();
    }
}
