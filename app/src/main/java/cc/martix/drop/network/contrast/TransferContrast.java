package cc.martix.drop.network.contrast;

public abstract class TransferContrast {
    public final static int SOCKET_SO_TIMEOUT = 1000 * 60;


    public static final int TYPE_DISCONNECT = -1;             // 取消连接请求
    public static final int TYPE_CONNECTING = 0;             // 表示连接上
    public static final int TYPE_FILE = 1;                  // 文件       body为二进制流
    public static final int TYPE_FILE_DESCRIPTOR = 2;       // 文件描述    body为FileDescriptorTransferBody
    public static final int TYPE_IDENTIFY_ACCEPT_FILE = 3;           // 确认接收    body为AcceptTransferBody

    public static final int TYPE_RECEIVE_RESULT = 4;           // 接受结果    body为AcceptTransferBody


}
