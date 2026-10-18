package android.net.wifi;

/**
 * 桌面端 android.net.wifi.WifiManager 编译桩：连接信息恒为未连接（IP=0）
 */
public class WifiManager {

    public WifiInfo getConnectionInfo() {
        return new WifiInfo();
    }
}
