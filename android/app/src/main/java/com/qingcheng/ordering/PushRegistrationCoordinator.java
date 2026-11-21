package com.qingcheng.ordering;

import java.util.concurrent.Executor;

/** Keeps push retries and remote completions bound to the account which started them. */
final class PushRegistrationCoordinator {
    interface Session {
        String token();
        boolean update(String expectedToken, String registrationId, boolean ready);
        boolean clear(String expectedToken);
    }

    interface Gateway {
        boolean register(String registrationId, String authToken) throws Exception;
        void unregister(String registrationId, String authToken) throws Exception;
    }

    interface Dispatcher {
        void post(Runnable action);
        void later(Runnable action, long delayMillis);
    }

    interface RegistrationId { String get(); }
    interface Callback { void complete(Result result); }

    record Result(boolean success, boolean expired, boolean cancelled,
                  String message, String registrationId) {
        static Result cancelledResult() { return new Result(false, false, true, "", ""); }
    }

    private final Executor worker;
    private final Dispatcher main;

    PushRegistrationCoordinator(Executor worker, Dispatcher main) {
        this.worker = worker;
        this.main = main;
    }

    void register(Session session, Gateway gateway, RegistrationId registrationId,
                  String authToken, int retries, long retryDelay, Callback callback) {
        attempt(session, gateway, registrationId, authToken, retries, retryDelay, callback);
    }

    private void attempt(Session session, Gateway gateway, RegistrationId idProvider,
                         String authToken, int retries, long retryDelay, Callback callback) {
        TaskSubmission.execute(worker, () -> {
            if (!current(session, authToken)) {
                notifyCancelled(callback);
                return;
            }
            String rawId = idProvider.get();
            String id = rawId == null ? "" : rawId.trim();
            if (id.isEmpty()) {
                main.post(() -> {
                    if (!session.update(authToken, null, false)) {
                        notifyResult(callback, Result.cancelledResult());
                    } else if (retries > 0) {
                        main.later(() -> attempt(session, gateway, idProvider, authToken,
                                retries - 1, retryDelay, callback), retryDelay);
                    } else {
                        notifyResult(callback, new Result(false, false, false,
                                "推送服务初始化超时，请检查网络后重试", ""));
                    }
                });
                return;
            }
            // Publish the SDK ID before starting the remote registration, so a logout while
            // registration is in flight can enqueue a matching old-token unregister request.
            main.post(() -> {
                if (!session.update(authToken, id, false)) {
                    notifyResult(callback, Result.cancelledResult());
                    return;
                }
                registerDevice(session, gateway, id, authToken, callback);
            });
        }, error -> main.post(() -> {
            if (current(session, authToken)) {
                notifyResult(callback, new Result(false, false, false, error.getMessage(), ""));
            } else {
                notifyResult(callback, Result.cancelledResult());
            }
        }), () -> { });
    }

    private void registerDevice(Session session, Gateway gateway, String id,
                                String authToken, Callback callback) {
        TaskSubmission.execute(worker, () -> {
            try {
                if (!current(session, authToken)) {
                    notifyCancelled(callback);
                    return;
                }
                boolean configured = gateway.register(id, authToken);
                main.post(() -> {
                    if (!session.update(authToken, id, configured)) {
                        notifyResult(callback, Result.cancelledResult());
                        return;
                    }
                    String suffix = id.length() <= 10 ? id : id.substring(0, 10);
                    notifyResult(callback, new Result(configured, false, false,
                            configured ? "推送绑定成功：" + suffix : "服务器推送通道未配置", id));
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (!current(session, authToken)) {
                        notifyResult(callback, Result.cancelledResult());
                        return;
                    }
                    boolean expired = callback != null && error instanceof ApiClient.UnauthorizedException;
                    boolean accepted = expired ? session.clear(authToken) : session.update(authToken, id, false);
                    if (!accepted) {
                        notifyResult(callback, Result.cancelledResult());
                        return;
                    }
                    String message = error.getMessage() == null
                            ? (expired ? "账号已在其他设备登录，请重新登录" : "推送绑定失败")
                            : error.getMessage();
                    notifyResult(callback, new Result(false, expired, false, message, id));
                });
            }
        }, error -> main.post(() -> {
            if (current(session, authToken)) {
                notifyResult(callback, new Result(false, false, false, error.getMessage(), id));
            } else {
                notifyResult(callback, Result.cancelledResult());
            }
        }), () -> { });
    }

    void unregister(Session session, Gateway gateway, String registrationId, String authToken) {
        if (authToken == null || authToken.isEmpty() || registrationId == null
                || registrationId.trim().isEmpty()) return;
        String id = registrationId.trim();
        // Logout clears local state immediately; this request must still use the captured old token.
        TaskSubmission.execute(worker, () -> {
            try {
                gateway.unregister(id, authToken);
                main.post(() -> session.update(authToken, id, false));
            } catch (Exception ignored) {
                // Best effort; failure must not alter the next account's binding state.
            }
        }, ignored -> {
            // Logout cleanup is best effort. A full queue must not delay logout or run IO inline.
        }, () -> { });
    }

    private static boolean current(Session session, String authToken) {
        return authToken != null && !authToken.isEmpty() && authToken.equals(session.token());
    }

    private void notifyCancelled(Callback callback) {
        main.post(() -> notifyResult(callback, Result.cancelledResult()));
    }

    private static void notifyResult(Callback callback, Result result) {
        if (callback != null) callback.complete(result);
    }
}
