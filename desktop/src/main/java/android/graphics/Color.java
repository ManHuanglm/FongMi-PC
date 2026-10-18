package android.graphics;

/**
 * 桌面端 android.graphics.Color 等价实现（ARGB 整型语义与安卓一致）
 */
public class Color {

    public static final int BLACK = 0xFF000000;
    public static final int DKGRAY = 0xFF444444;
    public static final int GRAY = 0xFF888888;
    public static final int LTGRAY = 0xFFCCCCCC;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int TRANSPARENT = 0x00000000;

    private Color() {
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int parseColor(String colorString) {
        if (colorString.startsWith("#")) {
            try {
                long value = Long.parseLong(colorString.substring(1), 16);
                if (colorString.length() == 7) return (int) (0xFF000000L | value);
                if (colorString.length() == 9) return (int) value;
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("Unknown color: " + colorString);
    }
}
