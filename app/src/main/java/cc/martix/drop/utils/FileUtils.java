package cc.martix.drop.utils;

import android.annotation.SuppressLint;

public abstract class FileUtils {


    /* 字节大小转换成易读的大小
     * 1KB
     * 1MB
     * 1GB
     *  */
    @SuppressLint("DefaultLocale")
    public static String byteSizeToHumanSize(long size) {
        double s1 = size;
        double s2 = s1 / 1024;
        if (s2 < 1) {
            return String.format("%.2fB", s1);
        }
        s1 = s2 / 1024;
        if (s1 < 1) {
            return String.format("%.2fKB", s2);
        }
        s2 = s1 / 1024;
        if (s2 < 1) {
            return String.format("%.2fMB", s1);
        }
        return String.format("%.2fGB", s2);
    }
}
