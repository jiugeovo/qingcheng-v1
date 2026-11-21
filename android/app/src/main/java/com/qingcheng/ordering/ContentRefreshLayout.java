package com.qingcheng.ordering;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** A single refresh owner, including when the page contains a nested scrolling pane. */
final class ContentRefreshLayout extends SwipeRefreshLayout {
    private float touchX;
    private float touchY;
    private final int touchSlop;
    private boolean horizontalGesture;
    private boolean verticalGesture;
    private boolean cancelledGesture;

    ContentRefreshLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setColorSchemeColors(Ui.GREEN);
        setDistanceToTriggerSync(Ui.dp(context, 70));
        setEnabled(false);
        setOnChildScrollUpCallback((parent, child) -> horizontalGesture || cancelledGesture
                || child != null
                && canScrollUpAt(child, touchX - child.getLeft(), touchY - child.getTop()));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        trackGesture(event);
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && !isRefreshing()) {
            resetPendingPull();
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        trackGesture(event);
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        trackGesture(event);
        boolean handled = super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && !isRefreshing()) {
            resetPendingPull();
        }
        return handled;
    }

    @Override
    public void onStopNestedScroll(View target) {
        boolean wasRefreshing = isRefreshing();
        super.onStopNestedScroll(target);
        if ((horizontalGesture || cancelledGesture) && !wasRefreshing) {
            // 1.1.0 completes its nested pull on stop even when the touch was cancelled.
            // Cancel that pending animation before its refresh callback can run.
            super.setRefreshing(false);
            resetPendingPull();
        }
    }

    private void trackGesture(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchX = event.getX();
            touchY = event.getY();
            horizontalGesture = false;
            verticalGesture = false;
            cancelledGesture = false;
        } else if (action == MotionEvent.ACTION_MOVE && !horizontalGesture && !verticalGesture) {
            float dx = Math.abs(event.getX() - touchX);
            float dy = Math.abs(event.getY() - touchY);
            if (dx > touchSlop || dy > touchSlop) {
                horizontalGesture = dx > dy;
                verticalGesture = !horizontalGesture;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelledGesture = true;
        }
    }

    private void resetPendingPull() {
        // SwipeRefreshLayout has no public reset for a cancelled, not-yet-refreshing pull.
        // Disabling resets its indicator; restore availability for the next gesture.
        boolean enabled = isEnabled();
        super.setEnabled(false);
        super.setEnabled(enabled);
    }

    static boolean canScrollUpAt(View view, float x, float y) {
        if (view.canScrollVertically(-1)) {
            return true;
        }
        if (view instanceof ViewGroup group) {
            for (int index = group.getChildCount() - 1; index >= 0; index--) {
                View child = group.getChildAt(index);
                float childX = x + view.getScrollX() - child.getX();
                float childY = y + view.getScrollY() - child.getY();
                if (child.getVisibility() == View.VISIBLE && childX >= 0 && childY >= 0
                        && childX < child.getWidth() && childY < child.getHeight()) {
                    return canScrollUpAt(child, childX, childY);
                }
            }
        }
        return false;
    }
}
