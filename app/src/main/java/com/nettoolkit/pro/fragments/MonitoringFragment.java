package com.nettoolkit.pro.fragments;

import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.nettoolkit.pro.R;
import com.nettoolkit.pro.widgets.SpeedGraphView;

import java.util.Locale;

/**
 * سرعت لحظه‌ای را از طریق دلتای بایت‌های کل دستگاه (TrafficStats) هر ۱ ثانیه اندازه می‌گیرد.
 * این یک تست سرعت فعال نیست؛ مصرف واقعی جاری دستگاه را نشان می‌دهد (دقیقاً مثل اپ‌های NetSpeed معروف).
 */
public class MonitoringFragment extends Fragment {

    private static final int INTERVAL_MS = 1000;

    private TextView tvDownloadSpeed, tvUploadSpeed, tvSessionUsage;
    private SpeedGraphView speedGraph;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long lastRxBytes = -1;
    private long lastTxBytes = -1;
    private long sessionStartRx = -1;
    private long sessionStartTx = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateSpeed();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup group, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_monitoring, group, false);
        tvDownloadSpeed = root.findViewById(R.id.tv_download_speed);
        tvUploadSpeed = root.findViewById(R.id.tv_upload_speed);
        tvSessionUsage = root.findViewById(R.id.tv_session_usage);
        speedGraph = root.findViewById(R.id.speed_graph);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTxBytes = TrafficStats.getTotalTxBytes();
        sessionStartRx = lastRxBytes;
        sessionStartTx = lastTxBytes;
        speedGraph.clear();
        handler.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void updateSpeed() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();

        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
            tvDownloadSpeed.setText("—");
            tvUploadSpeed.setText("—");
            return;
        }

        if (lastRxBytes >= 0) {
            long deltaRx = Math.max(0, rx - lastRxBytes);
            long deltaTx = Math.max(0, tx - lastTxBytes);

            float downKbps = deltaRx / 1024f / (INTERVAL_MS / 1000f);
            float upKbps = deltaTx / 1024f / (INTERVAL_MS / 1000f);

            tvDownloadSpeed.setText(formatSpeed(downKbps));
            tvUploadSpeed.setText(formatSpeed(upKbps));
            speedGraph.pushSample(downKbps, upKbps);

            long sessionRxMb = (rx - sessionStartRx) / 1024 / 1024;
            long sessionTxMb = (tx - sessionStartTx) / 1024 / 1024;
            tvSessionUsage.setText(String.format(Locale.getDefault(),
                    "%d مگابایت دانلود • %d مگابایت آپلود", sessionRxMb, sessionTxMb));
        }

        lastRxBytes = rx;
        lastTxBytes = tx;
    }

    private String formatSpeed(float kbps) {
        return String.format(Locale.getDefault(), "%.1f", kbps);
    }
}
