package com.qingcheng.ordering;

import android.content.Context;

import cn.jpush.android.api.NotificationMessage;
import cn.jpush.android.service.JPushMessageReceiver;

public final class QingchengJPushReceiver extends JPushMessageReceiver {

    @Override
    public void onRegister(Context context, String registrationId) {
        if (registrationId != null && !registrationId.trim().isEmpty()) {
            PushManager.registerWithBackend(context, registrationId);
        }
    }

    @Override
    public void onNotifyMessageOpened(Context context, NotificationMessage message) {
        PushManager.openApp(context, message == null ? "" : resolveTarget(message.notificationExtras));
    }

    @Override
    public void onNotifyMessageArrived(Context context, NotificationMessage message) {
        PushManager.notifyPushArrived(context, message == null ? "" : resolveTarget(message.notificationExtras));
    }

    private String resolveTarget(String extras) {
        return JPushReceiver.resolveTarget(extras);
    }
}
