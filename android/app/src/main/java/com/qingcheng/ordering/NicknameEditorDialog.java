package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;

final class NicknameEditorDialog {
    interface Actions {
        void save(String nickname);

        void invalid(String message);
    }

    record Result(AlertDialog dialog, EditText input) {
    }

    private final Activity activity;
    private final String initialNickname;
    private final Actions actions;

    NicknameEditorDialog(Activity activity, String initialNickname, Actions actions) {
        this.activity = activity;
        this.initialNickname = initialNickname == null ? "" : initialNickname;
        this.actions = actions;
    }

    Result show() {
        EditText input = Ui.input(activity, "新昵称（2-20个字）");
        input.setText(initialNickname);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("修改昵称")
                .setMessage("确认后扣除 10 青程币。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认修改", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(ignored -> submit(dialog, input));
        return new Result(dialog, input);
    }

    private void submit(AlertDialog dialog, EditText input) {
        String nickname = input.getText().toString().trim();
        if (nickname.length() < 2 || nickname.length() > 20) {
            actions.invalid("昵称长度需要在 2 到 20 个字之间");
            dialog.dismiss();
            return;
        }
        actions.save(nickname);
        dialog.dismiss();
    }
}
