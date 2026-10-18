package android.view;

/**
 * 桌面端 android.view.View 编译桩（仅覆盖挂载代码用到的可见性常量）
 */
public class View {

    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    private View() {
    }
}
