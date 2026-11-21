package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;

/** Data and scroll anchors only: no detached Activity views retained across navigation. */
final class OrderScreenState {
    private record Snapshot(String json, OrdersView.Anchor anchor, int outerY) { }
    private final Map<String, OrdersView.Prepared> prepared = new HashMap<>();
    private final Map<String, Snapshot> screens = new HashMap<>();
    private String token = "";
    private String role = "";

    void useScope(String token, String role) {
        if (!matchesScope(token, role)) {
            clear();
            this.token = token;
            this.role = role;
        }
    }

    boolean matchesScope(String token, String role) {
        return this.token.equals(token) && this.role.equals(role);
    }

    JSONArray items(String scope) throws JSONException {
        Snapshot snapshot = screens.get(scope);
        return snapshot == null || snapshot.json() == null ? null : new JSONArray(snapshot.json());
    }

    OrdersView.Prepared prepared(String scope, boolean admin, boolean menu) {
        OrdersView.Prepared value = prepared.get(scope);
        return value != null && value.admin() == admin && value.menu() == menu ? value : null;
    }

    void remember(String scope, JSONArray items) {
        try {
            remember(scope, OrdersView.prepare(items, !"orders-personal".equals(scope),
                    !"orders-shop-queue".equals(scope)));
        } catch (Exception error) {
            throw new IllegalArgumentException("订单数据无效", error);
        }
    }

    void remember(String scope, OrdersView.Prepared value) {
        Snapshot previous = screens.get(scope);
        prepared.put(scope, value);
        screens.put(scope, new Snapshot(value.json(), previous == null ? null : previous.anchor(),
                previous == null ? 0 : previous.outerY()));
    }

    void rememberPosition(String scope, OrdersView.Anchor anchor, int outerY) {
        Snapshot previous = screens.get(scope);
        screens.put(scope, new Snapshot(previous == null ? null : previous.json(), anchor, outerY));
    }

    OrdersView.Anchor anchor(String scope) {
        Snapshot snapshot = screens.get(scope);
        return snapshot == null ? null : snapshot.anchor();
    }

    int outerY(String scope) {
        Snapshot snapshot = screens.get(scope);
        return snapshot == null ? 0 : snapshot.outerY();
    }

    void invalidateData() {
        for (Map.Entry<String, Snapshot> entry : screens.entrySet()) {
            Snapshot value = entry.getValue();
            entry.setValue(new Snapshot(null, value.anchor(), value.outerY()));
        }
        prepared.clear();
    }

    void clear() {
        screens.clear();
        prepared.clear();
        token = "";
        role = "";
    }
}
