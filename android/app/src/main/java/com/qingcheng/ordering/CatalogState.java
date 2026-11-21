package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

/** Activity-scoped snapshots only; never shared across accounts or permission changes. */
final class CatalogState {
    private String scope = "";
    private String menu;
    private String shop;
    private CatalogRows.Prepared menuPrepared;
    private CatalogRows.Prepared shopPrepared;
    private ShopView.Viewport menuViewport = ShopView.Viewport.TOP;
    private ShopView.Viewport shopViewport = ShopView.Viewport.TOP;

    void useScope(String token, boolean manager) {
        String next = token + ":" + manager;
        if (!next.equals(scope)) {
            clear();
            scope = next;
        }
    }

    boolean isScope(String token, boolean manager) {
        return scope.equals(token + ":" + manager);
    }

    JSONArray items(boolean showingMenu) throws JSONException {
        String value = showingMenu ? menu : shop;
        return value == null ? null : new JSONArray(value);
    }

    CatalogRows.Prepared prepared(boolean showingMenu) {
        return showingMenu ? menuPrepared : shopPrepared;
    }

    boolean matches(boolean showingMenu, JSONArray items) {
        return items.toString().equals(showingMenu ? menu : shop);
    }

    void remember(boolean showingMenu, JSONArray items) {
        // Legacy callers have no category presets. Keep validated JSON for the renderer to map.
        try {
            CatalogRows.Prepared value = CatalogRows.prepare(items, showingMenu, List.of());
            if (showingMenu) {
                menu = value.json();
                menuPrepared = null;
            } else {
                shop = value.json();
                shopPrepared = null;
            }
        } catch (JSONException error) {
            throw new IllegalArgumentException("目录数据无效", error);
        }
    }

    void remember(boolean showingMenu, CatalogRows.Prepared prepared) {
        if (showingMenu != prepared.menu()) throw new IllegalArgumentException("目录类型不匹配");
        String value = prepared.json();
        if (showingMenu) {
            menu = value;
            menuPrepared = prepared;
        } else {
            shop = value;
            shopPrepared = prepared;
        }
    }

    void rememberViewport(boolean showingMenu, ShopView.Viewport viewport) {
        if (showingMenu) {
            menuViewport = viewport;
        } else {
            shopViewport = viewport;
        }
    }

    ShopView.Viewport viewport(boolean showingMenu) {
        return showingMenu ? menuViewport : shopViewport;
    }

    void clear() {
        scope = "";
        menu = null;
        shop = null;
        menuPrepared = null;
        shopPrepared = null;
        menuViewport = ShopView.Viewport.TOP;
        shopViewport = ShopView.Viewport.TOP;
    }
}
