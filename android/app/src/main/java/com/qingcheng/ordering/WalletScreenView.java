package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Refresh feedback is independent of the last successfully rendered ledger. */
final class WalletScreenView {
    static final class Result {
        private final Context context;
        private final LinearLayout root;
        private final LinearLayout body;
        private final TextView status;
        private WalletView.State rendered;

        Result(Context context, Runnable refresh) {
            this(context, ignored -> refresh.run());
        }

        Result(Context context, java.util.function.Consumer<Result> refresh) {
            this.context = context;
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            status = Ui.text(context, "", 13, Ui.MUTED, Typeface.NORMAL);
            status.setSingleLine(false);
            status.setMinHeight(Ui.dp(context, 48));
            status.setGravity(android.view.Gravity.CENTER_VERTICAL);
            status.setPadding(Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8));
            PressFeedback.bind(status, MotionPolicy::systemAnimatorsEnabled);
            status.setOnClickListener(ignored -> refresh.accept(this));
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            root.addView(status);
            body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            root.addView(body);
        }

        LinearLayout root() { return root; }
        TextView status() { return status; }

        void render(WalletScreenState.Snapshot snapshot) {
            if (!java.util.Objects.equals(rendered, snapshot.data())) {
                body.removeAllViews();
                rendered = snapshot.data();
                if (rendered != null) body.addView(new WalletView(context, rendered).build());
            }
            boolean cached = snapshot.data() != null;
            status.setText(switch (snapshot.phase()) {
                case EMPTY, LOADING -> "正在加载钱包…";
                case REFRESHING -> "已显示上次数据 · 正在刷新…";
                case READY -> "已更新 · 点击刷新";
                case ERROR -> cached ? "刷新失败，保留上次数据 · 点击重试" : "钱包加载失败 · 点击重试";
            });
            boolean busy = snapshot.phase() == WalletScreenState.Phase.LOADING
                    || snapshot.phase() == WalletScreenState.Phase.REFRESHING;
            status.setEnabled(!busy);
            status.setClickable(!busy);
            status.setContentDescription(status.getText());
        }
    }

    private WalletScreenView() { }
}
