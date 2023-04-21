package cc.martix.drop.pages.send;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import cc.martix.drop.MyApplication;
import cc.martix.drop.R;
import cc.martix.drop.dao.HistoryDao;
import cc.martix.drop.network.contrast.ConnectingBody;
import cc.martix.drop.network.contrast.TransferContrast;
import cc.martix.drop.network.contrast.AcceptBody;
import cc.martix.drop.network.contrast.FileDescriptorBody;
import cc.martix.drop.pojo.HistoryInfo;
import cc.martix.drop.pojo.ServerInfo;
import cc.martix.drop.network.contrast.ReceiveResultBody;
import cc.martix.drop.network.TransferHeader;
import cc.martix.drop.network.Transfer;
import cc.martix.drop.utils.FileUtils;
import cc.martix.drop.utils.NetworkUtils;
import cc.martix.drop.utils.ToastUtils;

public class SendFileActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = "SendFileActivity";

    public static final String EXTRA_SSID = "SSID";

    public static final String EXTRA_PASSWORD = "PASS";
    public static final String EXTRA_PORT = "PORT";

    private ServerInfo mServerInfo;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private ActivityResultLauncher<Intent> mOpenFileActivityResultLauncher;
    private boolean mIsSendingFile;
    private Uri mFileUri;
    private Button mChooseFileBtn;
    private ConnectivityManager.NetworkCallback mNetworkConnectCallback;
    private TextView mConnectedToTextView;

    private ViewGroup mBeforeFileSendWidgets;
    private Thread mSendThread;
    private boolean mIsConnectedToServer;
    private ViewGroup mConnectingWidgets;
    private ViewGroup mFileSendingWidgets;
    private CircularProgressIndicator mSendingProgressIndicator;
    private TextView mSendProgressView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_file);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        /*
         * View
         */
        /* 连接中 */
        mConnectingWidgets = findViewById(R.id.widgets_connecting);
        /* 发送前 */
        mBeforeFileSendWidgets = findViewById(R.id.widgets_before_file_send);

        mConnectedToTextView = findViewById(R.id.tv_connected_to);
        mBeforeFileSendWidgets.setOnClickListener(this);
        mChooseFileBtn = findViewById(R.id.btn_choose_file);
        mChooseFileBtn.setOnClickListener(this);

        /* 发送中 */
        mFileSendingWidgets = findViewById(R.id.widgets_file_sending);
        mSendingProgressIndicator = findViewById(R.id.progress_indicator_sending);
        mSendProgressView = findViewById(R.id.tv_send_progress);
        /*
         * 得到
         * 封装HotspotInfo
         * ip在连接热点后获取
         */
        Bundle data = getIntent().getExtras();
        mIsConnectedToServer = false;
        mServerInfo = new ServerInfo();
        mServerInfo.ssid = data.getString(EXTRA_SSID);
        mServerInfo.passphrase = data.getString(EXTRA_PASSWORD);
        mServerInfo.port = data.getInt(EXTRA_PORT);

        /* 打开文件异步调用 */
        mOpenFileActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> onFileOpened(result.getData()));
        /*
         * 热点连接
         */
        Log.i(TAG, "尝试连接热点: " + mServerInfo);
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(mServerInfo.ssid)
                .setWpa2Passphrase(mServerInfo.passphrase)
                .build();
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                // 部分OEM厂商需要
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();
        mNetworkConnectCallback = new ConnectNetworkCallback();
        mConnectivityManager.requestNetwork(request, mNetworkConnectCallback);

    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("确认返回？这将断开连接")
                .setPositiveButton("确认", (dialog, which) -> {
                    notifyDisconnectAsync();
                    finish();
                })
                .setNegativeButton("取消", ((dialog, which) -> dialog.dismiss()))
                .create()
                .show();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        notifyDisconnectAsync();
        super.onDestroy();
    }

    /* 主动和服务端断开连接 */
    private void notifyDisconnectAsync() {
        if (!mIsConnectedToServer) {
            Log.i(TAG, "已经关闭");
            return;
        }
        mIsConnectedToServer = false;
        new Thread(() -> {
            Log.i(TAG, "正在主动断开连接...");
            /* Socket 主动通知服务器断开连接 */
            try (Transfer transfer = new Transfer(mServerInfo.ip, mServerInfo.port, TransferContrast.SOCKET_SO_TIMEOUT)) {
                TransferHeader header = new TransferHeader();
                header.contentType = TransferContrast.TYPE_DISCONNECT;
                transfer.writeHeader(header);
            } catch (IOException e) {
                Log.i(TAG, "断开连接Socket出现错误" + e);
            } finally {
                Log.i(TAG, "即将断开热点...");
                mConnectivityManager.bindProcessToNetwork(null);
                mConnectivityManager.unregisterNetworkCallback(mNetworkConnectCallback);
                mIsSendingFile = false;
                /* 发送线程停止 */
                if (mSendThread != null) {
                    mSendThread.interrupt();
                    mSendThread = null;
                }
            }
        }).start();
    }

    private class ConnectNetworkCallback extends ConnectivityManager.NetworkCallback {

        /* 连接 */

        @Override
        public void onAvailable(@NonNull Network network) {
            Log.i(TAG, "onAvailable, HotSpot connected");
            boolean b = mConnectivityManager.bindProcessToNetwork(network);
            Log.i(TAG, "bindProcessToNetwork: " + b);
//            mConnectivityManager.unregisterNetworkCallback(mNetworkConnectCallback);
            WifiInfo connectionInfo = mWifiManager.getConnectionInfo();
            Log.i(TAG, "My IP Address: " + NetworkUtils.ipAddressToString(connectionInfo.getIpAddress()));
            String ip = NetworkUtils.getGatewayIp(mWifiManager);
            mServerInfo.ip = ip;
            Log.i(TAG, "Gateway IP Address: " + ip);

            Log.i(TAG, "try connect to server socket");
            /* 发送连接信息 */
            try (Transfer transfer = new Transfer(mServerInfo.ip, mServerInfo.port, TransferContrast.SOCKET_SO_TIMEOUT)) {
                TransferHeader header = new TransferHeader();
                header.contentType = TransferContrast.TYPE_CONNECTING;
                transfer.writeHeader(header);
                ConnectingBody body = new ConnectingBody();
                body.deviceName = MyApplication.getInstance().getAppPreferences(SendFileActivity.this).deviceName;
                transfer.write(body);
                transfer.finishWrite();

                TransferHeader readHeader = transfer.readHeader();
                ConnectingBody readBody = transfer.read();
                transfer.finishRead();
                mServerInfo.deviceName = readBody.deviceName;

                if (readHeader.contentType == TransferContrast.TYPE_CONNECTING) {
                    // 连接成功
                    mIsConnectedToServer = true;

                    runOnUiThread(SendFileActivity.this::onHotspotConnectedSuccess);
                }
            } catch (IOException e) {
                Log.e(TAG, "连接失败", e);
                mIsConnectedToServer = false;
                runOnUiThread(SendFileActivity.this::onHotspotConnectedFailure);
            }
        }

        /* 连接不可用 */

        @Override
        public void onUnavailable() {
            mIsConnectedToServer = false;
            /* 已经释放了 */
            Log.i(TAG, "onUnavailable");
            runOnUiThread(SendFileActivity.this::onHotspotDisconnected);
        }

        /* 连接丢失 */
        @Override
        public void onLost(@NonNull Network network) {
            Log.i(TAG, "onLost");
            mIsConnectedToServer = false;
            runOnUiThread(SendFileActivity.this::onHotspotDisconnected);
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            Log.i(TAG, "onCapabilitiesChanged");
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.i(TAG, "wifi已经连接");
                } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    Log.i(TAG, "数据流量已经连接");
                } else {
                    Log.i(TAG, "其他网络已连接");
                }
            }
        }

    }


    /**
     * 连接相关
     */

    /* 热点连接成功 */
    public void onHotspotConnectedSuccess() {
        mConnectingWidgets.setVisibility(View.INVISIBLE);
        mBeforeFileSendWidgets.setVisibility(View.VISIBLE);
        mConnectedToTextView.setText(String.format("已连接到%s", mServerInfo.deviceName));

        mChooseFileBtn.setVisibility(View.VISIBLE);
    }

    /* 连接失败 */
    public void onHotspotConnectedFailure() {
        Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show();
        finish();
    }

    /* 热点断开 */
    public void onHotspotDisconnected() {
        Toast.makeText(this, "连接断开", Toast.LENGTH_SHORT).show();
//        mBeforeFileSendWidgets.setVisibility(ImageView.INVISIBLE);
//        mConnectedToTextView.setVisibility(View.INVISIBLE);
//        mConnectingWidgets.setVisibility(View.VISIBLE);
        finish();
    }

    /**
     * 文件相关
     */

    /* 请求文件选择 */
    private void startChooseFile() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        /* onFileOpened */
        mOpenFileActivityResultLauncher.launch(intent);
    }

    /* 文件打开后 */
    private void onFileOpened(Intent data) {
        if (data == null) {
            Log.i(TAG, "文件为空");
            return;
        }
        mFileUri = data.getData();
        Log.i(TAG, "onFileOpened  " + mFileUri);
    }

    private void onSendFileSuccess() {
        Toast.makeText(this, "文件发送成功", Toast.LENGTH_SHORT).show();
    }

    private void onSendFileFailure() {
        Toast.makeText(this, "文件发送失败", Toast.LENGTH_SHORT).show();
    }


    private void onBeginSending() {
        mIsSendingFile = true;
        mBeforeFileSendWidgets.setVisibility(View.INVISIBLE);

        ViewGroup.LayoutParams layoutParams = mSendingProgressIndicator.getLayoutParams();
        int width = (int) (mFileSendingWidgets.getWidth() * 0.7);
        layoutParams.width = width;
        layoutParams.height = width;

        mSendingProgressIndicator.setLayoutParams(layoutParams);
        mSendingProgressIndicator.setIndicatorSize(width);
        mSendingProgressIndicator.setProgress(0);
        mSendProgressView.setText(String.format(Locale.getDefault(), "%d%%", 0));
        mFileSendingWidgets.setVisibility(View.VISIBLE);
    }

    /* 发送中，参数为进度(0 - 1) */

    private void onSendingFileProgressChange(int progress) {
        Log.i(TAG, "onSendingFileProgressChange: " + progress);
        mSendingProgressIndicator.setProgress(progress);
        mSendProgressView.setText(String.format(Locale.getDefault(), "%d%%", progress));
    }

    private void onEndSending() {
        mIsSendingFile = false;
        mSendingProgressIndicator.setProgress(100);
        mFileSendingWidgets.setVisibility(View.INVISIBLE);
        mBeforeFileSendWidgets.setVisibility(View.VISIBLE);
        mSendingProgressIndicator.setProgress(0);
    }

    /* 文件上传，必须存在文件Uri */
    @SuppressLint("Range")
    private void sendFile() {
        if (mIsSendingFile) {
            Toast.makeText(this, "正在发送，请稍后", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mFileUri == null) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(this::onBeginSending);
        mSendThread = new Thread(() -> {
            try {
            /*
                获取文件元信息
              */
                Log.i(TAG, "即将获取文件元信息...");
                FileDescriptorBody fileDescriptorBody = new FileDescriptorBody();
                try (Cursor cursor = getContentResolver().query(mFileUri, null, null, null)) {
                    if (cursor != null && cursor.moveToNext()) {
                        fileDescriptorBody.fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                        /* 大小有可能为空 */
                        int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                        if (!cursor.isNull(index)) {
                            fileDescriptorBody.fileSize = cursor.getLong(index);
                        } else {
                            fileDescriptorBody.fileSize = FileDescriptorBody.FILE_SIZE_UNKNOWN;
                        }
                        String[] splitFileName = fileDescriptorBody.fileName.split("\\.");
                        Log.i(TAG, "split" + Arrays.toString(splitFileName));
                        if (splitFileName.length < 1) {
                            fileDescriptorBody.fileType = "未知";
                        } else {
                            fileDescriptorBody.fileType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(splitFileName[splitFileName.length - 1]);
                            if (fileDescriptorBody.fileType == null) {
                                fileDescriptorBody.fileType = "未知";
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "文件打开错误", e);
                    runOnUiThread(() -> Toast.makeText(this, "无法获取文件信息!", Toast.LENGTH_SHORT).show());
                    return;
                }

                Log.i(TAG, "文件描述: " + fileDescriptorBody);

            /*
                发送文件描述符，观察服务端是否接收
             */
                boolean isServerAccept;
                try (Transfer transfer = new Transfer(mServerInfo.ip, mServerInfo.port,
                        TransferContrast.SOCKET_SO_TIMEOUT)) {
                    /*  send */
                    TransferHeader sendHeader = new TransferHeader();
                    sendHeader.contentType = TransferContrast.TYPE_FILE_DESCRIPTOR;
                    transfer.writeHeader(sendHeader);
                    transfer.write(fileDescriptorBody);
                    transfer.finishWrite();
                    Log.i(TAG, "文件描述header-body发送成功，开始接收服务端响应");

                    /* receive */
                    /* 接收服务端响应，阻塞 */
                    TransferHeader receiveHeader = transfer.readHeader();
                    if (receiveHeader.contentType != TransferContrast.TYPE_IDENTIFY_ACCEPT_FILE)
                        throw new IllegalArgumentException();
                    AcceptBody acceptBody = transfer.read();
                    transfer.finishRead();
                    isServerAccept = acceptBody.accept;
                    Log.i(TAG, "服务端接受?" + isServerAccept);

                } catch (IOException | IllegalArgumentException e) {
                    Log.e(TAG, "连接错误", e);
                    runOnUiThread(() -> ToastUtils.showConnectionErrorToast(this));
                    finish();
                    return;
                }

                if (!isServerAccept) {
                    runOnUiThread(() -> Toast.makeText(this, "发送方拒绝接收", Toast.LENGTH_SHORT).show());
                    return;
                }

                Log.i(TAG, "服务端接收，将创建连接...");

            /*
                发送文件本体
             */
                try (Transfer transfer = new Transfer(mServerInfo.ip, mServerInfo.port, TransferContrast.SOCKET_SO_TIMEOUT)) {
                    Log.i(TAG, "发送文件连接建立成功");
                    /* == write begin */
                    /* header */
                    TransferHeader header = new TransferHeader();
                    header.contentType = TransferContrast.TYPE_FILE;
                    transfer.writeHeader(header);
                    /* body */
                    final boolean isNeedShowProgress = fileDescriptorBody.fileSize != FileDescriptorBody.FILE_SIZE_UNKNOWN;
                    Log.i(TAG, "isNeedShowProgress?" + isNeedShowProgress);
                    final long allSize = fileDescriptorBody.fileSize;
                    long accumulatedSize = 0;
                    final byte[] buf = new byte[1024];
                    int len;
                    try (InputStream fileInputStream = getContentResolver().openInputStream(mFileUri)) {
                        // -1 end of stream  0 no more data
                        while (-1 != (len = fileInputStream.read(buf, 0, buf.length))) {
                            transfer.write(buf, 0, len);
                            accumulatedSize += len;
                            if (isNeedShowProgress) {
                                int progress = (int) Math.min(100 * ((float) accumulatedSize / allSize), 100);
                                runOnUiThread(() -> onSendingFileProgressChange(progress));
                            }
                        }
                    }
                    transfer.finishWrite();
                    transfer.safelyShutDownOutputStream();
                    /* == write end == */


                    Log.i(TAG, "文件发送结束 size: " + accumulatedSize + "/" + allSize);

                    Log.i(TAG, "即将获取服务端结果...");
                /*
                    获取服务端接收结果
                 */
                    TransferHeader resultHeader = transfer.readHeader();
                    boolean successReceived;
                    if (resultHeader.contentType != TransferContrast.TYPE_RECEIVE_RESULT) {
                        successReceived = false;
                    } else {
                        ReceiveResultBody resultBody = transfer.read();
                        successReceived = resultBody.success;
                    }
                    transfer.finishRead();
                    Log.i(TAG, "文件流输出完成，服务端接收结果: " + successReceived);

                    if (successReceived) {
                        SendFileActivity.this.runOnUiThread(SendFileActivity.this::onSendFileSuccess);
                    /*
                     添加到历史
                    */
                        HistoryInfo historyInfo = new HistoryInfo();
                        historyInfo.fileName = fileDescriptorBody.fileName;
                        historyInfo.fileSize = FileUtils.byteSizeToHumanSize(fileDescriptorBody.fileSize);
                        historyInfo.fileType = fileDescriptorBody.fileType;
                        historyInfo.deviceName = mServerInfo.ssid;
                        historyInfo.time = new Date();
                        historyInfo.transmissionType = HistoryInfo.TYPE_SEND;
                        addHistoryInfo(historyInfo);
                    } else {
                        SendFileActivity.this.runOnUiThread(SendFileActivity.this::onSendFileFailure);
                    }

                } catch (IOException e) {
                    /* 接收端强行关闭或者其他问题 */
                    Log.v(TAG, "Transfer", e);
                    SendFileActivity.this.runOnUiThread(SendFileActivity.this::onSendFileFailure);
                    finish();
                }
            } finally {
                runOnUiThread(SendFileActivity.this::onEndSending);
                mFileUri = null;
            }
        });
        mSendThread.start();
    }

    private void addHistoryInfo(@NonNull HistoryInfo historyInfo) {
        new Thread(() -> {
            HistoryDao historyDao = MyApplication.getInstance().getAppDatabase().historyDao();
            historyDao.insert(historyInfo);
        }).start();
    }

    /* View.OnClickListener */
    @Override
    public void onClick(View v) {
        if (mBeforeFileSendWidgets == v) {
            sendFile();
        } else if (mChooseFileBtn == v) {
            startChooseFile();
        }
    }

}