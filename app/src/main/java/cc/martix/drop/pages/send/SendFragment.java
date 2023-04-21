package cc.martix.drop.pages.send;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.Map;
import java.util.Set;

import cc.martix.drop.R;
import cc.martix.drop.utils.PermissionUtils;
import cc.martix.drop.utils.ToastUtils;

/**
 * 上传文件
 * 为客户端
 */
public class SendFragment extends Fragment implements View.OnClickListener {
    public final static String TAG = "SendFragment";
    private View mView;
    private FragmentActivity mActivity;

    private Button mGoConnectBtn;
    private Intent mGoToScanIntent;


    private ActivityResultLauncher<String[]> mScanPermissionsRequestResultLauncher;


    public SendFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();
        assert mActivity != null;

        mGoToScanIntent = new Intent(mActivity, ScanDeviceActivity.class);

        /* 扫描二维码权限获取 */
        mScanPermissionsRequestResultLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean hasGranted = PermissionUtils.validatePermissionGrantedResult(result);
                    if (hasGranted) {
                        launchScanDeviceActivity();
                    } else {
                        ToastUtils.showRequirePermissionToast(mActivity);
                    }
                });

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_send, container, false);

        mGoConnectBtn = mView.findViewById(R.id.btn_go_connect);
        mGoConnectBtn.setOnClickListener(this);

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();
    }


    /* View.OnClickListener */
    @Override
    public void onClick(View v) {
        if (mGoConnectBtn == v) {
            /* 检查权限 */
            if (PermissionUtils.checkIfHasPermissions(mActivity, ScanDeviceActivity.getRequiredPermissions())) {
                launchScanDeviceActivity();
            } else {
                requestPermissionsForScanDeviceActivity();
            }
        }
    }

    /* 打开二维码扫描界面 */
    private void launchScanDeviceActivity() {
        Log.i(TAG, "launchScanDeviceActivity");
        startActivity(mGoToScanIntent);
    }

    private void requestPermissionsForScanDeviceActivity() {
        mScanPermissionsRequestResultLauncher.launch(ScanDeviceActivity.getRequiredPermissions());
    }
}
