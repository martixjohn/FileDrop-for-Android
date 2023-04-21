package cc.martix.drop.network;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * 协议封装
 * 一次连接可以多次请求，复用Socket
 * 发送请求后，响应方响应接收结束
 * <p>
 * 一个Transfer对应一个Socket
 */
public class Transfer implements AutoCloseable {

    public static final String TAG = "Transfer";

    private Socket mSocket;
    private TransferHeader mReceiveHeader;

    public Transfer(String ip, int port, int soTimeout) throws IOException {
        mSocket = new Socket(ip, port);
        mSocket.setSoTimeout(soTimeout);
        Log.i(TAG, "Socket:" + mSocket);
    }

    private Transfer(Socket socket) {
        mSocket = socket;
    }

    public static Transfer ofSocket(Socket socket) {
        return new Transfer(socket);
    }

    public boolean isClosed() {
        if (mSocket != null) {
            return mSocket.isConnected() && mSocket.isClosed();
        }
        return true;
    }

    private ObjectOutputStream mOutputStream;

    private synchronized ObjectOutputStream getOutputStream() throws IOException {
        if (mOutputStream == null) {
            mOutputStream = new ObjectOutputStream(new BufferedOutputStream(mSocket.getOutputStream()));
        }
        return mOutputStream;
    }


    private ObjectInputStream mInputStream;

    private synchronized ObjectInputStream getInputStream() throws IOException {
        if (mInputStream == null) {
            mInputStream = new ObjectInputStream(new BufferedInputStream(mSocket.getInputStream()));
        }
        return mInputStream;
    }


    /* WRITE */

    private TransferHeader mSendHeader;


    public void writeHeader(TransferHeader header) throws IOException {
        if (mSendHeader != null) throw new IOException("Header has been written");
        mSendHeader = header;
        getOutputStream().writeObject(header);
        flush();
    }

    public void write(@NonNull Object data) throws IOException {
        if (mSendHeader == null) throw new IOException("Header not written");
        getOutputStream().writeObject(data);
    }

    public void write(@NonNull byte[] buf) throws IOException {
        write(buf, 0, buf.length);
    }

    public void write(@NonNull byte[] buf, int off, int len) throws IOException {
        if (mSendHeader == null) throw new IOException("Header not written");
        getOutputStream().write(buf, off, len);
    }

    /* 完成本轮写 */
    public void finishWrite() throws IOException {
        mSendHeader = null;
        flush();
    }

    public void flush() throws IOException {
        if (mOutputStream != null) {
            mOutputStream.flush();
        }
    }


    /**
     * 表示此次发送结束，需要接收应答
     * 阻塞接收对方应答
     *
     * @apiNote 对方必须搭配 notifyReadFinished
     */


    /* READ */
    public TransferHeader readHeader() throws IOException {
        if (mReceiveHeader != null) throw new IOException("Header has been read");
        ObjectInputStream inputStream = getInputStream();
        try {
            mReceiveHeader = (TransferHeader) inputStream.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Internal Error");
        }
        return mReceiveHeader;
    }

    @SuppressWarnings("unchecked")
    public <T> T read() throws IOException {
        if (mReceiveHeader == null) throw new IOException("Header has not been read");
        try {
            return (T) getInputStream().readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Read Error");
        }
    }

    public int read(byte[] bytes) throws IOException {
        return read(bytes, 0, bytes.length);
    }

    public int read(byte[] bytes, int off, int len) throws IOException {
        return getInputStream().read(bytes, off, len);
    }

    /* 完成本轮读 */
    public void finishRead() {
        mReceiveHeader = null;
    }


    /* 必须先于 closeOutputStream 执行 */
    public void safelyShutDownInputStream() throws IOException {
        if (mInputStream != null) {
            finishRead();
            if (!mSocket.isInputShutdown()) {
                try {
                    /* 有可能对方已经关闭 */
                    mSocket.shutdownInput();
                } catch (IOException e) {
                    Log.v(TAG, "safelyShutDownOutputStream", e);
                }
            }
            mInputStream = null;
        }
    }

    /* 必须后于 closeInputStream 执行 */
    public void safelyShutDownOutputStream() throws IOException {
        if (mOutputStream != null) {
            finishWrite();
            if (!mSocket.isOutputShutdown()) {
                try {
                    /* 有可能对方已经关闭 */
                    mSocket.shutdownOutput();
                } catch (IOException e) {
                    Log.v(TAG, "safelyShutDownOutputStream" + e);
                }
            }
            mOutputStream = null;
        }
    }

    @Override
    public void close() throws IOException {
        if (mSocket != null && !mSocket.isClosed()) {
            safelyShutDownInputStream();
            safelyShutDownOutputStream();
            mSocket.close();
            mSocket = null;
            Log.i(TAG, "Socket closed");
        }
    }
}
