package cc.martix.drop.dao;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import cc.martix.drop.MyApplication;
import cc.martix.drop.pojo.HistoryInfo;

@TypeConverters(DatabaseTypeConverter.class)
@Database(entities = {HistoryInfo.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract HistoryDao historyDao();

    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance() {
        if (instance == null) {
            instance = MyApplication.getInstance().getAppDatabase();
        }
        return instance;
    }
}
