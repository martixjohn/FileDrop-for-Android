package cc.martix.drop.pages.home;

import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import cc.martix.drop.MainActivity;
import cc.martix.drop.MyApplication;
import cc.martix.drop.PubSub;
import cc.martix.drop.R;
import cc.martix.drop.pojo.AppPreferences;


public class HomeFragment extends Fragment implements View.OnClickListener {

    public static final String TAG = "HomeFragment";
    private View mView;
    private Button mSendBtn;
    private Button mReceiveBtn;
    private FragmentActivity mActivity;
    private AppPreferences mAppPreferences;
    private TextView mDeviceNameTextView;
    private FloatingActionButton mRenameBtn;
    private AlertDialog mRenameAlertDialog;
    private View mRenameDialogContentView;
    private TextInputEditText mDeviceNameEditText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = getActivity();
        mAppPreferences = MyApplication.getInstance().getAppPreferences(mActivity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        /* View */
        mView = inflater.inflate(R.layout.fragment_home, container, false);
        mDeviceNameTextView = mView.findViewById(R.id.tv_my_device_name);
        mSendBtn = mView.findViewById(R.id.btn_home_send);
        mSendBtn.setOnClickListener(this);
        mReceiveBtn = mView.findViewById(R.id.btn_home_receive);
        mReceiveBtn.setOnClickListener(this);
        mRenameBtn = mView.findViewById(R.id.btn_rename_device);
        mRenameBtn.setOnClickListener(this);
        mDeviceNameTextView.setText(mAppPreferences.deviceName);
        mRenameDialogContentView = inflater.inflate(R.layout.dialog_content_rename_device, container, false);
        mDeviceNameEditText = mRenameDialogContentView.findViewById(R.id.dialog_content_rename_device_input);
        mRenameAlertDialog = new MaterialAlertDialogBuilder(mActivity)
                .setTitle("修改设备名称")
                .setView(mRenameDialogContentView)
                .setPositiveButton("确定", (dialog, which) -> {
                    Editable text = mDeviceNameEditText.getText();
                    if (text == null || "".equals(text.toString())) {
                        Toast.makeText(mActivity, "名称不能为空", Toast.LENGTH_SHORT).show();
                    } else {
                        String s = text.toString();
                        if (s.length() > 20) {
                            Toast.makeText(mActivity, "文字过长！", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        mAppPreferences.deviceName = s;
                        MyApplication.getInstance().setAppPreferences(mAppPreferences);
                        mDeviceNameTextView.setText(mAppPreferences.deviceName);
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onClick(View v) {
        if (mSendBtn == v) {
            PubSub.instance.publish(MainActivity.PUBSUB_KEY_SWITCH_FRAGMENT, MainActivity.SWITCH_TO_SEND);
        } else if (mReceiveBtn == v) {
            PubSub.instance.publish(MainActivity.PUBSUB_KEY_SWITCH_FRAGMENT, MainActivity.SWITCH_TO_RECEIVE);
        } else if (mRenameBtn == v) {
            mDeviceNameEditText.setText(mAppPreferences.deviceName);
            mRenameAlertDialog.show();
        }
    }
}