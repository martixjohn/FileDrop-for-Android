package cc.martix.drop.pojo;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * App偏好设置
 * 使用SharedPreferences
 */
public class AppPreferences implements Serializable, Cloneable {
    /* 设备名称 */
    public String deviceName;

    /* 文件保存路径 */
    public String fileSavingLocation;

    @NonNull
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
