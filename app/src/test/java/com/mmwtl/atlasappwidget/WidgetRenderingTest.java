package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w1440dp-h1920dp-mdpi")
public final class WidgetRenderingTest {
    @Test
    public void fullScreenWidgetKeepsHostSizeAndFitsRemoteViewsBitmapBudget() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_COLUMNS, 3);
        prefs.putInt(Prefs.KEY_ROWS, 2);
        prefs.putBoolean(Prefs.KEY_SHOW_APP_LABELS, true);
        prefs.putBoolean(Prefs.KEY_APP_LABEL_OUTLINE_ENABLED, true);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 1440);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 1920);
        var entries = Collections.nCopies(6, AppEntry.fuel("Топливо", ""));
        var remote = AtlasAppWidgetProvider.panelViews(context, prefs, prefs.panelConfig(),
                entries, options, AppWidgetManager.INVALID_APPWIDGET_ID);
        View view = remote.apply(context, new FrameLayout(context));
        view.measure(View.MeasureSpec.makeMeasureSpec(1440, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1440, 1920);
        View cell = view.findViewById(R.id.widget_cell);
        assertTrue(cell.getWidth() > 300);
        BitmapDrawable icon = (BitmapDrawable) ((ImageView) view.findViewById(
                R.id.widget_cell_icon)).getDrawable();
        assertTrue(icon.getIntrinsicWidth() > icon.getBitmap().getWidth());
        assertTrue(cell.getHeight() > 400);
        Set<Bitmap> bitmaps = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(view, bitmaps);
        long bytes = bitmaps.stream().mapToLong(Bitmap::getAllocationByteCount).sum();
        assertTrue("RemoteViews bitmap bytes: " + bytes, bytes < 1440L * 1920 * 4 * 1.5);
    }

    private static void collect(View view, Set<Bitmap> bitmaps) {
        if (view instanceof ImageView image && image.getDrawable() instanceof BitmapDrawable d) {
            bitmaps.add(d.getBitmap());
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), bitmaps);
        }
    }
}
