package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import org.json.JSONObject;

final class OrderSuccessDialog {
    record State(long orderId, String total, String typeLabel, String storeLabel) {
        State {
            total = total == null ? "0" : total;
        }

        static State from(JSONObject order, boolean menu) {
            return new State(
                    order == null ? 0L : order.optLong("id"),
                    order == null ? "0" : order.optString("totalAmount", "0"),
                    DisplayFormatters.orderType(order == null ? new JSONObject() : order, menu),
                    order == null ? "" : DisplayFormatters.storeOrderLabel(order, menu)
            );
        }
    }

    private final Activity activity;
    private final State state;
    private final Runnable viewOrders;

    OrderSuccessDialog(Activity activity, State state, Runnable viewOrders) {
        this.activity = activity;
        this.state = state;
        this.viewOrders = viewOrders;
    }

    AlertDialog show() {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("提交成功")
                .setMessage(message())
                .setNegativeButton("继续逛", null)
                .setPositiveButton("查看订单", null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(clicked -> {
            viewOrders.run();
            dialog.dismiss();
        });
        return dialog;
    }

    String message() {
        boolean storeOrder = !state.storeLabel().isEmpty();
        String first = storeOrder ? state.storeLabel() + " 已提交" : state.orderId() > 0
                ? "订单 #" + state.orderId() + " 已提交" : "订单已提交";
        return first
                + (storeOrder ? "" : "\n类型：" + state.typeLabel())
                + "\n合计：" + state.total() + " 青程币"
                + "\n可在我的订单里查看进度";
    }
}
