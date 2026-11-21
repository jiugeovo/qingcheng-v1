package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class CheckInDialogView {
    enum Mode {
        LOADING,
        READY,
        LOAD_ERROR,
        SUBMITTING,
        SUBMIT_ERROR
    }

    record WalletData(
            boolean checked,
            int streak,
            String dailyReward,
            String nextReward,
            String monthLabel,
            String dateLabel,
            JSONArray monthCalendar
    ) {
        static WalletData from(JSONObject wallet) {
            JSONObject value = wallet == null ? new JSONObject() : wallet;
            boolean checked = value.optBoolean("checkedInToday", false);
            return new WalletData(
                    checked,
                    value.optInt("currentStreak", checked ? 1 : 0),
                    DisplayFormatters.coinAmount(value.optString("dailyReward", "5.00")),
                    DisplayFormatters.coinAmount(value.optString("nextReward", "6.00")),
                    value.optString("currentMonthLabel", "本月签到"),
                    value.optString("currentDateLabel", ""),
                    value.optJSONArray("monthCalendar")
            );
        }
    }

    record State(Mode mode, WalletData wallet, String message) {
        static State from(Mode mode, JSONObject wallet, String message) {
            Mode safeMode = mode == null ? Mode.LOADING : mode;
            return switch (safeMode) {
                case LOADING -> loading();
                case READY -> ready(wallet);
                case LOAD_ERROR -> loadError(message);
                case SUBMITTING -> submitting();
                case SUBMIT_ERROR -> submitError(message);
            };
        }

        static State loading() {
            return new State(Mode.LOADING, null, "");
        }

        static State ready(JSONObject wallet) {
            return new State(Mode.READY, WalletData.from(wallet), "");
        }

        static State loadError(String message) {
            return new State(Mode.LOAD_ERROR, null,
                    fallback(message, "签到数据加载失败"));
        }

        static State submitting() {
            return new State(Mode.SUBMITTING, null, "");
        }

        static State submitError(String message) {
            return new State(Mode.SUBMIT_ERROR, null,
                    fallback(message, "签到失败"));
        }

        private static String fallback(String value, String fallback) {
            return value == null || value.isEmpty() ? fallback : value;
        }
    }

    interface Actions {
        void bindClick(View view, Runnable action);

        void submit(Result result);

        void dismiss();
    }

    static final class Result {
        private final View root;
        private final AlertDialog dialog;
        private final LinearLayout body;
        private State state;

        Result(View root, AlertDialog dialog, LinearLayout body, State state) {
            this.root = root;
            this.dialog = dialog;
            this.body = body;
            this.state = state;
        }

        View root() {
            return root;
        }

        AlertDialog dialog() {
            return dialog;
        }

        LinearLayout body() {
            return body;
        }

        State state() {
            return state;
        }
    }

    private final Activity activity;
    private final Actions actions;
    private final State initialState;

    CheckInDialogView(Activity activity, Actions actions, State initialState) {
        this.activity = activity;
        this.actions = actions;
        this.initialState = initialState;
    }

    Result build() {
        FrameLayout frame = new FrameLayout(activity);
        frame.setPadding(dp(10), dp(16), dp(10), dp(14));

        ImageView background = new ImageView(activity);
        background.setImageResource(R.drawable.checkin_garden_bg);
        background.setScaleType(ImageView.ScaleType.FIT_XY);
        background.setAdjustViewBounds(false);
        frame.addView(background, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(frame).create();
        Result result = new Result(frame, dialog, body, initialState);
        dialog.setOnDismissListener(ignored -> actions.dismiss());
        render(result, initialState);
        return result;
    }

    Result show() {
        Result result = build();
        result.dialog().show();
        Window window = result.dialog().getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return result;
    }

    void render(Result result, State state) {
        result.state = state;
        LinearLayout body = result.body();
        body.removeAllViews();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        if (state.mode() == Mode.READY) {
            renderReady(result, state.wallet());
            return;
        }
        body.setPadding(dp(8), dp(10), dp(8), dp(8));
        switch (state.mode()) {
            case LOADING -> body.addView(Ui.text(activity,
                    "签到数据加载中...", 15, Ui.MUTED, Typeface.NORMAL));
            case LOAD_ERROR -> {
                body.addView(Ui.text(activity,
                        state.message(), 15, Ui.RED, Typeface.NORMAL));
                gap(body, 12);
                TextView close = closeButton();
                actions.bindClick(close, result.dialog()::dismiss);
                body.addView(close, centered(-2, dp(Ui.MIN_TOUCH_DP)));
            }
            case SUBMITTING -> body.addView(Ui.text(activity,
                    "签到中...", 15, Ui.MUTED, Typeface.BOLD));
            case SUBMIT_ERROR -> body.addView(Ui.text(activity,
                    state.message(), 15, Ui.RED, Typeface.NORMAL));
            case READY -> throw new IllegalStateException("READY rendered separately");
        }
    }

    private void renderReady(Result result, WalletData wallet) {
        LinearLayout body = result.body();
        body.setPadding(dp(6), dp(72), dp(6), dp(8));
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(4), dp(10), dp(12));
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        if (wallet.monthCalendar() != null && wallet.monthCalendar().length() > 0) {
            CheckInCalendarView calendar = new CheckInCalendarView(activity);
            calendar.setData(wallet.monthCalendar(), wallet.monthLabel(), wallet.dateLabel());
            panel.addView(calendar, centered(dp(320), dp(422)));
        } else {
            TextView empty = Ui.text(activity,
                    "本月日历会在服务更新后显示。", 13, Ui.MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            panel.addView(empty, centered(-1, -2));
        }

        gap(panel, 10);
        LinearLayout note = new LinearLayout(activity);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        note.setBackground(Ui.stroke(
                Color.argb(218, 255, 252, 239), dp(18),
                Color.argb(170, 236, 188, 116), dp(1)));
        note.addView(Ui.text(activity,
                wallet.checked() ? "今天已经签到" : "今天还没有签到",
                16, wallet.checked() ? Ui.GREEN : Ui.TEXT, Typeface.BOLD));
        String rewardLine = wallet.checked()
                ? "连续签到 " + wallet.streak() + " 天，明天可领 "
                        + wallet.nextReward() + " 青程币"
                : "连续签到 " + wallet.streak() + " 天，今天可领 "
                        + wallet.dailyReward() + " 青程币";
        note.addView(Ui.text(activity,
                rewardLine,
                13, Ui.MUTED, Typeface.NORMAL));
        note.addView(Ui.text(activity,
                "已签到日期会显示 √，今天会用浅金色标出。",
                12, Ui.MUTED, Typeface.NORMAL));
        panel.addView(note, centered(dp(306), -2));

        gap(panel, 12);
        TextView submit = pill(
                wallet.checked() ? "今天已签到" : "签到 +" + wallet.dailyReward(),
                wallet.checked() ? Ui.MUTED : Ui.GREEN);
        submit.setTag("submit");
        submit.setEnabled(!wallet.checked());
        submit.setAlpha(wallet.checked() ? 0.72f : 1f);
        if (!wallet.checked()) {
            actions.bindClick(submit, () -> actions.submit(result));
        }
        panel.addView(submit, centered(dp(306), dp(48)));
        body.addView(panel, centered(-1, -2));
        gap(body, 8);
        TextView close = closeButton();
        actions.bindClick(close, result.dialog()::dismiss);
        body.addView(close, centered(dp(92), dp(Ui.MIN_TOUCH_DP)));
    }

    private TextView closeButton() {
        TextView view = Ui.text(activity, "关闭", 15, Ui.GREEN, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(Ui.stroke(
                Color.argb(190, 255, 255, 252), dp(12),
                Color.argb(160, 215, 228, 219), dp(1)));
        view.setTag("close");
        Ui.ensureTouchTarget(view);
        return view;
    }

    private TextView pill(String label, int color) {
        TextView view = Ui.text(activity, label, 14, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(13), dp(8), dp(13), dp(8));
        view.setBackground(Ui.block(color, dp(12), dp(1)));
        Ui.ensureTouchTarget(view);
        return view;
    }

    private LinearLayout.LayoutParams centered(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new Space(activity), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Ui.dp(activity, value);
    }
}
