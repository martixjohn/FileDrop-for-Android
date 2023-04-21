package cc.martix.drop.dao;

import androidx.room.TypeConverter;

import java.util.Date;

public class DatabaseTypeConverter {

    @TypeConverter
    public static Long dateToTimestamp(Date date){
        return date.getTime();
    }

    @TypeConverter
    public static Date timestampToDate(Long timestamp){
        return new Date(timestamp);
    }
}
