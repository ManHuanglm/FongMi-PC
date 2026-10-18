package android.text;

/**
 * 桌面端 android.text.SpannableStringBuilder 编译桩（仅满足 Sniffer 编译）
 */
public class SpannableStringBuilder {

    private final StringBuilder builder = new StringBuilder();

    public SpannableStringBuilder append(CharSequence text) {
        builder.append(text);
        return this;
    }

    public SpannableStringBuilder append(CharSequence text, int start, int end) {
        builder.append(text, start, end);
        return this;
    }

    public SpannableStringBuilder setSpan(Object what, int start, int end, int flags) {
        return this;
    }

    public int length() {
        return builder.length();
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
