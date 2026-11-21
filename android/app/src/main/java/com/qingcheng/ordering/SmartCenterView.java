package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

final class SmartCenterView {
    interface Actions {
        void bindClick(View view, Runnable action);
        void refreshFriendBadge();
        void openAiChat();
        void openFriends();
        void openWeather();
        void openLoveRules();
        void openPiggy();
        default void openAnniversaries() { }
    }
    record Result(View root, TextView friendBadge) { }

    private final Context context;
    private final Actions actions;
    private final long friendUnreadCount;
    private final boolean canManageItems;

    SmartCenterView(Context context, Actions actions, long friendUnreadCount, boolean canManageItems) {
        this.context = context;
        this.actions = actions;
        this.friendUnreadCount = friendUnreadCount;
        this.canManageItems = canManageItems;
    }

    Result build() {
        LinearLayout page = column();
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("日常小陪伴", 18, RefreshUi.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = text("", 12, RefreshUi.GREEN, true);
        Ui.setStartIcon(refresh, R.drawable.ic_action_refresh, 22, 0);
        refresh.setGravity(Gravity.CENTER);
        refresh.setContentDescription("刷新");
        refresh.setBackground(Ui.round(RefreshUi.SAGE, dp(16)));
        heading.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        actions.bindClick(refresh, actions::refreshFriendBadge);
        page.addView(heading, margin(12));

        TextView badge = text(friendUnreadCount > 99 ? "99+" : String.valueOf(Math.max(0L, friendUnreadCount)),
                10, Ui.RED, true);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setBackground(Ui.round(RefreshUi.BLUSH, dp(99)));
        badge.setVisibility(friendUnreadCount > 0 ? View.VISIBLE : View.GONE);
        LinearLayout first = row();
        first.addView(tool(R.drawable.ic_smart_bubu, null, "小助手布布", "陪你聊天、看图解惑",
                RefreshUi.SAGE, actions::openAiChat), weighted());
        gap(first, 12, false);
        first.addView(tool(R.drawable.ic_smart_yier, badge, "好友消息", "互相联系",
                RefreshUi.APRICOT, actions::openFriends), weighted());
        page.addView(first, margin(12));
        LinearLayout second = row();
        second.addView(tool(R.drawable.ic_smart_weather, null, "天气卡片", "查询当天天气",
                RefreshUi.WHITE, actions::openWeather), weighted());
        gap(second, 12, false);
        second.addView(tool(R.drawable.ic_smart_rules, null, "情侣守则",
                canManageItems ? "管理员维护" : "查看守则", RefreshUi.BLUSH, actions::openLoveRules), weighted());
        page.addView(second, margin(16));
        page.addView(anniversaries(), margin(16));
        page.addView(piggy(), margin(12));
        return new Result(page, badge);
    }

    private View tool(int resource, TextView overlay, String title, String detail, int color, Runnable action) {
        LinearLayout card = column();
        card.setMinimumHeight(dp(178));
        card.setPadding(dp(14), dp(12), dp(14), dp(16));
        card.setBackground(RefreshUi.card(context, color, 22));
        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.addView(image(resource), new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER));
        if (overlay != null) iconFrame.addView(overlay,
                new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END));
        card.addView(iconFrame, new LinearLayout.LayoutParams(-1, dp(82)));
        gap(card, 8, true);
        TextView label = text(title, 16, RefreshUi.TEXT, true);
        card.addView(label);
        gap(card, 4, true);
        card.addView(text(detail, 11, RefreshUi.MUTED, false));
        card.setContentDescription(title + "，" + detail);
        actions.bindClick(card, action);
        return card;
    }

    private View anniversaries() {
        FrameLayout card = new FrameLayout(context);
        card.setMinimumHeight(dp(148));
        card.setBackground(Ui.round(RefreshUi.SAGE, dp(22)));
        card.setClipToOutline(true);
        ImageView scene = image(R.drawable.anniversary_bears_hero);
        scene.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.setTag("anniversary-entry-artwork");
        card.addView(scene, new FrameLayout.LayoutParams(-1, dp(148), Gravity.CENTER_VERTICAL));
        View wash = new View(context);
        wash.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{RefreshUi.SAGE, Color.argb(225, 233, 237, 227), Color.TRANSPARENT}));
        card.addView(wash, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout copy = column();
        copy.setPadding(dp(18), dp(18), dp(8), dp(18));
        copy.addView(text("纪念日", 22, RefreshUi.TEXT, true));
        gap(copy, 8, true);
        copy.addView(text("一起记录值得记住的日子", 12, RefreshUi.MUTED, false));
        gap(copy, 10, true);
        copy.addView(text("打开我们的小日历  ›", 12, RefreshUi.GREEN, true));
        card.addView(copy, new FrameLayout.LayoutParams(dp(184), -2, Gravity.CENTER_VERTICAL));
        card.setContentDescription("纪念日，查看月历、已过天数和下次倒数");
        actions.bindClick(card, actions::openAnniversaries);
        return card;
    }

    private View piggy() {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 22));
        card.addView(RefreshUi.piggyIcon(context), new LinearLayout.LayoutParams(dp(76), dp(80)));
        LinearLayout copy = column();
        copy.setPadding(dp(8), 0, 0, 0);
        copy.addView(text("电子猪猪", 18, RefreshUi.TEXT, true));
        gap(copy, 5, true);
        copy.addView(text("轻轻敲一敲，猪德加一", 12, RefreshUi.MUTED, false));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(image(R.drawable.ic_action_chevron), new LinearLayout.LayoutParams(dp(20), dp(48)));
        card.setContentDescription("电子猪猪，轻轻敲一敲，猪德加一，点击进入");
        actions.bindClick(card, actions::openPiggy);
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return RefreshUi.text(context, value, size, color, bold);
    }
    private ImageView image(int resource) {
        ImageView view = new ImageView(context);
        if (resource == R.drawable.anniversary_bears_hero) RefreshUi.anniversaryArtwork(view);
        else Ui.setImageResource(view, resource);
        view.setTag(resource);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout row() { return new LinearLayout(context); }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private LinearLayout.LayoutParams margin(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(bottom);
        return params;
    }
    private void gap(LinearLayout parent, int size, boolean vertical) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(vertical ? 1 : dp(size), vertical ? dp(size) : 1));
    }
    private int dp(int value) { return Ui.dp(context, value); }
}
