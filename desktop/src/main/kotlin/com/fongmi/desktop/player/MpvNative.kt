package com.fongmi.desktop.player

import com.sun.jna.CallbackThreadInitializer
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * libmpv client.h 核心子集 JNA 绑定：
 * 属性/命令统一走 STRING/DOUBLE/INT64/FLAG 格式（track-list 等复杂数据用 STRING JSON 取回，避免 node 结构开销）
 */
object MpvNative {

    // mpv_format
    const val FORMAT_NONE = 0
    const val FORMAT_STRING = 1
    const val FORMAT_OSD_STRING = 2
    const val FORMAT_FLAG = 3
    const val FORMAT_INT64 = 4
    const val FORMAT_DOUBLE = 5

    // mpv_event_id
    const val EVENT_NONE = 0
    const val EVENT_SHUTDOWN = 1
    const val EVENT_LOG_MESSAGE = 2
    const val EVENT_START_FILE = 6
    const val EVENT_END_FILE = 7
    const val EVENT_FILE_LOADED = 8
    const val EVENT_SEEK = 20
    const val EVENT_PLAYBACK_RESTART = 21
    const val EVENT_PROPERTY_CHANGE = 22
    const val EVENT_VIDEO_RECONFIG = 26

    // mpv_end_file_reason
    const val END_FILE_EOF = 0
    const val END_FILE_ERROR = 4

    // 属性观察请求格式
    const val OBSERVE_DOUBLE = FORMAT_DOUBLE
    const val OBSERVE_INT64 = FORMAT_INT64
    const val OBSERVE_FLAG = FORMAT_FLAG

    interface MpvLibrary : Library {

        fun mpv_create(): Pointer

        fun mpv_initialize(ctx: Pointer): Int

        fun mpv_terminate_destroy(ctx: Pointer)

        fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int

        fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int

        fun mpv_set_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

        fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

        fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?

        fun mpv_command(ctx: Pointer, args: Array<String>): Int

        fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int

        fun mpv_wait_event(ctx: Pointer, timeout: Double): MpvEvent

        fun mpv_wakeup(ctx: Pointer)

        fun mpv_free(data: Pointer)

        fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int

        fun mpv_error_string(error: Int): Pointer?

        fun mpv_client_api_version(): Long
    }

    /** mpv_event（client.h 布局） */
    @Structure.FieldOrder("eventId", "error", "replyUserdata", "data")
    class MpvEvent : Structure() {
        @JvmField
        var eventId: Int = 0

        @JvmField
        var error: Int = 0

        @JvmField
        var replyUserdata: Long = 0

        @JvmField
        var data: Pointer? = null

        override fun toString(): String = "MpvEvent(id=$eventId, error=$error)"
    }

    /** mpv_event_property */
    @Structure.FieldOrder("name", "format", "data")
    class MpvEventProperty : Structure() {
        @JvmField
        var name: String = ""

        @JvmField
        var format: Int = 0

        @JvmField
        var data: Pointer? = null
    }

    /** mpv_event_end_file */
    @Structure.FieldOrder("reason", "error", "playlistEntryId", "playlistInsertId", "playlistInsertNumEntries")
    class MpvEventEndFile : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField
        var reason: Int = 0

        @JvmField
        var error: Int = 0

        @JvmField
        var playlistEntryId: Int = 0

        @JvmField
        var playlistInsertId: Int = 0

        @JvmField
        var playlistInsertNumEntries: Int = 0
    }

    /** mpv_event_log_message */
    @Structure.FieldOrder("prefix", "level", "text", "logLevel")
    class MpvEventLogMessage : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField
        var prefix: String = ""

        @JvmField
        var level: String = ""

        @JvmField
        var text: String = ""

        @JvmField
        var logLevel: Int = 0
    }

    private var library: MpvLibrary? = null

    /** 加载 libmpv-2.dll；失败抛出明确异常（禁止无提示失败），由上层给出可读错误 */
    fun load(libraryPath: String?): MpvLibrary {
        library?.let { return it }
        val options = mapOf("jna.encoding" to "UTF-8")
        val loaded = if (libraryPath.isNullOrEmpty()) {
            Native.load("mpv", MpvLibrary::class.java, options)
        } else {
            Native.load(libraryPath, MpvLibrary::class.java, options)
        }
        library = loaded
        return loaded
    }

    fun errorString(lib: MpvLibrary, error: Int): String = lib.mpv_error_string(error)?.getString(0) ?: "unknown($error)"

    fun setDouble(lib: MpvLibrary, ctx: Pointer, name: String, value: Double): Int =
        DoubleByReference(value).let { lib.mpv_set_property(ctx, name, FORMAT_DOUBLE, it.pointer) }

    fun getDouble(lib: MpvLibrary, ctx: Pointer, name: String): Double? =
        DoubleByReference().let { ref -> if (lib.mpv_get_property(ctx, name, FORMAT_DOUBLE, ref.pointer) == 0) ref.value else null }

    fun setLong(lib: MpvLibrary, ctx: Pointer, name: String, value: Long): Int =
        LongByReference(value).let { lib.mpv_set_property(ctx, name, FORMAT_INT64, it.pointer) }

    fun getLong(lib: MpvLibrary, ctx: Pointer, name: String): Long? =
        LongByReference().let { ref -> if (lib.mpv_get_property(ctx, name, FORMAT_INT64, ref.pointer) == 0) ref.value else null }

    fun setFlag(lib: MpvLibrary, ctx: Pointer, name: String, value: Boolean): Int =
        IntByReference(if (value) 1 else 0).let { lib.mpv_set_property(ctx, name, FORMAT_FLAG, it.pointer) }

    fun getFlag(lib: MpvLibrary, ctx: Pointer, name: String): Boolean? =
        IntByReference().let { ref -> if (lib.mpv_get_property(ctx, name, FORMAT_FLAG, ref.pointer) == 0) ref.value != 0 else null }
}
