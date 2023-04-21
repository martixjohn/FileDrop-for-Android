package cc.martix.drop;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.room.Room;

import cc.martix.drop.dao.AppDatabase;
import cc.martix.drop.pojo.AppPreferences;

public class MyApplication extends Application {

    private static MyApplication instance;

    private AppDatabase mAppDatabase;
    private SharedPreferences mSharedPreferences;
    private AppPreferences mAppPreferences;

    public static MyApplication getInstance() {
        return instance;
    }

    public AppDatabase getAppDatabase() {
        return mAppDatabase;
    }

    /**
     * 获取最新的AppPreferences
     */
    public AppPreferences getAppPreferences(Context context) {
        if (mAppPreferences == null) {
            synchronized (this) {
                mSharedPreferences = context.getSharedPreferences(getString(R.string.shared_settings_name), Context.MODE_PRIVATE);
                mAppPreferences = new AppPreferences();
                mAppPreferences.deviceName = mSharedPreferences.getString(getString(R.string.shared_settings_device_name), Build.MODEL);
                mAppPreferences.fileSavingLocation = mSharedPreferences.getString(getString(R.string.shared_settings_device_name), null);
            }
        }
        return (AppPreferences) mAppPreferences.clone();
    }

    @SuppressLint("ApplySharedPref")
    public synchronized void setAppPreferences(@NonNull AppPreferences appPreferences) {
        SharedPreferences.Editor edit = mSharedPreferences.edit();
        mAppPreferences = (AppPreferences) appPreferences.clone();
        edit.putString(getString(R.string.shared_settings_device_name), mAppPreferences.deviceName);
        edit.putString(getString(R.string.shared_settings_saving_location), mAppPreferences.fileSavingLocation);
        edit.commit();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mAppDatabase = Room.databaseBuilder(this, AppDatabase.class, "db").build();
    }

}
