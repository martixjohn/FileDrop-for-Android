package cc.martix.drop.utils;

import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

public abstract class NetworkUtils {
    public static final String TAG = "NetworkUtils";


    public static String ipAddressToString(int ipAddress) {
        return (ipAddress & 0xff) + "." + (ipAddress >> 8 & 0xff) + "." + (ipAddress >> 16 & 0xff) + "." + (ipAddress >> 24 & 0xff);
    }

    public static String getGatewayIp(WifiManager wifiManager) {
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        return ipAddressToString(dhcpInfo.gateway);
    }
}
