package androidx.media3.common;

/**
 * 桌面端 androidx.media3.common.MimeTypes 常量桩（取值与 media3 1.11.0 一致）
 */
public final class MimeTypes {

    public static final String APPLICATION_M3U8 = "application/x-mpegURL";
    public static final String APPLICATION_MPD = "application/dash+xml";
    public static final String APPLICATION_SS = "application/vnd.ms-sstr+xml";
    public static final String APPLICATION_RTSP = "application/x-rtsp";

    public static final String VIDEO_MP4 = "video/mp4";
    public static final String VIDEO_WEBM = "video/webm";
    public static final String VIDEO_H264 = "video/avc1";
    public static final String VIDEO_H265 = "video/hevc";

    public static final String AUDIO_MP4 = "audio/mp4";
    public static final String AUDIO_AAC = "audio/mp4a-latm";
    public static final String AUDIO_MPEG = "audio/mpeg";
    public static final String AUDIO_FLAC = "audio/flac";

    public static final String TEXT_VTT = "text/vtt";
    public static final String TEXT_SSA = "text/x-ssa";
    public static final String APPLICATION_SUBRIP = "application/x-subrip";
    public static final String APPLICATION_TTML = "application/ttml+xml";

    private MimeTypes() {
    }
}
