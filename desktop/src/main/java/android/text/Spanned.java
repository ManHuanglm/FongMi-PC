package android.text;

/**
 * 桌面端 android.text.Spanned 编译桩
 */
public interface Spanned extends CharSequence {

    int SPAN_EXCLUSIVE_EXCLUSIVE = 33;
    int SPAN_EXCLUSIVE_INCLUSIVE = 34;
    int SPAN_INCLUSIVE_EXCLUSIVE = 17;
    int SPAN_INCLUSIVE_INCLUSIVE = 18;

    @Override
    default int length() {
        return toString().length();
    }

    @Override
    default char charAt(int index) {
        return toString().charAt(index);
    }

    @Override
    default CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }
}
