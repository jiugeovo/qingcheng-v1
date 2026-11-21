package com.qingcheng.ordering;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.function.BooleanSupplier;

/** Visual feedback only: Android retains ownership of touch, click and accessibility actions. */
final class PressFeedback implements View.OnTouchListener, View.OnAttachStateChangeListener {
    static final float PRESSED_SCALE = 0.98f;
    static final long PRESS_DURATION_MS = 70L;
    static final long RELEASE_DURATION_MS = 120L;

    private final View view;
    private final BooleanSupplier animatorsEnabled;
    private final int touchSlop;
    private AnimatorSet animation;
    private boolean tracking;
    private boolean hasBaseline;
    private float downX;
    private float downY;
    private float baseScaleX;
    private float baseScaleY;

    private PressFeedback(View view, BooleanSupplier animatorsEnabled) {
        this.view = view;
        this.animatorsEnabled = animatorsEnabled;
        touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    static void bind(View view, BooleanSupplier animatorsEnabled) {
        Object previous = view.getTag(R.id.qingcheng_press_feedback);
        if (previous instanceof PressFeedback feedback) {
            feedback.restoreImmediately();
            view.removeOnAttachStateChangeListener(feedback);
        }
        PressFeedback feedback = new PressFeedback(view, animatorsEnabled);
        view.setTag(R.id.qingcheng_press_feedback, feedback);
        view.setOnTouchListener(feedback);
        view.addOnAttachStateChangeListener(feedback);
    }

    @Override
    public boolean onTouch(View ignored, MotionEvent event) {
        if (!view.isEnabled() || !animatorsEnabled.getAsBoolean()) {
            restoreImmediately();
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                if (!hasBaseline) {
                    baseScaleX = view.getScaleX();
                    baseScaleY = view.getScaleY();
                    hasBaseline = true;
                }
                downX = event.getX();
                downY = event.getY();
                tracking = true;
                animateTo(baseScaleX * PRESSED_SCALE, baseScaleY * PRESSED_SCALE,
                        PRESS_DURATION_MS, false);
            }
            case MotionEvent.ACTION_MOVE -> {
                if (tracking && (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop)) {
                    release();
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_POINTER_DOWN -> release();
            default -> { }
        }
        return false;
    }

    private void release() {
        if (!tracking) {
            return;
        }
        tracking = false;
        animateTo(baseScaleX, baseScaleY, RELEASE_DURATION_MS, true);
    }

    private void animateTo(float scaleX, float scaleY, long duration, boolean releasing) {
        cancelAnimation();
        AnimatorSet next = new AnimatorSet();
        next.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, scaleX),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, scaleY));
        next.setDuration(duration);
        next.setInterpolator(new DecelerateInterpolator());
        next.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator completed) {
                if (animation == completed) {
                    animation = null;
                    if (releasing) {
                        hasBaseline = false;
                    }
                }
            }
        });
        animation = next;
        next.start();
    }

    private void cancelAnimation() {
        if (animation != null) {
            animation.removeAllListeners();
            animation.cancel();
            animation = null;
        }
    }

    private void restoreImmediately() {
        tracking = false;
        cancelAnimation();
        if (hasBaseline) {
            view.setScaleX(baseScaleX);
            view.setScaleY(baseScaleY);
            hasBaseline = false;
        }
    }

    @Override
    public void onViewAttachedToWindow(View attached) {
        // A detached view was already reset and is safe to reuse.
    }

    @Override
    public void onViewDetachedFromWindow(View detached) {
        restoreImmediately();
    }
}
