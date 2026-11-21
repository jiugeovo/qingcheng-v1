package com.qingcheng.ordering;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Stores the account's local taps until the server acknowledges its device counter. */
final class PiggyCloud {
    static final long RETRY_DELAY_MS = 5000L;

    record Member(String key, long userId, AccountScope scope) { }

    record Progress(long count, long revision, long deviceTaps, boolean legacyImportClosed) {
        static Progress from(JSONObject data) throws org.json.JSONException {
            return new Progress(data.getLong("count"), data.getLong("revision"),
                    data.getLong("deviceTaps"), data.getBoolean("legacyImportClosed"));
        }
    }

    record State(long count, long revision, long taps, long acknowledged, long legacyCount, boolean importClosed) {
        long visible() { return add(add(count, taps - acknowledged), legacyCount); }
        boolean pending() { return taps > acknowledged || legacyCount > 0; }

        State accept(Progress remote) {
            if (remote.revision() < revision) return this;
            long currentTaps;
            if (revision < 0) {
                currentTaps = add(remote.deviceTaps(), taps);
            } else if (remote.revision() > revision) {
                currentTaps = remote.deviceTaps();
            } else {
                currentTaps = Math.max(taps, remote.deviceTaps());
            }
            return new State(remote.count(), remote.revision(), currentTaps, remote.deviceTaps(),
                    remote.legacyImportClosed() ? 0 : legacyCount, remote.legacyImportClosed());
        }
    }

    interface Gateway {
        Progress read(String device, String token) throws Exception;
        Progress sync(String device, long revision, long taps, long legacyCount, String token) throws Exception;
        Progress reset(String device, long revision, String token) throws Exception;
    }

    private static Context sharedApplication;
    private static PiggyCloud shared;

    private final SharedPreferences preferences;
    private final SharedPreferences legacyPreferences;
    private final SessionStore sessions;
    private final Gateway gateway;
    private final Executor worker;
    private final Handler main = new Handler(Looper.getMainLooper());

    static synchronized PiggyCloud shared(Context context) {
        Context app = context.getApplicationContext();
        if (shared == null || sharedApplication != app) {
            SessionStore sessions = SessionStore.shared(app);
            sharedApplication = app;
            shared = new PiggyCloud(app.getSharedPreferences("electronic-piggy.sync.v1", Context.MODE_PRIVATE),
                    app.getSharedPreferences(PiggyState.PREFERENCES, Context.MODE_PRIVATE),
                    sessions, new ApiClient(sessions), WorkerPools.fixed("piggy-sync", 1, 4));
        }
        return shared;
    }

    PiggyCloud(SharedPreferences preferences, SharedPreferences legacyPreferences,
               SessionStore sessions, Gateway gateway, Executor worker) {
        this.preferences = preferences;
        this.legacyPreferences = legacyPreferences;
        this.sessions = sessions;
        this.gateway = gateway;
        this.worker = worker;
    }

    Member member() {
        synchronized (sessions) {
            var scope = sessions.accountScope();
            JSONObject user = sessions.getUser();
            if (!scope.isAuthenticated() || user == null) return null;
            JSONObject couple = user.optJSONObject("couple");
            if (couple == null) return null;
            String mode = couple.optString("mode");
            String identity = couple.optString("identity");
            long id = user.optLong("id");
            if ((!mode.equals("FORMAL") && !mode.equals("TEST")) || id <= 0
                    || (!identity.equals("JIUGE") && !identity.equals("BAOBAO"))) return null;
            return new Member(mode + "." + id + ".", id, scope);
        }
    }

    boolean current(Member member) {
        return sessions.isCurrent(member.scope());
    }

    synchronized State state(Member member) {
        String key = member.key();
        return new State(preferences.getLong(key + "count", 0), preferences.getLong(key + "revision", -1),
                preferences.getLong(key + "taps", 0), preferences.getLong(key + "acknowledged", 0),
                preferences.getLong(key + "legacy", 0), preferences.getBoolean(key + "importClosed", false));
    }

    synchronized boolean tap(Member member) {
        if (!current(member)) return false;
        State s = state(member);
        write(member, new State(s.count(), s.revision(), add(s.taps(), 1), s.acknowledged(),
                s.legacyCount(), s.importClosed()), preferences.edit()).apply();
        return true;
    }

    synchronized void rememberLegacyOwner() {
        if (preferences.contains("legacyOwner") || preferences.contains("legacyUserId")
                || PiggyState.load(legacyPreferences).count == 0) return;
        synchronized (sessions) {
            JSONObject user = sessions.getUser();
            if (user == null || (user.optJSONObject("couple") != null && member() == null)) return;
            long userId = user.optLong("id");
            // Preserve the cached owner before a forced re-login can clear the old profile.
            if (userId > 0) preferences.edit().putLong("legacyUserId", userId).commit();
        }
    }

    private synchronized void queueLegacyImport(Member member) throws IOException {
        if (!current(member) || preferences.contains("legacyOwner")) return;
        long owner = preferences.getLong("legacyUserId", 0);
        if (owner > 0 && owner != member.userId()) return;
        long oldCount = PiggyState.load(legacyPreferences).count;
        if (oldCount == 0) return;

        State s = state(member);
        State queued = new State(s.count(), s.revision(), s.taps(), s.acknowledged(),
                s.importClosed() ? 0 : oldCount, s.importClosed());
        if (!write(member, queued, preferences.edit().putString("legacyOwner", member.key())).commit()) {
            write(member, s, preferences.edit().remove("legacyOwner")).apply();
            throw new IOException("未能保存猪德导入记录");
        }
    }

    void refresh(Member member, Consumer<Boolean> finished) {
        submit(member, () -> synchronize(member), finished);
    }

    void reset(Member member, Consumer<Boolean> finished) {
        long expected = state(member).revision();
        submit(member, () -> resetOnServer(member, expected), finished);
    }

    void synchronize(Member member) throws Exception {
        if (!current(member)) return;
        queueLegacyImport(member);
        String device = deviceId();
        String token = member.scope().token();
        State s = state(member);
        if (!s.pending() || s.revision() < 0) {
            receive(member, gateway.read(device, token));
            s = state(member);
        }
        if (s.pending() && current(member)) {
            State sent = persist(member);
            if (sent != null && current(member)) {
                receive(member, gateway.sync(device, sent.revision(), sent.taps(), sent.legacyCount(), token));
            }
        }
    }

    private void resetOnServer(Member member, long expected) throws Exception {
        if (!current(member)) return;
        String device = deviceId();
        String token = member.scope().token();
        if (expected < 0) {
            Progress beforeReset = gateway.read(device, token);
            receive(member, beforeReset);
            expected = beforeReset.revision();
        }
        if (persist(member) != null && current(member)) {
            receiveReset(member, gateway.reset(device, expected, token));
        }
    }

    private synchronized String deviceId() {
        String device = preferences.getString("deviceId", "");
        if (!device.isEmpty()) return device;
        device = UUID.randomUUID().toString();
        if (!preferences.edit().putString("deviceId", device).commit()) {
            preferences.edit().remove("deviceId").apply();
            throw new IllegalStateException("无法保存猪德同步标识");
        }
        return device;
    }

    private synchronized void receive(Member member, Progress remote) {
        if (current(member)) write(member, state(member).accept(remote), preferences.edit()).apply();
    }

    private synchronized void receiveReset(Member member, Progress remote) {
        if (current(member) && remote.revision() >= state(member).revision()) {
            write(member, new State(remote.count(), remote.revision(), remote.deviceTaps(), remote.deviceTaps(),
                    0, remote.legacyImportClosed()), preferences.edit()).apply();
        }
    }

    private synchronized State persist(Member member) throws IOException {
        if (!current(member)) return null;
        State snapshot = state(member);
        // Keep the revision and device watermark together on disk before sending a mutation.
        if (!write(member, snapshot, preferences.edit()).commit()) {
            throw new IOException("未能保存本机猪德");
        }
        return snapshot;
    }

    private SharedPreferences.Editor write(Member member, State snapshot, SharedPreferences.Editor editor) {
        String key = member.key();
        return editor.putLong(key + "count", snapshot.count()).putLong(key + "revision", snapshot.revision())
                .putLong(key + "taps", snapshot.taps()).putLong(key + "acknowledged", snapshot.acknowledged())
                .putLong(key + "legacy", snapshot.legacyCount()).putBoolean(key + "importClosed", snapshot.importClosed());
    }

    private interface Request { void run() throws Exception; }

    private void submit(Member member, Request request, Consumer<Boolean> finished) {
        try {
            worker.execute(() -> {
                if (!current(member)) return;
                boolean ok = false;
                try {
                    request.run();
                    ok = true;
                } catch (Exception ignored) {
                    // The durable counters will be retried without adding an acknowledged tap twice.
                }
                complete(member, finished, ok);
            });
        } catch (RejectedExecutionException busy) {
            complete(member, finished, false);
        }
    }

    private void complete(Member member, Consumer<Boolean> finished, boolean success) {
        if (finished != null) main.post(() -> {
            if (current(member)) finished.accept(success);
        });
    }

    static long add(long base, long delta) {
        return delta > Long.MAX_VALUE - base ? Long.MAX_VALUE : base + delta;
    }
}
