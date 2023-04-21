package cc.martix.drop.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Map;
import java.util.Set;

public abstract class PermissionUtils {
    public static boolean checkIfHasPermissions(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(context, permission)) {
                return false;
            }
        }
        return true;
    }

    public static boolean validatePermissionGrantedResult(int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean validatePermissionGrantedResult(Map<String, Boolean> grantResults) {
        Set<Map.Entry<String, Boolean>> entries = grantResults.entrySet();
        for (Map.Entry<String, Boolean> entry : entries) {
            if (!entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
