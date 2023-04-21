package cc.martix.drop.network;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class TransferHeader implements Serializable {
    public static final long serialVersionUID = 1L;

    /* 内容类型，自定义 */
    public int contentType;

    /* 额外Code */
    public int code;

    @NonNull
    @Override
    public String toString() {
        return "TransferHeader{" +
                "contentType=" + contentType +
                ", extraCode=" + code +
                '}';
    }
}
