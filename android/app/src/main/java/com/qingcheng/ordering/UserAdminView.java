package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class UserAdminView {
    enum Mode {
        COINS,
        ROLE_DELETE
    }

    record User(long id, String displayName, String username, String role, String coins) {
        static User from(JSONObject value) {
            String username = value == null ? "" : value.optString("username", "");
            String nickname = value == null ? "" : value.optString("nickname", "").trim();
            return new User(
                    value == null ? 0L : value.optLong("id"),
                    nickname.isEmpty() ? username : nickname,
                    username,
                    value == null ? "USER" : value.optString("role", "USER"),
                    value == null ? "0" : value.optString("qingchengCoins", "0")
            );
        }
    }

    interface Actions {
        void bindClick(View view, Runnable action);

        void back();

        void openCoins();

        void openRoleDelete();

        void editCoins(User user);

        void editRole(User user);

        void delete(User user);
    }

    record Result(View root, LinearLayout list) {
    }

    private final Context context;
    private final Actions actions;

    UserAdminView(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
    }

    View buildLanding() {
        LinearLayout root = root();
        root.addView(backButton("返回我的"));

        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        View coins = tool("修改用户余额", "调整用户青程币", Ui.GOLD, "coins");
        View roleDelete = tool("权限与删除", "管理权限、删除用户", Ui.GREEN,
                "role-delete");
        LinearLayout.LayoutParams toolParams = new LinearLayout.LayoutParams(0, dp(118), 1);
        tools.addView(coins, toolParams);
        tools.addView(horizontalSpace(10));
        tools.addView(roleDelete, new LinearLayout.LayoutParams(0, dp(118), 1));
        root.addView(tools, bottomMargin(10));

        actions.bindClick(coins, actions::openCoins);
        actions.bindClick(roleDelete, actions::openRoleDelete);
        return root;
    }

    Result buildList(Mode mode) {
        LinearLayout root = root();
        root.addView(backButton("返回用户管理"));
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        return new Result(root, list);
    }

    void render(LinearLayout list, JSONArray users, Mode mode) {
        list.removeAllViews();
        if (users == null || users.length() == 0) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER);
            empty.addView(Ui.text(context, "暂无用户", 15, Ui.MUTED, Typeface.NORMAL));
            list.addView(empty);
            return;
        }
        for (int index = 0; index < users.length(); index++) {
            JSONObject value = users.optJSONObject(index);
            if (value != null) {
                addUser(list, User.from(value), mode);
            }
        }
    }

    private void addUser(LinearLayout parent, User user, Mode mode) {
        LinearLayout card = card();
        TextView name = Ui.text(context, user.displayName(), 18, Ui.TEXT, Typeface.BOLD);
        name.setSingleLine(false);
        card.addView(name);
        TextView detail = Ui.text(context,
                user.username() + " · " + DisplayFormatters.role(user.role())
                        + " · " + user.coins() + " 青程币",
                14, Ui.MUTED, Typeface.NORMAL);
        detail.setSingleLine(false);
        card.addView(detail);

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.RIGHT);
        controls.setPadding(0, dp(10), 0, 0);
        if (mode == Mode.COINS) {
            TextView coins = pill("修改余额", Ui.GOLD, "coins:" + user.id());
            controls.addView(coins);
            actions.bindClick(coins, () -> actions.editCoins(user));
        } else {
            TextView role = pill("权限", Ui.GREEN, "role:" + user.id());
            TextView delete = pill("删除", Ui.RED, "delete:" + user.id());
            controls.addView(role);
            controls.addView(horizontalSpace(8));
            controls.addView(delete);
            actions.bindClick(role, () -> actions.editRole(user));
            actions.bindClick(delete, () -> actions.delete(user));
        }
        card.addView(controls);
        parent.addView(card);
    }

    private TextView backButton(String label) {
        TextView back = pill(label, Ui.GREEN, "back");
        Ui.setStartIcon(back, R.drawable.ic_action_back, 22, 3);
        back.setContentDescription(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, 0, 0, dp(10));
        back.setLayoutParams(params);
        actions.bindClick(back, actions::back);
        return back;
    }

    private View tool(String title, String detail, int accent, String tag) {
        LinearLayout tool = new LinearLayout(context);
        tool.setOrientation(LinearLayout.VERTICAL);
        tool.setGravity(Gravity.BOTTOM);
        tool.setPadding(dp(14), dp(14), dp(14), dp(14));
        int tint = accent == Ui.GREEN
                ? Color.rgb(238, 250, 244) : Color.rgb(255, 247, 230);
        tool.setBackground(Ui.stroke(tint, dp(18), Ui.LINE, dp(1)));
        tool.addView(Ui.text(context, title, 18, Ui.TEXT, Typeface.BOLD));
        Space gap = new Space(context);
        tool.addView(gap, new LinearLayout.LayoutParams(1, dp(6)));
        TextView detailView = Ui.text(context, detail, 12, Ui.MUTED, Typeface.BOLD);
        detailView.setSingleLine(false);
        tool.addView(detailView);
        tool.setTag(tag);
        tool.setContentDescription(title + "，" + detail);
        return tool;
    }

    private TextView pill(String label, int color, String tag) {
        TextView view = Ui.text(context, label, 13, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(Ui.block(color, dp(11), dp(1)));
        view.setSingleLine(true);
        view.setTag(tag);
        Ui.ensureTouchTarget(view);
        return view;
    }

    private LinearLayout root() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        return root;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(Ui.block(Ui.CARD, dp(14), dp(1)));
        card.setLayoutParams(bottomMargin(12));
        return card;
    }

    private Space horizontalSpace(int width) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
        return space;
    }

    private LinearLayout.LayoutParams bottomMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(margin));
        return params;
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
