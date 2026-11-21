package com.qingcheng.ordering;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Editable fields only; ownership is never supplied by the Android client. */
record AnniversaryDraft(String title, String date, String note, boolean repeatYearly,
                        AnniversaryType type) {
    AnniversaryDraft {
        type = type == null ? AnniversaryType.OTHER : type;
    }

    AnniversaryDraft(String title, String date, String note, boolean repeatYearly) {
        this(title, date, note, repeatYearly, AnniversaryType.OTHER);
    }

    AnniversaryDraft normalized() {
        return new AnniversaryDraft(strip(title), date == null ? "" : date,
                strip(note), repeatYearly, type);
    }

    Map<String, String> validate() {
        AnniversaryDraft value = normalized();
        Map<String, String> errors = new LinkedHashMap<>();
        if (value.title().isEmpty()) {
            errors.put("title", "给这个日子起个名字吧");
        } else if (value.title().length() > 100) {
            errors.put("title", "标题不能超过 100 字");
        }
        if (!AnniversaryDates.isValidDate(value.date())) {
            errors.put("date", "请选择 1000–9999 年间的有效公历日期");
        }
        if (value.note().length() > 1000) {
            errors.put("note", "备注不能超过 1000 字");
        }
        return Collections.unmodifiableMap(errors);
    }

    JSONObject toJson() throws JSONException {
        Map<String, String> errors = validate();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.values().iterator().next());
        }
        AnniversaryDraft value = normalized();
        return new JSONObject()
                .put("title", value.title())
                .put("date", value.date())
                .put("note", value.note())
                .put("repeatYearly", value.repeatYearly())
                .put("type", value.type().id());
    }

    /** Matches Java strip without depending on Android's newer String.strip API. */
    private static String strip(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && Character.isWhitespace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }
}
