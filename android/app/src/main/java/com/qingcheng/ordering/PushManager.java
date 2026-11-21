package com.qingcheng.ordering;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

final class PushManager {
    static final String ACTION_PUSH_ARRIVED = "com.qingcheng.ordering.PUSH_ARRIVED";
    static final String EXTRA_PUSH_TARGET = "pushTarget";
    static final String PROVIDER = "JPUSH";
    private static final ExecutorService EXECUTOR = WorkerPools.fixed("push", 1, 32);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final PushRegistrationCoordinator REGISTRATIONS = new PushRegistrationCoordinator(
            EXECUTOR, new PushRegistrationCoordinator.Dispatcher() {
                @Override public void post(Runnable action) { MAIN.post(action); }
                @Override public void later(Runnable action, long delayMillis) { MAIN.postDelayed(action, delayMillis); }
            });
    private static final int MAX_REGISTRATION_RETRIES = 12;
    private static final long REGISTRATION_RETRY_DELAY_MS = 5000L;
    private static boolean initialized;
    private static Boolean available;

    private PushManager() {
    }

    interface BindingCallback {
        void onResult(boolean success, String message, String registrationId);

        default void onSessionExpired(String message) { onResult(false, message, ""); }

        default void onCancelled() { }
    }

    static void init(Context context) {
        if (!isAvailable() || initialized) {
            return;
        }
        try {
            Class<?> jpush = Class.forName("cn.jpush.android.api.JPushInterface");
            Method debug = jpush.getMethod("setDebugMode", boolean.class);
            debug.invoke(null, false);
            Method init = jpush.getMethod("init", Context.class);
            init.invoke(null, context.getApplicationContext());
            Method resume = jpush.getMethod("resumePush", Context.class);
            resume.invoke(null, context.getApplicationContext());
            try {
                Method requestPermission = jpush.getMethod("requestPermission", Context.class);
                requestPermission.invoke(null, context);
            } catch (NoSuchMethodException ignored) {
            }
            initialized = true;
        } catch (Exception ignored) {
            available = false;
        }
    }

    static boolean isAvailable() {
        if (BuildConfig.COUPLE_LOCAL) return false;
        if (available != null) {
            return available;
        }
        try {
            Class.forName("cn.jpush.android.api.JPushInterface");
            available = true;
        } catch (ClassNotFoundException ignored) {
            available = false;
        }
        return available;
    }

    static boolean isConfigured(Context context) {
        if (context == null || !isAvailable()) {
            return false;
        }
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA
            );
            Bundle metadata = info.metaData;
            String appKey = metadata == null ? "" : metadata.getString("JPUSH_APPKEY", "");
            return appKey != null && !appKey.trim().isEmpty();
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    static void syncRegistration(Context context, SessionStore sessionStore, ApiClient apiClient) {
        String authToken = sessionStore.getToken();
        if (!sessionStore.isLoggedIn() || !isConfigured(context)) {
            sessionStore.savePushStateIfToken(authToken, null, false);
            return;
        }
        init(context);
        Context appContext = context.getApplicationContext();
        REGISTRATIONS.register(session(sessionStore), gateway(apiClient),
                () -> getRegistrationId(appContext), authToken,
                MAX_REGISTRATION_RETRIES, REGISTRATION_RETRY_DELAY_MS, null);
    }

    static void bindNow(Context context, SessionStore sessionStore, ApiClient apiClient, BindingCallback callback) {
        String authToken = sessionStore.getToken();
        if (!sessionStore.isLoggedIn()) {
            notifyBinding(callback, false, "请先登录", "");
            return;
        }
        if (!isAvailable()) {
            sessionStore.savePushStateIfToken(authToken, null, false);
            notifyBinding(callback, false, "当前 APK 未包含 JPush 组件", "");
            return;
        }
        if (!isConfigured(context)) {
            sessionStore.savePushStateIfToken(authToken, null, false);
            notifyBinding(callback, false, "当前安装包未配置推送服务，请更新到修复版本", "");
            return;
        }
        init(context);
        Context appContext = context.getApplicationContext();
        REGISTRATIONS.register(session(sessionStore), gateway(apiClient),
                () -> getRegistrationId(appContext), authToken, 5, 2000L,
                result -> {
                    if (callback == null) return;
                    if (result.cancelled()) callback.onCancelled();
                    else if (result.expired()) callback.onSessionExpired(result.message());
                    else callback.onResult(result.success(), result.message(), result.registrationId());
                });
    }

    static void registerWithBackend(Context context, String registrationId) {
        SessionStore sessionStore = SessionStore.shared(context);
        if (!sessionStore.isLoggedIn()) {
            return;
        }
        registerWithBackend(context, registrationId, sessionStore, new ApiClient(sessionStore));
    }

    static void unregisterFromBackend(Context context) {
        SessionStore sessionStore = SessionStore.shared(context);
        String authToken = sessionStore.getToken();
        String registrationId = sessionStore.pushRegistrationId();
        if (registrationId == null || registrationId.trim().isEmpty() || !sessionStore.isLoggedIn()) {
            return;
        }
        ApiClient apiClient = new ApiClient(sessionStore);
        REGISTRATIONS.unregister(session(sessionStore), gateway(apiClient), registrationId, authToken);
    }

    static void openApp(Context context, String target) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (target != null && !target.trim().isEmpty()) {
            intent.putExtra(EXTRA_PUSH_TARGET, target);
        }
        context.startActivity(intent);
    }

    static void notifyPushArrived(Context context, String target) {
        if (context == null || target == null || target.trim().isEmpty()) {
            return;
        }
        Intent intent = new Intent(ACTION_PUSH_ARRIVED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_PUSH_TARGET, target.trim());
        context.sendBroadcast(intent);
    }

    private static void registerWithBackend(Context context, String registrationId, SessionStore sessionStore, ApiClient apiClient) {
        String authToken = sessionStore.getToken();
        if (registrationId == null || registrationId.trim().isEmpty()) return;
        REGISTRATIONS.register(session(sessionStore), gateway(apiClient),
                () -> registrationId, authToken, 0, 0, null);
    }

    private static PushRegistrationCoordinator.Session session(SessionStore store) {
        return new PushRegistrationCoordinator.Session() {
            @Override public String token() { return store.getToken(); }
            @Override public boolean update(String expected, String registrationId, boolean ready) {
                return store.savePushStateIfToken(expected, registrationId, ready);
            }
            @Override public boolean clear(String expected) { return store.clearIfToken(expected); }
        };
    }

    private static PushRegistrationCoordinator.Gateway gateway(ApiClient client) {
        return new PushRegistrationCoordinator.Gateway() {
            @Override public boolean register(String registrationId, String authToken) throws Exception {
                return client.registerPushDevice(PROVIDER, registrationId, platformName(), authToken);
            }
            @Override public void unregister(String registrationId, String authToken) throws Exception {
                client.unregisterPushDevice(PROVIDER, registrationId, platformName(), authToken);
            }
        };
    }

    private static void notifyBinding(BindingCallback callback, boolean success, String message, String registrationId) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onResult(success, message, registrationId));
    }

    private static String getRegistrationId(Context context) {
        try {
            Class<?> jpush = Class.forName("cn.jpush.android.api.JPushInterface");
            Method method = jpush.getMethod("getRegistrationID", Context.class);
            Object result = method.invoke(null, context.getApplicationContext());
            return result == null ? "" : result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String platformName() {
        return "android-" + Build.VERSION.SDK_INT;
    }
}
