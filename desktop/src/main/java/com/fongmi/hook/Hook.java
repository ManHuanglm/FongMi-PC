package com.fongmi.hook;

/**
 * 桌面端 com.fongmi.hook.Hook 编译桩：
 * Hook 库用于 TV 端包名伪装，桌面端无此运行时，仅满足 bean/Core 编译（getHook 永不被桌面调用）
 */
public class Hook {

    public Hook(String sign, String pkg) {
    }
}
