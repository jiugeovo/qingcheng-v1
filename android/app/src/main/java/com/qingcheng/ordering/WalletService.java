package com.qingcheng.ordering;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** API boundary for wallet balance, ledger and check-in mutations. */
final class WalletService {
    interface Gateway {
        JSONObject wallet(String token) throws IOException, JSONException;
        JSONObject checkIn(String token) throws IOException, JSONException;
    }

    private final Gateway gateway;

    WalletService(ApiClient api) {
        this(new Gateway() {
            @Override public JSONObject wallet(String token) throws IOException, JSONException {
                return api.wallet(token);
            }
            @Override public JSONObject checkIn(String token) throws IOException, JSONException {
                return api.checkIn(token);
            }
        });
    }

    WalletService(Gateway gateway) { this.gateway = gateway; }
    JSONObject load(String token) throws IOException, JSONException { return gateway.wallet(token); }
    JSONObject checkIn(String token) throws IOException, JSONException { return gateway.checkIn(token); }
}
