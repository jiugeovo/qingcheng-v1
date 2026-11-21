package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Immutable page state shared by both the new cursor endpoint and the legacy list endpoint. */
final class AiHistoryState {
    record ConversationSummary(long id, String title, String detail) {
        ConversationSummary(long id, String title) { this(id, title, ""); }

        ConversationSummary {
            if (id <= 0) throw new IllegalArgumentException("Conversation ID must be positive");
            title = title == null || title.isBlank() ? "对话" : title;
            detail = detail == null ? "" : detail;
        }
    }

    record Page(List<ConversationSummary> items, String nextCursor, boolean hasMore) {
        Page {
            items = List.copyOf(items);
            nextCursor = nextCursor == null ? "" : nextCursor;
        }

        static Page from(Object payload) throws JSONException {
            if (payload instanceof JSONArray array) {
                return new Page(conversations(array), "", false);
            }
            if (!(payload instanceof JSONObject object)) {
                throw new JSONException("Invalid AI history response");
            }
            JSONArray array = object.optJSONArray("items");
            if (array == null) array = object.optJSONArray("content");
            if (array == null) array = object.optJSONArray("conversations");
            if (array == null) array = new JSONArray();
            String cursor = object.optString("nextCursor", object.optString("next_cursor", ""));
            boolean more = object.optBoolean("hasMore", object.optBoolean("has_more", !cursor.isEmpty()));
            return new Page(conversations(array), cursor, more);
        }
    }

    static List<ConversationSummary> conversations(JSONArray array) {
        if (array == null) return List.of();
        ArrayList<ConversationSummary> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            Object rawId = item.opt("id");
            if (!(rawId instanceof Integer) && !(rawId instanceof Long)) continue;
            long id = ((Number) rawId).longValue();
            if (id <= 0) continue;
            String time = item.isNull("updatedAt") ? item.optString("createdAt", "") : item.optString("updatedAt", "");
            if (time.isBlank() || "null".equals(time)) time = "";
            time = DisplayFormatters.shortTime(time);
            long messages = item.optLong("messageCount", -1);
            String detail = messages < 0 ? time : (time.isEmpty() ? "" : time + " · ") + messages + " 条消息";
            result.add(new ConversationSummary(id, item.optString("title", "对话"), detail));
        }
        return List.copyOf(result);
    }

    private AiHistoryState() { }
}
