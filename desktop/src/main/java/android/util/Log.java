package android.util;

/**
 * 桌面端 android.util.Log 等价实现（输出到标准流）
 */
public class Log {

    private Log() {
    }

    public static int d(String tag, String msg) {
        System.out.println("D/" + tag + ": " + msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.println("I/" + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.err.println("W/" + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.err.println("E/" + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        System.err.println("E/" + tag + ": " + msg);
        tr.printStackTrace();
        return 0;
    }
}
