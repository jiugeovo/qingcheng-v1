package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import org.json.JSONObject;

final class LoveRuleDeleteDialog {
    interface Actions {
        void delete(long ruleId);
    }

    private final Activity activity;
    private final JSONObject rule;
    private final Actions actions;

    LoveRuleDeleteDialog(Activity activity, JSONObject rule, Actions actions) {
        this.activity = activity;
        this.rule = rule;
        this.actions = actions;
    }

    AlertDialog show() {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("删除守则？")
                .setMessage(rule == null ? "" : rule.optString("content", ""))
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            if (rule != null) {
                actions.delete(rule.optLong("id"));
            }
            dialog.dismiss();
        });
        return dialog;
    }
}
