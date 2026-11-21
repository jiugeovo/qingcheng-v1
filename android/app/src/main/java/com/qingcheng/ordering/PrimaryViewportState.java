package com.qingcheng.ordering;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Account/permission scoped positions, never Activity or View instances. */
final class PrimaryViewportState {
    private final Map<ScreenRoute, Integer> positions = new EnumMap<>(ScreenRoute.class);
    private String token = "";
    private String role = "";

    static boolean supports(ScreenRoute route) {
        return route == ScreenRoute.HOME || route == ScreenRoute.SMART
                || route == ScreenRoute.PROFILE || route == ScreenRoute.WALLET;
    }

    void useScope(String token, String role) {
        if (!matchesScope(token, role)) {
            clear();
            this.token = Objects.requireNonNullElse(token, "");
            this.role = Objects.requireNonNullElse(role, "");
        }
    }

    boolean matchesScope(String token, String role) {
        return this.token.equals(token) && this.role.equals(role);
    }

    void remember(ScreenRoute route, int scrollY) {
        if (supports(route)) positions.put(route, Math.max(0, scrollY));
    }

    int scrollY(ScreenRoute route) { return positions.getOrDefault(route, 0); }

    void clear() {
        positions.clear();
        token = "";
        role = "";
    }
}
