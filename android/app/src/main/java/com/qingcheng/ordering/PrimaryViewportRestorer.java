package com.qingcheng.ordering;

import android.view.View;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

import java.util.function.BooleanSupplier;

/** One live shell binding. A pending restore never wins over a new touch gesture. */
final class PrimaryViewportRestorer implements AutoCloseable {
    private final ScrollView scroll;
    private long gestureSequence;
    private boolean gestureActive;
    private ViewTreeObserver.OnPreDrawListener pending;
    private int pendingY;

    PrimaryViewportRestorer(ScrollView scroll) {
        this.scroll = scroll;
    }

    void onTouchDown() {
        onTouch(MotionEvent.ACTION_DOWN);
    }

    void onTouch(int action) {
        if (action == MotionEvent.ACTION_DOWN) gestureActive = true;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) gestureActive = false;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            gestureSequence++;
            cancel();
        }
    }

    long gestureSequence() { return gestureSequence; }

    int captureY() { return pending == null ? scroll.getScrollY() : pendingY; }

    void restore(int y, long expectedGesture, BooleanSupplier current) {
        cancel();
        if (gestureActive || gestureSequence != expectedGesture || !current.getAsBoolean()) return;
        pendingY = Math.max(0, y);
        pending = () -> {
            if (gestureActive || gestureSequence != expectedGesture || !current.getAsBoolean()
                    || !scroll.isAttachedToWindow()) {
                cancel();
                return true;
            }
            View child = scroll.getChildCount() == 0 ? null : scroll.getChildAt(0);
            if (child == null || scroll.getHeight() == 0 || scroll.isLayoutRequested()
                    || child.isLayoutRequested()) return true;
            int nextY = pendingY;
            cancel();
            scroll.scrollTo(0, nextY);
            return true;
        };
        scroll.getViewTreeObserver().addOnPreDrawListener(pending);
        scroll.invalidate();
    }

    void cancel() {
        if (pending != null && scroll.getViewTreeObserver().isAlive()) {
            scroll.getViewTreeObserver().removeOnPreDrawListener(pending);
        }
        pending = null;
    }

    @Override public void close() {
        cancel();
    }
}
