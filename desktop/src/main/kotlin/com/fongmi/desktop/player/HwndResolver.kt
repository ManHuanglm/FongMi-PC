package com.fongmi.desktop.player

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.W32APIOptions

/**
 * HWND 定位：Compose 窗口（顶层 Frame）→ 枚举子窗口找到 AWT Canvas（SunAwtCanvas 类）原生句柄
 * 该句柄交给 libmpv 作为 wid 渲染目标
 */
object HwndResolver {

    private const val AWT_CANVAS_CLASS = "SunAwtCanvas"

    fun findCanvasHwnd(windowTitle: String): Long? {
        val user32 = Native.load("user32", User32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        val top: HWND = user32.FindWindow(null, windowTitle) ?: return null
        var found: Long? = null
        user32.EnumChildWindows(top, { hWnd, _ ->
            if (found == null && AWT_CANVAS_CLASS.equals(getClassName(user32, hWnd), ignoreCase = true)) {
                found = Pointer.nativeValue(hWnd.pointer)
            }
            found == null
        }, null)
        return found
    }

    private fun getClassName(user32: User32, hWnd: HWND): String {
        val buffer = CharArray(256)
        val length = user32.GetClassName(hWnd, buffer, 256)
        return if (length > 0) String(buffer, 0, length) else ""
    }
}
