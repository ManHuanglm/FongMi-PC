package androidx.media3.common;

import java.util.UUID;

/**
 * 桌面端 androidx.media3.common.C 常量桩（取值与 media3 1.11.0 一致）
 */
public final class C {

    public static final long TIME_UNSET = Long.MIN_VALUE + 1;

    public static final int SELECTION_FLAG_DEFAULT = 1;
    public static final int SELECTION_FLAG_FORCED = 2;

    public static final UUID WIDEVINE_UUID = UUID.fromString("EDEF8BA9-79D6-4ACE-A3C8-27DCB51E7FA2");
    public static final UUID PLAYREADY_UUID = UUID.fromString("9A04F079-9840-4286-AB92-E65BE0885F95");
    public static final UUID CLEARKEY_UUID = UUID.fromString("E2719D58-A985-B3C9-781A-B030AF78D30E");
    public static final UUID UUID_NIL = new UUID(0L, 0L);

    private C() {
    }
}
