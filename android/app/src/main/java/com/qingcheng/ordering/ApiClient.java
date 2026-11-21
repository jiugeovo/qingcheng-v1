package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

final class ApiClient implements PiggyCloud.Gateway {
    static final String BASE_URL = BuildConfig.API_BASE_URL;

    private final SessionStore sessionStore;
    private final ConnectionFactory connections;
    static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int DEFAULT_READ_TIMEOUT_MS = 25000;
    private static final int AI_READ_TIMEOUT_MS = 390000;

    static final class UnauthorizedException extends IOException {
        UnauthorizedException(String message) {
            super(message);
        }
    }

    ApiClient(SessionStore sessionStore) {
        this(sessionStore, url -> (HttpURLConnection) url.openConnection());
    }

    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }

    ApiClient(SessionStore sessionStore, ConnectionFactory connections) {
        this.sessionStore = sessionStore;
        this.connections = connections;
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        HttpURLConnection connection = connections.open(new URL(BASE_URL + path));
        // Even same-origin redirects are not part of the API contract. Never forward credentials.
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    /** A parsed, explicit business rejection, as opposed to an unknown transport outcome. */
    static final class RequestRejectedException extends IOException {
        private final int statusCode;
        RequestRejectedException(String message) {
            this(message, 400);
        }
        RequestRejectedException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }
        int statusCode() { return statusCode; }
    }

    JSONArray capabilities() throws IOException, JSONException {
        return get("/api/capabilities").getJSONArray("data");
    }

    List<Capability> capabilityCatalog() throws IOException, JSONException {
        JSONArray data = capabilities();
        List<Capability> result = new ArrayList<>(data.length());
        for (int index = 0; index < data.length(); index++) {
            result.add(Capability.fromJson(data.optJSONObject(index)));
        }
        return List.copyOf(result);
    }

    List<Anniversary> anniversaries(String authToken) throws IOException, JSONException {
        JSONArray data = requestWithToken("GET", "/api/anniversaries", null, authToken)
                .getJSONArray("data");
        List<Anniversary> result = new ArrayList<>(data.length());
        for (int index = 0; index < data.length(); index++) {
            result.add(Anniversary.fromJson(data.getJSONObject(index)));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    Anniversary createAnniversary(AnniversaryDraft draft, String authToken)
            throws IOException, JSONException {
        return Anniversary.fromJson(requestWithToken("POST", "/api/anniversaries",
                draft.toJson(), authToken).getJSONObject("data"));
    }

    Anniversary updateAnniversary(long id, AnniversaryDraft draft, String authToken)
            throws IOException, JSONException {
        if (id <= 0) throw new IllegalArgumentException("纪念日 ID 无效");
        return Anniversary.fromJson(requestWithToken("PUT", "/api/anniversaries/" + id,
                draft.toJson(), authToken).getJSONObject("data"));
    }

    void deleteAnniversary(long id, String authToken) throws IOException, JSONException {
        if (id <= 0) throw new IllegalArgumentException("纪念日 ID 无效");
        requestWithToken("DELETE", "/api/anniversaries/" + id, null, authToken);
    }

    JSONObject login(String username, String password) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        return post("/api/auth/login", body, false).getJSONObject("data");
    }

    JSONObject selectTestMember(String member, String developerToken) throws IOException, JSONException {
        JSONObject body = new JSONObject().put("member", member);
        return requestWithToken("POST", "/api/test/actor", body, developerToken).getJSONObject("data");
    }

    void resetTestOrders(String developerToken) throws IOException, JSONException {
        requestWithToken("POST", "/api/test/reset", new JSONObject(), developerToken);
    }

    void logout(String authToken) throws IOException, JSONException {
        requestWithToken("POST", "/api/auth/logout", new JSONObject(), authToken);
    }

    @Override
    public PiggyCloud.Progress read(String device, String token) throws IOException, JSONException {
        return PiggyCloud.Progress.from(requestWithToken("GET", "/api/piggy?deviceId=" + device,
                null, token, true, 6000).getJSONObject("data"));
    }

    @Override
    public PiggyCloud.Progress sync(String device, long revision, long taps, long legacyCount, String token)
            throws IOException, JSONException {
        JSONObject body = new JSONObject().put("deviceId", device).put("revision", revision).put("taps", taps);
        if (legacyCount > 0) body.put("legacyCount", legacyCount);
        return PiggyCloud.Progress.from(requestWithToken("POST", "/api/piggy/sync",
                body, token, true, 6000).getJSONObject("data"));
    }

    @Override
    public PiggyCloud.Progress reset(String device, long revision, String token) throws IOException, JSONException {
        JSONObject body = new JSONObject().put("deviceId", device).put("revision", revision);
        return PiggyCloud.Progress.from(requestWithToken("POST", "/api/piggy/reset",
                body, token, true, 6000).getJSONObject("data"));
    }

    JSONObject register(String username, String password, String nickname, String phone) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        body.put("nickname", nickname);
        body.put("phone", phone);
        return post("/api/auth/register", body, false).getJSONObject("data");
    }

    JSONObject me() throws IOException, JSONException {
        return me(sessionStore.getToken());
    }

    JSONObject me(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/auth/me", null, authToken)
                .getJSONObject("data");
    }

    JSONObject appVersion() throws IOException, JSONException {
        HttpURLConnection connection = openConnection("/app-version.json?v=" + System.currentTimeMillis());
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            String text = readAll(code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IOException("版本检测失败：" + code);
            }
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally {
            connection.disconnect();
        }
    }

    JSONArray menuItems() throws IOException, JSONException {
        return menuItems(sessionStore.getToken());
    }

    JSONArray menuItems(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/menu-items", null, authToken).getJSONArray("data");
    }

    JSONArray adminMenuItems() throws IOException, JSONException {
        return adminMenuItems(sessionStore.getToken());
    }

    JSONArray adminMenuItems(String authToken) throws IOException, JSONException {
        if (sessionStore.coupleProfile() != null) {
            return requestWithToken("GET", "/api/menu-items/manage", null, authToken).getJSONArray("data");
        }
        return requestWithToken("GET", "/api/admin/menu-items", null, authToken).getJSONArray("data");
    }

    JSONArray shopItems() throws IOException, JSONException {
        return shopItems(sessionStore.getToken());
    }

    JSONArray shopItems(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/shop-items", null, authToken).getJSONArray("data");
    }

    JSONObject wallet() throws IOException, JSONException {
        return wallet(sessionStore.getToken());
    }

    JSONObject wallet(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/wallet", null, authToken).getJSONObject("data");
    }

    JSONObject checkIn() throws IOException, JSONException {
        return checkIn(sessionStore.getToken());
    }

    JSONObject checkIn(String authToken) throws IOException, JSONException {
        return requestWithToken("POST", "/api/wallet/check-in", new JSONObject(), authToken)
                .getJSONObject("data");
    }

    JSONObject updateNickname(String nickname) throws IOException, JSONException {
        return updateNickname(nickname, sessionStore.getToken());
    }

    JSONObject updateNickname(String nickname, String authToken) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("nickname", nickname == null ? "" : nickname.trim());
        return requestWithToken("POST", "/api/auth/nickname", body, authToken).getJSONObject("data");
    }

    void updatePassword(String oldPassword, String newPassword) throws IOException, JSONException {
        updatePassword(oldPassword, newPassword, sessionStore.getToken());
    }

    void updatePassword(String oldPassword, String newPassword, String authToken)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("oldPassword", oldPassword == null ? "" : oldPassword);
        body.put("newPassword", newPassword == null ? "" : newPassword);
        requestWithToken("POST", "/api/auth/password", body, authToken);
    }

    boolean registerPushDevice(String provider, String registrationId, String platform) throws IOException, JSONException {
        return registerPushDevice(provider, registrationId, platform, sessionStore.getToken());
    }

    boolean registerPushDevice(String provider, String registrationId, String platform, String authToken)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("provider", provider == null ? "JPUSH" : provider);
        body.put("registrationId", registrationId == null ? "" : registrationId.trim());
        body.put("platform", platform == null ? "android" : platform);
        JSONObject data = requestWithToken("POST", "/api/push/devices", body, authToken).optJSONObject("data");
        return data != null && data.optBoolean("configured", false);
    }

    void unregisterPushDevice(String provider, String registrationId, String platform) throws IOException, JSONException {
        unregisterPushDevice(provider, registrationId, platform, sessionStore.getToken());
    }

    void unregisterPushDevice(String provider, String registrationId, String platform, String authToken)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("provider", provider == null ? "JPUSH" : provider);
        body.put("registrationId", registrationId == null ? "" : registrationId.trim());
        body.put("platform", platform == null ? "android" : platform);
        requestWithToken("POST", "/api/push/devices/unregister", body, authToken);
    }

    boolean pushConfigured() throws IOException, JSONException {
        JSONObject data = get("/api/push/status").optJSONObject("data");
        return data != null && data.optBoolean("configured", false);
    }

    JSONArray myOrders() throws IOException, JSONException {
        return myOrders(sessionStore.getToken());
    }

    JSONArray myOrders(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/orders/me", null, authToken).getJSONArray("data");
    }

    JSONArray myShopOrders() throws IOException, JSONException {
        return myShopOrders(sessionStore.getToken());
    }

    JSONArray myShopOrders(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/shop-orders/me", null, authToken).getJSONArray("data");
    }

    JSONArray adminMenuOrders() throws IOException, JSONException {
        return adminMenuOrders(sessionStore.getToken());
    }

    JSONArray adminMenuOrders(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/admin/orders", null, authToken).getJSONArray("data");
    }

    JSONArray adminShopOrders() throws IOException, JSONException {
        return adminShopOrders(sessionStore.getToken());
    }

    JSONArray adminShopOrders(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/admin/shop-orders", null, authToken).getJSONArray("data");
    }

    JSONArray users() throws IOException, JSONException {
        return users(sessionStore.getToken());
    }

    JSONArray users(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/admin/users", null, authToken)
                .getJSONArray("data");
    }

    JSONObject saveMenuItem(Long id, String name, String category, String description, String imageUrl, String price, boolean available)
            throws IOException, JSONException {
        return saveMenuItem(id, name, category, description, imageUrl, price, available,
                sessionStore.getToken());
    }

    JSONObject saveMenuItem(Long id, String name, String category, String description,
                            String imageUrl, String price, boolean available, String authToken)
            throws IOException, JSONException {
        JSONObject body = itemBody(name, category, description, imageUrl, price);
        body.put("available", available);
        String base = sessionStore.coupleProfile() == null ? "/api/admin/menu-items" : "/api/menu-items";
        String path = id == null ? base : base + "/" + id;
        return requestWithToken(id == null ? "POST" : "PUT", path, body, authToken)
                .getJSONObject("data");
    }

    JSONObject saveShopItem(Long id, String name, String category, String description, String imageUrl, String price)
            throws IOException, JSONException {
        return saveShopItem(id, name, category, description, imageUrl, price,
                sessionStore.getToken());
    }

    JSONObject saveShopItem(Long id, String name, String category, String description,
                            String imageUrl, String price, String authToken)
            throws IOException, JSONException {
        JSONObject body = itemBody(name, category, description, imageUrl, price);
        String base = sessionStore.coupleProfile() == null ? "/api/admin/shop-items" : "/api/shop-items";
        String path = id == null ? base : base + "/" + id;
        return requestWithToken(id == null ? "POST" : "PUT", path, body, authToken)
                .getJSONObject("data");
    }

    void deleteMenuItem(long id) throws IOException, JSONException {
        deleteMenuItem(id, sessionStore.getToken());
    }

    void deleteMenuItem(long id, String authToken) throws IOException, JSONException {
        if (sessionStore.coupleProfile() != null) {
            requestWithToken("DELETE", "/api/menu-items/" + id, null, authToken);
            return;
        }
        requestWithToken("DELETE", "/api/admin/menu-items/" + id, null, authToken);
    }

    void deleteShopItem(long id) throws IOException, JSONException {
        deleteShopItem(id, sessionStore.getToken());
    }

    void deleteShopItem(long id, String authToken) throws IOException, JSONException {
        if (sessionStore.coupleProfile() != null) {
            requestWithToken("DELETE", "/api/shop-items/" + id, null, authToken);
            return;
        }
        requestWithToken("DELETE", "/api/admin/shop-items/" + id, null, authToken);
    }

    void deleteMenuOrder(long id) throws IOException, JSONException {
        deleteMenuOrder(id, sessionStore.getToken());
    }

    void deleteMenuOrder(long id, String authToken) throws IOException, JSONException {
        requestWithToken("DELETE", "/api/admin/orders/" + id, null, authToken);
    }

    void deleteShopOrder(long id) throws IOException, JSONException {
        deleteShopOrder(id, sessionStore.getToken());
    }

    void deleteShopOrder(long id, String authToken) throws IOException, JSONException {
        requestWithToken("DELETE", "/api/admin/shop-orders/" + id, null, authToken);
    }

    JSONObject updateUserRole(long id, String role) throws IOException, JSONException {
        return updateUserRole(id, role, sessionStore.getToken());
    }

    JSONObject updateUserRole(long id, String role, String authToken)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("role", role);
        return requestWithToken("PUT", "/api/admin/users/" + id + "/role", body, authToken)
                .getJSONObject("data");
    }

    JSONObject updateUserCoins(long id, String coins) throws IOException, JSONException {
        return updateUserCoins(id, coins, sessionStore.getToken());
    }

    JSONObject updateUserCoins(long id, String coins, String authToken)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("qingchengCoins", coins);
        return requestWithToken("PUT", "/api/admin/users/" + id + "/coins", body, authToken)
                .getJSONObject("data");
    }

    void deleteUser(long id) throws IOException, JSONException {
        deleteUser(id, sessionStore.getToken());
    }

    void deleteUser(long id, String authToken) throws IOException, JSONException {
        requestWithToken("DELETE", "/api/admin/users/" + id, null, authToken);
    }

    JSONArray aiConversations() throws IOException, JSONException {
        return aiConversations(sessionStore.getToken());
    }

    JSONArray aiConversations(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/ai/conversations", null, authToken)
                .getJSONArray("data");
    }

    AiHistoryState.Page aiConversationsPage(String cursor, int limit, String authToken)
            throws IOException, JSONException {
        String path = "/api/ai/conversations/page?limit=" + Math.max(1, Math.min(100, limit));
        if (cursor != null && !cursor.isEmpty()) {
            path += "&cursor=" + java.net.URLEncoder.encode(cursor, "UTF-8");
        }
        return AiHistoryState.Page.from(requestWithToken("GET", path, null, authToken).get("data"));
    }

    JSONArray aiMessages(long conversationId) throws IOException, JSONException {
        return aiMessages(conversationId, sessionStore.getToken());
    }

    JSONArray aiMessages(long conversationId, String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/ai/conversations/" + conversationId + "/messages",
                null, authToken).getJSONArray("data");
    }

    void deleteAiConversation(long conversationId) throws IOException, JSONException {
        deleteAiConversation(conversationId, sessionStore.getToken());
    }

    void deleteAiConversation(long conversationId, String authToken)
            throws IOException, JSONException {
        requestWithToken("DELETE", "/api/ai/conversations/" + conversationId,
                null, authToken);
    }

    JSONObject friendCenter() throws IOException, JSONException {
        return friendCenter(sessionStore.getToken());
    }

    JSONObject friendCenter(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/friends", null, authToken).getJSONObject("data");
    }

    JSONArray searchFriends(String query) throws IOException, JSONException {
        return searchFriends(query, sessionStore.getToken());
    }

    JSONArray searchFriends(String query, String authToken) throws IOException, JSONException {
        String encoded = java.net.URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8.name());
        return requestWithToken("GET", "/api/friends/search?query=" + encoded, null, authToken)
                .getJSONArray("data");
    }

    void requestFriend(long userId) throws IOException, JSONException {
        requestFriend(userId, sessionStore.getToken());
    }

    void requestFriend(long userId, String authToken) throws IOException, JSONException {
        requestWithToken("POST", "/api/friends/requests/" + userId, new JSONObject(), authToken);
    }

    void acceptFriendRequest(long friendshipId) throws IOException, JSONException {
        acceptFriendRequest(friendshipId, sessionStore.getToken());
    }

    void acceptFriendRequest(long friendshipId, String authToken) throws IOException, JSONException {
        requestWithToken("POST", "/api/friends/requests/" + friendshipId + "/accept",
                new JSONObject(), authToken);
    }

    void rejectFriendRequest(long friendshipId) throws IOException, JSONException {
        rejectFriendRequest(friendshipId, sessionStore.getToken());
    }

    void rejectFriendRequest(long friendshipId, String authToken) throws IOException, JSONException {
        requestWithToken("POST", "/api/friends/requests/" + friendshipId + "/reject",
                new JSONObject(), authToken);
    }

    JSONArray friendMessages(long userId) throws IOException, JSONException {
        return friendMessages(userId, sessionStore.getToken());
    }

    JSONArray friendMessages(long userId, String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/friends/" + userId + "/messages", null, authToken)
                .getJSONArray("data");
    }

    JSONObject sendFriendMessage(long userId, String content) throws IOException, JSONException {
        return sendFriendMessage(userId, content, null, sessionStore.getToken());
    }

    JSONObject sendFriendMessage(long userId, String content, String authToken)
            throws IOException, JSONException {
        return sendFriendMessage(userId, content, null, authToken);
    }

    JSONObject sendFriendMessage(
            long userId,
            String content,
            Long replyToMessageId,
            String authToken
    ) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("content", content == null ? "" : content.trim());
        if (replyToMessageId != null && replyToMessageId > 0L) {
            body.put("replyToMessageId", replyToMessageId);
        }
        return requestWithToken("POST", "/api/friends/" + userId + "/messages", body, authToken)
                .getJSONObject("data");
    }

    JSONObject sendFriendImage(
            long userId,
            InputStream input,
            String filename,
            String mimeType,
            String authToken
    ) throws IOException, JSONException {
        return uploadFriendMultipart(
                "/api/friends/" + userId + "/messages/image",
                input,
                filename,
                mimeType,
                authToken
        ).getJSONObject("data");
    }

    JSONArray friendStickers(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/friends/media/stickers", null, authToken)
                .getJSONArray("data");
    }

    JSONObject addFriendSticker(
            InputStream input,
            String filename,
            String mimeType,
            String authToken
    ) throws IOException, JSONException {
        return uploadFriendMultipart(
                "/api/friends/media/stickers", input, filename, mimeType, authToken)
                .getJSONObject("data");
    }

    JSONObject sendFriendSticker(long userId, long stickerId, String authToken)
            throws IOException, JSONException {
        return requestWithToken(
                "POST",
                "/api/friends/" + userId + "/messages/sticker/" + stickerId,
                new JSONObject(),
                authToken
        ).getJSONObject("data");
    }

    void markFriendRead(long userId) throws IOException, JSONException {
        markFriendRead(userId, sessionStore.getToken());
    }

    void markFriendRead(long userId, String authToken) throws IOException, JSONException {
        requestWithToken("POST", "/api/friends/" + userId + "/read", new JSONObject(), authToken);
    }

    void clearFriendMessages(long userId) throws IOException, JSONException {
        clearFriendMessages(userId, sessionStore.getToken());
    }

    void clearFriendMessages(long userId, String authToken) throws IOException, JSONException {
        requestWithToken("DELETE", "/api/friends/" + userId + "/messages", null, authToken);
    }

    JSONArray loveRules() throws IOException, JSONException {
        return loveRules(sessionStore.getToken());
    }

    JSONArray loveRules(String authToken) throws IOException, JSONException {
        return requestWithToken("GET", "/api/love-rules", null, authToken).getJSONArray("data");
    }

    JSONObject weather(String query) throws IOException, JSONException {
        return weather(query, sessionStore.getToken());
    }

    JSONObject weather(String query, String authToken) throws IOException, JSONException {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return requestWithToken("GET", "/api/weather", null, authToken).getJSONObject("data");
        }
        String encoded = java.net.URLEncoder.encode(normalized, StandardCharsets.UTF_8.name());
        return requestWithToken("GET", "/api/weather?query=" + encoded, null, authToken)
                .getJSONObject("data");
    }

    JSONObject createLoveRule(String content) throws IOException, JSONException {
        return createLoveRule(content, sessionStore.getToken());
    }

    JSONObject createLoveRule(String content, String authToken) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("content", content == null ? "" : content.trim());
        return requestWithToken("POST", "/api/love-rules", body, authToken)
                .getJSONObject("data");
    }

    JSONObject updateLoveRule(long id, String content, int sortOrder) throws IOException, JSONException {
        return updateLoveRule(id, content, sortOrder, sessionStore.getToken());
    }

    JSONObject updateLoveRule(long id, String content, int sortOrder, String authToken) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("content", content == null ? "" : content.trim());
        body.put("sortOrder", sortOrder);
        return requestWithToken("PUT", "/api/love-rules/" + id, body, authToken)
                .getJSONObject("data");
    }

    void deleteLoveRule(long id) throws IOException, JSONException {
        deleteLoveRule(id, sessionStore.getToken());
    }

    void deleteLoveRule(long id, String authToken) throws IOException, JSONException {
        requestWithToken("DELETE", "/api/love-rules/" + id, null, authToken);
    }

    JSONObject createMenuOrder(long itemId, int quantity, String remark) throws IOException, JSONException {
        return createMenuOrder(itemId, quantity, remark, sessionStore.getToken());
    }

    JSONObject createMenuOrder(long itemId, int quantity, String remark, String authToken)
            throws IOException, JSONException {
        return createMenuOrder(itemId, quantity, remark, authToken, null);
    }

    JSONObject createMenuOrder(long itemId, int quantity, String remark, String authToken, String idempotencyKey)
            throws IOException, JSONException {
        JSONObject line = new JSONObject();
        line.put("menuItemId", itemId);
        line.put("quantity", quantity);
        JSONArray items = new JSONArray();
        items.put(line);
        JSONObject body = new JSONObject();
        body.put("items", items);
        body.put("remark", remark == null ? "" : remark);
        return requestWithToken("POST", "/api/orders", body, authToken,
                true, DEFAULT_READ_TIMEOUT_MS, idempotencyKey).getJSONObject("data");
    }

    JSONObject createShopOrder(long itemId, int quantity, String remark) throws IOException, JSONException {
        return createShopOrder(itemId, quantity, remark, sessionStore.getToken());
    }

    JSONObject createShopOrder(long itemId, int quantity, String remark, String authToken)
            throws IOException, JSONException {
        return createShopOrder(itemId, quantity, remark, authToken, null);
    }

    JSONObject createShopOrder(long itemId, int quantity, String remark, String authToken, String idempotencyKey)
            throws IOException, JSONException {
        JSONObject line = new JSONObject();
        line.put("shopItemId", itemId);
        line.put("quantity", quantity);
        JSONArray items = new JSONArray();
        items.put(line);
        JSONObject body = new JSONObject();
        body.put("items", items);
        body.put("remark", remark == null ? "" : remark);
        return requestWithToken("POST", "/api/shop-orders", body, authToken,
                true, DEFAULT_READ_TIMEOUT_MS, idempotencyKey).getJSONObject("data");
    }

    JSONArray receivedOrders(boolean menu, String authToken) throws IOException, JSONException {
        String path = menu ? "/api/orders/received" : "/api/shop-orders/received";
        return requestWithToken("GET", path, null, authToken).getJSONArray("data");
    }

    void actOnOrder(boolean menu, long orderId, String action, String authToken) throws IOException, JSONException {
        String path = menu ? "/api/orders/" : "/api/shop-orders/";
        requestWithToken("PUT", path + orderId + "/status", new JSONObject().put("action", action), authToken);
    }

    JSONObject updateMenuOrderStatus(long orderId, String status) throws IOException, JSONException {
        return updateMenuOrderStatus(orderId, status, sessionStore.getToken());
    }

    JSONObject updateMenuOrderStatus(long orderId, String status, String authToken)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("status", status);
        return requestWithToken(
                "PUT", "/api/admin/orders/" + orderId + "/status", body, authToken)
                .getJSONObject("data");
    }

    JSONObject updateShopOrderStatus(long orderId, String status) throws IOException, JSONException {
        return updateShopOrderStatus(orderId, status, sessionStore.getToken());
    }

    JSONObject updateShopOrderStatus(long orderId, String status, String authToken)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("status", status);
        return requestWithToken(
                "PUT", "/api/admin/shop-orders/" + orderId + "/status", body, authToken)
                .getJSONObject("data");
    }

    String uploadImage(InputStream input, String filename, String mimeType) throws IOException, JSONException {
        return uploadImage(input, filename, mimeType, sessionStore.getToken());
    }

    String uploadImage(
            InputStream input,
            String filename,
            String mimeType,
            String authToken
    ) throws IOException, JSONException {
        byte[] fileBytes = readBytes(input, 5 * 1024 * 1024);
        String safeName = safeMultipartFilename(filename);
        String contentType = safeMultipartContentType(mimeType);
        String boundary = "----QingchengAndroid" + System.currentTimeMillis();

        byte[] header = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = openConnection("/api/uploads");
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String token = authToken == null ? "" : authToken;
            if (!token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(header.length + fileBytes.length + footer.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(header);
                output.write(fileBytes);
                output.write(footer);
            }

            int code = connection.getResponseCode();
            String text = readAll(code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream());
            if (code == 401) {
                throw new UnauthorizedException(unauthorizedMessage(text));
            }
            JSONObject response = parseResponse(text, code);
            if (code < 200 || code >= 300 || !response.optBoolean("success", false)) {
                throw new IOException(response.optString("message", "上传失败：" + code));
            }
            return response.getJSONObject("data").optString("url", "");
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject uploadFriendMultipart(
            String path,
            InputStream input,
            String filename,
            String mimeType,
            String authToken
    ) throws IOException, JSONException {
        byte[] fileBytes = readBytes(input, 5 * 1024 * 1024);
        String safeName = safeMultipartFilename(filename);
        String contentType = safeMultipartContentType(mimeType);
        String boundary = "----QingchengFriend" + System.currentTimeMillis();
        byte[] header = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = openConnection(path);
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String token = authToken == null ? "" : authToken;
            if (!token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(header.length + fileBytes.length + footer.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(header);
                output.write(fileBytes);
                output.write(footer);
            }
            int code = connection.getResponseCode();
            String text = readAll(code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream());
            if (code == 401) {
                throw new UnauthorizedException(unauthorizedMessage(text));
            }
            JSONObject response = parseResponse(text, code);
            if (code < 200 || code >= 300 || !response.optBoolean("success", false)) {
                throw new IOException(response.optString("message", "上传失败：" + code));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private String safeMultipartFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "qingcheng-image.png";
        }
        String cleaned = filename.replace("\"", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return cleaned.isEmpty() ? "qingcheng-image.png" : cleaned;
    }

    private String safeMultipartContentType(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("image/(jpeg|jpg|png|gif|webp)")
                ? normalized : "image/png";
    }

    JSONObject aiResponse(
            String prompt,
            Long conversationId,
            List<String> imageDataUrls,
            String authToken
    ) throws IOException, JSONException {
        JSONObject body = aiRequestBody(prompt, conversationId, imageDataUrls);
        return requestWithToken("POST", "/api/ai/responses", body, authToken,
                true, AI_READ_TIMEOUT_MS).getJSONObject("data");
    }

    JSONObject aiOrderingMessage(String message, Long conversationId, String authToken, AiStreamSession session)
            throws IOException, JSONException {
        JSONObject body = new JSONObject().put("message", message);
        if (conversationId != null) body.put("conversationId", conversationId);
        HttpURLConnection connection = openConnection("/api/ai/ordering/messages");
        session.attach(connection);
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(AI_READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            if (session.isCancelled()) throw new IOException("已停止生成");
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int code = connection.getResponseCode();
            String text = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (session.isCancelled()) throw new IOException("已停止生成");
            if (code == 401) throw new UnauthorizedException(unauthorizedMessage(text));
            JSONObject response = parseResponse(text, code);
            if (code < 200 || code >= 300 || !response.optBoolean("success", false))
                throw new RequestRejectedException(response.optString("message", "点菜助手请求失败"), code);
            return response.getJSONObject("data");
        } finally {
            connection.disconnect();
            session.detach();
        }
    }

    JSONObject confirmAiOrder(String quoteToken, String authToken) throws IOException, JSONException {
        return requestWithToken("POST", "/api/ai/ordering/confirm",
                new JSONObject().put("quoteToken", quoteToken), authToken).getJSONObject("data");
    }

    JSONObject aiResponseStream(String prompt, Long conversationId, List<String> imageDataUrls,
                                String authToken, AiStreamState state, AiStreamSession session,
                                java.util.function.Consumer<AiStreamState.Update> listener)
            throws IOException, JSONException {
        return aiResponseStream(prompt, conversationId, imageDataUrls, authToken, null, state, session, listener);
    }

    JSONObject aiModels(String authToken, boolean refresh) throws IOException, JSONException {
        return requestWithToken("GET", "/api/ai/models?refresh=" + refresh, null, authToken,
                true, 15000).getJSONObject("data");
    }

    JSONObject aiResponseStream(String prompt, Long conversationId, List<String> imageDataUrls,
                                String authToken, String model, AiStreamState state, AiStreamSession session,
                                java.util.function.Consumer<AiStreamState.Update> listener)
            throws IOException, JSONException {
        HttpURLConnection connection = openConnection("/api/ai/responses/stream");
        session.attach(connection);
        try {
            connection.setConnectTimeout(12000);
            // Heartbeats keep this idle timeout alive; the backend bounds the whole generation.
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
            JSONObject body = aiRequestBody(prompt, conversationId, imageDataUrls);
            if (model != null) {
                if (!AiModels.valid(model)) throw new JSONException("模型名称格式不正确");
                body.put("model", model);
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            if (session.isCancelled()) throw new IOException("已停止生成");
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                String text = readAll(connection.getErrorStream());
                if (code == 401) throw new UnauthorizedException(unauthorizedMessage(text));
                JSONObject error = parseResponse(text, code);
                throw new IOException(error.optString("message", "AI 请求失败：" + code));
            }
            try (InputStream input = connection.getInputStream()) {
                return AiStreamReader.read(input, state, session, listener);
            }
        } catch (java.net.SocketTimeoutException timeout) {
            throw new IOException("AI 连接长时间没有更新，已保留收到的内容，请稍后再试", timeout);
        } finally {
            connection.disconnect();
            session.detach();
        }
    }

    private JSONObject aiRequestBody(String prompt, Long conversationId, List<String> imageDataUrls)
            throws JSONException {
        JSONArray content = new JSONArray();
        if (prompt != null && !prompt.trim().isEmpty()) {
            JSONObject textPart = new JSONObject();
            textPart.put("type", "input_text");
            textPart.put("text", prompt);
            content.put(textPart);
        }
        if (imageDataUrls != null) {
            for (String imageDataUrl : imageDataUrls) {
                if (imageDataUrl == null || imageDataUrl.trim().isEmpty()) {
                    continue;
                }
                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "input_image");
                imagePart.put("image_url", imageDataUrl);
                imagePart.put("detail", "auto");
                content.put(imagePart);
            }
        }
        if (content.length() == 0) {
            JSONObject textPart = new JSONObject();
            textPart.put("type", "input_text");
            textPart.put("text", "");
            content.put(textPart);
        }

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", content);
        JSONArray input = new JSONArray();
        input.put(message);

        JSONObject body = new JSONObject();
        body.put("input", input);
        body.put("displayText", prompt == null || prompt.trim().isEmpty() ? "[图片]" : prompt);
        body.put("max_output_tokens", 1200);
        if (conversationId != null) {
            body.put("conversationId", conversationId.longValue());
        }
        return body;
    }

    private JSONObject get(String path) throws IOException, JSONException {
        return request("GET", path, null, true);
    }

    private JSONObject post(String path, JSONObject body, boolean auth) throws IOException, JSONException {
        return request("POST", path, body, auth);
    }

    private JSONObject put(String path, JSONObject body) throws IOException, JSONException {
        return request("PUT", path, body, true);
    }

    private JSONObject delete(String path) throws IOException, JSONException {
        return request("DELETE", path, null, true);
    }

    private JSONObject itemBody(String name, String category, String description, String imageUrl, String price) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("name", name == null ? "" : name.trim());
        body.put("category", category == null ? "" : category.trim());
        body.put("description", description == null ? "" : description.trim());
        body.put("imageUrl", imageUrl == null ? "" : imageUrl.trim());
        body.put("price", price == null || price.trim().isEmpty() ? "1.00" : price.trim());
        return body;
    }

    private JSONObject request(String method, String path, JSONObject body, boolean auth) throws IOException, JSONException {
        return request(method, path, body, auth, DEFAULT_READ_TIMEOUT_MS);
    }

    private JSONObject request(String method, String path, JSONObject body, boolean auth, int readTimeoutMs) throws IOException, JSONException {
        return requestWithToken(method, path, body, auth ? sessionStore.getToken() : null,
                auth, readTimeoutMs);
    }

    private JSONObject requestWithToken(
            String method,
            String path,
            JSONObject body,
            String authToken
    ) throws IOException, JSONException {
        return requestWithToken(method, path, body, authToken, true, DEFAULT_READ_TIMEOUT_MS);
    }

    private JSONObject requestWithToken(
            String method,
            String path,
            JSONObject body,
            String authToken,
            boolean auth,
            int readTimeoutMs
    ) throws IOException, JSONException {
        return requestWithToken(method, path, body, authToken, auth, readTimeoutMs, null);
    }

    private JSONObject requestWithToken(String method, String path, JSONObject body,
                                        String authToken, boolean auth, int readTimeoutMs,
                                        String idempotencyKey) throws IOException, JSONException {
        HttpURLConnection connection = openConnection(path);
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (idempotencyKey != null) {
                if (!idempotencyKey.matches("[A-Za-z0-9_-]{1,100}")) {
                    throw new IOException("订单重试标识无效");
                }
                connection.setRequestProperty("Idempotency-Key", idempotencyKey);
            }
            String token = authToken == null ? "" : authToken;
            if (auth && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null && !"GET".equals(method)) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            String text = readAll(code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream());
            if (auth && code == 401) {
                throw new UnauthorizedException(unauthorizedMessage(text));
            }
            JSONObject response = parseResponse(text, code);
            if (code < 200 || code >= 300 || !response.optBoolean("success", false)) {
                if (isExplicitRejection(code, response)) {
                    throw new RequestRejectedException(response.optString("message", "请求被拒绝"), code);
                }
                throw new IOException(response.optString("message", "请求失败：" + code));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    static boolean isExplicitRejection(int code, JSONObject response) {
        return code >= 200 && code < 500 && code != 408
                && Boolean.FALSE.equals(response.opt("success"));
    }

    private JSONObject parseResponse(String text, int code) throws IOException, JSONException {
        if (text == null || text.trim().isEmpty()) {
            return new JSONObject();
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            if (code == 429) {
                throw new IOException("请求太快了，稍等一下再试");
            }
            if (code == 401 || code == 403) {
                throw new IOException("登录已失效，请重新登录");
            }
            throw new IOException("服务器返回异常：" + code);
        }
        return new JSONObject(trimmed);
    }

    private String unauthorizedMessage(String text) {
        if (text != null && !text.trim().isEmpty() && text.trim().startsWith("{")) {
            try {
                String message = new JSONObject(text.trim()).optString("message", "");
                if (message != null && !message.trim().isEmpty()) {
                    return message.trim();
                }
            } catch (JSONException ignored) {
            }
        }
        return "账号已在其他设备登录，请重新登录";
    }

    static String readAll(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream source = input) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("服务器响应过大，请稍后重试");
                if (Thread.currentThread().isInterrupted()) throw new IOException("请求已取消");
                output.write(buffer, 0, read);
            }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private byte[] readBytes(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("图片不能超过 5MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
