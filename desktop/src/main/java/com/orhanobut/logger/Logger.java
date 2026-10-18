package com.orhanobut.logger;

/**
 * 桌面端 com.orhanobut.logger.Logger 等价实现：
 * 安卓端 logger 库为 AAR 无法在桌面 JVM 使用，此处以标准输出等价替代（仅用到 t().d() 链）
 */
public class Logger {

    private Logger() {
    }

    public static Printer t(String tag) {
        return new Printer(tag);
    }

    public static void d(String message, Object... args) {
        System.out.println("D: " + format(message, args));
    }

    public static void i(String message, Object... args) {
        System.out.println("I: " + format(message, args));
    }

    public static void w(String message, Object... args) {
        System.err.println("W: " + format(message, args));
    }

    public static void e(String message, Object... args) {
        System.err.println("E: " + format(message, args));
    }

    private static String format(String message, Object[] args) {
        return args == null || args.length == 0 ? message : String.format(message, args);
    }

    public static class Printer {

        private final String tag;

        private Printer(String tag) {
            this.tag = tag;
        }

        public void d(String message, Object... args) {
            System.out.println("D/" + tag + ": " + format(message, args));
        }

        public void i(String message, Object... args) {
            System.out.println("I/" + tag + ": " + format(message, args));
        }

        public void w(String message, Object... args) {
            System.err.println("W/" + tag + ": " + format(message, args));
        }

        public void e(String message, Object... args) {
            System.err.println("E/" + tag + ": " + format(message, args));
        }

        private String format(String message, Object[] args) {
            return args == null || args.length == 0 ? message : String.format(message, args);
        }
    }
}
