package com.nettoolkit.pro.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.nettoolkit.pro.R;
import com.nettoolkit.pro.utils.NetworkToolsHelper;

public class ToolsFragment extends Fragment {

    private LinearLayout container;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup group, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_tools, group, false);
        container = root.findViewById(R.id.tools_container);

        buildPingCard(inflater);
        buildDnsCard(inflater);
        buildReverseDnsCard(inflater);
        buildPortScanCard(inflater);
        buildWhoisCard(inflater);
        buildWolCard(inflater);

        return root;
    }

    // ---------- ابزار ساز مشترک ----------

    private static class ToolViews {
        View root;
        LinearLayout inputsContainer;
        MaterialButton button;
        TextView console;
        ProgressBar progress;
    }

    private ToolViews inflateToolCard(LayoutInflater inflater, String title) {
        View card = inflater.inflate(R.layout.item_tool_card, container, false);
        ToolViews tv = new ToolViews();
        tv.root = card;
        tv.inputsContainer = card.findViewById(R.id.inputs_container);
        tv.button = card.findViewById(R.id.btn_run);
        tv.console = card.findViewById(R.id.tv_console);
        tv.progress = card.findViewById(R.id.progress);
        tv.console.setMovementMethod(new ScrollingMovementMethod());
        tv.console.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });
        ((TextView) card.findViewById(R.id.tv_tool_title)).setText(title);
        container.addView(card);
        return tv;
    }

    private EditText makeInput(String hint, boolean flex) {
        Context ctx = requireContext();
        EditText et = new EditText(ctx);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                flex ? 0 : LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (flex) params.weight = 1;
        params.setMarginEnd(dp(8));
        et.setLayoutParams(params);
        et.setHint(hint);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setTextColor(ctx.getResources().getColor(R.color.text_primary));
        et.setHintTextColor(ctx.getResources().getColor(R.color.text_disabled));
        et.setTextSize(13);
        et.setSingleLine(true);
        return et;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void showConsole(TextView console, String text, boolean success) {
        console.setVisibility(View.VISIBLE);
        console.setText(text.isEmpty() ? "خروجی خالی بود" : text);
        console.setTextColor(getResources().getColor(success ? R.color.status_good : R.color.status_bad));
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- Ping ----------
    private void buildPingCard(LayoutInflater inflater) {
        ToolViews tv = inflateToolCard(inflater, "Ping");
        EditText host = makeInput("آدرس یا IP (مثلاً 8.8.8.8)", true);
        tv.inputsContainer.addView(host);

        tv.button.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            if (h.isEmpty()) { toast("آدرس رو وارد کن"); return; }
            tv.progress.setVisibility(View.VISIBLE);
            tv.console.setVisibility(View.GONE);
            NetworkToolsHelper.ping(h, 4, (output, success) -> {
                tv.progress.setVisibility(View.GONE);
                showConsole(tv.console, output, success);
            });
        });
    }

    // ---------- DNS Lookup ----------
    private void buildDnsCard(LayoutInflater inflater) {
        ToolViews tv = inflateToolCard(inflater, "DNS Lookup");
        EditText host = makeInput("دامنه (مثلاً google.com)", true);
        tv.inputsContainer.addView(host);

        tv.button.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            if (h.isEmpty()) { toast("دامنه رو وارد کن"); return; }
            tv.progress.setVisibility(View.VISIBLE);
            tv.console.setVisibility(View.GONE);
            NetworkToolsHelper.dnsLookup(h, (output, success) -> {
                tv.progress.setVisibility(View.GONE);
                showConsole(tv.console, output, success);
            });
        });
    }

    // ---------- Reverse DNS ----------
    private void buildReverseDnsCard(LayoutInflater inflater) {
        ToolViews tv = inflateToolCard(inflater, "Reverse DNS");
        EditText ip = makeInput("IP (مثلاً 1.1.1.1)", true);
        tv.inputsContainer.addView(ip);

        tv.button.setOnClickListener(v -> {
            String i = ip.getText().toString().trim();
            if (i.isEmpty()) { toast("IP رو وارد کن"); return; }
            tv.progress.setVisibility(View.VISIBLE);
            tv.console.setVisibility(View.GONE);
            NetworkToolsHelper.reverseDns(i, (output, success) -> {
                tv.progress.setVisibility(View.GONE);
                showConsole(tv.console, output, success);
            });
        });
    }

    // ---------- Port Scanner ----------
    private void buildPortScanCard(LayoutInflater inflater) {
        ToolViews tv = inflateToolCard(inflater, "بررسی باز بودن پورت");
        EditText host = makeInput("IP یا آدرس", true);
        EditText startPort = makeInput("از پورت", false);
        EditText endPort = makeInput("تا پورت", false);
        startPort.setEms(4);
        endPort.setEms(4);
        tv.inputsContainer.addView(host);
        tv.inputsContainer.addView(startPort);
        tv.inputsContainer.addView(endPort);

        tv.button.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            String startStr = startPort.getText().toString().trim();
            String endStr = endPort.getText().toString().trim();
            if (h.isEmpty() || startStr.isEmpty() || endStr.isEmpty()) { toast("همه فیلدها رو پر کن"); return; }

            int start, end;
            try {
                start = Integer.parseInt(startStr);
                end = Integer.parseInt(endStr);
            } catch (NumberFormatException e) { toast("شماره پورت نامعتبره"); return; }

            if (end < start || end - start > 500) { toast("بازه رو تا حداکثر ۵۰۰ پورت نگه دار"); return; }

            StringBuilder result = new StringBuilder();
            tv.progress.setVisibility(View.VISIBLE);
            tv.console.setVisibility(View.VISIBLE);
            tv.console.setTextColor(getResources().getColor(R.color.status_good));
            tv.console.setText("در حال اسکن...");

            NetworkToolsHelper.scanPorts(h, start, end, 400, new NetworkToolsHelper.PortScanCallback() {
                @Override
                public void onPortResult(int port, boolean open) {
                    result.append("پورت ").append(port).append(" باز است ✅\n");
                    tv.console.setText(result.toString());
                }

                @Override
                public void onProgress(int scanned, int total) {
                    // پیشرفت در پس‌زمینه؛ کنسول فقط پورت‌های باز رو نشون می‌ده
                }

                @Override
                public void onDone() {
                    tv.progress.setVisibility(View.GONE);
                    if (result.length() == 0) {
                        result.append("هیچ پورت بازی در این بازه پیدا نشد");
                    }
                    tv.console.setText(result.toString());
                }
            });
        });
    }

    // ---------- Whois ----------
    private void buildWhoisCard(LayoutInflater inflater) {
        ToolViews tv = inflateToolCard(inflater, "Whois");
        EditText domain = makeInput("دامنه (مثلاً example.com)", true);
        tv.inputsContainer.addView(domain);

        tv.button.setOnClickListener(v -> {
            String d = domain.getText().toString().trim();
            if (d.isEmpty()) { toast("دامنه رو وارد کن"); return; }
            tv.progress.setVisibility(View.VISIBLE);
            tv.console.setVisibility(View.GONE);
            NetworkToolsHelper.whois(d, (output, success) -> {
                tv.progress.setVisibility(View.GONE);
                showConsole(tv.console, output, success);
            });
        });
    }

    // ---------- Wake-on-LAN ----------
    private void buildWolCard(LayoutInflater inflater) {
        ToolViews tv = inflateToolCard(inflater, "Wake-on-LAN");
        EditText mac = makeInput("MAC (مثلاً AA:BB:CC:DD:EE:FF)", true);
        EditText broadcast = makeInput("Broadcast IP (مثلاً 192.168.1.255)", true);
        tv.inputsContainer.addView(mac);
        tv.inputsContainer.addView(broadcast);

        tv.button.setOnClickListener(v -> {
            String m = mac.getText().toString().trim();
            String b = broadcast.getText().toString().trim();
            if (m.isEmpty() || b.isEmpty()) { toast("MAC و Broadcast IP رو وارد کن"); return; }
            tv.progress.setVisibility(View.VISIBLE);
            tv.console.setVisibility(View.GONE);
            NetworkToolsHelper.sendWakeOnLan(m, b, (output, success) -> {
                tv.progress.setVisibility(View.GONE);
                showConsole(tv.console, output, success);
            });
        });
    }
}
