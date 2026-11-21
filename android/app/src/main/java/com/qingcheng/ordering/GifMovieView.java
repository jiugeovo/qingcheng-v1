package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.view.View;

final class GifMovieView extends View {
    private Movie movie;
    private long animationStart;

    GifMovieView(Context context) {
        super(context);
    }

    void setMovieResource(int resourceId) {
        movie = resourceId == 0 ? null : Movie.decodeStream(getResources().openRawResource(resourceId));
        animationStart = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (movie == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (animationStart == 0) {
            animationStart = now;
        }
        int duration = movie.duration() <= 0 ? 1000 : movie.duration();
        movie.setTime((int) ((now - animationStart) % duration));
        float scale = Math.min(getWidth() / (float) movie.width(), getHeight() / (float) movie.height());
        float left = (getWidth() - movie.width() * scale) / 2f;
        float top = (getHeight() - movie.height() * scale) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        movie.draw(canvas, 0, 0);
        canvas.restore();
        postInvalidateOnAnimation();
    }
}
