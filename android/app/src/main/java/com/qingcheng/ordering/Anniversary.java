package com.qingcheng.ordering;

import org.json.JSONException;
import org.json.JSONObject;

/** Shared API data with caller-specific management permission. Server-computed countdown fields are not recalculated on receipt. */
record Anniversary(long id, String title, String date, String note, boolean repeatYearly,
                   String nextOccurrence, Long daysUntil, AnniversaryType type, boolean canManage) {

    Anniversary(long id, String title, String date, String note, boolean repeatYearly,
                String nextOccurrence, Long daysUntil, AnniversaryType type) {
        this(id, title, date, note, repeatYearly, nextOccurrence, daysUntil, type, false);
    }

    Anniversary {
        type = type == null ? AnniversaryType.OTHER : type;
    }

    Anniversary(long id, String title, String date, String note, boolean repeatYearly,
                String nextOccurrence, Long daysUntil) {
        this(id, title, date, note, repeatYearly, nextOccurrence, daysUntil, AnniversaryType.OTHER);
    }

    static Anniversary fromJson(JSONObject object) throws JSONException {
        if (object == null) {
            throw new JSONException("纪念日数据为空");
        }
        long id = requiredLong(object, "id");
        String title = requiredString(object, "title");
        String date = requiredString(object, "date");
        String note = requiredString(object, "note");
        Object repeat = object.get("repeatYearly");
        if (!(repeat instanceof Boolean)) {
            throw new JSONException("纪念日 repeatYearly 必须是布尔值");
        }
        AnniversaryType type = responseType(object);
        AnniversaryDraft draft = new AnniversaryDraft(title, date, note, (Boolean) repeat, type);
        if (id <= 0 || !draft.validate().isEmpty()) {
            throw new JSONException("纪念日基础字段无效");
        }
        // Both fields must exist, including the explicit null pair used for ended events.
        Object nextValue = object.get("nextOccurrence");
        Object daysValue = object.get("daysUntil");
        String next = nextValue == JSONObject.NULL ? null : requiredString(object, "nextOccurrence");
        Long days = daysValue == JSONObject.NULL ? null : requiredLong(object, "daysUntil");
        if ((next == null) != (days == null)
                || (next != null && (!AnniversaryDates.isValidDate(next)
                || next.compareTo(date) < 0 || days < 0))) {
            throw new JSONException("纪念日倒计时字段无效");
        }
        Object permission = object.has("canManage") ? object.get("canManage") : Boolean.FALSE;
        if (!(permission instanceof Boolean)) throw new JSONException("纪念日 canManage 必须是布尔值");
        return new Anniversary(id, title, date, note, (Boolean) repeat, next, days, type, (Boolean) permission);
    }

    private static AnniversaryType responseType(JSONObject object) throws JSONException {
        if (!object.has("type") || object.isNull("type")) {
            return AnniversaryType.OTHER;
        }
        return AnniversaryType.fromResponseId(requiredString(object, "type"));
    }

    private static String requiredString(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) {
            throw new JSONException("纪念日 " + key + " 必须是文本");
        }
        return (String) value;
    }

    private static long requiredLong(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        // JSONObject's coercing getters accept strings and fractions; the API does not.
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new JSONException("纪念日 " + key + " 必须是整数");
        }
        return ((Number) value).longValue();
    }
}
