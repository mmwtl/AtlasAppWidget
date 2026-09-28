package com.mmwtl.atlasappwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;

/** Real AppWidget RemoteViews previews shared by the settings screen and the setup dialog. */
final class WidgetPreview {
    private WidgetPreview() {
    }

    /**
     * Applies the widget RemoteViews at the host-reported size, scaled down to fit the host
     * width and {@code maxHeightPx}. A transparent blocker keeps the preview inert.
     */
    static void render(Context context, FrameLayout host, Prefs prefs, List<AppEntry> entries,
            Bundle options, int maxHeightPx) {
        int width = Ui.dp(context, AtlasAppWidgetProvider.widthDp(context, options));
        int height = Ui.dp(context, AtlasAppWidgetProvider.heightDp(context, options));
        View preview = AtlasAppWidgetProvider.panelViews(context, prefs, prefs.panelConfig(),
                entries, options, AppWidgetManager.INVALID_APPWIDGET_ID).apply(context, host);
        int available = Math.max(1,
                host.getWidth() - host.getPaddingLeft() - host.getPaddingRight());
        float scale = Math.min(1f, Math.min(available / (float) width,
                maxHeightPx / (float) height));
        preview.setScaleX(scale);
        preview.setScaleY(scale);
        host.removeAllViews();
        host.addView(preview, new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
        View blocker = new View(context);
        blocker.setClickable(true);
        host.addView(blocker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewGroup.LayoutParams params = host.getLayoutParams();
        params.height = Math.round(height * scale) + host.getPaddingTop()
                + host.getPaddingBottom();
        host.setLayoutParams(params);
    }
}
