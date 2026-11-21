package com.qingcheng.ordering;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.util.function.BooleanSupplier;

final class MotionPolicy {
    private MotionPolicy() {
    }

    static boolean shouldTransform(boolean animatorsEnabled) {
        return animatorsEnabled;
    }

    static boolean systemAnimatorsEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return systemAnimatorsEnabled(Build.VERSION.SDK_INT, ValueAnimator::areAnimatorsEnabled);
    }

    static boolean systemAnimatorsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return systemAnimatorsEnabled();
        }
        // The platform query was added in API 26; respect the same setting on API 23–25.
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (SecurityException ignored) {
            return true;
        }
    }

    static boolean systemAnimatorsEnabled(int sdkInt, BooleanSupplier api26Check) {
        return sdkInt < Build.VERSION_CODES.O || api26Check.getAsBoolean();
    }
}
