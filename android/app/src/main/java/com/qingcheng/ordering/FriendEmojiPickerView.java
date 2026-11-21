package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.Space;
import android.widget.TextView;

final class FriendEmojiPickerView {
    private static final String[] EMOJIS = {
            "😀", "😄", "😊", "🥰", "😍", "😘",
            "😆", "😂", "🥹", "😎", "🤔", "😴",
            "😭", "😤", "👍", "👏", "🙏", "💪",
            "🎉", "❤️", "🌸", "🍀", "🍚", "🐶",
            "😋", "🤗", "😌", "😇", "🙈", "😳",
            "😢", "😡", "🤧", "🤝", "👌", "✌️",
            "💖", "💕", "✨", "⭐", "🌙", "☀️",
            "🍜", "🍰", "🍓", "🧋", "🎁", "🔥"
    };
    private static final int COLUMNS = 5;
    private static final int ROWS = 4;
    private static final int EMOJIS_PER_PAGE = COLUMNS * ROWS;

    interface Actions {
        void bindClick(View view, Runnable action);

        void select(String emoji);

        void addCustom();

        void selectCustom(long stickerId);

        void loadImage(String url, ImageView target);

        void showPage(int page);

        void shown();
    }

    record State(int requestedPage) {
    }

    record Result(
            AlertDialog dialog,
            GridLayout grid,
            TextView previous,
            TextView indicator,
            TextView next,
            int page,
            int pageCount
    ) {
    }

    private final Activity activity;
    private final Actions actions;
    private final State state;
    private final org.json.JSONArray customStickers;

    FriendEmojiPickerView(Activity activity, Actions actions, State state) {
        this(activity, actions, state, new org.json.JSONArray());
    }

    FriendEmojiPickerView(
            Activity activity,
            Actions actions,
            State state,
            org.json.JSONArray customStickers
    ) {
        this.activity = activity;
        this.actions = actions;
        this.state = state;
        this.customStickers = customStickers == null
                ? new org.json.JSONArray() : customStickers;
    }

    Result build() {
        int pageCount = Math.max(1, (EMOJIS.length + EMOJIS_PER_PAGE - 1) / EMOJIS_PER_PAGE);
        int page = Math.max(0, Math.min(state.requestedPage(), pageCount - 1));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(4));

        LinearLayout customPanel = new LinearLayout(activity);
        customPanel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(customPanel);

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(COLUMNS);
        grid.setRowCount(ROWS);
        grid.setPadding(0, 0, 0, dp(8));
        panel.addView(grid);

        PagerResult pager = buildPager(page, pageCount);
        panel.addView(pager.root());

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("选择表情")
                .setView(panel)
                .setNegativeButton("取消", null)
                .create();

        addCustomStickers(customPanel, dialog);
        addEmojiOptions(grid, dialog, page);
        actions.bindClick(pager.previous(), () -> {
            if (page <= 0) {
                return;
            }
            dialog.dismiss();
            actions.showPage(page - 1);
        });
        actions.bindClick(pager.next(), () -> {
            if (page >= pageCount - 1) {
                return;
            }
            dialog.dismiss();
            actions.showPage(page + 1);
        });
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                int width = Math.min(screenWidth() - dp(28), dp(420));
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            actions.shown();
        });
        return new Result(
                dialog,
                grid,
                pager.previous(),
                pager.indicator(),
                pager.next(),
                page,
                pageCount
        );
    }

    Result show() {
        Result result = build();
        result.dialog().show();
        return result;
    }

    private PagerResult buildPager(int page, int pageCount) {
        LinearLayout pager = new LinearLayout(activity);
        pager.setGravity(Gravity.CENTER);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        TextView previous = smallPill("上一页", page > 0 ? Ui.GREEN : Ui.MUTED);
        TextView indicator = Ui.text(
                activity, (page + 1) + " / " + pageCount, 13, Ui.MUTED, Typeface.BOLD);
        indicator.setGravity(Gravity.CENTER);
        TextView next = smallPill(
                "下一页", page < pageCount - 1 ? Ui.GREEN : Ui.MUTED);
        pager.addView(previous, new LinearLayout.LayoutParams(dp(74), dp(36)));
        addHorizontalGap(pager, 12);
        pager.addView(indicator, new LinearLayout.LayoutParams(dp(72), dp(36)));
        addHorizontalGap(pager, 12);
        pager.addView(next, new LinearLayout.LayoutParams(dp(74), dp(36)));
        return new PagerResult(pager, previous, indicator, next);
    }

    private void addCustomStickers(LinearLayout panel, AlertDialog dialog) {
        TextView heading = Ui.text(activity, "自定义表情", 14, Ui.TEXT, Typeface.BOLD);
        panel.addView(heading);
        addVerticalGap(panel, 6);

        LinearLayout customGrid = new LinearLayout(activity);
        customGrid.setOrientation(LinearLayout.HORIZONTAL);
        int cellSize = Math.max(dp(Ui.MIN_TOUCH_DP), Math.min(dp(54), (screenWidth() - dp(118)) / COLUMNS));
        TextView add = Ui.text(activity, "", 26, Ui.GREEN, Typeface.BOLD);
        add.setGravity(Gravity.CENTER);
        Ui.setStartIcon(add, android.R.drawable.ic_input_add, 22, 0);
        add.setContentDescription("添加自定义表情");
        add.setBackground(Ui.stroke(Color.WHITE, dp(10), Ui.GREEN, dp(1)));
        customGrid.addView(add, cellParams(cellSize));
        actions.bindClick(add, () -> {
            dialog.dismiss();
            actions.addCustom();
        });

        org.json.JSONArray stickers = customStickers;
        int count = stickers.length();
        for (int index = 0; index < count; index++) {
            org.json.JSONObject sticker = stickers.optJSONObject(index);
            if (sticker == null) {
                continue;
            }
            long stickerId = sticker.optLong("id", 0);
            String imageUrl = sticker.optString("imageUrl", "");
            if (stickerId <= 0 || imageUrl.isBlank()) {
                continue;
            }
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("发送自定义表情");
            image.setBackground(Ui.stroke(Color.WHITE, dp(10), Ui.LINE, dp(1)));
            customGrid.addView(image, cellParams(cellSize));
            actions.loadImage(imageUrl, image);
            actions.bindClick(image, () -> {
                dialog.dismiss();
                actions.selectCustom(stickerId);
            });
        }
        HorizontalScrollView customScroll = new HorizontalScrollView(activity);
        customScroll.setHorizontalScrollBarEnabled(false);
        customScroll.addView(customGrid, new HorizontalScrollView.LayoutParams(-2, -2));
        panel.addView(customScroll);
        addVerticalGap(panel, 10);
        panel.addView(Ui.text(activity, "系统表情", 14, Ui.TEXT, Typeface.BOLD));
        addVerticalGap(panel, 4);
    }

    private LinearLayout.LayoutParams cellParams(int cellSize) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cellSize, cellSize);
        params.width = cellSize;
        params.height = cellSize;
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private void addEmojiOptions(GridLayout grid, AlertDialog dialog, int page) {
        int start = page * EMOJIS_PER_PAGE;
        int end = Math.min(start + EMOJIS_PER_PAGE, EMOJIS.length);
        int cellSize = Math.max(
                dp(Ui.MIN_TOUCH_DP),
                Math.min(dp(54), (screenWidth() - dp(118)) / COLUMNS)
        );
        for (int index = start; index < end; index++) {
            String emoji = EMOJIS[index];
            TextView option = Ui.text(activity, emoji, 24, Ui.TEXT, Typeface.NORMAL);
            option.setGravity(Gravity.CENTER);
            option.setIncludeFontPadding(true);
            option.setLineSpacing(0, 1.08f);
            option.setContentDescription("表情 " + emoji);
            option.setBackground(Ui.stroke(Color.WHITE, dp(10), Ui.LINE, dp(1)));
            Ui.ensureTouchTarget(option);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(option, params);
            actions.bindClick(option, () -> {
                actions.select(emoji);
                dialog.dismiss();
            });
        }
    }

    private TextView smallPill(String label, int color) {
        TextView view = Ui.text(activity, label, 13, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(Ui.block(color, dp(11), dp(1)));
        view.setSingleLine(true);
        Ui.ensureTouchTarget(view);
        return view;
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        parent.addView(new Space(activity), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private void addVerticalGap(LinearLayout parent, int heightDp) {
        parent.addView(new Space(activity), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int screenWidth() {
        return activity.getResources().getDisplayMetrics().widthPixels;
    }

    private int dp(int value) {
        return Ui.dp(activity, value);
    }

    private record PagerResult(
            View root,
            TextView previous,
            TextView indicator,
            TextView next
    ) {
    }
}
