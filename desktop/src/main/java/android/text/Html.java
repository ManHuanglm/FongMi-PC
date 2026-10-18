package android.text;

import java.util.regex.Pattern;

/**
 * 桌面端 android.text.Html 等价实现：fromHtml 直接剥离标签（桌面端无富文本展示需求）
 */
public class Html {

    public static final int FROM_HTML_MODE_LEGACY = 0;
    public static final int FROM_HTML_MODE_COMPACT = 63;

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    private Html() {
    }

    public static Spanned fromHtml(String source, int flags) {
        final String text = source == null ? "" : TAGS.matcher(source).replaceAll("").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return new Spanned() {
            @Override
            public String toString() {
                return text;
            }
        };
    }
}
