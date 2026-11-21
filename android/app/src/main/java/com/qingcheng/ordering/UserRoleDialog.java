package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.function.Consumer;

final class UserRoleDialog {
    private static final String[] ROLES = {"USER", "ADMIN", "SUPER_ADMIN"};

    private final Activity activity;
    private final Consumer<String> selection;

    UserRoleDialog(Activity activity, Consumer<String> selection) {
        this.activity = activity;
        this.selection = selection;
    }

    AlertDialog show() {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("修改权限")
                .setItems(ROLES, (ignored, which) -> select(which))
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        return dialog;
    }

    void select(int index) {
        if (index >= 0 && index < ROLES.length) {
            selection.accept(ROLES[index]);
        }
    }
}
