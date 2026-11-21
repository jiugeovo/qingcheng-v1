package com.qingcheng.ordering;

import java.util.List;
import java.util.Map;

final class MainNav {
    record NavSpec(String route, String label, int viewId, int iconRes, String contentDescription) {
    }

    private static final List<String> TABS = List.of("home", "shop", "orders", "ai", "wallet", "me");
    private static final Map<String, NavSpec> SPECS = Map.of(
            "home", new NavSpec("home", "首页", R.id.nav_home, R.drawable.ic_nav_home_badge, "首页"),
            "shop", new NavSpec("shop", "商店", R.id.nav_shop, R.drawable.ic_nav_shop_badge, "商店"),
            "orders", new NavSpec("orders", "订单", R.id.nav_orders, R.drawable.ic_nav_orders_badge, "订单"),
            "ai", new NavSpec("ai", "智能", R.id.nav_smart, R.drawable.ic_nav_smart_badge, "智能"),
            "wallet", new NavSpec("wallet", "钱包", R.id.nav_wallet, R.drawable.ic_nav_wallet_badge, "钱包"),
            "me", new NavSpec("me", "我的", R.id.nav_profile, R.drawable.ic_nav_profile_badge, "我的")
    );

    private MainNav() {
    }

    static List<String> tabs() {
        return TABS;
    }

    static NavSpec spec(String route) {
        NavSpec spec = SPECS.get(route);
        return spec == null ? SPECS.get("home") : spec;
    }

    static String badge(long unread) {
        if (unread <= 0) {
            return "";
        }
        return unread > 99 ? "99+" : Long.toString(unread);
    }

    static String contentDescription(String route, long friendUnreadCount) {
        NavSpec spec = spec(route);
        String badge = "ai".equals(spec.route()) ? badge(friendUnreadCount) : "";
        return badge.isEmpty() ? spec.contentDescription()
                : spec.contentDescription() + "，" + badge + " 条好友未读消息";
    }

    static String label(String route) {
        return spec(route).label();
    }
}
