package com.qingcheng.ordering;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** A short-lived snapshot around a message update, never an Activity-wide view cache. */
final class ChatViewport {
    record Snapshot(boolean followBottom, int scrollY, View anchor, int anchorOffset) { }

    static Snapshot capture(ScrollView scroll, LinearLayout messages, boolean forceBottom) {
        if (scroll == null || messages == null) return new Snapshot(false, 0, null, 0);
        int y = scroll.getScrollY();
        int viewport = Math.max(0, scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom());
        boolean nearBottom = messages.getHeight() - viewport - y <= Ui.dp(scroll.getContext(), 48);
        View anchor = null;
        for (int i = 0; i < messages.getChildCount(); i++) {
            View candidate = messages.getChildAt(i);
            if (candidate.getBottom() > y) { anchor = candidate; break; }
        }
        return new Snapshot(forceBottom || messages.getChildCount() == 0 || nearBottom,
                y, anchor, anchor == null ? 0 : anchor.getTop() - y);
    }

    static void restore(Snapshot snapshot, ScrollView scroll, LinearLayout messages, EditText input) {
        if (snapshot == null || scroll == null || messages == null) return;
        // A gesture made after this update was scheduled wins over its old position snapshot.
        if (scroll.getScrollY() != snapshot.scrollY()) return;
        if (snapshot.followBottom()) {
            FriendChatView.scrollToBottomPreservingFocus(scroll, messages, input);
        } else {
            View anchor = snapshot.anchor();
            int y = anchor != null && anchor.getParent() == messages
                    ? anchor.getTop() - snapshot.anchorOffset() : snapshot.scrollY();
            scroll.scrollTo(0, Math.max(0, y));
        }
    }

    private ChatViewport() { }
}
