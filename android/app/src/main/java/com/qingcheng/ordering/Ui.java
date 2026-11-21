package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.BooleanSupplier;

final class Ui {
    static final int SURFACE_BASE = Color.rgb(250, 248, 241);
    static final int SURFACE_RAISED = Color.rgb(255, 254, 250);
    static final int SURFACE_MINT = Color.rgb(228, 245, 239);
    static final int SURFACE_PEACH = Color.rgb(255, 240, 223);
    static final int SURFACE_BLUSH = Color.rgb(253, 233, 236);
    static final int TEXT_PRIMARY = Color.rgb(36, 51, 46);
    static final int TEXT_SECONDARY = Color.rgb(102, 115, 109);
    static final int ACCENT_MINT = Color.rgb(19, 138, 116);
    static final int ACCENT_CORAL = Color.rgb(242, 139, 98);
    static final int ACCENT_PINK = Color.rgb(217, 95, 118);
    static final int DANGER = Color.rgb(201, 59, 67);
    static final int MIN_TOUCH_DP = 48;

    // Legacy aliases keep existing screens compiling while they migrate to semantic names.
    static final int INK = TEXT_PRIMARY;
    static final int TEXT = TEXT_PRIMARY;
    static final int MUTED = TEXT_SECONDARY;
    static final int BG = SURFACE_BASE;
    static final int CLOTH = SURFACE_MINT;
    static final int PATCH = SURFACE_PEACH;
    static final int CARD = SURFACE_RAISED;
    static final int LINE = Color.rgb(215, 228, 219);
    static final int GREEN = ACCENT_MINT;
    static final int RED = DANGER;
    static final int CORAL = ACCENT_CORAL;
    static final int GOLD = Color.rgb(238, 151, 45);
    static final int SHADOW = Color.argb(28, 36, 92, 78);

    private Ui() {
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context context, String value, int sp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    static Button button(Context context, String label, int color) {
        Button button = baseButton(context, label);
        button.setTextColor(Color.WHITE);
        button.setBackground(pressedBlock(color, dp(context, 18), dp(context, 2)));
        return button;
    }

    static Button primaryButton(Context context, String label) {
        return button(context, label, ACCENT_MINT);
    }

    static Button secondaryButton(Context context, String label) {
        Button button = baseButton(context, label);
        button.setTextColor(TEXT_PRIMARY);
        button.setBackground(stroke(SURFACE_PEACH, dp(context, 18), ACCENT_CORAL, dp(context, 1)));
        return button;
    }

    static Button dangerButton(Context context, String label) {
        return button(context, label, DANGER);
    }

    private static Button baseButton(Context context, String label) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        ensureTouchTarget(button);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dp(context, 1));
        }
        return button;
    }

    static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setPadding(dp(context, 13), 0, dp(context, 13), 0);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_SECONDARY);
        input.setBackground(stroke(SURFACE_RAISED, dp(context, 12), LINE, dp(context, 1)));
        ensureTouchTarget(input);
        return input;
    }

    static GradientDrawable surface(Context context, int color) {
        return stroke(color, dp(context, 18), LINE, dp(context, 1));
    }

    static View ensureTouchTarget(View view) {
        int minimum = dp(view.getContext(), MIN_TOUCH_DP);
        view.setMinimumWidth(Math.max(view.getMinimumWidth(), minimum));
        view.setMinimumHeight(Math.max(view.getMinimumHeight(), minimum));
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (params.width >= 0 && params.width < minimum) {
                params.width = minimum;
            }
            if (params.height >= 0 && params.height < minimum) {
                params.height = minimum;
            }
            view.setLayoutParams(params);
        }
        return view;
    }

    static GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static GradientDrawable stroke(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = round(color, radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    static LayerDrawable block(int color, int radius, int strokeWidth) {
        GradientDrawable shadow = round(SHADOW, radius);
        GradientDrawable face = stroke(color, radius, LINE, strokeWidth);
        LayerDrawable layer = new LayerDrawable(new android.graphics.drawable.Drawable[]{shadow, face});
        layer.setLayerInset(0, 0, 4, 0, 0);
        layer.setLayerInset(1, 0, 0, 0, 4);
        return layer;
    }

    static LayerDrawable pressedBlock(int color, int radius, int strokeWidth) {
        return block(color, radius, strokeWidth);
    }

    static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    static void setImageResource(ImageView view, int resourceId) {
        try {
            view.setImageResource(resourceId);
        } catch (android.content.res.Resources.NotFoundException ignored) {
            // Resource-less Robolectric tests still exercise layout and behavior.
        }
    }

    static void setStartIcon(TextView view, int resourceId, int sizeDp, int paddingDp) {
        Drawable icon;
        try {
            icon = view.getContext().getDrawable(resourceId);
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return;
        }
        if (icon != null) {
            int size = dp(view.getContext(), sizeDp);
            icon.setBounds(0, 0, size, size);
            view.setCompoundDrawables(icon, null, null, null);
        }
        view.setCompoundDrawablePadding(dp(view.getContext(), paddingDp));
    }

    static void setTopIcon(TextView view, int resourceId, int sizeDp) {
        Drawable icon;
        try {
            icon = view.getContext().getDrawable(resourceId);
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return;
        }
        if (icon != null) {
            int size = dp(view.getContext(), sizeDp);
            icon.setBounds(0, 0, size, size);
            view.setCompoundDrawables(null, icon, null, null);
        }
    }

    static void click(View view, View.OnClickListener listener) {
        click(view, listener, (View.OnClickListener) null);
    }

    static void click(View view, View.OnClickListener listener, View.OnClickListener effect) {
        click(view, listener, effect, () -> MotionPolicy.systemAnimatorsEnabled(view.getContext()));
    }

    static void click(View view, View.OnClickListener listener, BooleanSupplier animatorsEnabled) {
        click(view, listener, null, animatorsEnabled);
    }

    private static void click(View view, View.OnClickListener listener, View.OnClickListener effect,
                              BooleanSupplier animatorsEnabled) {
        ensureTouchTarget(view);
        PressFeedback.bind(view, animatorsEnabled);
        view.setOnClickListener(v -> {
            if (effect != null && animatorsEnabled.getAsBoolean()) {
                effect.onClick(v);
            }
            listener.onClick(v);
        });
    }
}
