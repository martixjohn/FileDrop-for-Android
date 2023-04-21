package cc.martix.drop.utils;

import android.content.Context;
import android.widget.Toast;

/**
 * 业务相关工具类
 */
public abstract class ToastUtils {
    public static void showRequirePermissionToast(Context context){
        Toast.makeText(context, "请授予必要权限", Toast.LENGTH_SHORT).show();
    }

    public static void showConnectionErrorToast(Context context){
        Toast.makeText(context, "连接错误", Toast.LENGTH_SHORT).show();
    }
}
