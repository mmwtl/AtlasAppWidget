package com.mmwtl.atlasappwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
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
import android.view.View;
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
        new Prefs(context).setWidgetsConfigured(appWidgetIds, false);
        WidgetFuelService.sync(context);
    }

    @Override
    public void onDisabled(Context context) {
        context.stopService(new Intent(context, WidgetFuelService.class));
    }

    static int[] ids(Context context) {
        Context app = context.getApplicationContext();
        return AppWidgetManager.getInstance(app).getAppWidgetIds(
                new ComponentName(app, AtlasAppWidgetProvider.class));
    }

    static boolean owns(Context context, int widgetId) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return false;
        }
        Context app = context.getApplicationContext();
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(app).getAppWidgetInfo(widgetId);
        return info != null
                && new ComponentName(app, AtlasAppWidgetProvider.class).equals(info.provider);
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

    /** Redraws only the system status strip, leaving the icon grid on the host untouched. */
    static void refreshStatus(Context context) {
        Context app = context.getApplicationContext();
        int[] ids = ids(app);
        if (ids.length > 0) {
            RENDERER.execute(() -> renderStatus(app, ids));
        }
    }

    private static void renderStatus(Context context, int[] ids) {
        Prefs prefs = new Prefs(context);
        PanelConfig config = prefs.panelConfig();
        if (!prefs.isWidgetMode() || !config.showSystemStatus
                || prefs.selectedComponents().isEmpty()) {
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        SystemStatusSnapshot snapshot = WidgetFuelService.statusSnapshot();
        FuelLevelProvider.Reading fuelReading = WidgetFuelService.reading();
        for (int widgetId : ids) {
            try {
                Bundle options = manager.getAppWidgetOptions(widgetId);
                RemoteViews views = new RemoteViews(context.getPackageName(),
                        R.layout.widget_panel);
                applyStatus(context, config, views, grid(context, config, options),
                        snapshot, fuelReading);
                manager.partiallyUpdateAppWidget(widgetId, views);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot update Android widget status " + widgetId, error);
            }
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

    private static WidgetGrid grid(Context context, PanelConfig config, Bundle options) {
        int labelHeight = config.showAppLabels
                ? PanelConfig.appLabelHeightPixels(context, config.appLabelTextSizeSp) : 0;
        boolean sideStatus = sideStatus(config);
        return WidgetGrid.forSize(dp(context, widthDp(context, options)),
                dp(context, heightDp(context, options)), config.columns, config.rows,
                dp(context, config.iconSizeDp),
                dp(context, config.paddingDp + (config.backgroundStrokeEnabled
                        ? config.backgroundStrokeWidthDp : 0)),
                dp(context, config.gapDp), labelHeight,
                config.showAppLabels ? dp(context, config.appLabelGapDp) : 0,
                sideStatus, statusSize(context, config, sideStatus),
                config.showSystemStatus ? dp(context, SystemStatusView.GAP_DP) : 0);
    }

    private static boolean sideStatus(PanelConfig config) {
        return config.showSystemStatus
                && (config.systemStatusPosition == PanelConfig.STATUS_LEFT
                || config.systemStatusPosition == PanelConfig.STATUS_RIGHT);
    }

    private static int statusSize(Context context, PanelConfig config, boolean side) {
        if (!config.showSystemStatus) {
            return 0;
        }
        return side
                ? SystemStatusView.sideWidthPixels(context, config.systemStatusLineHeightDp,
                        config.systemStatusMetricCount())
                : SystemStatusView.heightPixels(context, config.systemStatusTextSizeSp,
                        config.systemStatusTextWeight, config.systemStatusLineHeightDp);
    }

    // Indexed by PanelConfig.STATUS_TOP, STATUS_BOTTOM, STATUS_LEFT and STATUS_RIGHT.
    private static final int[] STATUS_VIEW_IDS = {R.id.widget_status_top,
            R.id.widget_status_bottom, R.id.widget_status_left, R.id.widget_status_right};

    // A themed strip is three bitmaps: a white mask the host tints with the theme text color,
    // the fixed-color metric fills on top of it and a fill mask the host tints to shade them.
    // An inverse strip tints its mask the other way and keeps the fills as they are.
    private static void applyStatus(Context context, PanelConfig config, RemoteViews views,
            WidgetGrid grid, SystemStatusSnapshot snapshot,
            FuelLevelProvider.Reading fuelReading) {
        PanelConfig.TextMode text = config.textMode();
        boolean themed = text == PanelConfig.TextMode.THEME;
        boolean inverse = text == PanelConfig.TextMode.INVERSE;
        int position = config.systemStatusPosition;
        RemoteViews layers = new RemoteViews(context.getPackageName(), R.layout.widget_status);
        Bitmap image = statusBitmap(context, config, grid);
        CharSequence description = drawStatus(context, config, grid, snapshot, fuelReading,
                text.masked() ? SystemStatusView.FILLS
                        : text == PanelConfig.TextMode.DARK ? SystemStatusView.ON_LIGHT
                        : SystemStatusView.DARK, image);
        layers.setImageViewBitmap(R.id.widget_status_image, image);
        if (text.masked()) {
            Bitmap mask = statusBitmap(context, config, grid);
            drawStatus(context, config, grid, snapshot, fuelReading, SystemStatusView.MASK, mask);
            layers.setImageViewBitmap(inverse ? R.id.widget_status_inverse
                    : R.id.widget_status_themed, mask);
        }
        if (themed) {
            Bitmap shade = statusBitmap(context, config, grid);
            drawStatus(context, config, grid, snapshot, fuelReading,
                    SystemStatusView.FILL_MASK, shade);
            layers.setImageViewBitmap(R.id.widget_status_shade, shade);
        }
        for (int layerId : new int[] {R.id.widget_status_image, R.id.widget_status_themed,
                R.id.widget_status_inverse, R.id.widget_status_shade}) {
            layers.setInt(layerId, "setImageAlpha", config.contentAlpha);
        }
        layers.setViewVisibility(R.id.widget_status_themed, themed ? View.VISIBLE : View.GONE);
        layers.setViewVisibility(R.id.widget_status_inverse, inverse ? View.VISIBLE : View.GONE);
        layers.setViewVisibility(R.id.widget_status_shade, themed ? View.VISIBLE : View.GONE);
        int viewId = STATUS_VIEW_IDS[position];
        hideStatus(views, viewId);
        views.removeAllViews(viewId);
        views.addView(viewId, layers);
        views.setContentDescription(viewId, description);
        views.setViewVisibility(viewId, View.VISIBLE);
    }

    // The strip bitmap carries the gap on its grid side, like cell bitmaps carry theirs.
    private static Bitmap statusBitmap(Context context, PanelConfig config, WidgetGrid grid) {
        boolean side = sideStatus(config);
        int size = statusSize(context, config, side);
        int gap = dp(context, SystemStatusView.GAP_DP);
        Bitmap bitmap = Bitmap.createBitmap(
                Math.max(1, side ? size + gap : grid.layout.gridWidth),
                Math.max(1, side ? grid.layout.gridHeight : size + gap),
                Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        return bitmap;
    }

    private static CharSequence drawStatus(Context context, PanelConfig config, WidgetGrid grid,
            SystemStatusSnapshot snapshot, FuelLevelProvider.Reading fuelReading,
            SystemStatusView.Palette palette, Bitmap bitmap) {
        boolean side = sideStatus(config);
        int size = statusSize(context, config, side);
        int gap = dp(context, SystemStatusView.GAP_DP);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(config.systemStatusPosition == PanelConfig.STATUS_RIGHT ? gap : 0,
                config.systemStatusPosition == PanelConfig.STATUS_BOTTOM ? gap : 0);
        return SystemStatusView.draw(context, config, side,
                Math.max(1, side ? size : grid.layout.gridWidth),
                Math.max(1, side ? grid.layout.gridHeight : size), snapshot, fuelReading,
                palette, canvas);
    }

    // Hosts reapply updates onto the existing views, so an earlier position stays visible
    // unless every update hides it explicitly.
    private static void hideStatus(RemoteViews views, int keptViewId) {
        for (int viewId : STATUS_VIEW_IDS) {
            if (viewId != keptViewId) {
                views.setViewVisibility(viewId, View.GONE);
            }
        }
    }

    static RemoteViews panelViews(Context context, Prefs prefs, PanelConfig config,
            List<AppEntry> entries, Bundle options, int widgetId) {
        int widthDp = widthDp(context, options);
        int heightDp = heightDp(context, options);
        WidgetGrid grid = grid(context, config, options);
        RemoteViews panel = new RemoteViews(context.getPackageName(), R.layout.widget_panel);
        RemoteViews background = new RemoteViews(context.getPackageName(),
                R.layout.widget_background);
        // Only colors that differ between the modes need a panel for each of them.
        boolean dayColors = config.dayBackgroundColor != config.backgroundColor
                || config.backgroundStrokeEnabled
                && config.dayBackgroundStrokeColor != config.backgroundStrokeColor;
        boolean themed = !dayColors && config.backgroundColor == PanelConfig.THEME_COLOR;
        PanelConfig.TextMode text = config.textMode();
        boolean inverseText = text == PanelConfig.TextMode.INVERSE;
        int fill = withBackgroundAlpha(config, config.backgroundColor);
        if (dayColors) {
            // Both panels are drawn whole; the host shows the one for the current theme.
            background.setImageViewBitmap(R.id.widget_background_day, background(context, config,
                    widthDp, heightDp, withBackgroundAlpha(config,
                            PanelConfig.surface(config.dayBackgroundColor, false)),
                    PanelConfig.stroke(config.dayBackgroundStrokeColor)));
            background.setImageViewBitmap(R.id.widget_background_night, background(context, config,
                    widthDp, heightDp, withBackgroundAlpha(config,
                            PanelConfig.surface(config.backgroundColor, true)),
                    PanelConfig.stroke(config.backgroundStrokeColor)));
        }
        if (themed) {
            // The host tints this white shape with the day or night surface color.
            background.setImageViewBitmap(R.id.widget_background_themed, background(context, config,
                    widthDp, heightDp, fill | 0x00FFFFFF, Color.TRANSPARENT));
        }
        boolean fixedBackground = !dayColors && (!themed || config.backgroundStrokeEnabled);
        if (fixedBackground) {
            background.setImageViewBitmap(R.id.widget_background, background(context, config,
                    widthDp, heightDp, themed ? Color.TRANSPARENT : fill,
                    PanelConfig.stroke(config.backgroundStrokeColor)));
        }
        background.setViewVisibility(R.id.widget_background_day,
                dayColors ? View.VISIBLE : View.GONE);
        background.setViewVisibility(R.id.widget_background_night,
                dayColors ? View.VISIBLE : View.GONE);
        background.setViewVisibility(R.id.widget_background_themed,
                themed ? View.VISIBLE : View.GONE);
        background.setViewVisibility(R.id.widget_background,
                fixedBackground ? View.VISIBLE : View.GONE);
        panel.removeAllViews(R.id.widget_background_host);
        panel.addView(R.id.widget_background_host, background);
        panel.removeAllViews(R.id.widget_grid);
        int shown = Math.min(entries.size(), grid.capacity());
        FuelLevelProvider.Reading fuelReading = WidgetFuelService.reading();
        if (config.showSystemStatus) {
            SystemStatusSnapshot snapshot = WidgetFuelService.statusSnapshot();
            FuelLevelProvider.Reading statusFuel = fuelReading;
            // Settings previews have no live sampler yet; show the overlay preview values.
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                    && snapshot.equals(SystemStatusSnapshot.unavailable())) {
                snapshot = SystemStatusSnapshot.preview();
                statusFuel = fuelReading.isAvailable() ? fuelReading : null;
            }
            applyStatus(context, config, panel, grid, snapshot, statusFuel);
        } else {
            hideStatus(panel, 0);
        }
        // RemoteViews shares identical Bitmap instances, so blank cells cost one bitmap per gap shape.
        Bitmap[] blankCells = new Bitmap[4];
        for (int rowIndex = 0; rowIndex < grid.rows; rowIndex++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
            for (int column = 0; column < grid.columns; column++) {
                int index = rowIndex * grid.columns + column;
                RemoteViews cell = new RemoteViews(context.getPackageName(),
                        inverseText ? R.layout.widget_cell_inverse : R.layout.widget_cell);
                // Host views cannot fade a whole cell, so each image layer carries the alpha.
                cell.setInt(R.id.widget_cell_icon, "setImageAlpha", config.contentAlpha);
                cell.setInt(R.id.widget_cell_label, "setImageAlpha", config.contentAlpha);
                cell.setInt(R.id.widget_cell_outline, "setImageAlpha", config.contentAlpha);
                if (index >= shown) {
                    int shape = (column == 0 ? 0 : 1) + (rowIndex == 0 ? 0 : 2);
                    if (blankCells[shape] == null) {
                        blankCells[shape] = cellBitmap(context, prefs, config, null, grid,
                                rowIndex, column, fuelReading);
                    }
                    cell.setImageViewBitmap(R.id.widget_cell_icon, blankCells[shape]);
                } else {
                    AppEntry entry = entries.get(index);
                    cell.setImageViewBitmap(R.id.widget_cell_icon, cellBitmap(context, prefs,
                            config, entry, grid, rowIndex, column, fuelReading));
                    if (text.masked() && config.showAppLabels && grid.labelScale > 0) {
                        cell.setImageViewBitmap(R.id.widget_cell_label, labelBitmap(context,
                                config, entry.label, grid, rowIndex, column, false));
                        if (config.appLabelOutlineEnabled) {
                            cell.setImageViewBitmap(R.id.widget_cell_outline, labelBitmap(
                                    context, config, entry.label, grid, rowIndex, column, true));
                        }
                    }
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
        // Themed labels go to their own tinted layers, see labelBitmap.
        PanelConfig.TextMode text = config.textMode();
        if (config.showAppLabels && grid.labelScale > 0 && !text.masked()) {
            boolean dark = text == PanelConfig.TextMode.DARK;
            drawLabel(context, config, entry.label, grid, left, top,
                    dark ? 0xFF202124 : Color.rgb(245, 245, 245),
                    !config.appLabelOutlineEnabled ? Color.TRANSPARENT
                            : dark ? Color.WHITE : Color.BLACK, canvas);
        }
        return bitmap;
    }

    /**
     * Draws the label area under a cell icon as a white mask for the host to tint: the text
     * with the theme text color, or the text with its shadow with the theme outline color.
     */
    private static Bitmap labelBitmap(Context context, PanelConfig config, String label,
            WidgetGrid grid, int row, int column, boolean outline) {
        PanelLayout layout = grid.layout;
        int left = column == 0 ? 0 : layout.horizontalGap;
        int top = row == 0 ? 0 : layout.verticalGap;
        int labelTop = labelTop(context, config, grid, top);
        Bitmap bitmap = Bitmap.createBitmap(layout.cellWidth + left,
                Math.max(1, top + layout.cellHeight - labelTop), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(0, -labelTop);
        drawLabel(context, config, label, grid, left, top, Color.WHITE,
                outline ? Color.WHITE : Color.TRANSPARENT, canvas);
        return bitmap;
    }

    private static int labelTop(Context context, PanelConfig config, WidgetGrid grid, int top) {
        return top + grid.layout.iconSize
                + (int) (dp(context, config.appLabelGapDp) * grid.labelScale);
    }

    private static void drawLabel(Context context, PanelConfig config, String text,
            WidgetGrid grid, int left, int top, int color, int shadowColor, Canvas canvas) {
        PanelLayout layout = grid.layout;
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                config.appLabelTextSizeSp, context.getResources().getDisplayMetrics())
                * grid.labelScale);
        int inset = Math.round(dp(context, 2) * grid.labelScale);
        if (shadowColor != Color.TRANSPARENT) {
            paint.setShadowLayer(dp(context, 2) * grid.labelScale, 0,
                    dp(context, 1) * grid.labelScale, shadowColor);
        }
        String label = TextUtils.ellipsize(text, paint,
                Math.max(0, layout.cellWidth - inset * 2), TextUtils.TruncateAt.END).toString();
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = (labelTop(context, config, grid, top) + top + layout.cellHeight
                - metrics.ascent - metrics.descent) / 2f;
        canvas.drawText(label, left + (layout.cellWidth - paint.measureText(label)) / 2f,
                baseline, paint);
    }

    private static int withBackgroundAlpha(PanelConfig config, int color) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, config.backgroundAlpha)) << 24);
    }

    /** Draws the panel shape, with the stroke in {@code strokeColor} when the stroke is on. */
    private static Bitmap background(Context context, PanelConfig config,
            int widthDp, int heightDp, int fill, int strokeColor) {
        boolean stroke = config.backgroundStrokeEnabled && strokeColor != Color.TRANSPARENT;
        int requestedWidth = Math.max(1, dp(context, widthDp));
        int requestedHeight = Math.max(1, dp(context, heightDp));
        float scale = Math.min(1f, 512f / Math.max(requestedWidth, requestedHeight));
        int width = Math.max(1, Math.round(requestedWidth * scale));
        int height = Math.max(1, Math.round(requestedHeight * scale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = config.panelRadiusDp == PanelConfig.PANEL_RADIUS_FULLY_ROUNDED
                ? Math.min(width, height) / 2f
                : dp(context, Math.max(0, config.panelRadiusDp)) * scale;
        RectF rect = new RectF(0, 0, width, height);
        paint.setColor(fill);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (stroke) {
            float strokeWidth = Math.max(1f, dp(context, config.backgroundStrokeWidthDp) * scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor((strokeColor & 0x00FFFFFF)
                    | (Math.max(0, Math.min(255, config.backgroundStrokeAlpha)) << 24));
            rect.inset(strokeWidth / 2f, strokeWidth / 2f);
            canvas.drawRoundRect(rect, Math.max(0, radius - strokeWidth / 2f),
                    Math.max(0, radius - strokeWidth / 2f), paint);
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
