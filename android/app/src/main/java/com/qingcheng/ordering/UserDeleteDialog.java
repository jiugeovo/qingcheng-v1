package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import java.util.function.LongConsumer;

final class UserDeleteDialog {
    private final Activity activity;
    private final long userId;
    private final String username;
    private final LongConsumer delete;

    UserDeleteDialog(Activity activity, long userId, String username, LongConsumer delete) {
        this.activity = activity;
        this.userId = userId;
        this.username = username == null ? "" : username;
        this.delete = delete;
    }

    AlertDialog show() {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("删除用户？")
                .setMessage(username)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            delete.accept(userId);
            dialog.dismiss();
        });
        return dialog;
    }
}
