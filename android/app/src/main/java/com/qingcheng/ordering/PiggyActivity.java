package com.qingcheng.ordering;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Random;

public class PiggyActivity extends android.app.Activity {
    private static final int WARM_PAPER = Color.rgb(255, 248, 246);
    private static final int PIGGY_TEXT = Color.rgb(59, 34, 43);
    private static final int PIGGY_MUTED = Color.rgb(143, 91, 105);
    private static final int PIGGY_PINK = Color.rgb(255, 110, 145);
    private static final int PIGGY_PINK_SOFT = Color.rgb(255, 218, 225);
    private static final int MAX_FLOATERS = 5;
    private static final int SHORT_SCREEN_HEIGHT_DP = 790;
    private static final int HORIZONTAL_SCREEN_CHROME_DP = 60;
    private static final int MIN_PIGGY_SIZE_DP = 240;
    private static final int BASE_SCORE_TEXT_SP = 64;
    private static final int MIN_SCORE_TEXT_SP = 18;
    private static final long PRESS_DURATION_MS = 72L;
    private static final long RELEASE_DURATION_MS = 300L;
    private static final long SYNC_DELAY_MS = 650L;
    private static final long LOVE_TIER_ONE_MAX_MS = 1000L;
    private static final long LOVE_TIER_TWO_MAX_MS = 2000L;
    private static final float PRESSED_SCALE_X = 1.045f;
    private static final float PRESSED_SCALE_Y = 0.90f;
    private static final float PRESSED_ROTATION_X = 5.5f;
    private static final String[] BLESSINGS = {
            "今天也被软软猪猪接住了",
            "猪事顺利，坏心情退退退",
            "轻轻一敲，烦恼变小",
            "猪猪说：先别急，慢慢来",
            "摸鱼无罪，猪德有功"
    };

    private final Random random = new Random();
    private final Deque<TextView> floaters = new ArrayDeque<>();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.CHINA);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable syncRequest = this::syncPiggy;

    private SharedPreferences preferences;
    private PiggyState state;
    private PiggyCloud cloud;
    private PiggyCloud.Member cloudMember;
    private TextView syncStatus;
    private boolean active;
    private boolean syncing;
    private boolean resetting;
    private TextView scoreView;
    private TextView blessingView;
    private FrameLayout piggyButton;
    private View piggyGlow;
    private View piggyShadow;
    private ImageView piggyImage;
    private LinearLayout soundToggle;
    private View soundDot;
    private TextView soundText;
    private LinearLayout hapticsToggle;
    private View hapticsDot;
    private TextView hapticsText;
    private SoundPool soundPool;
    private int soundId;
    private int superLoveSoundId;
    private int superDuperLoveSoundId;
    private int lastSoundStream;
    private boolean soundReady;
    private boolean superLoveSoundReady;
    private boolean superDuperLoveSoundReady;
    private boolean piggyPressed;
    private long piggyPressStartedAtMs;
    private long pendingPressDurationMs;
    private Boolean animationsEnabledOverride;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        preferences = getSharedPreferences(PiggyState.PREFERENCES, Context.MODE_PRIVATE);
        state = PiggyState.load(preferences);
        cloud = PiggyCloud.shared(this);
        cloudMember = cloud.member();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        prepareSound();
        setContentView(buildScreen());
        renderState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        if (cloudMember != null) {
            if (cloud.current(cloudMember)) syncPiggy();
            else finish();
        }
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applySafeAreaPadding(root);
        root.setBackground(new PicnicBackgroundDrawable());

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, Ui.dp(this, 68)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(buildPiggyCard(), new LinearLayout.LayoutParams(-1, -1));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void applySafeAreaPadding(View root) {
        int horizontal = Ui.dp(this, 14);
        int top = Ui.dp(this, 10);
        int bottom = Ui.dp(this, 10);
        root.setPadding(horizontal, top, horizontal, bottom);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(
                        horizontal,
                        top + insets.getSystemWindowInsetTop(),
                        horizontal,
                        bottom + insets.getSystemWindowInsetBottom()
                );
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        header.setBackground(Ui.stroke(Color.argb(238, 255, 255, 252), Ui.dp(this, 22), Ui.LINE, Ui.dp(this, 1)));

        int sideButtonWidth = headerSideButtonWidthDp();
        TextView back = headerButton("返回", Ui.GREEN);
        Ui.setStartIcon(back, R.drawable.ic_action_back, 20, 0);
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, sideButtonWidth), Ui.dp(this, 44)));
        back.setContentDescription("返回智能中心");
        Ui.click(back, view -> finish());

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        titles.addView(Ui.text(this, "电子猪猪", 21, Ui.TEXT, Typeface.BOLD));
        titles.addView(Ui.text(this, "一二布布小窝里的解压角落", 12, Ui.MUTED, Typeface.BOLD));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        TextView reset = headerButton("归零", Color.rgb(112, 71, 82));
        header.addView(reset, new LinearLayout.LayoutParams(Ui.dp(this, sideButtonWidth), Ui.dp(this, 44)));
        reset.setContentDescription("归零猪德");
        Ui.click(reset, view -> confirmReset());
        return header;
    }

    private TextView headerButton(String label, int color) {
        TextView button = Ui.text(this, label, 14, color, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(Ui.dp(this, 44));
        button.setBackground(Ui.stroke(Color.WHITE, Ui.dp(this, 18), Ui.LINE, Ui.dp(this, 1)));
        return button;
    }

    private View buildPiggyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 16));
        card.setBackground(Ui.stroke(WARM_PAPER, Ui.dp(this, 24), Color.rgb(239, 215, 220), Ui.dp(this, 1)));

        TextView eyebrow = Ui.text(this, "SOFT PIGGY TAP", 11, PIGGY_MUTED, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.04f);
        card.addView(eyebrow);
        card.addView(Ui.text(this, "猪德", 14, PIGGY_MUTED, Typeface.BOLD));

        scoreView = Ui.text(this, "0", 64, PIGGY_TEXT, Typeface.BOLD);
        scoreView.setId(R.id.piggy_score);
        scoreView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        scoreView.setSingleLine(true);
        scoreView.setMinHeight(Ui.dp(this, 76));
        scoreView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(scoreView, new LinearLayout.LayoutParams(-1, -2));

        if (cloudMember != null) {
            syncStatus = Ui.text(this, "", 12, PIGGY_MUTED, Typeface.NORMAL);
            card.addView(syncStatus);
        }
        blessingView = Ui.text(this, "", 14, PIGGY_MUTED, Typeface.NORMAL);
        blessingView.setMinHeight(Ui.dp(this, 24));
        card.addView(blessingView);

        int piggySize = piggySizeDp();
        LinearLayout piggyCenter = new LinearLayout(this);
        piggyCenter.setGravity(Gravity.CENTER);
        piggyCenter.setPadding(0, Ui.dp(this, 4), 0, 0);
        piggyButton = buildPiggyButton(piggySize);
        piggyCenter.addView(piggyButton, new LinearLayout.LayoutParams(Ui.dp(this, piggySize), Ui.dp(this, piggySize)));
        card.addView(piggyCenter, new LinearLayout.LayoutParams(-1, Ui.dp(this, piggySize + 4)));

        TextView tapTitle = Ui.text(this, "轻轻敲猪猪", 19, PIGGY_TEXT, Typeface.BOLD);
        tapTitle.setGravity(Gravity.CENTER);
        card.addView(tapTitle);
        TextView tapHint = Ui.text(this, "轻点或按住猪猪，按得越久越爱你", 13, PIGGY_MUTED, Typeface.NORMAL);
        tapHint.setGravity(Gravity.CENTER);
        card.addView(tapHint);

        LinearLayout toggles = new LinearLayout(this);
        toggles.setOrientation(LinearLayout.HORIZONTAL);
        toggles.setPadding(0, Ui.dp(this, 14), 0, 0);
        toggles.addView(createFeedbackToggle("音效", true), new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        View gap = new View(this);
        toggles.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        toggles.addView(createFeedbackToggle("震动", false), new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        card.addView(toggles);
        return card;
    }

    private FrameLayout buildPiggyButton(int sizeDp) {
        FrameLayout button = new FrameLayout(this);
        button.setId(R.id.piggy_button);
        button.setClickable(true);
        button.setFocusable(true);
        button.setClipChildren(false);
        button.setClipToPadding(false);

        piggyGlow = new View(this);
        piggyGlow.setId(R.id.piggy_glow);
        piggyGlow.setBackground(Ui.round(Color.rgb(255, 222, 228), Ui.dp(this, 999)));
        piggyGlow.setAlpha(0.72f);
        piggyGlow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(
                Ui.dp(this, Math.round(sizeDp * 0.80f)),
                Ui.dp(this, Math.round(sizeDp * 0.71f)),
                Gravity.CENTER
        );
        glowParams.topMargin = Ui.dp(this, Math.round(sizeDp * 0.06f));
        button.addView(piggyGlow, glowParams);

        piggyShadow = new View(this);
        piggyShadow.setId(R.id.piggy_shadow);
        GradientDrawable shadow = new GradientDrawable();
        shadow.setShape(GradientDrawable.OVAL);
        shadow.setColor(Color.rgb(93, 48, 61));
        piggyShadow.setBackground(shadow);
        piggyShadow.setAlpha(0.20f);
        piggyShadow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams shadowParams = new FrameLayout.LayoutParams(
                Ui.dp(this, Math.round(sizeDp * 0.45f)),
                Ui.dp(this, Math.max(18, Math.round(sizeDp * 0.065f))),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        shadowParams.bottomMargin = Ui.dp(this, Math.round(sizeDp * 0.255f));
        button.addView(piggyShadow, shadowParams);

        piggyImage = new ImageView(this);
        piggyImage.setId(R.id.piggy_image);
        piggyImage.setImageResource(R.drawable.electronic_piggy);
        piggyImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        piggyImage.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        piggyImage.setCameraDistance(Ui.dp(this, sizeDp) * 8f);
        piggyImage.setPivotX(Ui.dp(this, sizeDp) * 0.50f);
        piggyImage.setPivotY(Ui.dp(this, sizeDp) * 0.72f);
        button.addView(piggyImage, new FrameLayout.LayoutParams(-1, -1));

        setButtonAccessibility(button);
        button.setOnClickListener(view -> tapPiggy());
        button.setOnTouchListener((view, event) -> handlePiggyTouch(event));
        return button;
    }

    private boolean handlePiggyTouch(MotionEvent event) {
        if (piggyButton == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                piggyPressed = true;
                piggyPressStartedAtMs = event.getEventTime();
                piggyButton.setPressed(true);
                showPiggyPressedState();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                boolean inside = isPointInside(event.getX(), event.getY(), piggyButton);
                if (inside != piggyPressed) {
                    piggyPressed = inside;
                    piggyButton.setPressed(inside);
                    if (inside) {
                        piggyPressStartedAtMs = event.getEventTime();
                        showPiggyPressedState();
                    } else {
                        pendingPressDurationMs = 0L;
                        releasePiggy(false);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                boolean confirm = piggyPressed && isPointInside(event.getX(), event.getY(), piggyButton);
                pendingPressDurationMs = confirm
                        ? Math.max(0L, event.getEventTime() - piggyPressStartedAtMs)
                        : 0L;
                piggyPressed = false;
                piggyButton.setPressed(false);
                releasePiggy(confirm);
                if (confirm) {
                    piggyButton.performClick();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                piggyPressed = false;
                pendingPressDurationMs = 0L;
                piggyButton.setPressed(false);
                releasePiggy(false);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean isPointInside(float x, float y, View view) {
        return x >= 0f && x <= view.getWidth() && y >= 0f && y <= view.getHeight();
    }

    private void showPiggyPressedState() {
        cancelPiggyStageAnimations();
        if (!animationsEnabled()) {
            if (piggyImage != null) {
                piggyImage.setAlpha(0.78f);
            }
            if (piggyGlow != null) {
                piggyGlow.setAlpha(0.92f);
            }
            return;
        }
        if (piggyImage != null) {
            piggyImage.animate()
                    .scaleX(PRESSED_SCALE_X)
                    .scaleY(PRESSED_SCALE_Y)
                    .translationY(Ui.dp(this, 10))
                    .rotationX(PRESSED_ROTATION_X)
                    .setDuration(PRESS_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
        if (piggyShadow != null) {
            piggyShadow.animate()
                    .scaleX(1.16f)
                    .scaleY(0.82f)
                    .alpha(0.31f)
                    .translationY(Ui.dp(this, 4))
                    .setDuration(PRESS_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
        if (piggyGlow != null) {
            piggyGlow.animate()
                    .scaleX(0.94f)
                    .scaleY(0.90f)
                    .alpha(0.86f)
                    .setDuration(PRESS_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
    }

    private void releasePiggy(boolean confirmed) {
        cancelPiggyStageAnimations();
        if (!animationsEnabled()) {
            resetPiggyStageImmediately();
            return;
        }
        if (piggyImage != null) {
            piggyImage.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .rotationX(0f)
                    .alpha(1f)
                    .setDuration(RELEASE_DURATION_MS)
                    .setInterpolator(new OvershootInterpolator(confirmed ? 1.55f : 0.9f))
                    .start();
        }
        if (piggyShadow != null) {
            piggyShadow.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(0.20f)
                    .translationY(0f)
                    .setDuration(RELEASE_DURATION_MS)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
        }
        if (piggyGlow != null) {
            piggyGlow.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(0.72f)
                    .setDuration(RELEASE_DURATION_MS)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
        }
    }

    private void cancelPiggyStageAnimations() {
        if (piggyImage != null) {
            piggyImage.animate().cancel();
        }
        if (piggyShadow != null) {
            piggyShadow.animate().cancel();
        }
        if (piggyGlow != null) {
            piggyGlow.animate().cancel();
        }
    }

    private void resetPiggyStageImmediately() {
        if (piggyImage != null) {
            piggyImage.setScaleX(1f);
            piggyImage.setScaleY(1f);
            piggyImage.setTranslationY(0f);
            piggyImage.setRotationX(0f);
            piggyImage.setAlpha(1f);
        }
        if (piggyShadow != null) {
            piggyShadow.setScaleX(1f);
            piggyShadow.setScaleY(1f);
            piggyShadow.setTranslationY(0f);
            piggyShadow.setAlpha(0.20f);
        }
        if (piggyGlow != null) {
            piggyGlow.setScaleX(1f);
            piggyGlow.setScaleY(1f);
            piggyGlow.setAlpha(0.72f);
        }
    }

    private LinearLayout createFeedbackToggle(String label, boolean sound) {
        LinearLayout toggle = new LinearLayout(this);
        toggle.setGravity(Gravity.CENTER);
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);

        View dot = new View(this);
        toggle.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12)));

        TextView text = Ui.text(this, label, 15, PIGGY_TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.setMargins(Ui.dp(this, 9), 0, 0, 0);
        toggle.addView(text, textParams);

        if (sound) {
            soundToggle = toggle;
            toggle.setId(R.id.piggy_sound_toggle);
            soundDot = dot;
            soundText = text;
            toggle.setOnClickListener(view -> toggleSound());
        } else {
            hapticsToggle = toggle;
            toggle.setId(R.id.piggy_haptics_toggle);
            hapticsDot = dot;
            hapticsText = text;
            toggle.setOnClickListener(view -> toggleHaptics());
        }
        return toggle;
    }

    private void tapPiggy() {
        if (resetting) return;
        if (cloudMember == null) {
            state = state.tapped();
            state.save(preferences);
        } else {
            if (!cloud.tap(cloudMember)) return;
            scheduleSync(SYNC_DELAY_MS);
        }
        renderState();
        addFloater();
        playFeedback();
    }

    private void scheduleSync(long delay) {
        main.removeCallbacks(syncRequest);
        if (active) main.postDelayed(syncRequest, delay);
    }

    private void syncPiggy() {
        if (!active || syncing || resetting || !cloud.current(cloudMember)) return;
        syncing = true;
        cloud.refresh(cloudMember, ok -> {
            syncing = false;
            if (!active) return;
            renderState();
            if (cloud.state(cloudMember).pending()) scheduleSync(ok ? SYNC_DELAY_MS : PiggyCloud.RETRY_DELAY_MS);
        });
    }

    private void addFloater() {
        if (piggyButton == null) {
            return;
        }
        while (floaters.size() >= MAX_FLOATERS) {
            TextView oldest = floaters.removeFirst();
            Object tag = oldest.getTag();
            if (tag instanceof Animator animator) {
                animator.cancel();
            }
            piggyButton.removeView(oldest);
        }

        TextView floater = Ui.text(this, "+1", 28, PIGGY_PINK, Typeface.BOLD);
        floater.setGravity(Gravity.CENTER);
        floater.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int width = Ui.dp(this, 64);
        int height = Ui.dp(this, 42);
        int left = Math.max(0, piggyButton.getWidth() / 2 - width / 2 + Ui.dp(this, random.nextInt(49) - 24));
        int top = Math.round(piggyButton.getHeight() * 0.49f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        piggyButton.addView(floater, params);
        floaters.addLast(floater);

        if (!animationsEnabled()) {
            floater.animate().alpha(0f).setDuration(360L).withEndAction(() -> removeFloater(floater)).start();
            return;
        }

        ObjectAnimator rise = ObjectAnimator.ofFloat(floater, View.TRANSLATION_Y,
                Ui.dp(this, 8), -Ui.dp(this, 92));
        ObjectAnimator drift = ObjectAnimator.ofFloat(floater, View.TRANSLATION_X,
                0f, Ui.dp(this, random.nextInt(25) - 12));
        ObjectAnimator fade = ObjectAnimator.ofFloat(floater, View.ALPHA, 0f, 1f, 1f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(rise, drift, fade);
        set.setDuration(720L);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeFloater(floater);
            }
        });
        floater.setTag(set);
        set.start();
    }

    private void removeFloater(TextView floater) {
        floaters.remove(floater);
        if (piggyButton != null) {
            piggyButton.removeView(floater);
        }
    }

    static int loveTierForDuration(long durationMs) {
        long safeDurationMs = Math.max(0L, durationMs);
        if (safeDurationMs < LOVE_TIER_ONE_MAX_MS) {
            return 1;
        }
        if (safeDurationMs < LOVE_TIER_TWO_MAX_MS) {
            return 2;
        }
        return 3;
    }

    private void playFeedback() {
        int tier = loveTierForDuration(pendingPressDurationMs);
        int selectedSoundId = soundId;
        boolean selectedSoundReady = soundReady;
        if (tier == 2) {
            selectedSoundId = superLoveSoundId;
            selectedSoundReady = superLoveSoundReady;
        } else if (tier == 3) {
            selectedSoundId = superDuperLoveSoundId;
            selectedSoundReady = superDuperLoveSoundReady;
        }
        if (!selectedSoundReady && soundReady) {
            selectedSoundId = soundId;
            selectedSoundReady = true;
        }
        pendingPressDurationMs = 0L;
        if (state.soundEnabled && selectedSoundReady && soundPool != null) {
            try {
                if (lastSoundStream != 0) {
                    soundPool.stop(lastSoundStream);
                }
                lastSoundStream = soundPool.play(
                        selectedSoundId, 0.42f, 0.42f, 1, 0, 1.0f);
            } catch (RuntimeException ignored) {
                lastSoundStream = 0;
            }
        }
        if (state.hapticsEnabled && vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(14L, 62));
                } else {
                    vibrator.vibrate(14L);
                }
            } catch (RuntimeException ignored) {
                // Feedback failures must never block tapping.
            }
        }
    }

    private void toggleSound() {
        state = state.withSoundEnabled(!state.soundEnabled);
        state.saveFeedback(preferences);
        if (!state.soundEnabled && soundPool != null) {
            soundPool.autoPause();
            if (lastSoundStream != 0) {
                soundPool.stop(lastSoundStream);
                lastSoundStream = 0;
            }
        }
        renderState();
    }

    private void toggleHaptics() {
        state = state.withHapticsEnabled(!state.hapticsEnabled);
        state.saveFeedback(preferences);
        if (!state.hapticsEnabled && vibrator != null) {
            vibrator.cancel();
        }
        renderState();
    }

    private void confirmReset() {
        if (resetting) return;
        new AlertDialog.Builder(this)
                .setTitle("归零猪德？")
                .setMessage(cloudMember == null ? "已经积攒的猪德会清空，但音效和震动设置会保留。"
                        : "当前账号的猪德会在所有设备上清空，需要联网确认。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认归零", (dialog, which) -> resetPiggy())
                .show();
    }

    private void resetPiggy() {
        if (cloudMember == null) {
            state = state.resetCount();
            state.save(preferences);
            renderState();
            return;
        }
        resetting = true;
        main.removeCallbacks(syncRequest);
        renderState();
        cloud.reset(cloudMember, ok -> {
            resetting = false;
            if (!active) return;
            if (!ok) Ui.toast(this, "归零未确认，请联网后重试");
            renderState();
            if (cloud.state(cloudMember).pending()) scheduleSync(SYNC_DELAY_MS);
        });
    }

    private void renderState() {
        PiggyCloud.State progress = cloudMember == null ? null : cloud.state(cloudMember);
        long count = progress == null ? state.count : progress.visible();
        if (scoreView != null) {
            renderScore(numberFormat.format(count));
        }
        if (syncStatus != null && progress != null) {
            syncStatus.setText(resetting ? "正在归零" : progress.pending() || progress.revision() < 0 ? "待同步" : "已同步");
        }
        if (blessingView != null) {
            blessingView.setText(BLESSINGS[(int) (count % BLESSINGS.length)]);
        }
        if (piggyButton != null) {
            piggyButton.setContentDescription("轻敲猪猪，当前猪德 " + numberFormat.format(count) + "，轻点加一");
        }
        styleToggle(soundToggle, soundDot, soundText, "音效", state.soundEnabled);
        styleToggle(hapticsToggle, hapticsDot, hapticsText, "震动", state.hapticsEnabled);
    }

    private void renderScore(String value) {
        scoreView.setText(value);
        scoreView.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SCORE_TEXT_SP);

        int availableWidth = scoreView.getWidth();
        if (availableWidth <= 0) {
            availableWidth = getResources().getDisplayMetrics().widthPixels
                    - Ui.dp(this, HORIZONTAL_SCREEN_CHROME_DP);
        }
        availableWidth = Math.max(Ui.dp(this, 120), availableWidth);
        float measuredWidth = scoreView.getPaint().measureText(value);
        if (measuredWidth > availableWidth) {
            float fittedSizePx = scoreView.getTextSize() * availableWidth / measuredWidth * 0.98f;
            scoreView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    Math.max(TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_SP,
                            MIN_SCORE_TEXT_SP,
                            getResources().getDisplayMetrics()
                    ), fittedSizePx)
            );
        }
    }

    private void styleToggle(LinearLayout toggle, View dot, TextView text, String label, boolean enabled) {
        if (toggle == null || dot == null || text == null) {
            return;
        }
        toggle.setBackground(Ui.stroke(
                enabled ? PIGGY_PINK_SOFT : Color.WHITE,
                Ui.dp(this, 24),
                Color.rgb(238, 190, 200),
                Ui.dp(this, 1)
        ));
        dot.setBackground(Ui.round(enabled ? PIGGY_PINK : Color.rgb(197, 164, 172), Ui.dp(this, 99)));
        text.setTextColor(enabled ? PIGGY_TEXT : Color.rgb(128, 87, 97));
        toggle.setContentDescription(label + "，" + (enabled ? "已开启" : "已关闭"));
        setSwitchAccessibility(toggle, enabled);
    }

    private void prepareSound() {
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    // Piggy's voice feedback should follow the media volume stream,
                    // rather than the system notification/sonification stream.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setAudioAttributes(attributes)
                    .setMaxStreams(4)
                    .build();
            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                boolean ready = status == 0;
                if (sampleId == soundId) {
                    soundReady = ready;
                } else if (sampleId == superLoveSoundId) {
                    superLoveSoundReady = ready;
                } else if (sampleId == superDuperLoveSoundId) {
                    superDuperLoveSoundReady = ready;
                }
            });
            soundId = soundPool.load(this, R.raw.piggy_boop, 1);
            superLoveSoundId = soundPool.load(this, R.raw.piggy_chao_ai_ni, 1);
            superDuperLoveSoundId = soundPool.load(this, R.raw.piggy_chao_ji_ai_ni, 1);
        } catch (RuntimeException ignored) {
            soundPool = null;
            soundReady = false;
            superLoveSoundReady = false;
            superDuperLoveSoundReady = false;
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(255, 243, 223));
        window.setNavigationBarColor(Color.rgb(255, 243, 223));
        int flags = window.getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    boolean animationsEnabled() {
        return animationsEnabledOverride != null
                ? animationsEnabledOverride
                : MotionPolicy.systemAnimatorsEnabled();
    }

    private int piggySizeDp() {
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        int heightDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        return calculatePiggySizeDp(widthDp, heightDp);
    }

    static int calculatePiggySizeDp(int widthDp, int heightDp) {
        int maximum = heightDp < SHORT_SCREEN_HEIGHT_DP ? 300 : 350;
        return Math.min(maximum, Math.max(MIN_PIGGY_SIZE_DP,
                widthDp - HORIZONTAL_SCREEN_CHROME_DP));
    }

    private int headerSideButtonWidthDp() {
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        return widthDp < 390 ? 64 : 68;
    }

    private void setButtonAccessibility(View view) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
                info.setClickable(true);
            }
        });
    }

    private void setSwitchAccessibility(View view, boolean checked) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Switch");
                info.setCheckable(true);
                info.setChecked(checked);
            }
        });
    }

    @Override
    protected void onPause() {
        active = false;
        main.removeCallbacks(syncRequest);
        if (cloudMember == null) {
            state.save(preferences);
        } else if (!resetting && cloud.state(cloudMember).pending()) {
            cloud.refresh(cloudMember, null);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        piggyPressed = false;
        cancelPiggyStageAnimations();
        for (TextView floater : new ArrayDeque<>(floaters)) {
            Object tag = floater.getTag();
            if (tag instanceof Animator animator) {
                animator.cancel();
            }
        }
        floaters.clear();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        super.onDestroy();
    }
}
