package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import java.util.function.Consumer;

final class UserCoinsDialog {
    record Result(AlertDialog dialog, EditText input) {
    }

    private final Activity activity;
    private final String initialCoins;
    private final Consumer<String> save;

    UserCoinsDialog(Activity activity, String initialCoins, Consumer<String> save) {
        this.activity = activity;
        this.initialCoins = initialCoins == null ? "0" : initialCoins;
        this.save = save;
    }

    Result show() {
        EditText input = Ui.input(activity, "青程币金额");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(initialCoins);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("修改青程币")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            String coins = input.getText().toString().trim();
            save.accept(coins);
            dialog.dismiss();
        });
        return new Result(dialog, input);
    }
}
