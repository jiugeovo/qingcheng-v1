package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ImageView;

final class CommonDialogs {
    private CommonDialogs() {
    }

    static AlertDialog message(
            Activity activity,
            String title,
            String message,
            String buttonLabel
    ) {
        return new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(buttonLabel, null)
                .show();
    }

    static AlertDialog confirm(
            Activity activity,
            String title,
            String message,
            Runnable action
    ) {
        return new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (ignored, which) -> action.run())
                .show();
    }

    static AlertDialog updateAvailable(
            Activity activity,
            String versionName,
            String notes,
            boolean forceUpdate,
            Runnable download
    ) {
        String message = notes == null || notes.isEmpty()
                ? "可以下载最新版安装。" : notes;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("发现新版本 " + versionName)
                .setMessage(message)
                .setPositiveButton("去下载", null);
        if (!forceUpdate) {
            builder.setNegativeButton("稍后", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!forceUpdate);
        dialog.setCanceledOnTouchOutside(!forceUpdate);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            download.run();
            if (!forceUpdate) {
                dialog.dismiss();
            }
        });
        return dialog;
    }

    static AlertDialog latestVersion(Activity activity, String currentVersion) {
        return message(activity, "已经是最新版", "当前版本 " + currentVersion, "好");
    }

    static AlertDialog pushBindingFailure(Activity activity, String message) {
        String detail = message == null || message.isEmpty() ? "请稍后再试" : message;
        return message(activity, "绑定推送失败", detail, "知道了");
    }

    static AlertDialog imagePreview(Activity activity, ImageView image) {
        return new AlertDialog.Builder(activity)
                .setTitle("查看图片")
                .setView(image)
                .setPositiveButton("关闭", null)
                .show();
    }
}
