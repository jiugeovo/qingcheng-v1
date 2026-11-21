package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class WalletView {
    record Transaction(
            String title,
            String detail,
            String createdAt,
            String balanceAfter,
            double amount
    ) {
    }

    record State(String balance, List<Transaction> transactions) {
        State {
            balance = balance == null ? "0" : balance;
            transactions = transactions == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(transactions));
        }

        static State from(JSONObject wallet) {
            if (wallet == null) {
                return new State("0", Collections.emptyList());
            }
            List<Transaction> transactions = new ArrayList<>();
            JSONArray values = wallet.optJSONArray("transactions");
            if (values != null) {
                for (int index = 0; index < values.length(); index++) {
                    JSONObject value = values.optJSONObject(index);
                    if (value == null) {
                        continue;
                    }
                    String typeLabel = DisplayFormatters.walletTransactionType(
                            value.optString("type"));
                    transactions.add(new Transaction(
                            value.optString("title", typeLabel),
                            value.optString("detail", typeLabel),
                            value.optString("createdAt", ""),
                            value.optString("balanceAfter", "0"),
                            value.optDouble("amount", 0)
                    ));
                }
            }
            return new State(wallet.optString("qingchengCoins", "0"), transactions);
        }
    }

    private final Context context;
    private final State state;

    WalletView(Context context, State state) {
        this.context = context;
        this.state = state == null ? new State("0", Collections.emptyList()) : state;
    }

    View build() {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(buildBalanceCard());
        page.addView(buildLedgerCard());
        return page;
    }

    private View buildBalanceCard() {
        LinearLayout card = card();
        card.setPadding(dp(22), dp(24), dp(22), dp(24));
        card.setBackground(Ui.round(RefreshUi.GREEN, dp(24)));
        card.addView(Ui.text(context, "可用青程币", 12, RefreshUi.SAGE, Typeface.NORMAL));
        addGap(card, 12);
        TextView balance = Ui.text(context, state.balance() + " 青程币",
                state.balance().length() > 10 ? 26 : 34, RefreshUi.WHITE, Typeface.BOLD);
        balance.setSingleLine(false);
        card.addView(balance);
        addGap(card, 12);
        TextView detail = Ui.text(context, "余额变化、签到奖励和订单消费都会记录在这里。",
                12, RefreshUi.SAGE, Typeface.NORMAL);
        detail.setSingleLine(false);
        card.addView(detail);
        return card;
    }

    private View buildLedgerCard() {
        LinearLayout card = card();
        card.addView(Ui.text(context, "流水明细", 18, RefreshUi.TEXT, Typeface.BOLD));
        if (state.transactions().isEmpty()) {
            addGap(card, 8);
            TextView empty = Ui.text(context,
                    "还没有流水。签到、点菜、购物和奖励都会记录在这里。",
                    14, Ui.MUTED, Typeface.NORMAL);
            empty.setSingleLine(false);
            card.addView(empty);
            return card;
        }
        for (Transaction transaction : state.transactions()) {
            if (transaction != null) {
                addTransactionRow(card, transaction);
            }
        }
        return card;
    }

    private void addTransactionRow(LinearLayout parent, Transaction transaction) {
        addGap(parent, 10);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(context, transaction.title(), 14, RefreshUi.TEXT, Typeface.BOLD);
        title.setSingleLine(false);
        texts.addView(title);
        String time = DisplayFormatters.shortTime(transaction.createdAt());
        String balance = "余额 " + transaction.balanceAfter() + " 青程币";
        String metadata = transaction.detail() + (time.isEmpty() ? "" : " · " + time)
                + " · " + balance;
        TextView detail = Ui.text(context, metadata, 11, RefreshUi.MUTED, Typeface.NORMAL);
        detail.setSingleLine(false);
        texts.addView(detail);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        double amount = transaction.amount();
        TextView amountView = Ui.text(context, DisplayFormatters.signedAmount(amount),
                18, amount >= 0 ? RefreshUi.GREEN : Ui.RED, Typeface.BOLD);
        amountView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        amountView.setSingleLine(false);
        row.addView(amountView, new LinearLayout.LayoutParams(dp(86), -2));
        parent.addView(row);
        View divider = new View(context);
        divider.setBackgroundColor(RefreshUi.LINE);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private void addGap(LinearLayout layout, int heightDp) {
        layout.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
