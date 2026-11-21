package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

final class LoginView {
    interface Actions {
        void bindClick(View view, Runnable action);

        void login(Result result, Draft draft);

        void register(Result result, Draft draft);
    }

    record Draft(String username, String password, String nickname, String phone) {
    }

    record Result(
            LinearLayout root,
            EditText username,
            EditText password,
            EditText nickname,
            EditText phone,
            TextView login,
            TextView register
    ) {
        Draft snapshot() {
            return new Draft(
                    text(username), text(password), text(nickname), text(phone));
        }

        private static String text(EditText input) {
            return input.getText().toString().trim();
        }
    }

    private final Context context;
    private final Actions actions;

    LoginView(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
    }

    Result build() {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(16));
        page.setBackgroundColor(RefreshUi.PAPER);

        page.addView(hero());
        LinearLayout form = card();
        form.setPadding(dp(16), dp(16), dp(16), dp(16));
        boolean registration = !BuildConfig.COUPLE_LOCAL;
        if (BuildConfig.COUPLE_TEST) {
            page.addView(Ui.text(context, "开发者测试区", 18, RefreshUi.TEXT, Typeface.BOLD));
        }
        EditText username = input(BuildConfig.COUPLE_TEST ? "开发者账号" : "账号", false);
        EditText password = input("密码", true);
        EditText nickname = input("昵称（注册时填写）", false);
        EditText phone = input("手机号（可空）", false);
        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView login = actionButton("登录", RefreshUi.GREEN);
        TextView register = actionButton("注册", RefreshUi.GREEN);
        buttonRow.addView(login, buttonParams());
        if (registration) buttonRow.addView(register, buttonParams());

        form.addView(username);
        gap(form, 9);
        form.addView(password);
        if (registration) {
            gap(form, 9);
            form.addView(nickname);
            gap(form, 9);
            form.addView(phone);
        }
        gap(form, 14);
        form.addView(buttonRow);
        page.addView(form, new LinearLayout.LayoutParams(-1, -2));

        Result result = new Result(
                page, username, password, nickname, phone, login, register);
        actions.bindClick(login, () -> actions.login(result, result.snapshot()));
        if (registration) actions.bindClick(register, () -> actions.register(result, result.snapshot()));
        return result;
    }

    private LinearLayout hero() {
        LinearLayout hero = card();
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(122));
        params.setMargins(0, 0, 0, dp(12));
        hero.setLayoutParams(params);
        hero.setPadding(dp(14), dp(14), dp(14), dp(14));
        hero.setMinimumHeight(0);
        hero.setMinimumWidth(0);
        hero.setBackground(Ui.round(RefreshUi.APRICOT, dp(24)));

        ImageView brandIcon = new ImageView(context);
        brandIcon.setTag("app-avatar");
        brandIcon.setImageResource(context.getResources().getIdentifier(
                "click_bubu", "drawable", context.getPackageName()));
        brandIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        iconParams.setMargins(0, 0, dp(14), 0);
        hero.addView(brandIcon, iconParams);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(context, "青程点菜", 26, RefreshUi.TEXT, Typeface.BOLD));
        gap(copy, 5);
        copy.addView(Ui.text(context, "一二布布风格的点菜小窝",
                12, RefreshUi.MUTED, Typeface.NORMAL));
        hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return hero;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private Drawable heroBackground() {
        int resourceId = context.getResources().getIdentifier(
                "hero_bg", "drawable", context.getPackageName());
        if (resourceId == 0) {
            return Ui.stroke(Color.rgb(77, 55, 48), dp(22), Ui.LINE, dp(3));
        }
        BitmapDrawable image = (BitmapDrawable) context.getResources().getDrawable(resourceId);
        image.setGravity(Gravity.CENTER);
        image.setTileModeX(null);
        image.setTileModeY(null);
        image.setDither(true);
        GradientDrawable overlay = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(178, 75, 31, 24), Color.argb(46, 75, 31, 24)});
        GradientDrawable stroke = Ui.stroke(
                Color.TRANSPARENT, dp(22), Ui.LINE, dp(3));
        return new LayerDrawable(new android.graphics.drawable.Drawable[]{
                image, overlay, stroke
        });
    }

    private EditText input(String hint, boolean password) {
        EditText input = Ui.input(context, hint);
        input.setBackground(RefreshUi.card(context, RefreshUi.PAPER, 14));
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        input.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(48)));
        return input;
    }

    private TextView actionButton(String label, int color) {
        TextView button = Ui.text(context, label, 14, Color.WHITE, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(13), dp(8), dp(13), dp(8));
        button.setBackground(Ui.round(color, dp(14)));
        Ui.ensureTouchTarget(button);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private void gap(LinearLayout parent, int heightDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
