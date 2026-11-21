package com.qingcheng.ordering;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public final class JPushReceiver extends BroadcastReceiver {
    private static final String ACTION_REGISTRATION = "cn.jpush.android.intent.REGISTRATION";
    private static final String ACTION_NOTIFICATION_RECEIVED = "cn.jpush.android.intent.NOTIFICATION_RECEIVED";
    private static final String ACTION_NOTIFICATION_OPENED = "cn.jpush.android.intent.NOTIFICATION_OPENED";
    private static final String EXTRA_REGISTRATION_ID = "cn.jpush.android.REGISTRATION_ID";
    private static final String EXTRA_EXTRA = "cn.jpush.android.EXTRA";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_REGISTRATION.equals(action)) {
            String registrationId = intent.getStringExtra(EXTRA_REGISTRATION_ID);
            if (registrationId != null && !registrationId.trim().isEmpty()) {
                PushManager.registerWithBackend(context, registrationId);
            }
        } else if (ACTION_NOTIFICATION_RECEIVED.equals(action)) {
            PushManager.notifyPushArrived(context, resolveTarget(intent.getStringExtra(EXTRA_EXTRA)));
        } else if (ACTION_NOTIFICATION_OPENED.equals(action)) {
            PushManager.openApp(context, resolveTarget(intent.getStringExtra(EXTRA_EXTRA)));
        }
    }

    static String resolveTarget(String extras) {
        if (extras == null || extras.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(extras);
            String type = json.optString("type", "");
            if ("shop_order".equals(type)) {
                return "shopOrders";
            }
            if ("menu_order".equals(type)) {
                return "orders";
            }
            if ("friend_request".equals(type) || "friend_accept".equals(type)) {
                return "friends";
            }
            if ("friend_message".equals(type)) {
                String userId = json.optString("userId", "");
                return userId.trim().isEmpty() ? "friends" : "friend:" + userId.trim();
            }
            if ("ai_conversation".equals(type)) {
                String conversationId = json.optString("conversationId", "");
                return conversationId.trim().isEmpty() ? "ai" : "ai:" + conversationId.trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
