package cc.martix.drop.pages.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDivider;

import java.text.SimpleDateFormat;
import java.util.List;

import cc.martix.drop.R;
import cc.martix.drop.pojo.HistoryInfo;

public class HistoryListAdapter extends RecyclerView.Adapter<HistoryListAdapter.ViewHolder> {

    private final Context context;
    private final List<HistoryInfo> historyInfoList;
    private final HistoryFragment historyFragment;

    public HistoryListAdapter(HistoryFragment historyFragment, List<HistoryInfo> historyInfoList) {
        this.historyFragment = historyFragment;
        this.context = historyFragment.getContext();
        this.historyInfoList = historyInfoList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_info, parent, false);
        return new ViewHolder(view, mOnItemClickListener);
    }

    private SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryInfo item = this.historyInfoList.get(position);
        holder.pos = position;
        holder.info = item;
        String fileName = item.fileName;
        String fileSize = item.fileSize;
        String deviceName = item.deviceName;
        String transmissionType = item.transmissionType == HistoryInfo.TYPE_RECEIVE ? HistoryInfo.TYPE_RECEIVE_STRING : HistoryInfo.TYPE_SEND_STRING;
        String time = dateTimeFormatter.format(item.time);

        holder.fileNameTextView.setText(fileName);
        holder.fileSizeTextView.setText(String.format("%s: %s", context.getString(R.string.size_prefix), fileSize));
        holder.deviceNameTextView.setText(deviceName);
        holder.transmissionTypeTextView.setText(transmissionType);
        holder.timeTextView.setText(time);


        if (position == historyInfoList.size() - 1) {
            holder.divider.setVisibility(View.INVISIBLE);
        } else {
            holder.divider.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return historyInfoList.size();
    }

    public interface OnItemClickListener {
        void handle(int pos, HistoryInfo info);
    }

    private OnItemClickListener mOnItemClickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }


    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public final TextView deviceNameTextView;
        public final TextView transmissionTypeTextView;
        public final TextView fileNameTextView;
        public final TextView fileSizeTextView;
        public final TextView timeTextView;

        public final MaterialDivider divider;

        public final OnItemClickListener onItemClickListener;

        /* 后续传入 */
        public int pos;
        public HistoryInfo info;

        public ViewHolder(@NonNull View itemView, @NonNull OnItemClickListener onItemClickListener) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.tv_history_device_name);
            transmissionTypeTextView = itemView.findViewById(R.id.tv_history_transimission_type);
            fileNameTextView = itemView.findViewById(R.id.tv_history_file_name);
            fileSizeTextView = itemView.findViewById(R.id.tv_history_file_size);
            timeTextView = itemView.findViewById(R.id.tv_history_time);
            divider = itemView.findViewById(R.id.divider_history);
            this.onItemClickListener = onItemClickListener;
            itemView.setOnClickListener(this);
        }


        @Override
        public void onClick(View v) {
            onItemClickListener.handle(pos, info);
        }
    }
}
