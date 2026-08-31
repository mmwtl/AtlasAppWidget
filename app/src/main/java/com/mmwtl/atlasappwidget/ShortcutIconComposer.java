package com.mmwtl.atlasappwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;

import java.util.List;

/** Builds the two-app artwork shown by launchers for a GSplit preset. */
final class ShortcutIconComposer {
    private static final int SIZE_PX = 192;
    private static final int ICON_PX = 142;
    private static final int SECOND_ICON_OFFSET_PX = SIZE_PX - ICON_PX;
    private static final float DIVIDER_PX = 5f;

    private ShortcutIconComposer() {
    }

    static Bitmap createGsplitPreset(Context context, String title, String targetComponent) {
        ComponentName target = ComponentName.unflattenFromString(targetComponent);
        if (!ShortcutSpec.isGsplitPreset(target)) return null;
        List<String> labels = ShortcutSpec.gsplitAppLabels(title);
        if (labels.size() != 2) return null;

        Drawable first = findLaunchableAppIcon(context, labels.get(0));
        Drawable second = findLaunchableAppIcon(context, labels.get(1));
        if (first == null || second == null) return null;

        Bitmap result = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Ui.SURFACE_RAISED);

        Path firstHalf = new Path();
        firstHalf.moveTo(0, 0);
        firstHalf.lineTo(ICON_PX, 0);
        firstHalf.lineTo(ICON_PX, SECOND_ICON_OFFSET_PX);
        firstHalf.lineTo(SECOND_ICON_OFFSET_PX, ICON_PX);
        firstHalf.lineTo(0, ICON_PX);
        firstHalf.close();
        int restore = canvas.save();
        canvas.clipPath(firstHalf);
        draw(first, canvas, 0, 0, ICON_PX);
        canvas.restoreToCount(restore);

        Path secondHalf = new Path();
        secondHalf.moveTo(ICON_PX, SECOND_ICON_OFFSET_PX);
        secondHalf.lineTo(SIZE_PX, SECOND_ICON_OFFSET_PX);
        secondHalf.lineTo(SIZE_PX, SIZE_PX);
        secondHalf.lineTo(SECOND_ICON_OFFSET_PX, SIZE_PX);
        secondHalf.lineTo(SECOND_ICON_OFFSET_PX, ICON_PX);
        secondHalf.close();
        restore = canvas.save();
        canvas.clipPath(secondHalf);
        draw(second, canvas, SECOND_ICON_OFFSET_PX, SECOND_ICON_OFFSET_PX, ICON_PX);
        canvas.restoreToCount(restore);

        Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
        divider.setColor(Ui.SURFACE_RAISED);
        divider.setStrokeWidth(DIVIDER_PX);
        divider.setStrokeCap(Paint.Cap.SQUARE);
        canvas.drawLine(ICON_PX, SECOND_ICON_OFFSET_PX,
                SECOND_ICON_OFFSET_PX, ICON_PX, divider);
        return result;
    }

    private static void draw(Drawable drawable, Canvas canvas, int left, int top, int size) {
        drawable.setBounds(left, top, left + size, top + size);
        drawable.draw(canvas);
    }

    private static Drawable findLaunchableAppIcon(Context context, String expectedLabel) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        String matchedPackage = null;
        ActivityInfo matchedActivity = null;
        for (ResolveInfo resolved : packageManager.queryIntentActivities(
                launcher, PackageManager.MATCH_ALL)) {
            ActivityInfo activity = resolved.activityInfo;
            if (activity == null || activity.applicationInfo == null
                    || !activity.enabled || !activity.applicationInfo.enabled) {
                continue;
            }
            CharSequence activityLabel = resolved.loadLabel(packageManager);
            CharSequence appLabel = activity.applicationInfo.loadLabel(packageManager);
            if (!sameLabel(expectedLabel, activityLabel) && !sameLabel(expectedLabel, appLabel)) {
                continue;
            }
            if (matchedPackage != null && !matchedPackage.equals(activity.packageName)) {
                return null;
            }
            matchedPackage = activity.packageName;
            matchedActivity = activity;
        }
        return matchedActivity == null ? null : matchedActivity.applicationInfo.loadIcon(packageManager);
    }

    private static boolean sameLabel(String expected, CharSequence actual) {
        return actual != null && expected.trim().equalsIgnoreCase(actual.toString().trim());
    }
}
