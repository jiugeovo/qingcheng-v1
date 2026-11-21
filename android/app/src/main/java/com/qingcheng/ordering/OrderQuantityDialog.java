package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;

final class OrderQuantityDialog {
    interface Actions {
        void submit(Result result, Draft draft);
    }

    record Draft(int quantity, String remark) {
    }

    enum State {
        EDITING, SUBMITTING, ERROR, UNCERTAIN, SUCCESS, DISMISSED
    }

    /** Owns presentation only; request ownership and authentication stay with the caller. */
    static final class Result {
        private final Activity activity;
        private final LinearLayout root;
        private final AlertDialog dialog;
        private final EditText quantity;
        private final EditText remark;
        private final LinearLayout feedback;
        private final ProgressBar progress;
        private final TextView status;
        private State state = State.EDITING;

        private Result(
                Activity activity,
                LinearLayout root,
                AlertDialog dialog,
                EditText quantity,
                EditText remark,
                LinearLayout feedback,
                ProgressBar progress,
                TextView status
        ) {
            this.activity = activity;
            this.root = root;
            this.dialog = dialog;
            this.quantity = quantity;
            this.remark = remark;
            this.feedback = feedback;
            this.progress = progress;
            this.status = status;
        }

        LinearLayout root() {
            return root;
        }

        AlertDialog dialog() {
            return dialog;
        }

        EditText quantity() {
            return quantity;
        }

        EditText remark() {
            return remark;
        }

        State state() {
            return state;
        }

        TextView status() {
            return status;
        }

        private boolean beginSubmission() {
            if (!isAvailable() || (state != State.EDITING && state != State.ERROR)) {
                return false;
            }
            state = State.SUBMITTING;
            quantity.setEnabled(false);
            remark.setEnabled(false);
            feedback.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE);
            status.setTextColor(Ui.TEXT_SECONDARY);
            status.setText("正在提交，关闭后可在订单中查看结果");
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("提交中…");
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText("关闭");
            return true;
        }

        /** Called on the UI thread after the caller has checked the request's origin. */
        boolean showError(String message) {
            if (!isAvailable() || state != State.SUBMITTING) {
                return false;
            }
            state = State.ERROR;
            quantity.setEnabled(true);
            remark.setEnabled(true);
            progress.setVisibility(View.GONE);
            status.setTextColor(Ui.DANGER);
            status.setText(message == null || message.isBlank() ? "提交失败，请重试" : message.trim());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("重试提交");
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText("取消");
            return true;
        }

        /** An unconfirmed POST must not offer another submission from the same form. */
        boolean showUncertain(String message) {
            if (!isAvailable() || state != State.SUBMITTING) {
                return false;
            }
            state = State.UNCERTAIN;
            progress.setVisibility(View.GONE);
            status.setTextColor(Ui.DANGER);
            status.setText(message == null || message.isBlank()
                    ? "提交结果尚未确认，请先查看订单，避免重复下单" : message.trim());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("结果待确认");
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText("关闭");
            return true;
        }

        /** Success is the only request outcome that closes the form automatically. */
        boolean complete() {
            if (!isAvailable() || state != State.SUBMITTING) {
                return false;
            }
            state = State.SUCCESS;
            dialog.dismiss();
            return true;
        }

        void dismiss() {
            if (state != State.SUCCESS) {
                state = State.DISMISSED;
            }
            dialog.dismiss();
        }

        private boolean isAvailable() {
            return !activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing();
        }

        private void onDismissed() {
            if (state != State.SUCCESS) {
                state = State.DISMISSED;
            }
        }
    }

    private final Activity activity;
    private final String itemName;
    private final boolean menu;
    private final Actions actions;

    OrderQuantityDialog(
            Activity activity,
            String itemName,
            boolean menu,
            Actions actions
    ) {
        this.activity = activity;
        this.itemName = itemName == null ? "" : itemName;
        this.menu = menu;
        this.actions = actions;
    }

    Result show() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, dp(12), 0);
        EditText quantity = input("数量");
        quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantity.setText("1");
        EditText remark = input("备注（可空）");
        box.addView(quantity);
        box.addView(new Space(activity), new LinearLayout.LayoutParams(1, dp(10)));
        box.addView(remark);

        LinearLayout feedback = new LinearLayout(activity);
        feedback.setGravity(Gravity.CENTER_VERTICAL);
        feedback.setPadding(0, dp(10), 0, 0);
        // Reserve one status row so the form does not jump as a request begins.
        feedback.setMinimumHeight(dp(52));
        feedback.setVisibility(View.INVISIBLE);
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        progressParams.setMarginEnd(dp(8));
        feedback.addView(progress, progressParams);
        TextView status = Ui.text(activity, "", 13, Ui.TEXT_SECONDARY, Typeface.NORMAL);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        feedback.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(feedback, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle((menu ? "点菜：" : "购买：") + itemName)
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", null)
                .create();
        dialog.show();
        Result result = new Result(activity, box, dialog, quantity, remark, feedback, progress, status);
        dialog.setOnDismissListener(ignored -> result.onDismissed());
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(clicked -> result.dismiss());
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(clicked -> {
            if (result.beginSubmission()) {
                actions.submit(result, new Draft(quantity(quantity), text(remark)));
            }
        });
        return result;
    }

    private EditText input(String hint) {
        EditText input = Ui.input(activity, hint);
        input.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(48)));
        return input;
    }

    private static int quantity(EditText input) {
        try {
            return Math.max(1, Integer.parseInt(text(input)));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static String text(EditText input) {
        return input.getText().toString().trim();
    }

    private int dp(int value) {
        return Ui.dp(activity, value);
    }
}
