package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;

import java.util.function.Consumer;

final class DefaultWeatherLocationDialog {
    record Result(AlertDialog dialog, EditText input) {
    }

    private final Activity activity;
    private final String initialLocation;
    private final Consumer<String> save;

    DefaultWeatherLocationDialog(
            Activity activity,
            String initialLocation,
            Consumer<String> save
    ) {
        this.activity = activity;
        this.initialLocation = initialLocation == null ? "" : initialLocation;
        this.save = save;
    }

    Result show() {
        EditText input = Ui.input(activity, "默认天气地区");
        input.setText(initialLocation);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("默认天气地区")
                .setMessage("首页和天气页会优先展示这个地区。初次未设置时默认合肥瑶海区。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            save.accept(input.getText().toString().trim());
            dialog.dismiss();
        });
        return new Result(dialog, input);
    }
}
