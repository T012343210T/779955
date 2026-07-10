package com.nettoolkit.pro.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nettoolkit.pro.R;
import com.nettoolkit.pro.models.InfoItem;

import java.util.List;

/**
 * می‌سازد: یک عنوان بخش + یک کارت گرد حاوی چند ردیف InfoItem.
 * برای جلوگیری از شلوغی، هر فراگمنت این را صدا می‌زند و نتیجه را به کانتینر اضافه می‌کند.
 */
public class UiSectionBuilder {

    public static View buildSection(Context ctx, ViewGroup parent, String title, List<InfoItem> items) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapperParams.setMargins(0, 0, 0, dp(ctx, 16));
        wrapper.setLayoutParams(wrapperParams);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextColor(ctx.getResources().getColor(R.color.text_primary));
        titleView.setTextSize(15);
        titleView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(dp(ctx, 4), 0, dp(ctx, 4), dp(ctx, 10));
        titleView.setLayoutParams(titleParams);
        wrapper.addView(titleView);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(ctx, 16), dp(ctx, 6), dp(ctx, 16), dp(ctx, 6));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (int i = 0; i < items.size(); i++) {
            InfoItem item = items.get(i);
            View row = inflater.inflate(R.layout.item_info_row, card, false);
            TextView label = row.findViewById(R.id.tv_label);
            TextView value = row.findViewById(R.id.tv_value);
            View dot = row.findViewById(R.id.dot_status);
            label.setText(item.getLabel());
            value.setText(item.getValue());

            if (item.getStatus() != InfoItem.Status.NONE) {
                dot.setVisibility(View.VISIBLE);
                int colorRes = R.color.status_good;
                if (item.getStatus() == InfoItem.Status.WARNING) colorRes = R.color.status_warning;
                if (item.getStatus() == InfoItem.Status.BAD) colorRes = R.color.status_bad;
                dot.setBackgroundTintList(ctx.getResources().getColorStateList(colorRes));
            }

            card.addView(row);

            if (i < items.size() - 1) {
                View divider = new View(ctx);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1));
                divider.setLayoutParams(dividerParams);
                divider.setBackgroundColor(ctx.getResources().getColor(R.color.divider));
                card.addView(divider);
            }
        }

        wrapper.addView(card);
        return wrapper;
    }

    private static int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }
}
