package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

record AiModels(String defaultModel, List<String> ids) {
    static final String DEFAULT_MODEL = "gpt-5.6-sol";
    AiModels { ids = List.copyOf(ids); }

    static AiModels parse(JSONObject data) throws JSONException {
        JSONArray models = data.getJSONArray("models");
        if (models.length() > 1000) throw new JSONException("模型列表过大");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < models.length(); i++) {
            String id = models.getJSONObject(i).optString("id", "");
            if (valid(id)) ids.add(id);
        }
        if (ids.isEmpty()) throw new JSONException("暂时没有可选模型，请稍后刷新");
        String defaultModel = data.optString("defaultModel", DEFAULT_MODEL);
        return new AiModels(valid(defaultModel) ? defaultModel : DEFAULT_MODEL, new ArrayList<>(ids));
    }

    static boolean valid(String id) {
        return id != null && id.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}");
    }

    String[] labels() {
        return ids.stream().map(id -> id.equals(defaultModel) ? id + "（默认）" : id).toArray(String[]::new);
    }
}
