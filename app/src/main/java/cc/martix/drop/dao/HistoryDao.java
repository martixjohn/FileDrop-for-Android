package cc.martix.drop.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import cc.martix.drop.pojo.HistoryInfo;

@Dao
public interface HistoryDao {
    @Query("SELECT * FROM history")
    List<HistoryInfo> getAll();

    @Query("DELETE FROM history")
    void deleteAll();

    @Delete(entity = HistoryInfo.class)
    void delete(HistoryInfo historyInfo);

    @Insert(entity = HistoryInfo.class)
    void insert(HistoryInfo historyInfo);
}

