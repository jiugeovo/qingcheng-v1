package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.widget.EditText;

import org.json.JSONObject;

final class LoveRuleEditorDialog {
    interface Actions {
        void save(Value value);
        void invalid(String message);
    }

    record Value(String content) {
    }

    record Result(AlertDialog dialog, EditText content) {
    }

    private final Activity activity;
    private final JSONObject rule;
    private final Actions actions;

    LoveRuleEditorDialog(Activity activity, JSONObject rule, Actions actions) {
        this.activity = activity;
        this.rule = rule;
        this.actions = actions;
    }

    Result show() {
        EditText content = Ui.input(activity, "守则内容");
        content.setSingleLine(false);
        content.setMinLines(3);
        content.setMaxLines(6);
        content.setGravity(Gravity.TOP | Gravity.START);
        content.setText(rule == null ? "" : rule.optString("content", ""));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(rule == null ? "新增守则" : "编辑守则")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(ignored -> submit(dialog, content));
        return new Result(dialog, content);
    }

    private void submit(AlertDialog dialog, EditText contentInput) {
        String content = contentInput.getText().toString().trim();
        if (content.isEmpty()) {
            actions.invalid("守则内容不能为空");
            dialog.dismiss();
            return;
        }
        actions.save(new Value(content));
        dialog.dismiss();
    }
}
