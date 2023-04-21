package cc.martix.drop.pojo;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class ServerInfo implements Serializable {

    private static final long serialVersionUID = 123L;

    public String deviceName;

    /* LocalArea Network */
    public String ssid;
    public String passphrase;

    /* ServerSocket */
    public String ip;
    public Integer port;

    @Override
    @NonNull
    public String toString() {
        return "ServerInfo{" +
                "ssid='" + ssid + '\'' +
                ", passphrase='" + passphrase + '\'' +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                '}';
    }

    @SuppressLint("DefaultLocale")
    public static String encodeSsidPassPort(@NonNull ServerInfo info) {
        return String.format("%s\t%s\t%d", info.ssid, info.passphrase, info.port);
    }

    public static String[] decodeSsidPassPort(@NonNull String encoded) {
        return encoded.split("\t");
    }
}
