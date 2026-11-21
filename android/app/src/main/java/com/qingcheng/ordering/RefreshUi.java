package com.qingcheng.ordering;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import android.widget.ImageView;
import android.view.View;

/** Palette for the refreshed main screens; independent of the existing Piggy theme. */
final class RefreshUi {
    static final int PAPER = Color.rgb(248, 247, 242);
    static final int WHITE = Color.rgb(255, 254, 250);
    static final int TEXT = Color.rgb(41, 61, 50);
    static final int MUTED = Color.rgb(116, 126, 115);
    static final int GREEN = Color.rgb(37, 77, 62);
    static final int SAGE = Color.rgb(233, 237, 227);
    static final int APRICOT = Color.rgb(244, 231, 206);
    static final int BLUSH = Color.rgb(241, 231, 227);
    static final int LINE = Color.rgb(228, 231, 220);

    private RefreshUi() { }

    static TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView view = Ui.text(context, value, sp, color,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setSingleLine(false);
        return view;
    }

    static GradientDrawable card(Context context, int color, int radiusDp) {
        return Ui.stroke(color, Ui.dp(context, radiusDp), LINE, Ui.dp(context, 1));
    }

    static void anniversaryArtwork(ImageView view) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        try {
            view.setImageBitmap(BitmapFactory.decodeResource(view.getResources(),
                    R.drawable.anniversary_bears_hero, options));
        } catch (Resources.NotFoundException ignored) { }
    }

    static ImageView piggyIcon(Context context) {
        ImageView image = new ImageView(context) {
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (getDrawable() == null || w == 0 || h == 0) return;
                float iw = getDrawable().getIntrinsicWidth();
                float ih = getDrawable().getIntrinsicHeight();
                // Center the visible pig, excluding the original PNG's uneven transparent margins.
                RectF subject = new RectF(iw * 236 / 1024, ih * 359 / 1024,
                        iw * 789 / 1024, ih * 735 / 1024);
                int inset = Ui.dp(context, 4);
                Matrix transform = new Matrix();
                transform.setRectToRect(subject, new RectF(inset, inset, w - inset, h - inset), Matrix.ScaleToFit.CENTER);
                setImageMatrix(transform);
            }
        };
        Ui.setImageResource(image, R.drawable.electronic_piggy);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setTag("piggy-entry-artwork");
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }
}
