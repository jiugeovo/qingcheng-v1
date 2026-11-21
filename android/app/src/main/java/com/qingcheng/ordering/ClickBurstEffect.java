package com.qingcheng.ordering;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class ClickBurstEffect {
    static final int PARTICLE_COUNT = 5;
    static final long MIN_INTERVAL_MS = 700L;

    private final Random random;
    private final Consumer<AnimatorSet> animationStarter;
    private final BooleanSupplier animatorsEnabled;
    private final LongSupplier clock;
    private final List<ImageView> activeParticles = new ArrayList<>(PARTICLE_COUNT);
    private final View.OnAttachStateChangeListener attachmentListener =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    cancel();
                }
            };
    private FrameLayout activeFrame;
    private AnimatorSet activeAnimation;
    private boolean emittedBefore;
    private long lastEmission;

    ClickBurstEffect() {
        this(new Random(), AnimatorSet::start);
    }

    ClickBurstEffect(Random random, Consumer<AnimatorSet> animationStarter) {
        this(random, animationStarter, null, SystemClock::uptimeMillis);
    }

    ClickBurstEffect(Random random, Consumer<AnimatorSet> animationStarter,
                     BooleanSupplier animatorsEnabled, LongSupplier clock) {
        this.random = random;
        this.animationStarter = animationStarter;
        this.animatorsEnabled = animatorsEnabled;
        this.clock = clock;
    }

    int emit(Context context, FrameLayout frame, View anchor) {
        boolean motionEnabled = animatorsEnabled == null
                ? MotionPolicy.systemAnimatorsEnabled(context) : animatorsEnabled.getAsBoolean();
        if (!motionEnabled) {
            cancel();
            return 0;
        }
        if (frame == null || anchor == null || frame.getWindowToken() == null
                || anchor.getWindowToken() != frame.getWindowToken()) {
            return 0;
        }
        long now = clock.getAsLong();
        if (activeAnimation != null || (emittedBefore && now - lastEmission < MIN_INTERVAL_MS)) {
            return 0;
        }
        int[] frameLocation = new int[2];
        int[] anchorLocation = new int[2];
        frame.getLocationOnScreen(frameLocation);
        anchor.getLocationOnScreen(anchorLocation);
        float originX = anchorLocation[0] - frameLocation[0] + anchor.getWidth() / 2f;
        float originY = anchorLocation[1] - frameLocation[1] + anchor.getHeight() / 2f;

        List<Animator> particles = new ArrayList<>(PARTICLE_COUNT);
        activeFrame = frame;
        frame.addOnAttachStateChangeListener(attachmentListener);
        for (int index = 0; index < PARTICLE_COUNT; index++) {
            ImageView particle = particle(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(context, 34), dp(context, 34));
            params.leftMargin = Math.round(originX - dp(context, 17));
            params.topMargin = Math.round(originY - dp(context, 17));
            frame.addView(particle, params);
            activeParticles.add(particle);

            float angle = (float) ((Math.PI * 2 * index / PARTICLE_COUNT)
                    + random.nextFloat() * 0.5f);
            float distance = dp(context, 18 + random.nextInt(26));
            particles.add(animation(context, particle, angle, distance,
                    440 + random.nextInt(100)));
        }
        AnimatorSet burst = new AnimatorSet();
        burst.playTogether(particles);
        burst.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finish(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                finish(animation);
            }
        });
        emittedBefore = true;
        lastEmission = now;
        activeAnimation = burst;
        try {
            animationStarter.accept(burst);
        } catch (RuntimeException failure) {
            cancel();
            throw failure;
        }
        return PARTICLE_COUNT;
    }

    void cancel() {
        AnimatorSet animation = activeAnimation;
        if (animation != null) {
            animation.removeAllListeners();
            animation.cancel();
        }
        cleanup();
    }

    private void finish(Animator animation) {
        if (activeAnimation == animation) {
            cleanup();
        }
    }

    private void cleanup() {
        if (activeFrame != null) {
            activeFrame.removeOnAttachStateChangeListener(attachmentListener);
            for (ImageView particle : activeParticles) {
                if (particle.getParent() == activeFrame) {
                    activeFrame.removeView(particle);
                }
            }
        }
        activeParticles.clear();
        activeFrame = null;
        activeAnimation = null;
    }

    private ImageView particle(Context context) {
        ImageView particle = new ImageView(context);
        String resource = random.nextBoolean() ? "click_yier" : "click_bubu";
        particle.setImageResource(context.getResources().getIdentifier(
                resource, "drawable", context.getPackageName()));
        particle.setScaleType(ImageView.ScaleType.CENTER_CROP);
        particle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        particle.setClickable(false);
        particle.setFocusable(false);
        particle.setAlpha(0.95f);
        particle.setBackground(Ui.stroke(
                Color.WHITE, dp(context, 999), Ui.LINE, dp(context, 1)));
        return particle;
    }

    private AnimatorSet animation(
            Context context,
            ImageView particle,
            float angle,
            float distance,
            long duration
    ) {
        ObjectAnimator moveX = ObjectAnimator.ofFloat(
                particle, "translationX", 0f, (float) Math.cos(angle) * distance);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(
                particle, "translationY", 0f,
                (float) Math.sin(angle) * distance - dp(context, 24));
        ObjectAnimator alpha = ObjectAnimator.ofFloat(particle, "alpha", 0.95f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(particle, "scaleX", 0.82f, 1.08f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(particle, "scaleY", 0.82f, 1.08f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(moveX, moveY, alpha, scaleX, scaleY);
        set.setDuration(duration);
        return set;
    }

    private int dp(Context context, int value) {
        return Ui.dp(context, value);
    }
}
