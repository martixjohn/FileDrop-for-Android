package cc.martix.drop.pages.send;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import cc.martix.drop.R;
import cc.martix.drop.pojo.ServerInfo;
import cn.bingoogolapple.qrcode.core.QRCodeView;
import cn.bingoogolapple.qrcode.zxing.ZXingView;

public class ScanDeviceActivity extends AppCompatActivity implements QRCodeView.Delegate {
    public static final String TAG = "ScanDeviceActivity";
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA
    };

    public static String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    private final int REQUIRED_PERMISSIONS_REQUEST_CODE = 1;
    private ZXingView mZXingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_device);
        /* view */
        mZXingView = findViewById(R.id.zxingview);
        mZXingView.setDelegate(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mZXingView.startCamera();
        mZXingView.startSpotAndShowRect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mZXingView.stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mZXingView.onDestroy();
    }

    @Override
    public void onScanQRCodeSuccess(String result) {
//        Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "扫描结果" + result);

        String[] strings = null;
        int port;
        String ssid, password;
        try {
            strings = ServerInfo.decodeSsidPassPort(result);
            if (strings.length != 3) throw new RuntimeException();
            port = Integer.parseInt(strings[2]);
            if (port < 0 || port > 65535) {
                throw new RuntimeException();
            }
            ssid = strings[0];
            password = strings[1];
        } catch (RuntimeException e) {
            Toast.makeText(this, "不合法的二维码", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, SendFileActivity.class);
        intent.putExtra(SendFileActivity.EXTRA_SSID, ssid);
        intent.putExtra(SendFileActivity.EXTRA_PASSWORD, password);
        intent.putExtra(SendFileActivity.EXTRA_PORT, port);
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        VibrationEffect vibrationEffect = VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE);
        vibrator.vibrate(vibrationEffect);
        startActivity(intent);
        finish();
    }


    @Override
    public void onCameraAmbientBrightnessChanged(boolean isDark) {

    }

    @Override
    public void onScanQRCodeOpenCameraError() {
        Toast.makeText(this, "请检查相机设置", Toast.LENGTH_SHORT).show();
        finish();
    }
}