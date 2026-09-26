package com.mmwtl.atlasappwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
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
        Intent settings = new Intent(context, MainActivity.class);
        views.setOnClickPendingIntent(R.id.widget_empty, PendingIntent.getActivity(context,
                widgetId, settings, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    private static RemoteViews panelViews(Context context, Prefs prefs, PanelConfig config,
            List<AppEntry> entries, Bundle options, int widgetId) {
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120);
        WidgetGrid grid = WidgetGrid.forSize(widthDp, heightDp, config.showAppLabels);
        RemoteViews panel = new RemoteViews(context.getPackageName(), R.layout.widget_panel);
        panel.setImageViewBitmap(R.id.widget_background,
                background(context, config, widthDp, heightDp));
        int padding = Math.max(4, Math.min(20, config.paddingDp));
        int paddingPx = dp(context, padding);
        panel.setViewPadding(R.id.widget_grid, paddingPx, paddingPx, paddingPx, paddingPx);
        panel.removeAllViews(R.id.widget_grid);
        int shown = Math.min(entries.size(), grid.capacity());
        FuelLevelProvider.Reading fuelReading = WidgetFuelService.reading();
        int rowCount = Math.min(grid.rows, Math.max(1,
                (shown + grid.columns - 1) / grid.columns));
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
            for (int column = 0; column < grid.columns; column++) {
                int index = rowIndex * grid.columns + column;
                RemoteViews cell = new RemoteViews(context.getPackageName(), R.layout.widget_cell);
                if (index < shown) {
                    AppEntry entry = entries.get(index);
                    int iconDp = Math.min(96, Math.max(24, config.iconSizeDp));
                    int cellWidthDp = Math.max(24, (widthDp - 2 * padding) / grid.columns - 4);
                    int cellHeightDp = Math.max(24,
                            (heightDp - 2 * padding) / rowCount
                                    - (config.showAppLabels ? 22 : 4));
                    int iconPx = dp(context, Math.min(iconDp, Math.min(cellWidthDp, cellHeightDp)));
                    cell.setImageViewBitmap(R.id.widget_icon,
                            icon(context, prefs, config, entry, iconPx, fuelReading));
                    if (config.showAppLabels) {
                        cell.setViewVisibility(R.id.widget_label, View.VISIBLE);
                        cell.setTextViewText(R.id.widget_label, entry.label);
                        cell.setTextViewTextSize(R.id.widget_label,
                                TypedValue.COMPLEX_UNIT_SP, config.appLabelTextSizeSp);
                    }
                    Intent launch = new Intent(context, WidgetLaunchActivity.class)
                            .putExtra(WidgetLaunchActivity.EXTRA_ENTRY_KEY, entry.componentKey)
                            .setData(Uri.parse("atlaswidget://launch/" + widgetId + "/" + index));
                    PendingIntent click = PendingIntent.getActivity(context,
                            widgetId * 100 + index, launch,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    cell.setOnClickPendingIntent(R.id.widget_cell, click);
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
