package androidx.preference;

import com.fongmi.desktop.compat.DesktopSharedPreferences;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 桌面端 androidx.preference.PreferenceManager 等价实现：
 * 返回桌面文件存储实现（%APPDATA%/FongMiTV/prefs.xml，key 与安卓端一致）
 */
public class PreferenceManager {

    private PreferenceManager() {
    }

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return DesktopSharedPreferences.getInstance();
    }
}
