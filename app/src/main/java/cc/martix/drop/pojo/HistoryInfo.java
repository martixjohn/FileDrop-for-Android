package cc.martix.drop.pojo;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Date;

@Entity(tableName = "history")
public class HistoryInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @PrimaryKey(autoGenerate = true)
    public Integer uid;

    @ColumnInfo(name = "device_name")
    public String deviceName;

    public Integer transmissionType;
    @ColumnInfo(name = "file_name")
    public String fileName;

    @ColumnInfo(name = "file_size")
    public String fileSize;

    public Date time;

    public static int TYPE_SEND = 1;
    public static int TYPE_RECEIVE = 2;

    public static String TYPE_SEND_STRING = "发送";
    public static String TYPE_RECEIVE_STRING = "接收";

    /* 文件类型, null未知 */
    public String fileType;
}
