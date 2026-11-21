package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Fixed date and marker slots prevent the calendar's selection/heart alignment from drifting. */
final class AnniversaryDayView extends View {
    private static final int MINT = Color.rgb(33, 115, 94);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF face = new RectF();
    private final RectF outline = new RectF();
    private final Paint.FontMetrics numberMetrics = new Paint.FontMetrics();
    private final Path heart = AnniversaryMarkerDrawable.dateHeartPath();
    private final AnniversaryMarkerDrawable cake = new AnniversaryMarkerDrawable(AnniversaryType.Marker.CAKE);
    private final AnniversaryMarkerDrawable flag = new AnniversaryMarkerDrawable(AnniversaryType.Marker.FLAG);
    private Set<AnniversaryType.Marker> markers = Collections.emptySet();
    private String number = "";
    private boolean today;
    private boolean muted;

    AnniversaryDayView(Context context) {
        super(context);
        setMinimumHeight(dp(56));
        numberPaint.setTextAlign(Paint.Align.CENTER);
        // A compact numeric grid must keep two-digit dates readable at larger system font sizes.
        numberPaint.setTextSize(Math.min(15 * getResources().getDisplayMetrics().scaledDensity, dp(18)));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setState(int dayOfMonth, Collection<AnniversaryType.Marker> markers,
                  boolean selected, boolean today, boolean muted) {
        if (dayOfMonth < 0 || dayOfMonth > 31) throw new IllegalArgumentException("Day must be between 0 and 31");
        EnumSet<AnniversaryType.Marker> copy = EnumSet.noneOf(AnniversaryType.Marker.class);
        if (markers != null) copy.addAll(markers);
        String number = dayOfMonth == 0 ? "日" : String.valueOf(dayOfMonth);
        if (this.markers.equals(copy) && this.number.equals(number)
                && isSelected() == selected && this.today == today && this.muted == muted) return;
        this.markers = Collections.unmodifiableSet(copy);
        this.number = number;
        this.today = today;
        this.muted = muted;
        setSelected(selected);
        numberPaint.setTypeface(selected || today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        invalidate();
    }

    /** Selection is a paint-only update; the fixed date and marker slots do not change. */
    void setDateSelected(boolean selected) {
        if (isSelected() == selected) return;
        setSelected(selected);
        numberPaint.setTypeface(selected || today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        invalidate();
    }

    Set<AnniversaryType.Marker> markers() { return markers; }

    /** A copy is returned so callers cannot change the component's measured visual bounds. */
    RectF dateFaceBounds() { updateFace(); return new RectF(face); }

    float numberBaseline() {
        updateFace();
        numberPaint.getFontMetrics(numberMetrics);
        return face.centerY() - (numberMetrics.ascent + numberMetrics.descent) / 2;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(dp(44), widthMeasureSpec), resolveSize(dp(56), heightMeasureSpec));
    }

    private void updateFace() {
        float width = Math.max(0, Math.min(dp(38), getWidth() - dp(6)));
        float top = (getHeight() - dp(50)) / 2f;
        face.set((getWidth() - width) / 2, top, (getWidth() + width) / 2, top + dp(34));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateFace();
        if (isSelected() || isPressed()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isSelected() ? MINT : Color.rgb(237, 243, 237));
            canvas.drawRoundRect(face, dp(11), dp(11), paint);
        }
        if (today) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(isSelected() ? 2 : 1));
            paint.setColor(isSelected() ? Color.argb(112, 255, 254, 250) : Color.argb(112, 33, 115, 94));
            outline.set(face);
            outline.inset(paint.getStrokeWidth() / 2, paint.getStrokeWidth() / 2);
            canvas.drawRoundRect(outline, dp(11), dp(11), paint);
        }
        if (isFocused()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(MINT);
            outline.set(face);
            outline.inset(-dp(2), -dp(2));
            canvas.drawRoundRect(outline, dp(12), dp(12), paint);
        }
        if (markers.contains(AnniversaryType.Marker.HEART)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.45f);
            paint.setColor(isSelected() ? Color.rgb(255, 245, 232)
                    : AnniversaryMarkerDrawable.colorFor(AnniversaryType.Marker.HEART));
            int save = canvas.save();
            float heartWidth = face.width() - dp(2);
            canvas.translate(face.centerX() - heartWidth / 2, face.top + dp(3));
            canvas.scale(heartWidth / 40f, dp(30) / 34f);
            canvas.drawPath(heart, paint);
            canvas.restoreToCount(save);
        }
        numberPaint.setColor(isSelected() ? Color.rgb(255, 254, 250) : today ? MINT
                : muted ? Color.rgb(110, 118, 111) : Ui.TEXT_PRIMARY);
        canvas.drawText(number, face.centerX(), numberBaseline(), numberPaint);
        drawLowerMarkers(canvas);
    }

    private void drawLowerMarkers(Canvas canvas) {
        boolean hasCake = markers.contains(AnniversaryType.Marker.CAKE);
        boolean hasFlag = markers.contains(AnniversaryType.Marker.FLAG);
        int count = (hasCake ? 1 : 0) + (hasFlag ? 1 : 0);
        int size = dp(16);
        int gap = dp(2);
        int left = Math.round((getWidth() - count * size - Math.max(0, count - 1) * gap) / 2f);
        int top = Math.round(face.bottom);
        if (hasCake) {
            cake.setBounds(left, top, left + size, top + size);
            cake.draw(canvas);
            left += size + gap;
        }
        if (hasFlag) {
            flag.setBounds(left, top, left + size, top + size);
            flag.draw(canvas);
        }
    }

    @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }

    @Override protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        invalidate();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(isClickable() ? Button.class.getName() : TextView.class.getName());
        info.setText(number);
    }

    private int dp(int value) { return Ui.dp(getContext(), value); }
}
