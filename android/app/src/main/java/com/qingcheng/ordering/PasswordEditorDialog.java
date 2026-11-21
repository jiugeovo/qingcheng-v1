package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;

final class PasswordEditorDialog {
    interface Actions {
        void save(Value value);

        void invalid(String message);
    }

    record Value(String oldPassword, String newPassword) {
    }

    record Result(
            AlertDialog dialog,
            EditText oldPassword,
            EditText newPassword,
            EditText confirmPassword
    ) {
    }

    private final Activity activity;
    private final Actions actions;

    PasswordEditorDialog(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
    }

    Result show() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        EditText oldPassword = passwordInput("原密码");
        EditText newPassword = passwordInput("新密码（至少6位）");
        EditText confirmPassword = passwordInput("确认新密码");
        form.addView(oldPassword);
        addGap(form, 8);
        form.addView(newPassword);
        addGap(form, 8);
        form.addView(confirmPassword);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("修改密码")
                .setMessage("修改成功后需要重新登录。")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认修改", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored ->
                submit(dialog, oldPassword, newPassword, confirmPassword));
        return new Result(dialog, oldPassword, newPassword, confirmPassword);
    }

    private void submit(
            AlertDialog dialog,
            EditText oldPasswordInput,
            EditText newPasswordInput,
            EditText confirmPasswordInput
    ) {
        String newPassword = trimmed(newPasswordInput);
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            actions.invalid("新密码长度需要在 6 到 64 位之间");
            dialog.dismiss();
            return;
        }
        if (!newPassword.equals(trimmed(confirmPasswordInput))) {
            actions.invalid("两次输入的新密码不一致");
            dialog.dismiss();
            return;
        }
        actions.save(new Value(oldPasswordInput.getText().toString(), newPassword));
        dialog.dismiss();
    }

    private EditText passwordInput(String hint) {
        EditText input = Ui.input(activity, hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(activity, 46)));
        return input;
    }

    private void addGap(LinearLayout layout, int heightDp) {
        layout.addView(new Space(activity),
                new LinearLayout.LayoutParams(1, Ui.dp(activity, heightDp)));
    }

    private static String trimmed(EditText input) {
        return input.getText().toString().trim();
    }
}
