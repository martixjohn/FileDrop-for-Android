package cc.martix.drop.pages.history;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cc.martix.drop.R;
import cc.martix.drop.dao.HistoryDao;
import cc.martix.drop.dao.AppDatabase;
import cc.martix.drop.pojo.HistoryInfo;


public class HistoryFragment extends Fragment implements View.OnClickListener {
    public static final String TAG = "HistoryFragment";

    private RecyclerView mListRecyclerView;
    private Button mClearHistoryBtn;
    private HistoryListAdapter mHistoryListAdapter;
    private HistoryDao mHistoryDao;
    private List<HistoryInfo> mHistoryList;

    private static HistoryFragment instance;
    private ViewGroup mView;
    private FragmentActivity mActivity;
    private Thread mInitThread;
    private AlertDialog mClearHistoryAlertDialog;

    public HistoryFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mView = (ViewGroup) inflater.inflate(R.layout.fragment_history, container, false);
        mActivity = getActivity();
        mClearHistoryBtn = mView.findViewById(R.id.btn_clear_history);
        mClearHistoryBtn.setOnClickListener(this);

        mListRecyclerView = mView.findViewById(R.id.rv_history);


        mInitThread = new Thread(() -> {
            mHistoryDao = AppDatabase.getInstance().historyDao();
        });
        mInitThread.start();

        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        new Thread(() -> {
            try {
                if (mInitThread.isAlive()) {
                    mInitThread.join();
                }
            } catch (InterruptedException e) {
                // 继续执行
            }
            /* 重新从数据中获取 */
            mHistoryList = mHistoryDao.getAll();
            mHistoryListAdapter = new HistoryListAdapter(HistoryFragment.this, mHistoryList);
            mHistoryListAdapter.setOnItemClickListener(HistoryFragment.this::createDialogFromHistoryInfo);
            mActivity.runOnUiThread(() -> mListRecyclerView.setAdapter(mHistoryListAdapter));
        }).start();
    }


    private void createDialogFromHistoryInfo(int pos, HistoryInfo info) {
        final View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.history_dialog_content, mView, false);
        final TextView dialogDeviceNameTextView = dialogView.findViewById(R.id.tv_history_device_name);
        final TextView dialogTransmissionTypeTextView = dialogView.findViewById(R.id.tv_history_transimission_type);
        final TextView dialogFileNameTextView = dialogView.findViewById(R.id.tv_history_file_name);
        final TextView dialogFileSizeTextView = dialogView.findViewById(R.id.tv_history_file_size);
        final TextView dialogTimeTextView = dialogView.findViewById(R.id.tv_history_time);
        final TextView dialogFileTypeTextView = dialogView.findViewById(R.id.tv_history_file_type);
        final Button dialogDeleteBtn = dialogView.findViewById(R.id.btn_delete_current_history);
        dialogFileNameTextView.setText(String.format("%s: %s", getString(R.string.file_name), info.fileName));
        dialogFileSizeTextView.setText(String.format("%s: %s", getString(R.string.file_size), info.fileSize));
        dialogFileTypeTextView.setText(String.format("%s: %s", getString(R.string.file_type), info.fileType == null ? "未知" : info.fileType));
        dialogDeviceNameTextView.setText(String.format("%s: %s", getString(R.string.device), info.deviceName));
        dialogTransmissionTypeTextView.setText(String.format("%s: %s", getString(R.string.transmission_type), info.transmissionType));
        dialogTimeTextView.setText(String.format("%s: %s", getString(R.string.transmission_timestamp), info.time));

        AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.detail)
                .setView(dialogView)
                .create();


        dialogDeleteBtn.setOnClickListener(b -> {
            synchronized (this) {
                Log.i(TAG, "delete at " + pos);
                mHistoryList.remove(pos);
                mHistoryListAdapter.notifyItemRemoved(pos);
                /* 异步删除 */
                new Thread(() -> {
                    mHistoryDao.delete(info);
                }).start();
            }
            Toast.makeText(mActivity, R.string.deleted, Toast.LENGTH_SHORT).show();
            alertDialog.hide();
        });

        alertDialog.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void clearHistory() {
        /* 如果为空，跳过 */
        if(mHistoryList.isEmpty()){
            return;
        }
        /* 清空 */
        new Thread(() -> {
            mHistoryDao.deleteAll();
        }).start();
        while (!mHistoryList.isEmpty()) {
            mHistoryList.remove(mHistoryList.size() - 1);
        }
        mHistoryListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        if (mClearHistoryBtn == v) {
            if (mClearHistoryAlertDialog == null) {
                mClearHistoryAlertDialog = new AlertDialog.Builder(mActivity)
                        .setTitle(R.string.is_sure_clear)
                        .setMessage(R.string.action_cannot_rollback)
                        .setPositiveButton(getString(R.string.sure), (dialog, which) -> clearHistory())
                        .setNegativeButton(getString(R.string.cancel), null)
                        .create();
            }
            mClearHistoryAlertDialog.show();
        }
    }
}