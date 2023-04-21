package cc.martix.drop.network;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;

import cc.martix.drop.network.contrast.TransferContrast;

public class Server {
    public static final String TAG = "ServerConnection";

    private boolean mIsRunning = false;
    private Thread mThread;
    private List<TransferCallback> mTransferCallbacks = new LinkedList<>();
    private Runnable mTimeOutCallback;

    public int getPort() {
        return mPort;
    }

    private int mPort = 0;

    private Server() {
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    private static final Server instance = new Server();

    public static Server getInstance() {
        return instance;
    }

    /**
     * 异步创建服务器
     */
    public void startServer(@Nullable ServerCreatedCallback serverCreatedCallback) {
        if (mIsRunning) {
            Log.w(TAG, "已经启动");
            throw new RuntimeException("Server Already Started");
        }
        mIsRunning = true;
        mThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                serverSocket.setSoTimeout(TransferContrast.SOCKET_SO_TIMEOUT);
                mPort = serverSocket.getLocalPort();
                if (serverCreatedCallback != null) {
                    serverCreatedCallback.run(mPort);
                }
                Log.i(TAG, "服务器启动在" + mPort + "端口");
                while (mIsRunning) {
                    try {
                        Log.i(TAG, "等待Socket...");
                        Socket socket = serverSocket.accept();
                        // 此时不再处理请求
                        if (!mIsRunning) return;
                        Log.i(TAG, "接收到请求，等待处理..." + socket);
                        try (Transfer transfer = Transfer.ofSocket(socket)) {
                            for (TransferCallback transferCallback : mTransferCallbacks) {
                                transferCallback.run(transfer);
                            }
                            Log.i(TAG, "请求处理完毕" + socket);
                        } catch (SocketTimeoutException e) {
                            Log.i(TAG, "SocketTimeoutException");
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "serverSocket.accept()", e);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Start server error", e);
            } finally {
                /* 此时关闭了 */
                closeServer();
            }
        });
        mThread.start();
    }

    public void register(@NonNull TransferCallback callback) {
        Log.i(TAG, "register " + callback);
        mTransferCallbacks.add(callback);
    }

    public void unRegister(@NonNull TransferCallback callback) {
        Log.i(TAG, "unRegister " + callback);
        mTransferCallbacks.remove(callback);
    }

//    public void registerTimeoutCallback(Runnable callback) {
//        mTimeOutCallback = callback;
//    }

    public void clearRegisters() {
        mTransferCallbacks = new LinkedList<>();
    }

    /**
     * 关闭服务器，清空线程，清空回调
     */
    public void closeServer() {
        if (mThread != null && mThread.isAlive()) {
            mThread.interrupt();
            mThread = null;
        }
        clearRegisters();
        mIsRunning = false;
        Log.i(TAG, "服务器已关闭");
    }

    public interface TransferCallback {
        void run(Transfer transfer);
    }

    public interface ServerCreatedCallback {
        void run(int port);
    }
}
