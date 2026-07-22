package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class SystemStatusView extends LinearLayout {
    static final int HEIGHT_DP = 30;
    static final int GAP_DP = 10;

    private static final int TRACK_COLOR = 0xFF454545;
    private static final int CPU_COLOR = 0xFF7893A0;
    private static final int RAM_COLOR = 0xFFC49A62;
    private static final int TEMPERATURE_COLOR = 0xFFC9786B;

    private final MetricView cpu;
    private final MetricView ram;
    private final MetricView temperature;

    SystemStatusView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        cpu = addMetric("CPU", CPU_COLOR);
        addDivider();
        ram = addMetric("RAM", RAM_COLOR);
        addDivider();
        temperature = addMetric("TEMP", TEMPERATURE_COLOR);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
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

    private MetricView addMetric(String name, int color) {
        MetricView metric = new MetricView(getContext(), name, color);
        addView(metric, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return metric;
    }

    private void addDivider() {
        View divider = new View(getContext());
        divider.setBackgroundColor(0xFF5A5A5A);
        LayoutParams params = new LayoutParams(Ui.dp(getContext(), 1), Ui.dp(getContext(), 20));
        params.leftMargin = Ui.dp(getContext(), 6);
        params.rightMargin = Ui.dp(getContext(), 6);
        params.gravity = Gravity.BOTTOM;
        addView(divider, params);
    }

    private static final class MetricView extends LinearLayout {
        private final String name;
        private final TextView label;
        private final FrameLayout track;
        private final View fill;
        private int progress;

        MetricView(Context context, String name, int color) {
            super(context);
            this.name = name;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);

            label = Ui.text(context, name + " —", 12, Ui.TEXT);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            addView(label, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(context, 18)
            ));

            track = new FrameLayout(context);
            track.setBackground(Ui.rounded(TRACK_COLOR, Ui.dp(context, 3)));
            track.addOnLayoutChangeListener((view, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> updateFillWidth());
            fill = new View(context);
            fill.setBackground(Ui.rounded(color, Ui.dp(context, 3)));
            track.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            LayoutParams trackParams = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(context, 6)
            );
            trackParams.topMargin = Ui.dp(context, 5);
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
