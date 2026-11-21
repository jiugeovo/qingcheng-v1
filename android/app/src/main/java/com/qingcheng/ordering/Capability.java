package com.qingcheng.ordering;

import org.json.JSONException;
import org.json.JSONObject;

/** Discoverable product capability exposed by the backend registry. */
record Capability(String id, String name, String description, String category,
                  String route, boolean executable) {
    static Capability fromJson(JSONObject json) throws JSONException {
        if (json == null) {
            throw new JSONException("capability item is null");
        }
        String id = required(json, "id");
        String name = required(json, "name");
        String description = required(json, "description");
        String category = required(json, "category");
        String route = required(json, "route");
        boolean executable = false;
        if (json.has("executable")) {
            Object value = json.get("executable");
            if (!(value instanceof Boolean)) {
                throw new JSONException("capability field must be boolean: executable");
            }
            executable = (Boolean) value;
        }
        return new Capability(id, name, description, category, route, executable);
    }

    private static String required(JSONObject json, String key) throws JSONException {
        Object raw = json.opt(key);
        if (!(raw instanceof String)) {
            throw new JSONException("capability field must be string: " + key);
        }
        String value = ((String) raw).trim();
        if (value.isEmpty()) {
            throw new JSONException("capability field is missing: " + key);
        }
        return value;
    }
}
