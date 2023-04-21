package cc.martix.drop.network.contrast;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class FileDescriptorBody implements Serializable {
    public static final long serialVersionUID = 1L;

    public static final long FILE_SIZE_UNKNOWN = -1;
    public String fileName;     // 文件名
    public String fileType;     // 文件类型可选
    public long fileSize;       // 字节 -1未知

    @NonNull
    @Override
    public String toString() {
        return "FileDescriptorTransferBody{" +
                "fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", fileSize=" + fileSize +
                '}';
    }
}
