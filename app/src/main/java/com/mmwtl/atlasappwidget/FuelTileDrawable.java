package com.mmwtl.atlasappwidget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/** Compact live tank visualization shared by the picker and overlay tile. */
final class FuelTileDrawable extends Drawable {
    private static final int EMPTY_COLOR = 0xFF202020;
    private static final int FILL_COLOR = 0xFF8BA37A;
    private static final int OUTLINE_COLOR = 0xFF7893A0;
    private static final int LIGHT_TEXT = 0xFFF5F5F5;
    private static final int DARK_TEXT = 0xFF171717;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final Path fillPath = new Path();
    private final String freeLabel;
    private final String filledLabel;
    private int liters = SystemStatusSnapshot.UNAVAILABLE;
    private int freeLiters = SystemStatusSnapshot.UNAVAILABLE;
    private int percent;
    private int alpha = 255;

    FuelTileDrawable(String freeLabel, String filledLabel) {
        this.freeLabel = freeLabel;
        this.filledLabel = filledLabel;
    }

    void update(FuelLevelProvider.Reading reading) {
        if (reading == null || !reading.isAvailable()) {
            liters = SystemStatusSnapshot.UNAVAILABLE;
            freeLiters = SystemStatusSnapshot.UNAVAILABLE;
            percent = 0;
        } else {
            liters = reading.liters;
            freeLiters = reading.freeLiters;
            percent = reading.percent;
        }
        invalidateSelf();
    }

    void showPreview() {
        liters = 29;
        freeLiters = FuelLevelProvider.TANK_CAPACITY_LITERS - liters;
        percent = Math.round(liters * 100f / FuelLevelProvider.TANK_CAPACITY_LITERS);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        float width = bounds.width();
        float height = bounds.height();
        float minimum = Math.min(width, height);
        float radius = minimum * 0.14f;
        RectF area = new RectF(bounds);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(EMPTY_COLOR));
        canvas.drawRoundRect(area, radius, radius, paint);

        clipPath.reset();
        clipPath.addRoundRect(area, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        float levelY = bounds.bottom - height
                * Math.max(0, Math.min(100, percent)) / 100f;
        if (percent > 0) {
            float wave = Math.max(1f, minimum * 0.025f);
            fillPath.reset();
            fillPath.moveTo(bounds.left, levelY);
            fillPath.cubicTo(
                    bounds.left + width * 0.16f, levelY - wave,
                    bounds.left + width * 0.34f, levelY + wave,
                    bounds.left + width * 0.50f, levelY
            );
            fillPath.cubicTo(
                    bounds.left + width * 0.66f, levelY - wave,
                    bounds.left + width * 0.84f, levelY + wave,
                    bounds.right, levelY
            );
            fillPath.lineTo(bounds.right, bounds.bottom);
            fillPath.lineTo(bounds.left, bounds.bottom);
            fillPath.close();
            paint.setColor(withAlpha(FILL_COLOR));
            canvas.drawPath(fillPath, paint);
        }
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, minimum * 0.045f));
        paint.setColor(withAlpha(OUTLINE_COLOR));
        canvas.drawRoundRect(area, radius, radius, paint);

        if (liters == SystemStatusSnapshot.UNAVAILABLE) {
            drawCenteredText(canvas, "—", bounds.centerX(),
                    bounds.centerY(), minimum * 0.30f, LIGHT_TEXT, true);
            return;
        }
        drawCenteredText(canvas,
                String.format(Locale.getDefault(), "%d л", freeLiters),
                bounds.centerX(), bounds.top + height * 0.29f,
                minimum * 0.22f,
                textColorAt(bounds.top + height * 0.29f, levelY),
                true);
        drawCenteredText(canvas, freeLabel,
                bounds.centerX(), bounds.top + height * 0.42f,
                minimum * 0.085f,
                textColorAt(bounds.top + height * 0.42f, levelY),
                false);
        drawCenteredText(canvas,
                String.format(Locale.getDefault(), "%d л", liters),
                bounds.centerX(), bounds.top + height * 0.72f,
                minimum * 0.22f,
                textColorAt(bounds.top + height * 0.72f, levelY),
                true);
        drawCenteredText(canvas, filledLabel,
                bounds.centerX(), bounds.top + height * 0.85f,
                minimum * 0.085f,
                textColorAt(bounds.top + height * 0.85f, levelY),
                false);
    }

    private void drawCenteredText(
            Canvas canvas,
            String text,
            float centerX,
            float baselineCenterY,
            float textSize,
            int color,
            boolean bold
    ) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(1f, textSize));
        paint.setTypeface(Typeface.create(
                Typeface.DEFAULT,
                bold ? Typeface.BOLD : Typeface.NORMAL
        ));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = baselineCenterY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, centerX, baseline, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 72;
    }

    @Override
    public int getIntrinsicHeight() {
        return 72;
    }

    private int withAlpha(int color) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int textColorAt(float centerY, float levelY) {
        return centerY >= levelY ? DARK_TEXT : LIGHT_TEXT;
    }
}
