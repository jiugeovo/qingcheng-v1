package com.qingcheng.ordering;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** The same vector vocabulary is used by the calendar, legend and type picker. */
final class AnniversaryMarkerDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path;
    private final int color;
    private int alpha = 255;

    AnniversaryMarkerDrawable(AnniversaryType.Marker marker) {
        color = colorFor(marker);
        path = iconPath(marker);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1.6f);
    }

    static int colorFor(AnniversaryType.Marker marker) {
        return switch (marker) {
            case HEART -> Color.rgb(175, 91, 100);
            case CAKE -> Color.rgb(155, 98, 60);
            case FLAG -> Color.rgb(33, 115, 94);
        };
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float size = Math.min(bounds.width(), bounds.height());
        int save = canvas.save();
        canvas.translate(bounds.centerX() - size / 2, bounds.centerY() - size / 2);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(color);
        paint.setAlpha(alpha);
        canvas.drawPath(path, paint);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    static Path dateHeartPath() {
        Path path = new Path();
        path.moveTo(20, 6);
        path.cubicTo(14, -2, 2, 0, 2, 11);
        path.cubicTo(2, 20, 13, 28, 20, 32);
        path.cubicTo(27, 28, 38, 20, 38, 11);
        path.cubicTo(38, 0, 26, -2, 20, 6);
        path.close();
        return path;
    }

    private static Path iconPath(AnniversaryType.Marker marker) {
        Path path = new Path();
        switch (marker) {
            case HEART -> {
                path.moveTo(20, 5);
                path.cubicTo(17, 2, 14, 4, 12, 6);
                path.cubicTo(10, 4, 7, 2, 4, 5);
                path.cubicTo(-1, 10, 7, 16, 12, 20);
                path.cubicTo(17, 16, 25, 10, 20, 5);
                path.close();
            }
            case CAKE -> {
                path.addRoundRect(new RectF(4, 12, 20, 21), 1, 1, Path.Direction.CW);
                path.moveTo(4, 16);
                path.cubicTo(6, 19, 8, 13, 10, 16);
                path.cubicTo(12, 19, 14, 13, 16, 16);
                path.cubicTo(18, 19, 20, 16, 20, 16);
                path.moveTo(12, 8); path.lineTo(12, 12);
                path.moveTo(3, 21); path.lineTo(21, 21);
                path.moveTo(12, 3);
                path.cubicTo(12, 3, 9, 6, 11, 7);
                path.cubicTo(14, 8, 13, 4, 12, 3);
                path.close();
            }
            case FLAG -> {
                path.moveTo(6, 21); path.lineTo(6, 4);
                path.cubicTo(10, 1, 14, 7, 19, 4);
                path.lineTo(19, 14);
                path.cubicTo(14, 17, 10, 11, 6, 14);
            }
        }
        return path;
    }
}
