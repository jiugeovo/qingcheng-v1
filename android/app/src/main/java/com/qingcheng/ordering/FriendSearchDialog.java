package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONArray;

import java.util.function.Consumer;

final class FriendSearchDialog {
    record QueryResult(AlertDialog dialog, EditText query) {
    }

    record ResultsResult(AlertDialog dialog, android.view.View list) {
    }

    private final Activity activity;
    private final FriendCenterView.Actions friendActions;

    FriendSearchDialog(Activity activity, FriendCenterView.Actions friendActions) {
        this.activity = activity;
        this.friendActions = friendActions;
    }

    QueryResult showQuery(Consumer<String> search) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        EditText query = Ui.input(activity, "输入用户名或昵称");
        form.addView(query);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("添加好友")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("搜索", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            search.accept(query.getText().toString().trim());
            dialog.dismiss();
        });
        return new QueryResult(dialog, query);
    }

    ResultsResult showResults(JSONArray users) {
        android.view.View list = new FriendCenterView(activity, friendActions)
                .buildSearchResults(users);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("搜索结果")
                .setView(list)
                .setPositiveButton("关闭", null)
                .create();
        dialog.show();
        return new ResultsResult(dialog, list);
    }
}
