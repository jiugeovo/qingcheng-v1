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

final class HomeView {
    interface Actions {
        void bindClick(View view, Runnable action);
        void openWeather();
        void openFriends();
        void openCheckIn();
        void openOrders();
        void openMenu();
        void openPiggy();
        void openTab(String tab);
        default void openAnniversaries() { }
        default void openAiChat() { }
    }

    record State(String weatherLocation, String walletCoins, long piggyCount,
                 long friendUnreadCount, boolean checkedInToday,
                 boolean showPendingOrders, long pendingOrderCount) {
        State {
            weatherLocation = weatherLocation == null || weatherLocation.isBlank()
                    ? "合肥瑶海区" : weatherLocation.trim();
            walletCoins = walletCoins == null || walletCoins.isBlank() ? "--" : walletCoins.trim();
            piggyCount = Math.max(0L, piggyCount);
        }
    }

    record Result(View root, View weatherAction, TextView weatherLocation,
                  TextView weatherChip, ImageView weatherIcon, TextView pendingOrderValue,
                  View pendingOrderCard, StatusResult status, ShortcutResult smart, PiggyResult piggy) {
        /** Keep the mounted weather targets and viewport intact during real data refreshes. */
        void update(State state) {
            status.balance().setText(state.walletCoins());
            status.balance().setTextSize(balanceTextSize(state.walletCoins()));
            status.wallet().setContentDescription("可用青程币 " + state.walletCoins() + "，打开钱包");
            status.checkInAction().setText(state.checkedInToday() ? "已签到" : "签到");
            status.checkInAction().setTextColor(state.checkedInToday() ? RefreshUi.GREEN : RefreshUi.WHITE);
            status.checkInAction().setBackground(Ui.round(
                    state.checkedInToday() ? RefreshUi.SAGE : RefreshUi.GREEN, Ui.dp(root.getContext(), 16)));
            status.checkIn().setContentDescription(state.checkedInToday() ? "今日已签到" : "去签到");
            pendingOrderCard.setVisibility(state.showPendingOrders() ? View.VISIBLE : View.GONE);
            pendingOrderCard.setEnabled(state.showPendingOrders());
            pendingOrderCard.setClickable(state.showPendingOrders());
            updatePendingOrders(state.pendingOrderCount());
            long unread = Math.max(0L, state.friendUnreadCount());
            smart.detail().setText(unread > 99 ? "99+" : String.valueOf(unread));
            smart.detail().setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            smart.root().setContentDescription("好友消息，" + unread + " 条未读");
            piggy.value().setText(String.valueOf(state.piggyCount()));
            piggy.root().setContentDescription("电子猪猪，已有 " + state.piggyCount() + " 次，点击进入");
        }

        void updatePendingOrders(long count) {
            String copy = "待处理 " + Math.max(0L, count) + " 单";
            pendingOrderValue.setText(copy);
            pendingOrderCard.setContentDescription("订单管理，" + copy);
        }
    }

    private final Context context;
    private final Actions actions;
    private final State state;

    HomeView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    Result build() {
        LinearLayout page = column();
        LinearLayout weather = row();
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setMinimumHeight(dp(48));
        weather.setContentDescription("查看天气，" + state.weatherLocation());
        ImageView weatherIcon = image(WeatherView.iconResource("晴"));
        weather.addView(weatherIcon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        gap(weather, 8, false);
        TextView weatherChip = text("天气加载中", 13, RefreshUi.GREEN, true);
        weatherChip.setMaxLines(2);
        weather.addView(weatherChip, new LinearLayout.LayoutParams(-2, -2));
        gap(weather, 8, false);
        TextView location = text(state.weatherLocation(), 12, RefreshUi.MUTED, false);
        location.setGravity(Gravity.END);
        location.setMaxLines(2);
        weather.addView(location, new LinearLayout.LayoutParams(0, -2, 1));
        actions.bindClick(weather, actions::openWeather);
        page.addView(weather, margin(0, 10));

        page.addView(kitchen(), margin(0, 14));
        LinearLayout status = row();
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        status.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 20));
        LinearLayout wallet = column();
        wallet.addView(text("可用青程币", 11, RefreshUi.MUTED, false));
        TextView balance = text(state.walletCoins(), balanceTextSize(state.walletCoins()), RefreshUi.GREEN, true);
        balance.setMaxLines(2);
        wallet.addView(balance);
        wallet.setMinimumHeight(dp(48));
        status.addView(wallet, new LinearLayout.LayoutParams(0, -2, 1));
        actions.bindClick(wallet, () -> actions.openTab("wallet"));
        View divider = new View(context);
        divider.setBackgroundColor(RefreshUi.LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(38));
        dividerParams.setMargins(dp(10), 0, dp(14), 0);
        status.addView(divider, dividerParams);
        TextView checkIn = text("签到", 13, RefreshUi.WHITE, true);
        checkIn.setGravity(Gravity.CENTER);
        status.addView(checkIn, new LinearLayout.LayoutParams(dp(76), dp(48)));
        actions.bindClick(checkIn, actions::openCheckIn);
        page.addView(status, margin(0, 18));

        LinearLayout pending = row();
        pending.setGravity(Gravity.CENTER_VERTICAL);
        pending.setPadding(dp(16), dp(12), dp(16), dp(12));
        pending.setMinimumHeight(dp(60));
        pending.setBackground(RefreshUi.card(context, RefreshUi.SAGE, 18));
        pending.addView(text("订单管理", 14, RefreshUi.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView pendingValue = text("", 13, RefreshUi.GREEN, true);
        pending.addView(pendingValue);
        actions.bindClick(pending, actions::openOrders);
        page.addView(pending, margin(0, 18));

        page.addView(text("日常小陪伴", 18, RefreshUi.TEXT, true), margin(0, 8));
        LinearLayout tools = row();
        FrameLayout calendar = quickTool(R.drawable.ic_nav_smart_badge, "纪念日", false);
        actions.bindClick(calendar, actions::openAnniversaries);
        tools.addView(calendar, toolParams());
        FrameLayout friends = quickTool(R.drawable.ic_smart_yier, "好友消息", false);
        TextView unread = text("", 10, RefreshUi.WHITE, true);
        unread.setGravity(Gravity.CENTER);
        unread.setBackground(Ui.round(Ui.RED, dp(99)));
        FrameLayout.LayoutParams unreadParams = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP | Gravity.END);
        unreadParams.setMargins(0, dp(2), dp(2), 0);
        friends.addView(unread, unreadParams);
        actions.bindClick(friends, actions::openFriends);
        tools.addView(friends, toolParams());
        FrameLayout assistant = quickTool(R.drawable.ic_smart_bubu, "小助手", false);
        actions.bindClick(assistant, actions::openAiChat);
        tools.addView(assistant, toolParams());
        FrameLayout piggy = quickTool(R.drawable.electronic_piggy, "电子猪猪", true);
        TextView piggyCount = text("", 10, RefreshUi.MUTED, false);
        piggyCount.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        piggy.addView(piggyCount, countParams);
        actions.bindClick(piggy, actions::openPiggy);
        tools.addView(piggy, toolParams());
        page.addView(tools, margin(0, 18));
        page.addView(anniversaries(), margin(0, 18));
        TextView footer = text("日常，也有小欢喜。", 12, RefreshUi.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer);

        Result result = new Result(page, weather, location, weatherChip, weatherIcon,
                pendingValue, pending, new StatusResult(wallet, balance, checkIn, checkIn),
                new ShortcutResult(friends, unread), new PiggyResult(piggy, piggyCount));
        result.update(state);
        return result;
    }

    private View kitchen() {
        FrameLayout card = new FrameLayout(context);
        card.setMinimumHeight(dp(220));
        card.setBackground(Ui.round(RefreshUi.APRICOT, dp(24)));
        card.setClipToOutline(true);
        ImageView scene = image(R.drawable.hero_bg);
        scene.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.setTag("home-kitchen-artwork");
        card.addView(scene, new FrameLayout.LayoutParams(dp(244), dp(220), Gravity.END | Gravity.CENTER_VERTICAL));
        View wash = new View(context);
        wash.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{RefreshUi.APRICOT, Color.argb(215, 244, 231, 206), Color.TRANSPARENT}));
        card.addView(wash, new FrameLayout.LayoutParams(-1, dp(220), Gravity.CENTER_VERTICAL));
        LinearLayout copy = column();
        copy.setPadding(dp(20), dp(20), 0, dp(16));
        copy.addView(text("我们的小厨房", 12, RefreshUi.GREEN, true));
        gap(copy, 12, true);
        TextView title = text("今天，\n好好吃饭。", 29, RefreshUi.TEXT, true);
        title.setLineSpacing(dp(2), 1);
        copy.addView(title);
        TextView action = text("挑一餐喜欢的  ›", 12, RefreshUi.GREEN, true);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setMinimumHeight(dp(48));
        copy.addView(action);
        card.addView(copy, new FrameLayout.LayoutParams(dp(184), -2, Gravity.CENTER_VERTICAL));
        card.setContentDescription("我们的小厨房，挑一餐喜欢的，打开菜单");
        actions.bindClick(card, actions::openMenu);
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
        copy.setPadding(dp(18), dp(16), dp(8), dp(16));
        copy.addView(text("记住每一个特别的日子", 10, RefreshUi.MUTED, false));
        gap(copy, 8, true);
        copy.addView(text("我们的小日历", 18, RefreshUi.TEXT, true));
        gap(copy, 8, true);
        copy.addView(text("一起记录小期待  ›", 12, RefreshUi.GREEN, false));
        card.addView(copy, new FrameLayout.LayoutParams(dp(186), -2, Gravity.CENTER_VERTICAL));
        card.setContentDescription("纪念日，打开我们的小日历");
        actions.bindClick(card, actions::openAnniversaries);
        return card;
    }

    private FrameLayout quickTool(int resource, String label, boolean piggy) {
        FrameLayout tool = new FrameLayout(context);
        tool.setMinimumHeight(dp(108));
        LinearLayout body = column();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, 0, 0, dp(22));
        FrameLayout iconFrame = new FrameLayout(context);
        ImageView icon = piggy ? RefreshUi.piggyIcon(context) : image(resource);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(-1, dp(60), Gravity.CENTER));
        body.addView(iconFrame, new LinearLayout.LayoutParams(-1, dp(62)));
        TextView text = text(label, 11, RefreshUi.TEXT, true);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        body.addView(text, new LinearLayout.LayoutParams(-1, -2));
        tool.addView(body, new FrameLayout.LayoutParams(-1, -2));
        tool.setContentDescription(label);
        return tool;
    }

    private static int balanceTextSize(String value) {
        int length = value.length();
        return length > 14 ? 14 : length > 10 ? 18 : length > 7 ? 22 : 26;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout row() { return new LinearLayout(context); }
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
    private void gap(LinearLayout parent, int size, boolean vertical) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(vertical ? 1 : dp(size), vertical ? dp(size) : 1));
    }
    private LinearLayout.LayoutParams margin(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(top), 0, dp(bottom));
        return params;
    }
    private LinearLayout.LayoutParams toolParams() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private int dp(int value) { return Ui.dp(context, value); }
    private record StatusResult(View wallet, TextView balance, View checkIn, TextView checkInAction) { }
    private record ShortcutResult(View root, TextView detail) { }
    private record PiggyResult(View root, TextView value) { }
}
