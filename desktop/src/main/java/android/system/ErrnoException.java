package android.system;

/**
 * 桌面端 android.system.ErrnoException 等价实现
 */
public class ErrnoException extends Exception {

    public ErrnoException(String functionName, Throwable cause) {
        super(functionName + " failed: " + (cause != null ? cause.getMessage() : ""), cause);
    }
}
