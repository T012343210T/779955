package com.nettoolkit.pro.fragments;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.nettoolkit.pro.R;
import com.nettoolkit.pro.utils.NetworkInfoHelper;
import com.nettoolkit.pro.utils.NetworkToolsHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScanFragment extends Fragment {

    private LinearLayout wifiContainer, btContainer, lanContainer;
    private ProgressBar progressWifi, progressBt, progressLan;
    private TextView tvLanProgress;

    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleScanCallback;

    private BroadcastReceiver wifiReceiver;
    private BroadcastReceiver btReceiver;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable wifiTimeoutRunnable = this::onWifiScanTimeout;
    private final Runnable bleStopRunnable = this::stopBleScan;

    private final List<BluetoothDevice> btFound = new ArrayList<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                boolean allGranted = true;
                for (Boolean granted : results.values()) allGranted &= Boolean.TRUE.equals(granted);
                if (allGranted) {
                    startBluetoothScan();
                } else {
                    toast("بدون مجوز بلوتوث، اسکن ممکن نیست");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup group, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_scan, group, false);

        wifiContainer = root.findViewById(R.id.wifi_results_container);
        btContainer = root.findViewById(R.id.bt_results_container);
        lanContainer = root.findViewById(R.id.lan_results_container);
        progressWifi = root.findViewById(R.id.progress_wifi);
        progressBt = root.findViewById(R.id.progress_bt);
        progressLan = root.findViewById(R.id.progress_lan);
        tvLanProgress = root.findViewById(R.id.tv_lan_progress);

        wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        BluetoothManager bm = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm != null ? bm.getAdapter() : null;

        root.findViewById(R.id.btn_scan_wifi).setOnClickListener(v -> startWifiScan());
        root.findViewById(R.id.btn_scan_bt).setOnClickListener(v -> requestBtPermissionsAndScan());
        root.findViewById(R.id.btn_scan_lan).setOnClickListener(v -> startLanScan());

        return root;
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ==================== LAN Devices ====================

    private void startLanScan() {
        if (getContext() == null) return;

        NetworkInfoHelper.NetworkSnapshot snap = NetworkInfoHelper.buildSnapshot(getContext());
        if (snap.internalIpv4 == null || snap.internalIpv4.equals("—") || snap.subnetMask.equals("—")) {
            toast("اول باید به یه شبکه Wi‑Fi متصل باشی");
            return;
        }

        lanContainer.removeAllViews();
        progressLan.setVisibility(View.VISIBLE);
        tvLanProgress.setText("در حال اسکن...");
        tvLanProgress.setTextColor(getResources().getColor(R.color.text_secondary));

        String gateway = snap.gateway;

        NetworkToolsHelper.scanLan(snap.internalIpv4, snap.subnetMask, new NetworkToolsHelper.LanScanCallback() {
            @Override
            public void onDeviceFound(String ip, long responseTimeMs) {
                if (getContext() == null || lanContainer == null) return;
                String badge = ip.equals(snap.internalIpv4) ? "این دستگاه" : (ip.equals(gateway) ? "روتر" : "IP فعال");
                String value = ip.equals(snap.internalIpv4) ? "—" : responseTimeMs + " ms";
                View row = addRow(lanContainer, ip, badge, value, R.color.status_good);

                if (!ip.equals(snap.internalIpv4)) {
                    TextView subtitleView = row.findViewById(R.id.tv_row_subtitle);
                    NetworkToolsHelper.resolveHostname(ip, (hostname, resolved) -> {
                        if (resolved && subtitleView != null) {
                            subtitleView.setText(badge + " • " + hostname);
                        }
                    });
                }
            }

            @Override
            public void onProgress(int scanned, int total) {
                if (getContext() != null) {
                    tvLanProgress.setText("بررسی شد: " + scanned + " از " + total);
                }
            }

            @Override
            public void onDone(boolean truncated) {
                progressLan.setVisibility(View.GONE);
                if (getContext() == null || lanContainer == null) return;
                if (lanContainer.getChildCount() == 0) {
                    addEmptyRow(lanContainer, "دستگاهی پیدا نشد");
                }
                tvLanProgress.setText(truncated
                        ? "اسکن کامل شد (بازه بزرگ بود، فقط بخشی بررسی شد)"
                        : "اسکن کامل شد • " + lanContainer.getChildCount() + " دستگاه پیدا شد");
                tvLanProgress.setTextColor(getResources().getColor(R.color.status_good));
            }
        });
    }

    // ==================== Wi-Fi ====================

    private void startWifiScan() {
        if (wifiManager == null) { toast("Wi‑Fi در دسترس نیست"); return; }
        if (!wifiManager.isWifiEnabled()) { toast("اول Wi‑Fi گوشی رو روشن کن"); return; }

        if (getContext() != null && !NetworkInfoHelper.isLocationEnabled(requireContext())) {
            wifiContainer.removeAllViews();
            addEmptyRow(wifiContainer, "📍 GPS/موقعیت مکانی گوشی خاموشه؛ بدون اون اندروید هیچ نتیجه‌ای نمی‌ده");
            toast("اول GPS رو از نوار بالای گوشی روشن کن");
            return;
        }

        progressWifi.setVisibility(View.VISIBLE);
        wifiContainer.removeAllViews();

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handler.removeCallbacks(wifiTimeoutRunnable);
                renderWifiResults();
                progressWifi.setVisibility(View.GONE);
                if (getContext() != null) {
                    try { getContext().unregisterReceiver(this); } catch (Exception ignored) { }
                }
                wifiReceiver = null;
            }
        };
        requireContext().registerReceiver(wifiReceiver, filter);

        boolean started = wifiManager.startScan();

        // فارغ از started، اگر تا ۶ ثانیه broadcast نیومد (خیلی از گوشی‌ها throttle می‌کنن)
        // آخرین نتایج کش‌شده رو نشون بده تا کاربر معطل یه اسپینر بی‌پایان نمونه
        handler.postDelayed(wifiTimeoutRunnable, 6000);

        if (!started) {
            toast("اسکن جدید محدود شد؛ آخرین نتایج موجود نمایش داده می‌شه");
        }
    }

    private void onWifiScanTimeout() {
        if (getContext() == null) return;
        renderWifiResults();
        progressWifi.setVisibility(View.GONE);
        if (wifiReceiver != null) {
            try { requireContext().unregisterReceiver(wifiReceiver); } catch (Exception ignored) { }
            wifiReceiver = null;
        }
    }

    private void renderWifiResults() {
        if (getContext() == null || wifiManager == null) return;
        wifiContainer.removeAllViews();

        List<android.net.wifi.ScanResult> results;
        try {
            results = wifiManager.getScanResults();
        } catch (SecurityException e) {
            toast("مجوز موقعیت مکانی برای دیدن جزئیات شبکه لازم است");
            return;
        }

        if (results == null || results.isEmpty()) {
            addEmptyRow(wifiContainer, "شبکه‌ای پیدا نشد (اگه تازه GPS رو روشن کردی، یه بار دیگه دکمه رو بزن)");
            return;
        }

        results.sort(Comparator.comparingInt((android.net.wifi.ScanResult r) -> r.level).reversed());

        for (android.net.wifi.ScanResult r : results) {
            String ssid = (r.SSID == null || r.SSID.isEmpty()) ? "(بدون نام)" : r.SSID;
            int channel = NetworkInfoHelper.frequencyToChannel(r.frequency);
            String band = r.frequency < 2500 ? "۲٫۴GHz" : (r.frequency < 5900 ? "۵GHz" : "۶GHz");
            String security = parseSecurity(r.capabilities);
            String subtitle = "کانال " + (channel > 0 ? channel : "؟") + " • " + band + " • " + security;

            int statusColor = R.color.status_good;
            if (r.level < -75) statusColor = R.color.status_bad;
            else if (r.level < -60) statusColor = R.color.status_warning;

            addRow(wifiContainer, ssid, subtitle, r.level + " dBm", statusColor);
        }
    }

    private String parseSecurity(String capabilities) {
        if (capabilities == null) return "نامشخص";
        if (capabilities.contains("WPA3")) return "WPA3";
        if (capabilities.contains("WPA2")) return "WPA2";
        if (capabilities.contains("WPA")) return "WPA";
        if (capabilities.contains("WEP")) return "WEP";
        return "باز (بدون رمز)";
    }

    // ==================== Bluetooth ====================

    private void requestBtPermissionsAndScan() {
        if (bluetoothAdapter == null) { toast("بلوتوث در این دستگاه پشتیبانی نمی‌شود"); return; }
        if (!bluetoothAdapter.isEnabled()) { toast("بلوتوث رو روشن کن"); return; }

        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (needed.isEmpty()) {
            startBluetoothScan();
        } else {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startBluetoothScan() {
        if (bluetoothAdapter == null || getContext() == null) return;

        btFound.clear();
        btContainer.removeAllViews();
        progressBt.setVisibility(View.VISIBLE);

        if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();

        btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    if (device != null && !containsDevice(device)) {
                        btFound.add(device);
                        addBtRow(device, rssi);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    // اسکن کلاسیک تموم شد؛ اسکن BLE رو هم شروع کن تا دستگاه‌های کم‌مصرف (ساعت، هندزفری و..) هم دیده بشن
                    startBleScan();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        requireContext().registerReceiver(btReceiver, filter);

        bluetoothAdapter.startDiscovery();
    }

    @SuppressWarnings("MissingPermission")
    private void startBleScan() {
        if (bluetoothAdapter == null || getContext() == null) {
            finishBtScan();
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            finishBtScan();
            return;
        }

        bleScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device != null && !containsDevice(device)) {
                    btFound.add(device);
                    addBtRow(device, (short) result.getRssi());
                }
            }
        };

        try {
            bleScanner.startScan(bleScanCallback);
        } catch (Exception e) {
            finishBtScan();
            return;
        }
        handler.postDelayed(bleStopRunnable, 6000);
    }

    @SuppressWarnings("MissingPermission")
    private void stopBleScan() {
        if (bluetoothAdapter != null && bleScanner != null && bleScanCallback != null) {
            try { bleScanner.stopScan(bleScanCallback); } catch (Exception ignored) { }
        }
        finishBtScan();
    }

    private void finishBtScan() {
        progressBt.setVisibility(View.GONE);
        if (getContext() == null) return;
        if (btFound.isEmpty()) addEmptyRow(btContainer, "دستگاهی پیدا نشد");
        if (btReceiver != null) {
            try { requireContext().unregisterReceiver(btReceiver); } catch (Exception ignored) { }
            btReceiver = null;
        }
    }

    private boolean containsDevice(BluetoothDevice device) {
        for (BluetoothDevice d : btFound) {
            if (d.getAddress().equals(device.getAddress())) return true;
        }
        return false;
    }

    @SuppressWarnings("MissingPermission")
    private void addBtRow(BluetoothDevice device, short rssi) {
        String name;
        try {
            name = device.getName();
        } catch (SecurityException e) {
            name = null;
        }
        if (name == null || name.isEmpty()) name = "دستگاه ناشناس";

        String subtitle = device.getAddress();
        String value = rssi != Short.MIN_VALUE ? rssi + " dBm" : "—";

        int statusColor = R.color.status_good;
        if (rssi != Short.MIN_VALUE) {
            if (rssi < -75) statusColor = R.color.status_bad;
            else if (rssi < -60) statusColor = R.color.status_warning;
        } else {
            statusColor = R.color.text_disabled;
        }

        addRow(btContainer, name, subtitle, value, statusColor);
    }

    // ==================== ابزار مشترک نمایش ردیف ====================

    private View addRow(LinearLayout container, String title, String subtitle, String value, int statusColorRes) {
        if (getContext() == null) return null;
        View row = LayoutInflater.from(getContext()).inflate(R.layout.item_scan_row, container, false);
        ((TextView) row.findViewById(R.id.tv_row_title)).setText(title);
        ((TextView) row.findViewById(R.id.tv_row_subtitle)).setText(subtitle);
        ((TextView) row.findViewById(R.id.tv_row_value)).setText(value);
        row.findViewById(R.id.dot_status).setBackgroundTintList(getResources().getColorStateList(statusColorRes));
        container.addView(row);
        return row;
    }

    private void addEmptyRow(LinearLayout container, String message) {
        if (getContext() == null) return;
        TextView tv = new TextView(getContext());
        tv.setText(message);
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        tv.setTextSize(12);
        tv.setPadding(0, dp(8), 0, dp(8));
        container.addView(tv);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (getContext() != null) {
            if (wifiReceiver != null) {
                try { getContext().unregisterReceiver(wifiReceiver); } catch (Exception ignored) { }
                wifiReceiver = null;
            }
            if (btReceiver != null) {
                try { getContext().unregisterReceiver(btReceiver); } catch (Exception ignored) { }
                btReceiver = null;
            }
        }
        if (bluetoothAdapter != null) {
            try {
                if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
                if (bleScanner != null && bleScanCallback != null) bleScanner.stopScan(bleScanCallback);
            } catch (SecurityException ignored) { }
        }
    }
}
