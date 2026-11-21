package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ActivityContentView {
    interface ClickBinder {
        void bind(View view, Runnable action);
    }

    private ActivityContentView() {
    }

    static void addTopAction(
            Context context,
            LinearLayout content,
            String label,
            int color,
            ClickBinder binder,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.RIGHT);
        row.setPadding(0, 0, 0, Ui.dp(context, 10));
        TextView button = Ui.text(context, label, 14, Color.WHITE, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(Ui.dp(context, 82));
        button.setPadding(
                Ui.dp(context, 13), Ui.dp(context, 8),
                Ui.dp(context, 13), Ui.dp(context, 8));
        button.setBackground(Ui.block(color, Ui.dp(context, 12), Ui.dp(context, 1)));
        row.addView(button);
        content.addView(row);
        binder.bind(button, action);
    }

    static void addEmpty(Context context, LinearLayout content, String value) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(
                Ui.dp(context, 14), Ui.dp(context, 14),
                Ui.dp(context, 14), Ui.dp(context, 14));
        card.setBackground(Ui.block(Ui.CARD, Ui.dp(context, 14), Ui.dp(context, 1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, Ui.dp(context, 12));
        card.setLayoutParams(params);
        card.addView(Ui.text(context, value == null ? "暂无内容" : value,
                15, Ui.MUTED, Typeface.NORMAL));
        content.addView(card);
    }
}
