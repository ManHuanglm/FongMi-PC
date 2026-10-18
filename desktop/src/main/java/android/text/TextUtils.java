package android.text;

import java.util.List;

/**
 * 桌面端 android.text.TextUtils 等价实现（行为与安卓版对齐）
 */
public class TextUtils {

    private TextUtils() {
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) return a.equals(b);
            for (int i = 0; i < length; i++) if (a.charAt(i) != b.charAt(i)) return false;
            return true;
        }
        return false;
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object token : tokens) {
            if (first) first = false;
            else sb.append(delimiter);
            sb.append(token);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object token : tokens) {
            if (first) first = false;
            else sb.append(delimiter);
            sb.append(token);
        }
        return sb.toString();
    }

    public static String join(List<String> list, CharSequence delimiter) {
        return join(delimiter, (Iterable) list);
    }
}
