package com.qingcheng.ordering;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Short, replaceable transitions; navigation and requests never wait for an animation. */
final class ScreenTransitions {
    private ScreenTransitions() { }

    static void enter(View view) {
        reset(view);
        if (!MotionPolicy.systemAnimatorsEnabled(view.getContext())) {
            return;
        }
        view.setAlpha(0.82f);
        view.setTranslationY(Ui.dp(view.getContext(), 6));
        view.animate().alpha(1f).translationY(0f).setDuration(160)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    static void reset(View view) {
        if (view != null) {
            view.animate().cancel();
            view.setAlpha(1f);
            view.setTranslationY(0f);
        }
    }
}
