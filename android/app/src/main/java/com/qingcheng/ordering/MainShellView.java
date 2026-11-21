package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

final class MainShellView {
    interface Actions {
        void bindClick(View view, Runnable action);

        void selectTab(String tab);

        void refreshCatalog();
    }

    record State(String selectedTab, long friendUnreadCount) {
        State {
            selectedTab = selectedTab == null || selectedTab.isBlank() ? "home" : selectedTab;
        }
    }

    record Result(
            FrameLayout frame,
            LinearLayout backgroundRoot,
            View header,
            LinearLayout content,
            LinearLayout aiComposerDock,
            TextView title,
            TextView subtitle,
            TextView walletText,
            ProgressBar progress,
            ScrollView mainScroll,
            ContentRefreshLayout refreshLayout,
            Navigation navigation
    ) {
    }

    static final class Navigation {
        private final Context context;
        private final LinearLayout navigationBar;

        Navigation(Context context, LinearLayout navigationBar) {
            this.context = context;
            this.navigationBar = navigationBar;
        }

        void update(String selectedTab, long friendUnreadCount) {
            for (int index = 0; index < navigationBar.getChildCount(); index++) {
                View child = navigationBar.getChildAt(index);
                if (child instanceof TextView item && child.getTag() instanceof String tab) {
                    updateItem(context, item, tab, selectedTab, friendUnreadCount);
                }
            }
        }
    }

    static final int NAV_ICON_DP = 30;
    static final int NAV_ITEM_HEIGHT_DP = 64;
    static final int NAV_LABEL_SP = 11;

    private final Context context;
    private final Actions actions;
    private final State state;

    MainShellView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    Result build() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(RefreshUi.PAPER);

        HeaderResult header = buildHeader();
        root.addView(header.root());

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setNestedScrollingEnabled(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        ContentRefreshLayout refreshLayout = new ContentRefreshLayout(context);
        refreshLayout.addView(scroll, new ViewGroup.LayoutParams(-1, -1));
        refreshLayout.setOnRefreshListener(actions::refreshCatalog);
        root.addView(refreshLayout, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout aiComposerDock = new LinearLayout(context);
        aiComposerDock.setOrientation(LinearLayout.VERTICAL);
        aiComposerDock.setPadding(dp(14), dp(4), dp(14), dp(8));
        aiComposerDock.setVisibility(View.GONE);
        root.addView(aiComposerDock, new LinearLayout.LayoutParams(-1, -2));

        NavigationResult navigation = buildNavigation();
        root.addView(navigation.root());

        FrameLayout frame = new FrameLayout(context);
        frame.addView(root);
        ProgressBar progress = new ProgressBar(context);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(46), dp(46), Gravity.CENTER);
        frame.addView(progress, progressParams);

        return new Result(
                frame,
                root,
                header.root(),
                content,
                aiComposerDock,
                header.title(),
                header.subtitle(),
                header.walletText(),
                progress,
                scroll,
                refreshLayout,
                navigation.controller()
        );
    }

    private HeaderResult buildHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(8));

        ImageView icon = new ImageView(context);
        icon.setTag("app-avatar");
        icon.setImageResource(R.drawable.site_icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setBackgroundColor(Color.TRANSPARENT);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams headerIconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        headerIconParams.setMargins(dp(12), 0, 0, 0);

        LinearLayout headerText = new LinearLayout(context);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(context, "青程点菜", 26, RefreshUi.TEXT, Typeface.BOLD);
        TextView subtitle = Ui.text(context, "正在同步账号信息", 12, RefreshUi.MUTED, Typeface.NORMAL);
        TextView walletText = Ui.text(context, "青程币 --", 13, Ui.GREEN, Typeface.BOLD);
        headerText.addView(title);
        addVerticalGap(headerText, 2);
        headerText.addView(subtitle);
        walletText.setVisibility(View.GONE);
        header.addView(headerText, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(icon, headerIconParams);
        return new HeaderResult(header, title, subtitle, walletText);
    }

    private NavigationResult buildNavigation() {
        LinearLayout navigationBar = new LinearLayout(context);
        navigationBar.setOrientation(LinearLayout.HORIZONTAL);
        navigationBar.setGravity(Gravity.CENTER);
        navigationBar.setPadding(dp(6), dp(5), dp(6), dp(6));
        navigationBar.setBackground(Ui.stroke(RefreshUi.WHITE, 0, RefreshUi.LINE, dp(1)));
        for (String tab : MainNav.tabs()) {
            addNavigationItem(navigationBar, tab);
        }
        Navigation navigation = new Navigation(context, navigationBar);
        navigation.update(state.selectedTab(), state.friendUnreadCount());
        return new NavigationResult(navigationBar, navigation);
    }

    private void addNavigationItem(LinearLayout navigationBar, String tab) {
        MainNav.NavSpec spec = MainNav.spec(tab);
        TextView item = Ui.text(context, spec.label(), NAV_LABEL_SP, Ui.MUTED, Typeface.BOLD);
        Ui.setTopIcon(item, spec.iconRes(), NAV_ICON_DP);
        item.setCompoundDrawablePadding(dp(2));
        item.setId(spec.viewId());
        item.setGravity(Gravity.CENTER);
        item.setLines(1);
        item.setPadding(0, dp(3), 0, dp(3));
        item.setTag(tab);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(NAV_ITEM_HEIGHT_DP), 1);
        params.setMargins(dp(1), 0, dp(1), 0);
        navigationBar.addView(item, params);
        actions.bindClick(item, () -> actions.selectTab(tab));
    }

    private static void updateItem(
            Context context,
            TextView item,
            String tab,
            String selectedTab,
            long friendUnreadCount
    ) {
        boolean selected = tab.equals(selectedTab);
        item.setSelected(selected);
        int color = selected ? RefreshUi.GREEN : RefreshUi.MUTED;
        item.setTextColor(color);
        item.setBackground(Ui.round(
                selected ? RefreshUi.SAGE : Color.TRANSPARENT,
                Ui.dp(context, 13)));
        item.setText(MainNav.label(tab));
        Drawable icon = item.getCompoundDrawables()[1];
        if (icon != null) {
            icon.setAlpha(selected ? 255 : 190);
        }
        item.setAlpha(1f);
        item.setContentDescription(MainNav.contentDescription(tab, friendUnreadCount));
    }

    private void addVerticalGap(LinearLayout parent, int heightDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }

    private record HeaderResult(
            View root,
            TextView title,
            TextView subtitle,
            TextView walletText
    ) {
    }

    private record NavigationResult(View root, Navigation controller) {
    }
}
