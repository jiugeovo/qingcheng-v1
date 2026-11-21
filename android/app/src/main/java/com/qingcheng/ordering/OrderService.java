package com.qingcheng.ordering;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** Creates menu or shop orders with one persistent idempotency key. */
final class OrderService {
    interface Gateway {
        JSONObject menu(long itemId, int quantity, String remark, String token, String key)
                throws IOException, JSONException;
        JSONObject shop(long itemId, int quantity, String remark, String token, String key)
                throws IOException, JSONException;
    }

    private final Gateway gateway;

    OrderService(ApiClient api) {
        this(new Gateway() {
            @Override public JSONObject menu(long id, int quantity, String remark, String token, String key)
                    throws IOException, JSONException {
                return api.createMenuOrder(id, quantity, remark, token, key);
            }
            @Override public JSONObject shop(long id, int quantity, String remark, String token, String key)
                    throws IOException, JSONException {
                return api.createShopOrder(id, quantity, remark, token, key);
            }
        });
    }

    OrderService(Gateway gateway) { this.gateway = gateway; }

    JSONObject create(boolean menu, long itemId, int quantity, String remark, String token, String key)
            throws IOException, JSONException {
        if (itemId <= 0 || quantity <= 0 || token == null || token.isEmpty()
                || key == null || key.isEmpty()) throw new IllegalArgumentException("Invalid order request");
        return menu ? gateway.menu(itemId, quantity, remark, token, key)
                : gateway.shop(itemId, quantity, remark, token, key);
    }
}
