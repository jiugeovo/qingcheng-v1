package com.qingcheng.ordering;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import java.util.function.BooleanSupplier;

final class StartupSplashController {
    static final int PAPER_COLOR = Color.rgb(229, 238, 236);
    static final long HOLD_MS = 1720L;
    static final long REDUCED_MOTION_HOLD_MS = 280L;
    static final long FADE_MS = 220L;

    record Result(FrameLayout root, LinearLayout mark, ImageView brandIcon) {
    }

    private final Activity activity;
    private final Handler handler;
    private final Runnable finishedAction;
    private final BooleanSupplier animationsEnabled;
    private final Runnable scheduledDismiss = this::dismiss;

    private View splash;
    private boolean completed;
    private boolean destroyed;

    StartupSplashController(Activity activity, Handler handler, Runnable finishedAction) {
        this(activity, handler, finishedAction, MotionPolicy::systemAnimatorsEnabled);
    }

    StartupSplashController(
            Activity activity,
            Handler handler,
            Runnable finishedAction,
            BooleanSupplier animationsEnabled
    ) {
        this.activity = activity;
        this.handler = handler;
        this.finishedAction = finishedAction;
        this.animationsEnabled = animationsEnabled;
    }

    Result show() {
        completed = false;
        destroyed = false;
        handler.removeCallbacks(scheduledDismiss);
        configureSystemBars();

        Result result = build();
        splash = result.root();
        activity.setContentView(result.root());
        boolean animate = animationsEnabled.getAsBoolean();
        if (animate) {
            result.mark().animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(120L)
                    .setDuration(520L)
                    .start();
        } else {
            result.mark().setAlpha(1f);
            result.mark().setTranslationY(0f);
        }
        handler.postDelayed(scheduledDismiss, animate ? HOLD_MS : REDUCED_MOTION_HOLD_MS);
        return result;
    }

    void dismiss() {
        if (completed || destroyed) {
            return;
        }
        handler.removeCallbacks(scheduledDismiss);
        View current = splash;
        if (current == null || current.getWindowToken() == null) {
            finish(current);
            return;
        }
        current.animate().cancel();
        current.animate()
                .alpha(0f)
                .setDuration(animationsEnabled.getAsBoolean() ? FADE_MS : 0L)
                .withEndAction(() -> finish(current))
                .start();
    }

    void destroy() {
        destroyed = true;
        handler.removeCallbacks(scheduledDismiss);
        if (splash != null) {
            splash.animate().cancel();
            splash = null;
        }
    }

    private Result build() {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(PAPER_COLOR);
        root.addView(new QingchengSplashView(activity), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout mark = new LinearLayout(activity);
        mark.setVisibility(View.GONE);
        mark.setOrientation(LinearLayout.VERTICAL);
        mark.setGravity(Gravity.CENTER);
        mark.setAlpha(0f);
        mark.setTranslationY(dp(8));
        mark.setPadding(dp(28), dp(28), dp(28), dp(28));

        ImageView brandIcon = new ImageView(activity);
        brandIcon.setImageResource(activity.getResources().getIdentifier(
                "site_icon", "drawable", activity.getPackageName()));
        brandIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        brandIcon.setBackground(Ui.stroke(
                Color.argb(236, 249, 251, 247), dp(24),
                Color.argb(132, 75, 103, 101), dp(1)));
        brandIcon.setPadding(dp(5), dp(5), dp(5), dp(5));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            brandIcon.setElevation(dp(3));
            brandIcon.setClipToOutline(true);
        }
        mark.addView(brandIcon, new LinearLayout.LayoutParams(dp(88), dp(88)));

        addGap(mark, 18);
        TextView appName = Ui.text(activity, appName(),
                26, Color.rgb(33, 48, 48), Typeface.BOLD);
        appName.setGravity(Gravity.CENTER);
        mark.addView(appName);

        addGap(mark, 5);
        TextView seal = Ui.text(activity, "QINGCHENG",
                11, Color.rgb(91, 122, 119), Typeface.BOLD);
        seal.setGravity(Gravity.CENTER);
        mark.addView(seal);

        root.addView(mark, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        return new Result(root, mark, brandIcon);
    }

    private void configureSystemBars() {
        Window window = activity.getWindow();
        window.setStatusBarColor(PAPER_COLOR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(PAPER_COLOR);
        }
        int flags = window.getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void finish(View current) {
        if (completed || destroyed) {
            return;
        }
        completed = true;
        if (splash == current) {
            splash = null;
        }
        finishedAction.run();
    }

    private void addGap(LinearLayout layout, int heightDp) {
        layout.addView(new Space(activity), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private String appName() {
        try {
            return activity.getString(R.string.app_name);
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return "青程点菜";
        }
    }

    private int dp(int value) {
        return Ui.dp(activity, value);
    }
}
