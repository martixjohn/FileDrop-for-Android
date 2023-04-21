package cc.martix.drop.pages.receive;


import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import cc.martix.drop.MyApplication;
import cc.martix.drop.R;
import cc.martix.drop.dao.HistoryDao;
import cc.martix.drop.network.Server;
import cc.martix.drop.network.Transfer;
import cc.martix.drop.network.TransferHeader;
import cc.martix.drop.network.contrast.AcceptBody;
import cc.martix.drop.network.contrast.FileDescriptorBody;
import cc.martix.drop.network.contrast.ReceiveResultBody;
import cc.martix.drop.network.contrast.TransferContrast;
import cc.martix.drop.pojo.ClientInfo;
import cc.martix.drop.pojo.HistoryInfo;
import cc.martix.drop.pojo.ServerInfo;
import cc.martix.drop.utils.FileUtils;
import cc.martix.drop.utils.ToastUtils;

public class ReceiveFileActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "ReceiveFileActivity";

    public static final String EXTRA_SERVER_INFO = "HS";

    public static final String EXTRA_CLIENT_INFO = "CL";

    public static final int RESULT_CODE_DISCONNECT = 2;
    private ActivityResultLauncher<String> mSaveFileResultLauncher;
    private OutputStream mFileOutputStream;
    private Server mServer;
    private Thread mSocketThread;
    private ServerInfo mServerInfo;
    private ClientInfo mClientInfo;
    private TextView mClientInfoTextView;
    private Button mDisconnectBtn;
    private ViewGroup mReceivingProgressWidgets;
    private CircularProgressIndicator mProgressIndicator;
    private TextView mProgressTextView;
    private TextView mFileReceivingTextView;
    private Button mPreviewFileBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_file);

        /* View */
        mDisconnectBtn = findViewById(R.id.btn_disconnect_client);
        mDisconnectBtn.setOnClickListener(this);
        mReceivingProgressWidgets = findViewById(R.id.widgets_file_receiving_progress);
        mProgressIndicator = findViewById(R.id.progress_indicator_sending);
        mProgressTextView = findViewById(R.id.tv_receive_progress);
        mFileReceivingTextView = findViewById(R.id.tv_file_receiving);
        mClientInfoTextView = findViewById(R.id.tv_connected_to_client);
        mPreviewFileBtn = findViewById(R.id.btn_preview_file);
//        mPreviewFileBtn.setVisibility(View.INVISIBLE);
//        mPreviewFileBtn.setOnClickListener(this);

        /* Intent */
        Intent intent = getIntent();
        mServerInfo = (ServerInfo) intent.getSerializableExtra(EXTRA_SERVER_INFO);
        mClientInfo = (ClientInfo) intent.getSerializableExtra(EXTRA_CLIENT_INFO);
        mClientInfoTextView.setText(String.format("%s%s", getString(R.string.connected_to), mClientInfo.deviceName));



        /* 保存文件 */


        mSaveFileResultLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(),
                this::onChooseFileActivityResult);

        /* 服务器实例 */
        mServer = Server.getInstance();
        /* 注册回调，取消注册交给上级 */
        mServer.register(transfer -> {
            mSocketThread = Thread.currentThread();
            try {
                /* 头信息 */
                TransferHeader header = transfer.readHeader();
                Log.i(TAG, "Header读取完毕，交给子方法处理Body: " + header);
                /* Body */
                /* 文件 */
                switch (header.contentType) {
                    /* 客户端要求断开连接 */
                    case TransferContrast.TYPE_DISCONNECT:
                        Log.i(TAG, "TYPE_DISCONNECT");
                        runOnUiThread(() -> Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show());
                        finish();
                        return;
                    /* 文件描述符 */
                    case TransferContrast.TYPE_FILE_DESCRIPTOR:
                        Log.i(TAG, "TYPE_FILE_DESCRIPTOR");
                        chooseAcceptOrNotAsync(transfer);
                        break;
                    /* 文件 */
                    case TransferContrast.TYPE_FILE:
                        Log.i(TAG, "TYPE_FILE");
                        if (mShouldAcceptCurrentFile) {
                            receiveFileAsync(transfer);
                        }
                        mFileDescriptor = null;
                        mShouldAcceptCurrentFile = false;
                        break;
                    default:
                        mFileDescriptor = null;
                        mShouldAcceptCurrentFile = false;
                }
                Log.i(TAG, "尾信息读取完毕" + header);

            } catch (IOException e) {
                Log.w(TAG, "receive transfer", e);
                runOnUiThread(() -> ToastUtils.showConnectionErrorToast(this));
                finish();
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "client error");
                finish();
            } finally {
                mSocketThread = null;
            }

        });


        /* 让上级处理关闭服务器 */
        setResult(RESULT_CODE_DISCONNECT);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }


    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("确认返回？这将断开连接")
                .setPositiveButton("确认", (dialog, which) -> {
                    super.onBackPressed();
                })
                .setNegativeButton("取消", ((dialog, which) -> {
                    dialog.dismiss();
                }))
                .create()
                .show();
    }

    private void _interruptTask() {
        mSocketThread.interrupt();
    }

    private void _waitUtilFinishTask(Transfer transfer) throws IOException {
        while (true) {
            try {
                /* 忙等检测 */
                Thread.sleep(1000);
                /* 关闭直接抛出异常 */
                if (transfer.isClosed()) {
                    throw new IOException("closed");
                }
            } catch (InterruptedException e) {
                // 空
                return;
            }
        }
    }

    /**
     * 文件相关，同一时刻只允许一个文件
     */
    /* 是否应该接收当前文件 */
    private boolean mShouldAcceptCurrentFile = false;

    /* 当前文件描述 */
    private FileDescriptorBody mFileDescriptor;

    /* 选择文件 */
    private void onChooseFileActivityResult(Uri uri) {
        Log.i(TAG, "onChooseFileActivityResult" + uri);
        if (uri == null) {
            mShouldAcceptCurrentFile = false;
            _interruptTask();
            return;
        }

        try {

            mFileOutputStream = new BufferedOutputStream(
                    getContentResolver().openOutputStream(uri),
                    1024 * 1024);// 1M 一个单位写
            Log.i(TAG, "获取到mFileOutputStream");
            mShouldAcceptCurrentFile = true;
            _interruptTask();
        } catch (FileNotFoundException e) {
            mFileOutputStream = null;
            Log.e(TAG, "获取到mFileOutputStream错误", e);
        }
    }

    private void sendAcceptResult(Transfer transfer, boolean accept) throws IOException {
        /* 返回结果 */
        TransferHeader resultHeader = new TransferHeader();
        resultHeader.contentType = TransferContrast.TYPE_IDENTIFY_ACCEPT_FILE;
        transfer.writeHeader(resultHeader);
        AcceptBody resultBody = new AcceptBody();
        resultBody.accept = accept;
        transfer.write(resultBody);
        transfer.finishWrite();
        Log.i(TAG, "sendAcceptResult: " + accept);
    }


    private void chooseAcceptOrNotAsync(Transfer transfer) throws IOException, ClassNotFoundException {
        Log.i(TAG, "chooseAcceptOrNotAsync");
        FileDescriptorBody fileDescriptor = transfer.read();
        mFileDescriptor = fileDescriptor;
        Log.i(TAG, "接收到的fileDescriptor: " + (mFileDescriptor));
        /* 转换成便于查看的大小 */
        String fileSize;
        if (fileDescriptor.fileSize == FileDescriptorBody.FILE_SIZE_UNKNOWN) {
            fileSize = "未知大小";
        } else {
            fileSize = FileUtils.byteSizeToHumanSize(fileDescriptor.fileSize);
        }

        final String fileName = fileDescriptor.fileName;
        final String fileType = fileDescriptor.fileType;

        Log.i(TAG, "等待用户选择是否接收");
        /* AlertDialog提示用户是否接收 */
        mShouldAcceptCurrentFile = false;
        runOnUiThread(() -> {
            final AlertDialog acceptAlertDialog = new AlertDialog.Builder(this)
                    .setTitle("有客户端请求发送文件")
                    .setMessage(String.format("文件名: %s\n文件类型: %s\n文件大小: %s\n", fileName, fileType, fileSize))
                    .setPositiveButton("接收", (dialog, which) -> mShouldAcceptCurrentFile = true)
                    .setNegativeButton("拒绝", (dialog, which) -> mShouldAcceptCurrentFile = false)
                    .setOnCancelListener(dialog -> mShouldAcceptCurrentFile = false)
                    .setOnDismissListener(dialog -> {
                        Log.i(TAG, "DISMISS DIALOG");
                        _interruptTask();
                    }).create();
            acceptAlertDialog.show();

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (acceptAlertDialog.isShowing()) {
                        runOnUiThread(() -> {
                            Toast.makeText(ReceiveFileActivity.this, "超时，取消接收", Toast.LENGTH_SHORT).show();
                            acceptAlertDialog.hide();
                        });
                        mShouldAcceptCurrentFile = false;
                        if (mSocketThread != null && mSocketThread.isAlive())
                            _interruptTask();
                    }
                }
            }, 1000 * 60);
        });

        /* 阻塞 */
        _waitUtilFinishTask(transfer);

        if (!mShouldAcceptCurrentFile) {
            sendAcceptResult(transfer, false);
            runOnUiThread(() -> Toast.makeText(this, R.string.reject_accept, Toast.LENGTH_SHORT).show());
            return;
        }

        Log.i(TAG, "用户选择接收文件");
        /* 创建文件 */
        mSaveFileResultLauncher.launch(mFileDescriptor.fileName);

        /* 阻塞，等待文件选择结束 */
        _waitUtilFinishTask(transfer);

        /* 选择文件回退，或者出现错误 */
        if (!mShouldAcceptCurrentFile || mFileOutputStream == null) {
            sendAcceptResult(transfer, false);
            runOnUiThread(() -> Toast.makeText(this, R.string.cancel_accept, Toast.LENGTH_SHORT).show());
            mFileOutputStream = null;
            return;
        }
        /* 返回结果 */
        sendAcceptResult(transfer, true);
        Log.i(TAG, "响应Accept消息结束");
    }

    /* 返回文件接收结果：成功/失败 */
    private void _sendReceiveResult(Transfer transfer, boolean success) throws IOException {
        /* 返回结果 */
        TransferHeader resultHeader = new TransferHeader();
        resultHeader.contentType = TransferContrast.TYPE_RECEIVE_RESULT;
        transfer.writeHeader(resultHeader);
        ReceiveResultBody resultBody = new ReceiveResultBody();
        resultBody.success = success;
        transfer.write(resultBody);
        transfer.finishWrite();
        Log.i(TAG, "返回接收结果: " + success);
    }


    private void onReceiveStart() {
        mReceivingProgressWidgets.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams layoutParams = mProgressIndicator.getLayoutParams();
        int width = (int) (mReceivingProgressWidgets.getWidth() * 0.7);
        layoutParams.width = layoutParams.height = width;
        mProgressIndicator.setLayoutParams(layoutParams);
        mProgressIndicator.setIndicatorSize(width);
        mProgressIndicator.setProgress(0);
        mFileReceivingTextView.setText(R.string.file_receiving);
//        mPreviewFileBtn.setVisibility(View.INVISIBLE);
    }

    private void onReceivingFileProgressChange(int progress) {
        mProgressIndicator.setProgress(progress);
        mProgressTextView.setText(String.format(Locale.getDefault(), "%d%%", progress));

    }

    private void onReceivingFileSuccess() {
        Toast.makeText(this, R.string.file_received, Toast.LENGTH_SHORT).show();
        mProgressIndicator.setProgress(100);
        mFileReceivingTextView.setText(R.string.file_received);
//        mPreviewFileBtn.setVisibility(View.VISIBLE);
    }

    private void onReceivingFileError() {
        Toast.makeText(this, R.string.file_receive_failed, Toast.LENGTH_SHORT).show();
        mProgressIndicator.setProgress(0);
        mFileReceivingTextView.setText(R.string.file_receive_failed);
//        mPreviewFileBtn.setVisibility(View.INVISIBLE);
    }


    /*
        接收文件
        外部已使用异步，不关闭流
    */
    private void receiveFileAsync(Transfer transfer) throws IOException {
        Log.i(TAG, "receiveFileAsync");
        if (mFileOutputStream == null) {
            Log.e(TAG, "receiveFileAsync 发现mFileOutputStream为空");
            return;
        }
        runOnUiThread(this::onReceiveStart);
        /* 文件选择结束，开始读取客户端，写入文件 */
        long allSize = mFileDescriptor.fileSize;
        boolean isNeedShowProgress = mFileDescriptor.fileSize != FileDescriptorBody.FILE_SIZE_UNKNOWN;
        /*
            数据读写
         */
        byte[] buf = new byte[1024];
        long accumulatedSize = 0;
        int len;
        Log.i(TAG, "开始文件读取和写入");
        long timeStamp = System.currentTimeMillis();
        try {
            while ((len = transfer.read(buf, 0, buf.length)) != -1) {
                accumulatedSize += len;
                mFileOutputStream.write(buf, 0, len);
                if (isNeedShowProgress) {
                    final int progress = (int) Math.min(100 * ((float) accumulatedSize / allSize), 100);
                    runOnUiThread(() -> onReceivingFileProgressChange(progress));
                }
            }
            mFileOutputStream.flush();
            transfer.finishRead();

            /* 返回结果 */
            _sendReceiveResult(transfer, true);

            /*
              添加到历史
            */
            HistoryInfo historyInfo = new HistoryInfo();

            historyInfo.fileName = mFileDescriptor.fileName;
            historyInfo.fileSize = FileUtils.byteSizeToHumanSize(mFileDescriptor.fileSize);
            historyInfo.deviceName = mClientInfo.deviceName;
            historyInfo.fileType = mFileDescriptor.fileType;
            historyInfo.time = new Date();
            historyInfo.transmissionType = HistoryInfo.TYPE_RECEIVE;
            _addHistoryInfo(historyInfo);
            runOnUiThread(this::onReceivingFileSuccess);
        } catch (IOException e) {
            Log.w(TAG, "接收文件出现错误", e);
            /* 返回结果 */
            _sendReceiveResult(transfer, false);
            runOnUiThread(this::onReceivingFileError);
        } finally {
            timeStamp = System.currentTimeMillis() - timeStamp;
            Log.i(TAG, "耗时" + timeStamp / 1000.0 + "s");
            Log.i(TAG, "文件流接收完毕 size: " + accumulatedSize + "/" + allSize);
            if (allSize != FileDescriptorBody.FILE_SIZE_UNKNOWN && accumulatedSize < allSize) {
                runOnUiThread(() -> Toast.makeText(this, R.string.file_damaged, Toast.LENGTH_SHORT).show());
            }
            if (mFileOutputStream != null) {
                mFileOutputStream.flush();
                mFileOutputStream.close();
                mFileOutputStream = null;
                Log.i(TAG, "文件输出流正常关闭");
            }

        }

    }

    private void _addHistoryInfo(@NonNull HistoryInfo historyInfo) {
        new Thread(() -> {
            HistoryDao historyDao = MyApplication.getInstance().getAppDatabase().historyDao();
            historyDao.insert(historyInfo);
        }).start();
    }

    @Override
    public void onClick(View v) {
        if (mDisconnectBtn == v) {
            finish();
        } else if (mPreviewFileBtn == v) {
            try {
//                Intent intent = getPackageManager().getLaunchIntentForPackage("com.android.documentsui");
//                if(intent == null){
//
//                }
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                String fileSavingLocation = MyApplication.getInstance().getAppPreferences(this).fileSavingLocation;
                Log.i(TAG, "fileLocation: " + fileSavingLocation);
                Uri parse = Uri.parse(fileSavingLocation);
                intent.setData(parse);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_found_app_can_open_document, Toast.LENGTH_SHORT).show();
                Log.w(TAG, e.getMessage(), e);
            }
        }
    }


}