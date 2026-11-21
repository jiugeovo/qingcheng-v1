package com.qingcheng.ordering;

import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Keeps history data available while creating bubbles and requesting images one page at a time. */
final class AiMessageHistory {
    static final int PAGE_SIZE = 40;
    static final String LOAD_EARLIER_TAG = "ai-load-earlier";

    interface BubbleFactory {
        View create(String role, String content);
    }

    interface ActionBinder {
        void bind(View view, Runnable action);
    }

    private record Message(String role, String content) { }

    private final LinearLayout target;
    private final List<Message> messages;
    private final BubbleFactory bubbles;
    private final TextView loadEarlier;
    private int firstVisible;
    private boolean loading;

    AiMessageHistory(LinearLayout target, JSONArray source, BubbleFactory bubbles, ActionBinder binder) {
        this.target = target;
        this.messages = snapshot(source);
        this.bubbles = bubbles;
        this.firstVisible = messages.size();
        this.loadEarlier = Ui.text(target.getContext(), "", 13, Ui.GREEN, Typeface.BOLD);
        loadEarlier.setTag(LOAD_EARLIER_TAG);
        loadEarlier.setGravity(Gravity.CENTER);
        loadEarlier.setPadding(0, Ui.dp(target.getContext(), 8), 0, Ui.dp(target.getContext(), 8));
        loadEarlier.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        Ui.ensureTouchTarget(loadEarlier);
        binder.bind(loadEarlier, this::loadEarlierPage);
    }

    void render() {
        suppressLayout(true);
        try {
            target.removeAllViews();
            if (messages.size() > PAGE_SIZE) target.addView(loadEarlier);
            prependPage();
        } finally {
            suppressLayout(false);
        }
    }

    private void loadEarlierPage() {
        // Removed controls cannot apply an old conversation's history to a reused container.
        if (loading || firstVisible == 0 || loadEarlier.getParent() != target) return;
        loading = true;
        // Keep focusability stable: re-enabling a control during layout can steal focus and scroll.
        loadEarlier.setAlpha(0.6f);
        ScrollView scroll = target.getParent() instanceof ScrollView parent ? parent : null;
        ChatViewport.Snapshot position = readingPosition(scroll);
        if (position != null) {
            target.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    target.removeOnLayoutChangeListener(this);
                    if (target.getParent() == scroll && position.anchor().getParent() == target) {
                        ChatViewport.restore(position, scroll, target, null);
                    }
                    finishLoading();
                }
            });
        }
        suppressLayout(true);
        try {
            prependPage();
        } finally {
            suppressLayout(false);
            if (position == null) finishLoading();
        }
    }

    private void prependPage() {
        int nextFirst = Math.max(0, firstVisible - PAGE_SIZE);
        int insertion = loadEarlier.getParent() == target ? 1 : 0;
        for (int index = nextFirst; index < firstVisible; index++) {
            Message message = messages.get(index);
            target.addView(bubbles.create(message.role(), message.content()), insertion++);
        }
        firstVisible = nextFirst;
        if (firstVisible == 0) {
            target.removeView(loadEarlier);
        } else {
            loadEarlier.setText("加载更早消息（还有 " + firstVisible + " 条）");
            loadEarlier.setContentDescription(loadEarlier.getText());
        }
    }

    private ChatViewport.Snapshot readingPosition(ScrollView scroll) {
        if (scroll == null || scroll.getHeight() == 0 || target.getHeight() == 0) return null;
        int scrollY = scroll.getScrollY();
        for (int index = 0; index < target.getChildCount(); index++) {
            View candidate = target.getChildAt(index);
            if (candidate != loadEarlier && candidate.getBottom() > scrollY) {
                // Explicitly loading older history never follows the bottom, even for a short page.
                return new ChatViewport.Snapshot(false, scrollY, candidate, candidate.getTop() - scrollY);
            }
        }
        return null;
    }

    private void finishLoading() {
        loading = false;
        loadEarlier.setAlpha(1f);
    }

    private void suppressLayout(boolean suppress) {
        if (Build.VERSION.SDK_INT >= 29) target.suppressLayout(suppress);
    }

    private static List<Message> snapshot(JSONArray source) {
        List<Message> result = new ArrayList<>();
        if (source != null) {
            for (int index = 0; index < source.length(); index++) {
                JSONObject message = source.optJSONObject(index);
                if (message != null) {
                    result.add(new Message(message.optString("role", "assistant"),
                            message.optString("content", "")));
                }
            }
        }
        return List.copyOf(result);
    }
}
