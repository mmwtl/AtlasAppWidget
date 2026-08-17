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

/** Static fuel-card artwork with live free/filled liter values. */
final class FuelTileDrawable extends Drawable {
    private static final int BACKGROUND_COLOR = 0xFF171717;
    private static final int FUEL_COLOR = 0xFF7893A0;
    private static final int DIVIDER_COLOR = 0xFFD4D4D4;
    private static final int DOT_COLOR = 0xFF333333;
    private static final int LIGHT_TEXT = 0xFFF5F5F5;
    private static final int SECONDARY_TEXT = 0xFFD4D4D4;
    private static final int DARK_TEXT = 0xFF171717;
    private static final float FIXED_FUEL_TOP = 0.48f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final String freeLabel;
    private final String filledLabel;
    private String liters;
    private String freeLiters;
    private int alpha = 255;

    FuelTileDrawable(String freeLabel, String filledLabel) {
        this.freeLabel = freeLabel;
        this.filledLabel = filledLabel;
    }

    void update(FuelLevelProvider.Reading reading) {
        if (reading == null || !reading.isAvailable()) {
            liters = null;
            freeLiters = null;
        } else {
            liters = reading.filledDisplayValue();
            freeLiters = reading.freeDisplayValue();
        }
        invalidateSelf();
    }

    void showPreview() {
        liters = "42";
        freeLiters = Integer.toString(FuelLevelProvider.DEFAULT_TANK_CAPACITY_LITERS - 42);
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
        float fuelTop = bounds.top + height * FIXED_FUEL_TOP;

        // The parent icon mask owns the outer shape. Filling the complete bounds avoids a
        // second, conflicting corner radius when the user changes the common icon shape.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(BACKGROUND_COLOR));
        canvas.drawRect(bounds.left, bounds.top, bounds.right, fuelTop, paint);
        paint.setColor(withAlpha(FUEL_COLOR));
        canvas.drawRect(bounds.left, fuelTop, bounds.right, bounds.bottom, paint);

        paint.setColor(withAlpha(DIVIDER_COLOR));
        canvas.drawRect(bounds.left, fuelTop,
                bounds.right, fuelTop + Math.max(1f, minimum * 0.008f), paint);

        drawDotField(canvas, bounds, minimum);

        String free = freeLiters == null ? "—" : freeLiters;
        String filled = liters == null ? "—" : liters;
        float left = bounds.left + width * 0.095f;
        drawValue(canvas, free, left, bounds.top + height * 0.245f,
                minimum * 0.225f, LIGHT_TEXT);
        drawLabel(canvas, freeLabel, left, bounds.top + height * 0.385f,
                minimum * 0.092f, SECONDARY_TEXT);
        drawValue(canvas, filled, left, bounds.top + height * 0.700f,
                minimum * 0.235f, DARK_TEXT);
        drawLabel(canvas, filledLabel, left, bounds.top + height * 0.855f,
                minimum * 0.096f, DARK_TEXT);
        drawFuelPump(canvas, bounds, minimum);
    }

    private void drawDotField(Canvas canvas, Rect bounds, float minimum) {
        paint.setStyle(Paint.Style.FILL);
        int columns = 6;
        int rows = 6;
        float startX = bounds.left + bounds.width() * 0.57f;
        float startY = bounds.top + bounds.height() * 0.12f;
        float stepX = bounds.width() * 0.065f;
        float stepY = bounds.height() * 0.047f;
        float radius = Math.max(0.7f, minimum * 0.0105f);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int fade = Math.max(20, 150 - row * 23 + column * 4);
                paint.setColor(withCombinedAlpha(DOT_COLOR, fade));
                canvas.drawCircle(startX + column * stepX,
                        startY + row * stepY, radius, paint);
            }
        }
    }

    private void drawValue(
            Canvas canvas,
            String value,
            float left,
            float centerY,
            float textSize,
            int color
    ) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(Math.max(1f, textSize));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value, left, baseline, paint);

        float numberWidth = paint.measureText(value);
        paint.setTextSize(Math.max(1f, textSize * 0.72f));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText("L", left + numberWidth + textSize * 0.15f, baseline, paint);
    }

    private void drawLabel(
            Canvas canvas,
            String text,
            float left,
            float centerY,
            float textSize,
            int color
    ) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(Math.max(1f, textSize));
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, left, baseline, paint);
    }

    private void drawFuelPump(Canvas canvas, Rect bounds, float minimum) {
        float left = bounds.left + bounds.width() * 0.785f;
        float top = bounds.top + bounds.height() * 0.755f;
        float bodyWidth = bounds.width() * 0.085f;
        float bodyHeight = bounds.height() * 0.145f;
        float stroke = Math.max(1f, minimum * 0.014f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(withAlpha(DARK_TEXT));
        RectF body = new RectF(left, top, left + bodyWidth, top + bodyHeight);
        canvas.drawRoundRect(body, stroke * 1.3f, stroke * 1.3f, paint);

        RectF display = new RectF(
                left + bodyWidth * 0.17f,
                top + bodyHeight * 0.13f,
                left + bodyWidth * 0.83f,
                top + bodyHeight * 0.48f
        );
        canvas.drawRoundRect(display, stroke * 0.45f, stroke * 0.45f, paint);
        canvas.drawLine(left - stroke * 1.3f, body.bottom,
                body.right + stroke * 1.3f, body.bottom, paint);

        path.reset();
        path.moveTo(body.right, top + bodyHeight * 0.31f);
        path.lineTo(body.right + bodyWidth * 0.27f, top + bodyHeight * 0.42f);
        path.lineTo(body.right + bodyWidth * 0.27f, top + bodyHeight * 0.69f);
        path.cubicTo(
                body.right + bodyWidth * 0.27f, top + bodyHeight * 0.90f,
                body.right + bodyWidth * 0.62f, top + bodyHeight * 0.91f,
                body.right + bodyWidth * 0.62f, top + bodyHeight * 0.70f
        );
        path.lineTo(body.right + bodyWidth * 0.62f, top + bodyHeight * 0.48f);
        canvas.drawPath(path, paint);

        paint.setStrokeCap(Paint.Cap.BUTT);
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

    private int withCombinedAlpha(int color, int localAlpha) {
        return (color & 0x00FFFFFF) | (alpha * localAlpha / 255 << 24);
    }
}
