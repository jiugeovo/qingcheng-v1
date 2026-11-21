package com.qingcheng.ordering;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Only stores a model ID; selection is isolated by app user ID, independent of authentication tokens. */
final class AiModelPreferences {
    private final SharedPreferences preferences;
    AiModelPreferences(Context context) { preferences = context.getSharedPreferences("qingcheng_ai_models", Context.MODE_PRIVATE); }

    String selected(JSONObject user) {
        String key = key(user);
        String value = key == null ? AiModels.DEFAULT_MODEL : preferences.getString(key, AiModels.DEFAULT_MODEL);
        return AiModels.valid(value) ? value : AiModels.DEFAULT_MODEL;
    }

    void select(JSONObject user, String model) {
        String key = key(user);
        if (key != null && AiModels.valid(model)) preferences.edit().putString(key, model).apply();
    }

    private String key(JSONObject user) {
        long id = user == null ? 0 : user.optLong("id", 0);
        return id > 0 ? "user_" + id : null;
    }
}
