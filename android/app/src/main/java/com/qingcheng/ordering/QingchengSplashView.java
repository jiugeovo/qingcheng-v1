package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class QingchengSplashView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF imageRect = new RectF();

    private Bitmap splashBitmap;

    QingchengSplashView(Context context) {
        this(context, new AnnualSplashSelector());
    }

    QingchengSplashView(Context context, AnnualSplashSelector splashSelector) {
        super(context);
        setWillNotDraw(false);
        int splashResource = splashSelector.drawableResource();
        try {
            splashBitmap = BitmapFactory.decodeResource(getResources(), splashResource);
        } catch (android.content.res.Resources.NotFoundException ignored) {
            splashBitmap = null;
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateImageRect(width, height);
    }

    private void updateImageRect(int width, int height) {
        if (splashBitmap == null || splashBitmap.getWidth() <= 0 || splashBitmap.getHeight() <= 0) {
            imageRect.set(0f, 0f, width, height);
            return;
        }
        float scale = Math.max(width / (float) splashBitmap.getWidth(), height / (float) splashBitmap.getHeight());
        float imageWidth = splashBitmap.getWidth() * scale;
        float imageHeight = splashBitmap.getHeight() * scale;
        float left = (width - imageWidth) * 0.5f;
        float top = (height - imageHeight) * 0.5f;
        imageRect.set(left, top, left + imageWidth, top + imageHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (splashBitmap == null) {
            canvas.drawColor(Color.rgb(239, 250, 247));
            return;
        }
        canvas.drawBitmap(splashBitmap, null, imageRect, imagePaint);
    }
}
