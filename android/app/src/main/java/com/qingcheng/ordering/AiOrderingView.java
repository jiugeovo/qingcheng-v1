package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Business cards only. Authenticated requests and lifecycle checks stay with the coordinator. */
final class AiOrderingView {
    interface Actions {
        void bindClick(View view, Runnable action);
        void confirm(JSONObject draft, Card card);
        void orders();
    }

    static final class Card {
        final LinearLayout root;
        final TextView status;
        final TextView confirm;
        final TextView cancel;
        private boolean sending;
        private boolean done;
        Card(LinearLayout root, TextView status, TextView confirm, TextView cancel) {
            this.root = root; this.status = status; this.confirm = confirm; this.cancel = cancel;
        }
        boolean begin() {
            if (sending || done || !confirm.isEnabled()) return false;
            sending = true;
            confirm.setEnabled(false); cancel.setEnabled(false);
            status.setText("正在提交，请稍候…");
            return true;
        }
        void confirmed(JSONObject order) {
            sending = false; done = true;
            confirm.setText("已提交"); confirm.setEnabled(false); cancel.setEnabled(false);
            String label = DisplayFormatters.storeOrderLabel(order, true);
            if (label.isEmpty()) label = "订单 #" + order.optLong("id");
            status.setText(label + " 已提交 · " + order.optString("totalAmount") + " 青程币");
        }
        void failed(String message, boolean retry) {
            sending = false;
            status.setText(message);
            confirm.setText("重试确认");
            confirm.setEnabled(retry && !done);
            cancel.setEnabled(!done);
        }
        void cancel() {
            if (sending || done) return;
            done = true;
            status.setText("已取消此草稿，未下单");
            confirm.setEnabled(false); cancel.setEnabled(false);
        }
    }

    private final Context context;
    private final Actions actions;
    AiOrderingView(Context context, Actions actions) { this.context = context; this.actions = actions; }

    Card add(LinearLayout messages, JSONObject data) {
        if (data == null) return null;
        String kind = data.optString("kind");
        if (!"draft".equals(kind) && !"menu".equals(kind) && !"orders".equals(kind)) return null;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackground(Ui.stroke(Color.WHITE, dp(14), Ui.LINE, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, dp(16), dp(12));
        messages.addView(root, params);
        TextView title = text("draft".equals(kind) ? "核对点菜草稿" : "menu".equals(kind) ? "上架菜品" : "我的点菜订单", true);
        root.addView(title);
        JSONArray items = data.optJSONArray("items");
        if (items != null && !"orders".equals(kind)) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String line = item.optString("name") + ("draft".equals(kind) ? " × " + item.optInt("quantity") : "");
                root.addView(text(line + "   " + item.optString("price") + " 青程币/份", false));
            }
        }
        if ("draft".equals(kind)) {
            if (!data.optString("remark").isEmpty()) root.addView(text("备注：" + data.optString("remark"), false));
            root.addView(text("合计 " + data.optString("totalAmount") + " 青程币", true));
            root.addView(text("当前余额 " + data.optString("balance") + " 青程币 · 草稿 15 分钟内有效", false));
            TextView status = text("尚未下单，确认后才扣除青程币", false);
            status.setTag("ai-order-status");
            root.addView(status);
            TextView confirm = button("确认下单", "ai-order-confirm", true);
            TextView cancel = button("取消草稿", "ai-order-cancel", false);
            Card card = new Card(root, status, confirm, cancel);
            LinearLayout buttons = new LinearLayout(context);
            buttons.addView(confirm, new LinearLayout.LayoutParams(0, dp(48), 1));
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(48), 1);
            cancelParams.setMargins(dp(8), 0, 0, 0);
            buttons.addView(cancel, cancelParams);
            root.addView(buttons);
            actions.bindClick(confirm, () -> { if (card.begin()) actions.confirm(data, card); });
            actions.bindClick(cancel, card::cancel);
            if (data.optDouble("balance") < data.optDouble("totalAmount")) {
                confirm.setEnabled(false);
                status.setText("余额不足，请补足余额后重新生成草稿");
            }
            addOrders(root);
            return card;
        }
        if ("orders".equals(kind)) addOrders(root);
        return null;
    }

    private void addOrders(LinearLayout root) {
        TextView orders = button("查看我的订单", "ai-order-open", false);
        root.addView(orders, new LinearLayout.LayoutParams(-1, dp(48)));
        actions.bindClick(orders, actions::orders);
    }
    private TextView text(String value, boolean bold) {
        TextView result = Ui.text(context, value, bold ? 15 : 13, bold ? Ui.TEXT : Ui.MUTED,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        result.setPadding(0, dp(4), 0, dp(5));
        return result;
    }
    private TextView button(String label, String tag, boolean primary) {
        TextView result = Ui.text(context, label, 14, primary ? Color.WHITE : Ui.GREEN, Typeface.BOLD);
        result.setGravity(Gravity.CENTER); result.setTag(tag); result.setContentDescription(label);
        result.setBackground(primary ? Ui.block(Ui.GREEN, dp(12), dp(1))
                : Ui.stroke(Color.WHITE, dp(12), Ui.LINE, dp(1)));
        return result;
    }
    private int dp(int value) { return Ui.dp(context, value); }
}
