package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

final class ProfileView {
    interface Actions {
        void bindClick(View view, Runnable action);

        void editWeatherLocation();

        void checkForUpdate();

        void editNickname();

        void editPassword();

        void bindPush();

        void openUserManagement();

        void logout();
    }

    record State(
            String displayName,
            String username,
            String roleLabel,
            String versionName,
            String weatherLocation,
            String pushBindingDetail,
            boolean showUserManagement
    ) {
        State {
            displayName = valueOr(displayName, "未登录");
            username = valueOr(username, "--");
            roleLabel = valueOr(roleLabel, "用户");
            versionName = valueOr(versionName, "");
            weatherLocation = valueOr(weatherLocation, "合肥瑶海区");
            pushBindingDetail = valueOr(pushBindingDetail, "");
        }

        private static String valueOr(String value, String fallback) {
            return value == null ? fallback : value;
        }
    }

    private final Context context;
    private final Actions actions;
    private final State state;

    ProfileView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    View build() {
        LinearLayout card = card();
        TextView displayName = Ui.text(context, state.displayName(),
                22, Ui.TEXT, Typeface.BOLD);
        displayName.setSingleLine(false);
        card.addView(displayName);
        card.addView(profileLine("账号：" + state.username()));
        card.addView(profileLine("权限：" + state.roleLabel()));
        card.addView(profileLine("版本：" + state.versionName()));
        addVerticalGap(card, 14);

        addSettingRow(card, "默认天气地区", state.weatherLocation(),
                Ui.GREEN, actions::editWeatherLocation);
        addSettingRow(card, "检测版本更新", "当前 " + state.versionName(),
                Ui.GREEN, actions::checkForUpdate);
        addSettingRow(card, "修改昵称", "需要 10 青程币",
                Ui.GOLD, actions::editNickname);
        addSettingRow(card, "修改密码", "修改后重新登录",
                Ui.GREEN, actions::editPassword);
        addSettingRow(card, "绑定推送", state.pushBindingDetail(),
                Ui.GREEN, actions::bindPush);
        if (state.showUserManagement()) {
            addSettingRow(card, "用户管理", "权限和青程币",
                    Ui.GREEN, actions::openUserManagement);
        }
        addSettingRow(card, "退出登录", "回到登录页", Ui.RED, actions::logout);
        return card;
    }

    private TextView profileLine(String value) {
        TextView line = Ui.text(context, value, 14, Ui.MUTED, Typeface.NORMAL);
        line.setSingleLine(false);
        return line;
    }

    private void addSettingRow(
            LinearLayout parent,
            String label,
            String detail,
            int accentColor,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(Ui.stroke(Color.WHITE, dp(12), Ui.LINE, dp(1)));

        TextView icon = Ui.text(context, settingIcon(label), 17, Color.WHITE, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.block(accentColor, dp(12), dp(1)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        addHorizontalGap(row, 10);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = Ui.text(context, label, 15, Ui.TEXT, Typeface.BOLD);
        labelView.setSingleLine(false);
        texts.addView(labelView);
        if (detail != null && !detail.isEmpty()) {
            TextView detailView = Ui.text(context, detail, 12, Ui.MUTED, Typeface.NORMAL);
            detailView.setSingleLine(false);
            texts.addView(detailView);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView arrow = new ImageView(context);
        Ui.setImageResource(arrow, R.drawable.ic_action_chevron);
        arrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(38)));
        row.setContentDescription(detail == null || detail.isEmpty()
                ? label : label + "，" + detail);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        parent.addView(row, params);
        actions.bindClick(row, action);
    }

    private String settingIcon(String label) {
        if (label.contains("版本")) {
            return "V";
        }
        if (label.contains("钱包")) {
            return "币";
        }
        if (label.contains("签到")) {
            return "签";
        }
        if (label.contains("天气")) {
            return "天";
        }
        if (label.contains("昵称")) {
            return "N";
        }
        if (label.contains("密码")) {
            return "P";
        }
        if (label.contains("推送")) {
            return "J";
        }
        if (label.contains("用户")) {
            return "U";
        }
        if (label.contains("退出")) {
            return "⎋";
        }
        return "•";
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(Ui.block(Ui.CARD, dp(14), dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private void addVerticalGap(LinearLayout parent, int heightDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
