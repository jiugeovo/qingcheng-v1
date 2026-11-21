package com.qingcheng.ordering;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class PicnicBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient gradient;
    private int lastWidth;
    private int lastHeight;

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        if (gradient == null || lastWidth != bounds.width() || lastHeight != bounds.height()) {
            lastWidth = bounds.width();
            lastHeight = bounds.height();
            gradient = new LinearGradient(
                    0, 0, bounds.width(), bounds.height(),
                    new int[]{Color.rgb(249, 252, 248), Ui.CLOTH, Color.rgb(255, 250, 239)},
                    new float[]{0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP
            );
        }
        paint.setShader(gradient);
        canvas.drawRect(bounds, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(18, 17, 129, 112));
        for (int y = bounds.top + 22; y < bounds.bottom + 56; y += 56) {
            for (int x = bounds.left + 22; x < bounds.right + 56; x += 56) {
                canvas.drawCircle(x, y, 6, paint);
            }
        }
        paint.setColor(Color.argb(16, 238, 151, 45));
        for (int y = bounds.top; y < bounds.bottom + 56; y += 56) {
            for (int x = bounds.left; x < bounds.right + 56; x += 56) {
                canvas.drawCircle(x, y, 7, paint);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
