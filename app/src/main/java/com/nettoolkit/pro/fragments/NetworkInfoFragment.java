package com.nettoolkit.pro.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

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

public class NetworkInfoFragment extends Fragment {

    private LinearLayout container;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup group, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_network_info, group, false);
        container = root.findViewById(R.id.net_sections_container);
        swipeRefresh = root.findViewById(R.id.swipe_refresh_net);
        swipeRefresh.setColorSchemeResources(R.color.accent_cyan, R.color.accent_violet);
        swipeRefresh.setOnRefreshListener(this::refresh);
        refresh();
        return root;
    }

    private void refresh() {
        if (getContext() == null) return;
        swipeRefresh.setRefreshing(true);
        NetworkInfoHelper.NetworkSnapshot snap = NetworkInfoHelper.buildSnapshot(getContext());
        container.removeAllViews();

        List<InfoItem> routing = new ArrayList<>();
        routing.add(new InfoItem(getString(R.string.label_gateway), snap.gateway));
        routing.add(new InfoItem(getString(R.string.label_dns), snap.dns));
        routing.add(new InfoItem(getString(R.string.label_subnet), snap.subnetMask));
        routing.add(new InfoItem(getString(R.string.label_dhcp), snap.dhcpServer));

        container.addView(UiSectionBuilder.buildSection(getContext(), container, "مسیریابی", routing));

        List<InfoItem> conn = new ArrayList<>();
        conn.add(new InfoItem(getString(R.string.label_connection_type), snap.connectionType));
        conn.add(new InfoItem(getString(R.string.label_internal_ip), snap.internalIpv4));
        conn.add(new InfoItem(getString(R.string.label_ipv6), snap.internalIpv6));

        container.addView(UiSectionBuilder.buildSection(getContext(), container, "اتصال فعلی", conn));

        swipeRefresh.setRefreshing(false);
    }
}
