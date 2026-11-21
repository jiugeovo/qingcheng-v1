package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

/** Authenticated API boundary for friend discovery, conversations and media. */
final class FriendService {
    interface Gateway {
        JSONObject center(String token) throws IOException, JSONException;
        JSONArray search(String query, String token) throws IOException, JSONException;
        void request(long userId, String token) throws IOException, JSONException;
        void accept(long requestId, String token) throws IOException, JSONException;
        void reject(long requestId, String token) throws IOException, JSONException;
        JSONArray messages(long userId, String token) throws IOException, JSONException;
        JSONObject sendText(long userId, String text, Long replyId, String token)
                throws IOException, JSONException;
        JSONObject sendImage(long userId, InputStream image, String name, String mime, String token)
                throws IOException, JSONException;
        JSONArray stickers(String token) throws IOException, JSONException;
        JSONObject addSticker(InputStream image, String name, String mime, String token)
                throws IOException, JSONException;
        JSONObject sendSticker(long userId, long stickerId, String token)
                throws IOException, JSONException;
        void markRead(long userId, String token) throws IOException, JSONException;
        void clearMessages(long userId, String token) throws IOException, JSONException;
    }

    private final Gateway gateway;

    FriendService(ApiClient api) {
        this(new Gateway() {
            @Override public JSONObject center(String token) throws IOException, JSONException {
                return api.friendCenter(token);
            }
            @Override public JSONArray search(String query, String token)
                    throws IOException, JSONException {
                return api.searchFriends(query, token);
            }
            @Override public void request(long userId, String token)
                    throws IOException, JSONException {
                api.requestFriend(userId, token);
            }
            @Override public void accept(long requestId, String token)
                    throws IOException, JSONException {
                api.acceptFriendRequest(requestId, token);
            }
            @Override public void reject(long requestId, String token)
                    throws IOException, JSONException {
                api.rejectFriendRequest(requestId, token);
            }
            @Override public JSONArray messages(long userId, String token)
                    throws IOException, JSONException {
                return api.friendMessages(userId, token);
            }
            @Override public JSONObject sendText(long userId, String text, Long replyId, String token)
                    throws IOException, JSONException {
                return api.sendFriendMessage(userId, text, replyId, token);
            }
            @Override public JSONObject sendImage(long userId, InputStream image, String name,
                    String mime, String token) throws IOException, JSONException {
                return api.sendFriendImage(userId, image, name, mime, token);
            }
            @Override public JSONArray stickers(String token) throws IOException, JSONException {
                return api.friendStickers(token);
            }
            @Override public JSONObject addSticker(InputStream image, String name, String mime,
                    String token) throws IOException, JSONException {
                return api.addFriendSticker(image, name, mime, token);
            }
            @Override public JSONObject sendSticker(long userId, long stickerId, String token)
                    throws IOException, JSONException {
                return api.sendFriendSticker(userId, stickerId, token);
            }
            @Override public void markRead(long userId, String token)
                    throws IOException, JSONException {
                api.markFriendRead(userId, token);
            }
            @Override public void clearMessages(long userId, String token)
                    throws IOException, JSONException {
                api.clearFriendMessages(userId, token);
            }
        });
    }

    FriendService(Gateway gateway) { this.gateway = gateway; }

    JSONObject center(String token) throws IOException, JSONException { return gateway.center(token); }
    JSONArray search(String query, String token) throws IOException, JSONException {
        return gateway.search(query, token);
    }
    void request(long userId, String token) throws IOException, JSONException {
        gateway.request(userId, token);
    }
    void accept(long requestId, String token) throws IOException, JSONException {
        gateway.accept(requestId, token);
    }
    void reject(long requestId, String token) throws IOException, JSONException {
        gateway.reject(requestId, token);
    }
    JSONArray messages(long userId, String token) throws IOException, JSONException {
        return gateway.messages(userId, token);
    }
    JSONObject sendText(long userId, String text, Long replyId, String token) throws IOException, JSONException {
        return gateway.sendText(userId, text, replyId, token);
    }
    JSONObject sendImage(long userId, InputStream image, String name, String mime, String token)
            throws IOException, JSONException { return gateway.sendImage(userId, image, name, mime, token); }
    JSONArray stickers(String token) throws IOException, JSONException { return gateway.stickers(token); }
    JSONObject addSticker(InputStream image, String name, String mime, String token)
            throws IOException, JSONException { return gateway.addSticker(image, name, mime, token); }
    JSONObject sendSticker(long userId, long stickerId, String token) throws IOException, JSONException {
        return gateway.sendSticker(userId, stickerId, token);
    }
    void markRead(long userId, String token) throws IOException, JSONException {
        gateway.markRead(userId, token);
    }
    void clearMessages(long userId, String token) throws IOException, JSONException {
        gateway.clearMessages(userId, token);
    }
}
