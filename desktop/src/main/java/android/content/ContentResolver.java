package android.content;

/**
 * 桌面端 android.content.ContentResolver 编译桩（仅覆盖挂载代码用到的常量）
 */
public class ContentResolver {

    public static final String SCHEME_FILE = "file";
    public static final String SCHEME_CONTENT = "content";
    public static final String SCHEME_ANDROID_RESOURCE = "android.resource";

    private ContentResolver() {
    }
}
