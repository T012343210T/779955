package com.nettoolkit.pro.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nettoolkit.pro.R;
import com.nettoolkit.pro.models.InfoItem;
import com.nettoolkit.pro.utils.NetworkInfoHelper;
import com.nettoolkit.pro.utils.UiSectionBuilder;

import java.util.ArrayList;
import java.util.List;

public class DashboardFragment extends Fragment {

    private LinearLayout sectionsContainer;
    private TextView tvScore, tvScoreReason, tvConnectionPill;
    private SwipeRefreshLayout swipeRefresh;
    private String publicIp = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        sectionsContainer = root.findViewById(R.id.sections_container);
        tvScore = root.findViewById(R.id.tv_score);
        tvScoreReason = root.findViewById(R.id.tv_score_reason);
        tvConnectionPill = root.findViewById(R.id.tv_connection_pill);
        swipeRefresh = root.findViewById(R.id.swipe_refresh);

        swipeRefresh.setColorSchemeResources(R.color.accent_cyan, R.color.accent_violet);
        swipeRefresh.setOnRefreshListener(this::refreshAll);

        refreshAll();
        return root;
    }

    private void refreshAll() {
        if (getContext() == null) return;
        swipeRefresh.setRefreshing(true);

        NetworkInfoHelper.NetworkSnapshot snap = NetworkInfoHelper.buildSnapshot(getContext());
        tvConnectionPill.setText(snap.connectionType.equals("—") ? "بدون اتصال" : snap.connectionType);

        NetworkInfoHelper.fetchPublicIp((ip, ok) -> {
            publicIp = ok ? ip : getString(R.string.not_available);
            renderSections(snap);
            updateScore(snap);
            swipeRefresh.setRefreshing(false);
        });
    }

    private void renderSections(NetworkInfoHelper.NetworkSnapshot snap) {
        sectionsContainer.removeAllViews();

        List<InfoItem> basic = new ArrayList<>();
        basic.add(new InfoItem(getString(R.string.label_internal_ip), snap.internalIpv4));
        basic.add(new InfoItem(getString(R.string.label_public_ip), publicIp != null ? publicIp : "…"));
        basic.add(new InfoItem(getString(R.string.label_ipv6), snap.internalIpv6));
        basic.add(new InfoItem(getString(R.string.label_connection_type), snap.connectionType));
        basic.add(new InfoItem(getString(R.string.label_internet_status),
                snap.internetConnected ? getString(R.string.status_connected) : getString(R.string.status_disconnected),
                snap.internetConnected ? InfoItem.Status.GOOD : InfoItem.Status.BAD));

        sectionsContainer.addView(UiSectionBuilder.buildSection(getContext(), sectionsContainer, getString(R.string.section_basic), basic));

        if ("Wi-Fi".equals(snap.connectionType)) {
            List<InfoItem> wifi = new ArrayList<>();

            boolean locationOff = getContext() != null && !NetworkInfoHelper.isLocationEnabled(getContext());
            boolean nameHidden = snap.wifiName == null || snap.wifiName.isEmpty()
                    || snap.wifiName.contains("unknown ssid") || snap.wifiName.equals("<unknown ssid>");

            if (nameHidden && locationOff) {
                wifi.add(new InfoItem(getString(R.string.label_wifi_name), "غیرقابل نمایش (GPS خاموشه)", InfoItem.Status.WARNING));
            } else {
                wifi.add(new InfoItem(getString(R.string.label_wifi_name), nameHidden ? getString(R.string.not_available) : snap.wifiName));
            }
            wifi.add(new InfoItem(getString(R.string.label_bssid), snap.bssid));

            InfoItem.Status signalStatus = InfoItem.Status.GOOD;
            String signalText = getString(R.string.not_available);
            if (snap.signalDbm != Integer.MIN_VALUE) {
                signalText = snap.signalDbm + " dBm";
                if (snap.signalDbm < -75) signalStatus = InfoItem.Status.BAD;
                else if (snap.signalDbm < -60) signalStatus = InfoItem.Status.WARNING;
            }
            wifi.add(new InfoItem(getString(R.string.label_signal), signalText, signalStatus));
            wifi.add(new InfoItem(getString(R.string.label_frequency), snap.frequencyLabel));
            wifi.add(new InfoItem(getString(R.string.label_channel), snap.channel > 0 ? String.valueOf(snap.channel) : getString(R.string.not_available)));
            wifi.add(new InfoItem(getString(R.string.label_link_speed), snap.linkSpeedMbps > 0 ? snap.linkSpeedMbps + " Mbps" : getString(R.string.not_available)));

            sectionsContainer.addView(UiSectionBuilder.buildSection(getContext(), sectionsContainer, getString(R.string.section_wifi), wifi));
        }
    }

    private void updateScore(NetworkInfoHelper.NetworkSnapshot snap) {
        int score = 100;
        List<String> reasons = new ArrayList<>();

        if (!snap.internetConnected) {
            score -= 50;
            reasons.add("اینترنت متصل نیست");
        }
        if ("Wi-Fi".equals(snap.connectionType) && snap.signalDbm != Integer.MIN_VALUE) {
            if (snap.signalDbm < -75) {
                score -= 25;
                reasons.add("قدرت سیگنال ضعیف است");
            } else if (snap.signalDbm < -60) {
                score -= 10;
                reasons.add("قدرت سیگنال متوسط است");
            }
        }
        if (publicIp == null || publicIp.equals(getString(R.string.not_available))) {
            score -= 15;
            reasons.add("دریافت IP عمومی ناموفق بود");
        }

        score = Math.max(0, Math.min(100, score));
        tvScore.setText(String.valueOf(score));
        tvScoreReason.setText(reasons.isEmpty() ? "همه‌چیز عالیه ✨" : String.join(" • ", reasons));
    }
}
