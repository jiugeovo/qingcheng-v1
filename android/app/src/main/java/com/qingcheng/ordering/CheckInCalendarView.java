package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

final class CheckInCalendarView extends View {
    private JSONArray days;
    private String monthLabel = "";
    private String dateLabel = "";
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    CheckInCalendarView(Context context) {
        super(context);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Ui.dp(context, 1));
        linePaint.setColor(Color.argb(128, 236, 188, 116));
        linePaint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 3), Ui.dp(context, 4)}, 0));
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(Ui.dp(context, 1));
        dashPaint.setColor(Color.argb(170, 240, 181, 104));
        dashPaint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 6), Ui.dp(context, 5)}, 0));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    void setData(JSONArray days, String monthLabel, String dateLabel) {
        this.days = days;
        this.monthLabel = monthLabel == null ? "" : monthLabel;
        this.dateLabel = dateLabel == null ? "" : dateLabel;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float cardLeft = Ui.dp(getContext(), 6);
        float cardTop = Ui.dp(getContext(), 70);
        float cardRight = width - Ui.dp(getContext(), 6);
        float cardBottom = height - Ui.dp(getContext(), 4);
        rect.set(cardLeft, cardTop, cardRight, cardBottom);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(226, 255, 249, 229));
        canvas.drawRoundRect(rect, Ui.dp(getContext(), 24), Ui.dp(getContext(), 24), fillPaint);
        canvas.drawRoundRect(rect, Ui.dp(getContext(), 24), Ui.dp(getContext(), 24), dashPaint);

        float gridLeft = cardLeft + Ui.dp(getContext(), 18);
        float gridRight = cardRight - Ui.dp(getContext(), 18);
        float weekTop = cardTop + Ui.dp(getContext(), 34);
        float gridTop = weekTop + Ui.dp(getContext(), 32);
        float gridBottom = cardBottom - Ui.dp(getContext(), 30);
        float cellWidth = (gridRight - gridLeft) / 7f;
        float cellHeight = (gridBottom - gridTop) / 6f;

        drawWeekdays(canvas, gridLeft, weekTop, cellWidth);
        drawCalendarGrid(canvas, gridLeft, gridTop, gridRight, gridBottom, cellWidth, cellHeight);
        float tagLeft = width * 0.16f;
        float tagTop = Ui.dp(getContext(), 2);
        float tagRight = width * 0.84f;
        float tagBottom = Ui.dp(getContext(), 94);

        for (int i = 0; i < 42; i++) {
            int row = i / 7;
            int column = i % 7;
            float left = gridLeft + column * cellWidth;
            float top = gridTop + row * cellHeight;
            float dayRight = left + cellWidth;
            float dayBottom = top + cellHeight;
            JSONObject day = days == null || i >= days.length() ? null : days.optJSONObject(i);
            boolean currentMonth = day != null && day.optBoolean("currentMonth", false);
            boolean today = day != null && day.optBoolean("today", false);
            boolean checked = day != null && day.optBoolean("checked", false);

            if (today) {
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(Color.argb(180, 255, 230, 175));
                canvas.drawCircle(left + cellWidth / 2f, top + cellHeight * 0.48f, Math.min(cellWidth, cellHeight) * 0.31f, fillPaint);
            }

            if (day != null) {
                int dayOfMonth = day.optInt("dayOfMonth", 0);
                if (dayOfMonth > 0) {
                    textPaint.setTypeface(Typeface.DEFAULT_BOLD);
                    textPaint.setTextSize(Ui.dp(getContext(), 18));
                    boolean weekend = column == 0 || column == 6;
                    textPaint.setColor(checked ? Ui.GREEN : today ? Ui.GOLD : weekend && currentMonth ? Color.rgb(233, 96, 99) : currentMonth ? Color.rgb(101, 61, 34) : Color.argb(58, 101, 61, 34));
                    Paint.FontMetrics metrics = textPaint.getFontMetrics();
                    float baseY = top + cellHeight * (checked ? 0.38f : 0.5f) - (metrics.ascent + metrics.descent) / 2f;
                    canvas.drawText(String.valueOf(dayOfMonth), left + cellWidth / 2f, baseY, textPaint);
                    if (checked) {
                        textPaint.setTextSize(Ui.dp(getContext(), 15));
                        Paint.FontMetrics checkMetrics = textPaint.getFontMetrics();
                        float checkY = top + cellHeight * 0.68f - (checkMetrics.ascent + checkMetrics.descent) / 2f;
                        canvas.drawText("√", left + cellWidth / 2f, checkY, textPaint);
                    }
                }
            }
        }
        drawMonthTag(canvas, tagLeft, tagTop, tagRight, tagBottom);
        drawTinyFlowers(canvas, width, height);
    }

    private void drawCalendarGrid(Canvas canvas, float left, float top, float right, float bottom, float cellWidth, float cellHeight) {
        for (int column = 0; column <= 7; column++) {
            float x = left + column * cellWidth;
            canvas.drawLine(x, top, x, bottom, linePaint);
        }
        for (int row = 0; row <= 6; row++) {
            float y = top + row * cellHeight;
            canvas.drawLine(left, y, right, y, linePaint);
        }
    }

    private void drawWeekdays(Canvas canvas, float left, float top, float cellWidth) {
        String[] labels = {"日", "一", "二", "三", "四", "五", "六"};
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(Ui.dp(getContext(), 14));
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseY = top + Ui.dp(getContext(), 12) - (metrics.ascent + metrics.descent) / 2f;
        for (int i = 0; i < labels.length; i++) {
            textPaint.setColor((i == 0 || i == 6) ? Color.rgb(233, 96, 99) : Color.rgb(101, 61, 34));
            canvas.drawText(labels[i], left + i * cellWidth + cellWidth / 2f, baseY, textPaint);
        }
    }

    private void drawMonthTag(Canvas canvas, float left, float top, float right, float bottom) {
        Path tag = new Path();
        float radius = Ui.dp(getContext(), 18);
        rect.set(left, top + Ui.dp(getContext(), 20), right, bottom);
        tag.addRoundRect(rect, radius, radius, Path.Direction.CW);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(232, 255, 248, 224));
        canvas.drawPath(tag, fillPaint);
        canvas.drawPath(tag, dashPaint);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.rgb(244, 154, 159));
        canvas.drawCircle((left + right) / 2f - Ui.dp(getContext(), 7), top + Ui.dp(getContext(), 18), Ui.dp(getContext(), 8), fillPaint);
        canvas.drawCircle((left + right) / 2f + Ui.dp(getContext(), 7), top + Ui.dp(getContext(), 18), Ui.dp(getContext(), 8), fillPaint);
        fillPaint.setColor(Color.rgb(255, 214, 216));
        canvas.drawCircle((left + right) / 2f, top + Ui.dp(getContext(), 18), Ui.dp(getContext(), 5), fillPaint);

        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(Color.rgb(117, 75, 43));
        textPaint.setTextSize(Ui.dp(getContext(), 27));
        Paint.FontMetrics monthMetrics = textPaint.getFontMetrics();
        String monthText = monthLabel == null || monthLabel.isEmpty() ? "本月" : monthLabel.replace("年", "年 ");
        canvas.drawText(monthText, (left + right) / 2f, top + Ui.dp(getContext(), 48) - (monthMetrics.ascent + monthMetrics.descent) / 2f, textPaint);

        textPaint.setTextSize(Ui.dp(getContext(), 10));
        textPaint.setColor(Color.rgb(151, 102, 60));
        Paint.FontMetrics dateMetrics = textPaint.getFontMetrics();
        String current = dateLabel == null || dateLabel.isEmpty() ? "" : dateLabel;
        canvas.drawText(current, (left + right) / 2f, top + Ui.dp(getContext(), 74) - (dateMetrics.ascent + dateMetrics.descent) / 2f, textPaint);
    }

    private void drawTinyFlowers(Canvas canvas, float width, float height) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.rgb(244, 154, 159));
        float cx = width / 2f;
        float cy = height - Ui.dp(getContext(), 20);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2 * i / 6;
            canvas.drawCircle(cx + (float) Math.cos(angle) * Ui.dp(getContext(), 7), cy + (float) Math.sin(angle) * Ui.dp(getContext(), 7), Ui.dp(getContext(), 4), fillPaint);
        }
        fillPaint.setColor(Color.rgb(255, 220, 135));
        canvas.drawCircle(cx, cy, Ui.dp(getContext(), 4), fillPaint);
    }
}
