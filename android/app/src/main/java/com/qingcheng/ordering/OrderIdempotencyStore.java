package com.qingcheng.ordering;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class OrderIdempotencyStore {
    private static final String PREFS = "qingcheng_pending_orders";
    private static final long RETENTION_MS = java.util.concurrent.TimeUnit.DAYS.toMillis(30);
    private final SharedPreferences preferences;
    private final java.util.function.Supplier<String> token;

    OrderIdempotencyStore(Context context, SessionStore sessions) {
        this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), sessions::accountKey);
    }

    OrderIdempotencyStore(SharedPreferences preferences, java.util.function.Supplier<String> token) {
        this.preferences = preferences;
        this.token = token;
    }

    synchronized String key(boolean menu, long itemId, int quantity, String remark) throws Exception {
        prune();
        String account = token.get();
        if (account == null || account.isEmpty()) throw new IllegalStateException("Login required");
        String fingerprint = fingerprint(account, menu, itemId, quantity, remark);
        String existing = preferences.getString(fingerprint, "");
        String restored = decodeKey(existing);
        if (!restored.isEmpty()) return restored;
        String value = java.util.UUID.randomUUID().toString();
        if (!preferences.edit().putString(fingerprint, encodeKey(value)).commit()) {
            throw new IllegalStateException("Could not persist pending order key");
        }
        return value;
    }

    synchronized void complete(boolean menu, long itemId, int quantity, String remark, String key)
            throws Exception {
        String fingerprint = fingerprint(token.get(), menu, itemId, quantity, remark);
        if (key.equals(decodeKey(preferences.getString(fingerprint, "")))) {
            preferences.edit().remove(fingerprint).commit();
        }
    }

    synchronized int pendingCount() { return preferences.getAll().size(); }

    private void prune() {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (var entry : preferences.getAll().entrySet()) {
            String encoded = entry.getValue() instanceof String value ? value : "";
            int separator = encoded.indexOf(':');
            long created;
            try { created = Long.parseLong(separator < 0 ? "" : encoded.substring(0, separator)); }
            catch (NumberFormatException error) { created = 0L; }
            if (created <= 0L || now - created > RETENTION_MS || created > now + RETENTION_MS) {
                editor.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) editor.commit();
    }

    private static String encodeKey(String key) { return System.currentTimeMillis() + ":" + key; }

    private static String decodeKey(String encoded) {
        if (encoded == null) return "";
        int separator = encoded.indexOf(':');
        return separator < 0 ? "" : encoded.substring(separator + 1);
    }

    private static String fingerprint(String account, boolean menu, long itemId, int quantity, String remark)
            throws Exception {
        String payload = account + "\n" + (menu ? "menu" : "shop") + "\n" + itemId + "\n"
                + quantity + "\n" + (remark == null ? "" : remark);
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) output.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return output.toString();
    }
}
