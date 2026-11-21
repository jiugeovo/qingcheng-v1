package com.qingcheng.ordering;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SessionStore {
    private static final String PREFS = "qingcheng_session";
    private static final String LEGACY_TOKEN = "token";
    private static final String ENCRYPTED_TOKEN = "tokenEncrypted";
    private static final String TOKEN_IV = "tokenIv";
    private static final String TOKEN_KEY_ALIAS = "qingcheng_session_token_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String DEFAULT_WEATHER_LOCATION = "合肥瑶海区";
    private static Context sharedApplication;
    private static SessionStore sharedStore;

    private final SharedPreferences preferences;
    private final TokenCipher tokenCipher;
    private volatile String inMemoryToken = "";
    private long accountGeneration;

    /** A narrow injection boundary; production always uses the device AndroidKeyStore. */
    interface TokenCipher {
        EncryptedToken encrypt(String token) throws Exception;
        String decrypt(String encrypted, String iv) throws Exception;
        void deleteKey() throws Exception;
    }

    record EncryptedToken(String encrypted, String iv) {
        EncryptedToken {
            if (encrypted == null || encrypted.isEmpty() || iv == null || iv.isEmpty()) {
                throw new IllegalArgumentException("Encrypted token and IV are required");
            }
        }
    }

    /** All production consumers share account changes and process-only keystore fallback tokens. */
    static synchronized SessionStore shared(Context context) {
        Context application = Objects.requireNonNull(
                Objects.requireNonNull(context, "context").getApplicationContext(), "application context");
        if (sharedStore == null || sharedApplication != application) {
            sharedApplication = application;
            sharedStore = new SessionStore(application);
        }
        return sharedStore;
    }

    SessionStore(Context context) {
        this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), new AndroidTokenCipher());
    }

    SessionStore(SharedPreferences preferences, TokenCipher tokenCipher) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.tokenCipher = Objects.requireNonNull(tokenCipher, "tokenCipher");
        migrateLegacyToken();
    }

    synchronized boolean save(String token, JSONObject user) {
        String normalizedToken = token == null ? "" : token;
        EncryptedToken encrypted = normalizedToken.isEmpty() ? null : encryptToken(normalizedToken);
        // Keep the session usable for this process; never write a new token in cleartext.
        inMemoryToken = normalizedToken;
        accountGeneration++;
        SharedPreferences.Editor editor = tokenEditor(encrypted)
                .putString("user", user == null ? "" : user.toString());
        // Token, IV, account details and legacy removal are one atomic preference update.
        boolean committed = editor.commit();
        return committed && (normalizedToken.isEmpty() || encrypted != null);
    }

    synchronized void saveUser(JSONObject user) {
        preferences.edit().putString("user", user == null ? "" : user.toString()).apply();
    }

    synchronized boolean saveUserIfToken(String expectedToken, JSONObject user) {
        String currentToken = getToken();
        String expected = expectedToken == null ? "" : expectedToken;
        if (!expected.equals(currentToken == null ? "" : currentToken)) {
            return false;
        }
        preferences.edit().putString("user", user == null ? "" : user.toString()).apply();
        return true;
    }

    synchronized String getToken() {
        if (inMemoryToken != null && !inMemoryToken.isEmpty()) {
            return inMemoryToken;
        }
        String encrypted = preferences.getString(ENCRYPTED_TOKEN, "");
        String iv = preferences.getString(TOKEN_IV, "");
        if (encrypted != null && !encrypted.isEmpty() && iv != null && !iv.isEmpty()) {
            String decrypted = decryptToken(encrypted, iv);
            if (decrypted != null) {
                inMemoryToken = decrypted;
                return decrypted;
            }
        }
        return "";
    }

    JSONObject getUser() {
        String raw = preferences.getString("user", "");
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException ignored) {
            return null;
        }
    }

    JSONObject coupleProfile() {
        JSONObject user = getUser();
        return user == null ? null : user.optJSONObject("couple");
    }

    String role() {
        JSONObject user = getUser();
        return user == null ? "USER" : user.optString("role", "USER");
    }

    boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty();
    }

    void savePushRegistrationId(String registrationId) {
        preferences.edit()
                .putString("pushProvider", "JPUSH")
                .putString("pushRegistrationId", registrationId == null ? "" : registrationId)
                .apply();
    }

    String pushRegistrationId() {
        return preferences.getString("pushRegistrationId", "");
    }

    void savePushReady(boolean ready) {
        preferences.edit().putBoolean("pushReady", ready).apply();
    }

    synchronized boolean savePushStateIfToken(String expectedToken, String registrationId, boolean ready) {
        if (expectedToken == null || expectedToken.isEmpty() || !expectedToken.equals(getToken())) {
            return false;
        }
        SharedPreferences.Editor editor = preferences.edit().putBoolean("pushReady", ready);
        if (registrationId != null) {
            editor.putString("pushProvider", "JPUSH")
                    .putString("pushRegistrationId", registrationId.trim());
        }
        editor.apply();
        return true;
    }

    synchronized boolean clearIfToken(String expectedToken) {
        if (expectedToken == null || expectedToken.isEmpty() || !expectedToken.equals(getToken())) {
            return false;
        }
        clear();
        return true;
    }

    boolean pushReady() {
        return preferences.getBoolean("pushReady", false);
    }

    String defaultWeatherLocation() {
        return preferences.getString("defaultWeatherLocation", DEFAULT_WEATHER_LOCATION);
    }

    void saveDefaultWeatherLocation(String location) {
        String value = location == null || location.trim().isEmpty() ? DEFAULT_WEATHER_LOCATION : location.trim();
        preferences.edit().putString("defaultWeatherLocation", value).apply();
    }

    synchronized void clear() {
        accountGeneration++;
        inMemoryToken = "";
        String defaultWeatherLocation = defaultWeatherLocation();
        preferences.edit()
                .clear()
                .putString("defaultWeatherLocation", defaultWeatherLocation)
                .commit();
        deleteTokenKey();
    }

    synchronized AccountScope accountScope() {
        return new AccountScope(getToken(), accountGeneration);
    }

    synchronized boolean isCurrent(AccountScope scope) {
        return scope != null && scope.generation() == accountGeneration
                && scope.token().equals(getToken());
    }

    synchronized String accountKey() {
        JSONObject user = getUser();
        if (user != null) {
            String id = user.optString("id", "");
            if (!id.isEmpty()) return "user:" + id;
            String username = user.optString("username", "");
            if (!username.isEmpty()) return "username:" + username;
        }
        return "session:" + getToken();
    }


    private synchronized void migrateLegacyToken() {
        String legacy = preferences.getString(LEGACY_TOKEN, "");
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        // A failed keystore migration must not keep plaintext as the durable fallback.
        inMemoryToken = legacy;
        tokenEditor(encryptToken(legacy)).commit();
    }

    private SharedPreferences.Editor tokenEditor(EncryptedToken encrypted) {
        SharedPreferences.Editor editor = preferences.edit()
                .remove(LEGACY_TOKEN)
                .remove(ENCRYPTED_TOKEN)
                .remove(TOKEN_IV);
        if (encrypted != null) {
            editor.putString(ENCRYPTED_TOKEN, encrypted.encrypted())
                    .putString(TOKEN_IV, encrypted.iv());
        }
        return editor;
    }

    private EncryptedToken encryptToken(String token) {
        try {
            return tokenCipher.encrypt(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String decryptToken(String encrypted, String iv) {
        try {
            return tokenCipher.decrypt(encrypted, iv);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void deleteTokenKey() {
        try {
            tokenCipher.deleteKey();
        } catch (Exception ignored) {
            // Preferences are already cleared; a stale key contains no token by itself.
        }
    }

    private static final class AndroidTokenCipher implements TokenCipher {
        @Override public EncryptedToken encrypt(String token) throws Exception {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateTokenKey());
            return new EncryptedToken(
                    Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP),
                    Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        }

        @Override public String decrypt(String encrypted, String iv) throws Exception {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            KeyStore.Entry entry = keyStore.getEntry(TOKEN_KEY_ALIAS, null);
            if (!(entry instanceof KeyStore.SecretKeyEntry)) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    ((KeyStore.SecretKeyEntry) entry).getSecretKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            );
            byte[] cleartext = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
            return new String(cleartext, StandardCharsets.UTF_8);
        }

        private SecretKey getOrCreateTokenKey() throws Exception {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            KeyStore.Entry existing = keyStore.getEntry(TOKEN_KEY_ALIAS, null);
            if (existing instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(
                    TOKEN_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        }

        @Override public void deleteKey() throws Exception {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(TOKEN_KEY_ALIAS)) {
                keyStore.deleteEntry(TOKEN_KEY_ALIAS);
            }
        }
    }
}
