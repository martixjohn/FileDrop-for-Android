package cc.martix.drop.pages.receive;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;
import java.util.Objects;

import cc.martix.drop.MyApplication;
import cc.martix.drop.R;
import cc.martix.drop.pojo.AppPreferences;
import cc.martix.drop.pojo.ClientInfo;
import cc.martix.drop.pojo.ServerInfo;
import cc.martix.drop.network.Server;
import cc.martix.drop.network.TransferHeader;
import cc.martix.drop.network.contrast.ConnectingBody;
import cc.martix.drop.network.contrast.TransferContrast;
import cn.bingoogolapple.qrcode.zxing.QRCodeEncoder;


public class ReceiveFragment extends Fragment implements View.OnClickListener {
    private static String[] REQUIRED_PERMISSIONS;
    private Button mSavingLocationBtn;
    private AppPreferences mAppPreferences;
    private TextView mSavingLocationInfoTextView;


    public static String[] getRequiredPermissions() {
        if (REQUIRED_PERMISSIONS == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                REQUIRED_PERMISSIONS = new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_NETWORK_STATE,
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                };
            } else {
                REQUIRED_PERMISSIONS = new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_NETWORK_STATE,
                        Manifest.permission.ACCESS_NETWORK_STATE
                };
            }
        }
        return REQUIRED_PERMISSIONS;
    }

    public static final String TAG = "ReceiveFragment";

    /* 当前热点信息，复用 */
    private ServerInfo mHotSpotAndServerInfo;
    private WifiManager mWifiManager;
    private FragmentActivity mActivity;


    /* 节流 */
    private boolean mIsStartingHotSpot = false;
    private WifiManager.LocalOnlyHotspotReservation mHotspotReservation;
    private Server mServer;
    private View mView;
    private TextView mNoticeTextView;
    private ImageView mQRCodeImageView;
    private TextView mServerInfoTextView;

    private Server.TransferCallback mFirstTransferCallback;



    /*
    视图容器
     */

    private Button mRefreshQRCodeBtn;
    private ActivityResultLauncher<Intent> mGoToReceiveFileActivityResultLauncher;
    private ActivityResultLauncher<Intent> mSetFileSavingLocationActivityResultLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        mActivity = getActivity();

        mServer = Server.getInstance();
        mWifiManager = (WifiManager) Objects.requireNonNull(mActivity).getSystemService(Context.WIFI_SERVICE);

        mAppPreferences = MyApplication.getInstance().getAppPreferences(mActivity);

        /* 跳转到接收文件 */
        mGoToReceiveFileActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result == null) return;
                    int resultCode = result.getResultCode();
                    Log.i(TAG, "resultCode: " + resultCode);
                    if (resultCode == ReceiveFileActivity.RESULT_CODE_DISCONNECT) {
                        Log.i(TAG, "RESULT_CODE_DISCONNECT");
                        stopHotSpotAndServer();
                    }
                });
        /* 设置文件保存路径 */
        mSetFileSavingLocationActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.i(TAG, "mSetFileSavingLocationActivityResultLauncher" + result);
                    if (result == null) {
                        return;
                    }
                    Uri uri = new ActivityResultContracts.OpenDocumentTree().parseResult(result.getResultCode(), result.getData());
                    if (uri == null) {
                        Toast.makeText(mActivity, R.string.set_failure, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mAppPreferences.fileSavingLocation = uri.toString();
                    MyApplication.getInstance().setAppPreferences(mAppPreferences);
                    Log.i(TAG, "文件存储路径: " + mAppPreferences.fileSavingLocation);
                    mSavingLocationInfoTextView.setText("");
                    startHotSpotAndServer();
                });

    }

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");

        mView = inflater.inflate(R.layout.fragment_receive, container, false);

        mNoticeTextView = mView.findViewById(R.id.tv_qr_notice);
        mQRCodeImageView = mView.findViewById(R.id.iv_qrcode);
        ViewGroup.LayoutParams imageParams = mQRCodeImageView.getLayoutParams();
        DisplayMetrics displayMetrics = mActivity.getResources().getDisplayMetrics();
        imageParams.width = displayMetrics.widthPixels / 2;
        imageParams.height = displayMetrics.widthPixels / 2;
        mQRCodeImageView.setLayoutParams(imageParams);
        mServerInfoTextView = mView.findViewById(R.id.tv_server_info);
        mRefreshQRCodeBtn = mView.findViewById(R.id.btn_refresh_qrcode);
        mRefreshQRCodeBtn.setOnClickListener(this);

        mSavingLocationBtn = mView.findViewById(R.id.btn_set_file_saving_location);
        mSavingLocationBtn.setOnClickListener(this);
        mSavingLocationInfoTextView = mView.findViewById(R.id.tv_file_saving_location);

        if (mAppPreferences.fileSavingLocation == null) {
            Toast.makeText(mActivity, R.string.please_set_file_saving_location, Toast.LENGTH_SHORT).show();
            mSavingLocationInfoTextView.setText(R.string.file_saving_location_not_set);
        } else {
            Log.i(TAG, "文件存储路径: " + mAppPreferences.fileSavingLocation);
            startHotSpotAndServer();
        }
        return mView;
    }


    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
        stopHotSpotAndServer();
        super.onStop();
    }

    /* 在视图上生成热点信息 */
    private void generateHotSpotInfoOnView() {
        /* 生成QR二维码 */
        String s = ServerInfo.encodeSsidPassPort(mHotSpotAndServerInfo);
        Log.i(TAG, "二维码数据: " + s);
        Bitmap bitmap = QRCodeEncoder.syncEncodeQRCode(s, 200);
        mNoticeTextView.setVisibility(View.VISIBLE);
        mQRCodeImageView.setImageBitmap(bitmap);
        mQRCodeImageView.clearColorFilter();
        mServerInfoTextView.setText(String.format("SSID: %s\n 密码: %s", mHotSpotAndServerInfo.ssid, mHotSpotAndServerInfo.passphrase));
    }

    /* 清除热点信息 */
    private void clearHotSpotInfoOnView() {
        mQRCodeImageView.setColorFilter(androidx.cardview.R.color.cardview_shadow_end_color);
        mServerInfoTextView.setText(R.string.qrcode_invalidate);
        mNoticeTextView.setVisibility(View.INVISIBLE);
    }

    /* 设置文件保存路径 */
    private void setFileSavingLocation() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        mSetFileSavingLocationActivityResultLauncher.launch(intent);
    }

    private boolean hasFileSavingLocation() {
        return mAppPreferences.fileSavingLocation != null;
    }

    @Override
    public void onClick(View v) {
        if (mRefreshQRCodeBtn == v) {
            /* 检查文件路径 */
            if (!hasFileSavingLocation()) {
                Toast.makeText(mActivity, R.string.please_set_file_saving_location, Toast.LENGTH_SHORT).show();
                return;
            }
            startHotSpotAndServer();
        } else if (mSavingLocationBtn == v) {
            setFileSavingLocation();
        }
    }

    /*
        热点和服务器相关
     */

    /* 开启热点和服务器，需要权限 */
    private void startHotSpotAndServer() {
        if (!mIsStartingHotSpot) {
            try {
                stopHotSpotAndServer();
            } catch (Exception e) {
                Log.i(TAG, "尝试启动前关闭失败");
            }
            mIsStartingHotSpot = true;
            mWifiManager.startLocalOnlyHotspot(new LocalOnlyHotspotCallback(), null);
        }
    }


    private void stopHotSpotAndServer() {
        if (mHotspotReservation != null) {
            mHotspotReservation.close();
            mHotspotReservation = null;
        }
        mIsStartingHotSpot = false;
        mServer.closeServer();
        clearHotSpotInfoOnView();
    }

    private class LocalOnlyHotspotCallback extends WifiManager.LocalOnlyHotspotCallback {


        @Override
        public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
            Log.v(TAG, "Hotspot Started");

            mHotspotReservation = reservation;
            /*
                封装热点信息
             */
            mHotSpotAndServerInfo = new ServerInfo();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                SoftApConfiguration softApConfiguration = reservation.getSoftApConfiguration();
                mHotSpotAndServerInfo.ssid = softApConfiguration.getSsid();
                mHotSpotAndServerInfo.passphrase = softApConfiguration.getPassphrase();
            } else {
                WifiConfiguration wifiConfiguration = reservation.getWifiConfiguration();
                mHotSpotAndServerInfo.ssid = wifiConfiguration.SSID;
                mHotSpotAndServerInfo.passphrase = wifiConfiguration.preSharedKey;
            }

            mServer.startServer((port) -> {
                mHotSpotAndServerInfo.port = port;
                mActivity.runOnUiThread(ReceiveFragment.this::generateHotSpotInfoOnView);
                Log.i(TAG, "Server Started");
                mIsStartingHotSpot = false;
            });

            /* 当初次设备连接请求触发 */
            mFirstTransferCallback = transfer -> {
                Log.i(TAG, "first transfer callback");
                try {
                    TransferHeader header = transfer.readHeader();
                    ConnectingBody body = transfer.read();
                    transfer.finishRead();

                    if (header.contentType == TransferContrast.TYPE_CONNECTING) {
                        transfer.writeHeader(header);
                        ConnectingBody writeBody = new ConnectingBody();
                        writeBody.deviceName = MyApplication.getInstance().getAppPreferences(mActivity).deviceName;
                        transfer.write(writeBody);
                        transfer.finishWrite();

                        // 连接成功
                        // 跳转
                        mActivity.runOnUiThread(() -> Toast.makeText(mActivity, "设备连接成功", Toast.LENGTH_SHORT).show());

                        Intent intent = new Intent(mActivity, ReceiveFileActivity.class);
                        intent.putExtra(ReceiveFileActivity.EXTRA_SERVER_INFO, mHotSpotAndServerInfo);
                        ClientInfo clientInfo = new ClientInfo();
                        clientInfo.deviceName = body.deviceName;
                        intent.putExtra(ReceiveFileActivity.EXTRA_CLIENT_INFO, clientInfo);
                        mGoToReceiveFileActivityResultLauncher.launch(intent);
                        mServer.unRegister(mFirstTransferCallback);
                    } else {
                        throw new IOException();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "连接失败", e);
                    mActivity.runOnUiThread(() -> Toast.makeText(mActivity, "设备连接失败", Toast.LENGTH_SHORT).show());
                    stopHotSpotAndServer();// 关闭
                }
            };
            mServer.register(mFirstTransferCallback);
        }


        /* 自然关闭，非手动close */
        @Override
        public void onStopped() {
            Log.v(TAG, "Hotspot Stopped");
            mIsStartingHotSpot = false;
            mHotspotReservation = null;
            mHotSpotAndServerInfo = null;
            mServer.closeServer();
            mFirstTransferCallback = null;
            mActivity.runOnUiThread(ReceiveFragment.this::clearHotSpotInfoOnView);
        }

        @Override
        public void onFailed(int reason) {
            Log.v(TAG, "Start hotspot failed: " + reason);
            mIsStartingHotSpot = false;
            mHotSpotAndServerInfo = null;
            mHotspotReservation = null;
            mFirstTransferCallback = null;
            mServer.closeServer();
            mActivity.runOnUiThread(ReceiveFragment.this::clearHotSpotInfoOnView);
        }
    }


}