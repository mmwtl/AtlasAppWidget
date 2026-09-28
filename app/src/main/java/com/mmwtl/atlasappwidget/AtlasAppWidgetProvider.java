package com.mmwtl.atlasappwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.RemoteViews;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AtlasAppWidgetProvider extends AppWidgetProvider {
    private static final ExecutorService RENDERER = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(action)) {
            PendingResult result = goAsync();
            Context app = context.getApplicationContext();
            WidgetFuelService.sync(app);
            int[] ids = AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                    ? intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    : new int[]{intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID)};
            RENDERER.execute(() -> {
                try {
                    render(app, ids);
                } finally {
                    result.finish();
                }
            });
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        updateAll(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        WidgetFuelService.sync(context);
    }

    @Override
    public void onDisabled(Context context) {
        context.stopService(new Intent(context, WidgetFuelService.class));
    }

    static void updateAll(Context context) {
        Context app = context.getApplicationContext();
        WidgetFuelService.sync(app);
        refreshFuel(app);
    }

    static void refreshFuel(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, AtlasAppWidgetProvider.class));
        if (ids.length > 0) {
            RENDERER.execute(() -> render(app, ids));
        }
    }

    private static void render(Context context, int[] ids) {
        if (ids == null || ids.length == 0) {
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Prefs prefs = new Prefs(context);
        List<AppEntry> entries = prefs.isWidgetMode()
                ? AppRepository.loadSelectedActivities(context, prefs) : List.of();
        PanelConfig config = prefs.panelConfig();
        for (int widgetId : ids) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                continue;
            }
            try {
                RemoteViews views = entries.isEmpty()
                        ? emptyViews(context, prefs, widgetId)
                        : panelViews(context, prefs, config, entries,
                                manager.getAppWidgetOptions(widgetId), widgetId);
                manager.updateAppWidget(widgetId, views);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot update Android widget " + widgetId, error);
            }
        }
    }

    private static RemoteViews emptyViews(Context context, Prefs prefs, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_empty);
        views.setTextViewText(R.id.widget_empty_text,
                context.getString(prefs.isWidgetMode()
                        ? R.string.widget_choose_apps : R.string.widget_switch_mode));
        Intent settings = new Intent(context, MainActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("atlaswidget://settings/" + widgetId));
        views.setOnClickPendingIntent(R.id.widget_empty, PendingIntent.getActivity(context,
                widgetId, settings, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    static int widthDp(Context context, Bundle options) {
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        return sizeDp(options, landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                sizeDp(options, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 400));
    }

    static int heightDp(Context context, Bundle options) {
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        return sizeDp(options, landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                sizeDp(options, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160));
    }

    // Hosts that never report a size leave zeros; render the example size instead of 1 dp.
    private static int sizeDp(Bundle options, String key, int fallback) {
        int value = options.getInt(key, 0);
        return value > 0 ? value : fallback;
    }

    static RemoteViews panelViews(Context context, Prefs prefs, PanelConfig config,
            List<AppEntry> entries, Bundle options, int widgetId) {
        int widthDp = widthDp(context, options);
        int heightDp = heightDp(context, options);
        int labelHeight = config.showAppLabels
                ? PanelConfig.appLabelHeightPixels(context, config.appLabelTextSizeSp) : 0;
        WidgetGrid grid = WidgetGrid.forSize(dp(context, widthDp),
                dp(context, heightDp), config.columns, config.rows, dp(context, config.iconSizeDp),
                dp(context, config.paddingDp + (config.backgroundStrokeEnabled
                        ? config.backgroundStrokeWidthDp : 0)),
                dp(context, config.gapDp), labelHeight,
                config.showAppLabels ? dp(context, config.appLabelGapDp) : 0);
        RemoteViews panel = new RemoteViews(context.getPackageName(), R.layout.widget_panel);
        panel.setImageViewBitmap(R.id.widget_background,
                background(context, config, widthDp, heightDp));
        panel.removeAllViews(R.id.widget_grid);
        int shown = Math.min(entries.size(), grid.capacity());
        FuelLevelProvider.Reading fuelReading = WidgetFuelService.reading();
        // RemoteViews shares identical Bitmap instances, so blank cells cost one bitmap per gap shape.
        Bitmap[] blankCells = new Bitmap[4];
        for (int rowIndex = 0; rowIndex < grid.rows; rowIndex++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
            for (int column = 0; column < grid.columns; column++) {
                int index = rowIndex * grid.columns + column;
                RemoteViews cell = new RemoteViews(context.getPackageName(), R.layout.widget_cell);
                if (index >= shown) {
                    int shape = (column == 0 ? 0 : 1) + (rowIndex == 0 ? 0 : 2);
                    if (blankCells[shape] == null) {
                        blankCells[shape] = cellBitmap(context, prefs, config, null, grid,
                                rowIndex, column, fuelReading);
                    }
                    cell.setImageViewBitmap(R.id.widget_cell, blankCells[shape]);
                } else {
                    AppEntry entry = entries.get(index);
                    cell.setImageViewBitmap(R.id.widget_cell, cellBitmap(context, prefs, config,
                            entry, grid, rowIndex, column, fuelReading));
                    if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        Intent launch = new Intent(context, entry.isFuel()
                                ? WidgetFuelActivity.class : WidgetLaunchActivity.class)
                                .putExtra(WidgetLaunchActivity.EXTRA_ENTRY_KEY, entry.componentKey)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .setData(Uri.parse("atlaswidget://launch/" + widgetId + "/" + index));
                        PendingIntent click = PendingIntent.getActivity(context,
                                widgetId * 100 + index, launch,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        cell.setOnClickPendingIntent(R.id.widget_cell, click);
                    }
                    cell.setContentDescription(R.id.widget_cell, entry.isFuel()
                            ? fuelReading.isAvailable()
                                    ? context.getString(R.string.fuel_tile_content_description,
                                            fuelReading.freeDisplayValue(),
                                            fuelReading.filledDisplayValue())
                                    : context.getString(R.string.fuel_data_unavailable)
                            : entry.label);
                }
                row.addView(R.id.widget_row, cell);
            }
            panel.addView(R.id.widget_grid, row);
        }
        return panel;
    }

    private static Bitmap cellBitmap(Context context, Prefs prefs, PanelConfig config,
            AppEntry entry, WidgetGrid grid, int row, int column,
            FuelLevelProvider.Reading fuelReading) {
        PanelLayout layout = grid.layout;
        int left = column == 0 ? 0 : layout.horizontalGap;
        int top = row == 0 ? 0 : layout.verticalGap;
        Bitmap bitmap = Bitmap.createBitmap(layout.cellWidth + left, layout.cellHeight + top,
                Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        if (entry == null) return bitmap;
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(icon(context, prefs, config, entry, layout.iconSize, fuelReading),
                left + (layout.cellWidth - layout.iconSize) / 2f, top, null);
        if (config.showAppLabels && grid.labelScale > 0) {
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(245, 245, 245));
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    config.appLabelTextSizeSp, context.getResources().getDisplayMetrics())
                    * grid.labelScale);
            int inset = Math.round(dp(context, 2) * grid.labelScale);
            if (config.appLabelOutlineEnabled) {
                paint.setShadowLayer(dp(context, 2) * grid.labelScale, 0,
                        dp(context, 1) * grid.labelScale, Color.BLACK);
            }
            String label = TextUtils.ellipsize(entry.label, paint,
                    Math.max(0, layout.cellWidth - inset * 2), TextUtils.TruncateAt.END).toString();
            Paint.FontMetrics metrics = paint.getFontMetrics();
            int labelTop = top + layout.iconSize
                    + (int) (dp(context, config.appLabelGapDp) * grid.labelScale);
            float baseline = (labelTop + top + layout.cellHeight
                    - metrics.ascent - metrics.descent) / 2f;
            canvas.drawText(label, left + (layout.cellWidth - paint.measureText(label)) / 2f,
                    baseline, paint);
        }
        return bitmap;
    }

    private static Bitmap background(Context context, PanelConfig config,
            int widthDp, int heightDp) {
        int requestedWidth = Math.max(1, dp(context, widthDp));
        int requestedHeight = Math.max(1, dp(context, heightDp));
        float scale = Math.min(1f, 512f / Math.max(requestedWidth, requestedHeight));
        int width = Math.max(1, Math.round(requestedWidth * scale));
        int height = Math.max(1, Math.round(requestedHeight * scale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int fill = (config.backgroundColor & 0x00FFFFFF)
                | (Math.max(0, Math.min(255, config.backgroundAlpha)) << 24);
        float radius = config.panelRadiusDp == PanelConfig.PANEL_RADIUS_FULLY_ROUNDED
                ? Math.min(width, height) / 2f
                : dp(context, Math.max(0, config.panelRadiusDp)) * scale;
        RectF rect = new RectF(0, 0, width, height);
        paint.setColor(fill);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (config.backgroundStrokeEnabled) {
            float stroke = Math.max(1f, dp(context, config.backgroundStrokeWidthDp) * scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor((config.backgroundStrokeColor & 0x00FFFFFF)
                    | (Math.max(0, Math.min(255, config.backgroundStrokeAlpha)) << 24));
            rect.inset(stroke / 2f, stroke / 2f);
            canvas.drawRoundRect(rect, Math.max(0, radius - stroke / 2f),
                    Math.max(0, radius - stroke / 2f), paint);
        }
        return bitmap;
    }

    private static Bitmap icon(Context context, Prefs prefs, PanelConfig config,
            AppEntry entry, int size, FuelLevelProvider.Reading fuelReading) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, size), Math.max(1, size),
                Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmap);
        float radius = size * Math.max(0, Math.min(50, config.iconCornerPercent)) / 100f;
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, size, size), radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        Drawable image;
        if (entry.isFuel()) {
            FuelTileDrawable fuel = new FuelTileDrawable(
                    context.getString(R.string.fuel_free_short),
                    context.getString(R.string.fuel_filled_short));
            fuel.update(fuelReading);
            image = fuel;
        } else {
            image = IconLoader.load(context, prefs, entry, size).drawable;
        }
        image.setBounds(0, 0, size, size);
        image.draw(canvas);
        return bitmap;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
