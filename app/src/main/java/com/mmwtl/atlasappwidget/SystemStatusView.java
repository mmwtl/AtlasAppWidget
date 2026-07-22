package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class SystemStatusView extends LinearLayout {
    private static final int MIN_HEIGHT_DP = 30;
    private static final int TEXT_TRACK_GAP_DP = 5;
    static final int GAP_DP = 16;

    private static final int TRACK_COLOR = 0xFF454545;
    private static final int CPU_COLOR = 0xFF7893A0;
    private static final int RAM_COLOR = 0xFFC49A62;
    private static final int TEMPERATURE_COLOR = 0xFFC9786B;

    private final MetricView cpu;
    private final MetricView ram;
    private final MetricView temperature;

    SystemStatusView(
            Context context,
            int textSizeSp,
            int textWeight,
            int lineHeightDp,
            int statusHeight
    ) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int labelHeight = labelHeightPixels(context, textSizeSp, textWeight);
        cpu = addMetric("CPU", CPU_COLOR, textSizeSp, textWeight,
                lineHeightDp, labelHeight);
        addDivider(statusHeight);
        ram = addMetric("RAM", RAM_COLOR, textSizeSp, textWeight,
                lineHeightDp, labelHeight);
        addDivider(statusHeight);
        temperature = addMetric("TEMP", TEMPERATURE_COLOR, textSizeSp, textWeight,
                lineHeightDp, labelHeight);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    static int heightPixels(Context context, int textSizeSp, int textWeight, int lineHeightDp) {
        return Math.max(
                Ui.dp(context, MIN_HEIGHT_DP),
                labelHeightPixels(context, textSizeSp, textWeight)
                        + Ui.dp(context, TEXT_TRACK_GAP_DP)
                        + Ui.dp(context, lineHeightDp)
        );
    }

    void update(SystemStatusSnapshot snapshot) {
        cpu.update(snapshot.cpuPercent, true);
        ram.update(snapshot.ramPercent, true);
        temperature.update(snapshot.temperatureCelsius, false);
        setContentDescription(String.format(
                Locale.getDefault(),
                "%s, %s, %s",
                cpu.label.getText(),
                ram.label.getText(),
                temperature.label.getText()
        ));
    }

    private MetricView addMetric(
            String name,
            int color,
            int textSizeSp,
            int textWeight,
            int lineHeightDp,
            int labelHeight
    ) {
        MetricView metric = new MetricView(getContext(), name, color,
                textSizeSp, textWeight, lineHeightDp, labelHeight);
        addView(metric, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return metric;
    }

    private void addDivider(int statusHeight) {
        View divider = new View(getContext());
        divider.setBackgroundColor(0xFF5A5A5A);
        LayoutParams params = new LayoutParams(
                Ui.dp(getContext(), 1),
                Math.max(Ui.dp(getContext(), 20), statusHeight - Ui.dp(getContext(), 4))
        );
        params.leftMargin = Ui.dp(getContext(), 6);
        params.rightMargin = Ui.dp(getContext(), 6);
        params.gravity = Gravity.CENTER_VERTICAL;
        addView(divider, params);
    }

    private static int labelHeightPixels(Context context, int textSizeSp, int textWeight) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.getResources().getDisplayMetrics()
        ));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, textWeight, false));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return Math.max(1, Math.round(metrics.descent - metrics.ascent) + Ui.dp(context, 2));
    }

    private static final class MetricView extends LinearLayout {
        private final String name;
        private final TextView label;
        private final FrameLayout track;
        private final View fill;
        private int progress;

        MetricView(
                Context context,
                String name,
                int color,
                int textSizeSp,
                int textWeight,
                int lineHeightDp,
                int labelHeight
        ) {
            super(context);
            this.name = name;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            label = Ui.text(context, name + " —", textSizeSp, Ui.TEXT);
            label.setTypeface(Typeface.create(Typeface.DEFAULT, textWeight, false));
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            addView(label, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    labelHeight
            ));

            int lineHeight = Ui.dp(context, lineHeightDp);
            track = new FrameLayout(context);
            track.setBackground(Ui.rounded(TRACK_COLOR, lineHeight / 2f));
            track.addOnLayoutChangeListener((view, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> updateFillWidth());
            fill = new View(context);
            fill.setBackground(Ui.rounded(color, lineHeight / 2f));
            track.addView(fill, new FrameLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            LayoutParams trackParams = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    lineHeight
            );
            trackParams.topMargin = Ui.dp(context, TEXT_TRACK_GAP_DP);
            addView(track, trackParams);
        }

        void update(int value, boolean percent) {
            if (value == SystemStatusSnapshot.UNAVAILABLE) {
                label.setText(name + " —");
                progress = 0;
            } else {
                label.setText(percent
                        ? String.format(Locale.getDefault(), "%s %d%%", name, value)
                        : String.format(Locale.getDefault(), "%s %d°", name, value));
                progress = percent ? value : Math.max(0, Math.min(100, value));
            }
            updateFillWidth();
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            updateFillWidth();
        }

        private void updateFillWidth() {
            int trackWidth = track.getWidth();
            if (trackWidth <= 0) {
                return;
            }
            ViewGroup.LayoutParams params = fill.getLayoutParams();
            params.width = Math.round(trackWidth * progress / 100f);
            fill.setLayoutParams(params);
        }
    }
}
