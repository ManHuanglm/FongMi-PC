package android.util;

import java.io.ByteArrayOutputStream;
import java.util.Base64.Decoder;

/**
 * 桌面端 android.util.Base64 等价实现，行为对齐安卓版（含 flags 组合语义）
 */
public class Base64 {

    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private Base64() {
    }

    public static byte[] decode(String str, int flags) {
        return decode(str.getBytes(java.nio.charset.StandardCharsets.US_ASCII), flags);
    }

    public static byte[] decode(byte[] input, int flags) {
        try {
            Decoder decoder = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlDecoder() : java.util.Base64.getDecoder();
            String text = new String(input, java.nio.charset.StandardCharsets.US_ASCII);
            // 安卓解码默认容忍换行符，桌面端先去除再解码
            text = text.replaceAll("\\s", "");
            if ((flags & NO_PADDING) != 0) {
                int remainder = text.length() % 4;
                if (remainder == 2) text += "==";
                else if (remainder == 3) text += "=";
            }
            return decoder.decode(text);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    public static String encodeToString(byte[] input, int flags) {
        byte[] encoded = encode(input, flags);
        return new String(encoded, java.nio.charset.StandardCharsets.US_ASCII);
    }

    public static byte[] encode(byte[] input, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlEncoder() : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) encoder = encoder.withoutPadding();
        byte[] encoded = encoder.encode(input);
        if ((flags & NO_WRAP) != 0) return encoded;
        // 非 NO_WRAP 时按 76 字符换行（对齐安卓行为）
        if ((flags & CRLF) != 0) return insertLineBreaks(encoded, 76, new byte[]{'\r', '\n'});
        return insertLineBreaks(encoded, 76, new byte[]{'\n'});
    }

    private static byte[] insertLineBreaks(byte[] input, int limit, byte[] separator) {
        if (input.length <= limit) return input;
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length + input.length / limit * separator.length);
        for (int i = 0; i < input.length; i++) {
            if (i > 0 && i % limit == 0) out.writeBytes(separator);
            out.write(input[i]);
        }
        return out.toByteArray();
    }
}
