package com.mmwtl.atlasappwidget;

import android.annotation.SuppressLint;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint("ViewConstructor")
final class SystemStatusView extends LinearLayout {
    private static final int MIN_HEIGHT_DP = 30;
    private static final int TEXT_TRACK_GAP_DP = 5;
    private static final int SIDE_TRACK_GAP_DP = 4;
    static final int GAP_DP = 16;

    private static final int TRACK_COLOR = 0xFF454545;
    private static final int CPU_COLOR = 0xFF7893A0;
    private static final int RAM_COLOR = 0xFFC49A62;
    private static final int FUEL_COLOR = 0xFF8BA37A;

    private final MetricView cpu;
    private final MetricView ram;
    private final MetricView fuel;

    SystemStatusView(
            Context context,
            int textSizeSp,
            int textWeight,
            int lineHeightDp,
            int statusHeight,
            boolean sideMode,
            boolean showCpu,
            boolean showRam,
            boolean showFuel
    ) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int labelHeight = labelHeightPixels(context, textSizeSp, textWeight);
        int added = 0;
        if (showCpu) {
            cpu = addMetric("CPU", CPU_COLOR, textSizeSp, textWeight,
                    lineHeightDp, labelHeight, sideMode);
            added++;
        } else {
            cpu = null;
        }
        if (showRam) {
            if (added > 0) {
                addSeparator(statusHeight, sideMode);
            }
            ram = addMetric("RAM", RAM_COLOR, textSizeSp, textWeight,
                    lineHeightDp, labelHeight, sideMode);
            added++;
        } else {
            ram = null;
        }
        if (showFuel) {
            if (added > 0) {
                addSeparator(statusHeight, sideMode);
            }
            fuel = addMetric("FUEL", FUEL_COLOR, textSizeSp, textWeight,
                    lineHeightDp, labelHeight, sideMode);
        } else {
            fuel = null;
        }
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

    static int sideWidthPixels(Context context, int lineHeightDp, int metricCount) {
        int boundedCount = Math.max(1, metricCount);
        return Ui.dp(context, lineHeightDp * boundedCount
                + SIDE_TRACK_GAP_DP * (boundedCount - 1));
    }

    void update(SystemStatusSnapshot snapshot) {
        List<CharSequence> descriptions = new ArrayList<>(3);
        if (cpu != null) {
            cpu.updatePercent(snapshot.cpuPercent);
            descriptions.add(cpu.label.getText());
        }
        if (ram != null) {
            ram.updatePercent(snapshot.ramPercent);
            descriptions.add(ram.label.getText());
        }
        if (fuel != null) {
            fuel.updateLiters(snapshot.fuelLiters, snapshot.fuelPercent);
            descriptions.add(fuel.label.getText());
        }
        setContentDescription(android.text.TextUtils.join(", ", descriptions));
    }

    private MetricView addMetric(
            String name,
            int color,
            int textSizeSp,
            int textWeight,
            int lineHeightDp,
            int labelHeight,
            boolean sideMode
    ) {
        MetricView metric = new MetricView(getContext(), name, color,
                textSizeSp, textWeight, lineHeightDp, labelHeight, sideMode);
        LayoutParams params = sideMode
                ? new LayoutParams(
                Ui.dp(getContext(), lineHeightDp),
                ViewGroup.LayoutParams.MATCH_PARENT)
                : new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        addView(metric, params);
        return metric;
    }

    private void addSideGap() {
        View gap = new View(getContext());
        addView(gap, new LayoutParams(
                Ui.dp(getContext(), SIDE_TRACK_GAP_DP),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void addSeparator(int statusHeight, boolean sideMode) {
        if (sideMode) {
            addSideGap();
        } else {
            addDivider(statusHeight);
        }
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
        private final boolean sideMode;
        private int progress;

        MetricView(
                Context context,
                String name,
                int color,
                int textSizeSp,
                int textWeight,
                int lineHeightDp,
                int labelHeight,
                boolean sideMode
        ) {
            super(context);
            this.name = name;
            this.sideMode = sideMode;
            setGravity(Gravity.CENTER);

            label = Ui.text(context, name + " —", textSizeSp, Ui.TEXT);
            label.setTypeface(Typeface.create(Typeface.DEFAULT, textWeight, false));
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);

            int lineHeight = Ui.dp(context, lineHeightDp);
            track = new FrameLayout(context);
            track.setBackground(Ui.rounded(TRACK_COLOR, lineHeight / 2f));
            track.addOnLayoutChangeListener((view, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> updateFillLength());
            fill = new View(context);
            fill.setBackground(Ui.rounded(color, lineHeight / 2f));
            if (sideMode) {
                FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        Gravity.BOTTOM
                );
                track.addView(fill, fillParams);
                addView(track, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            } else {
                setOrientation(VERTICAL);
                addView(label, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        labelHeight
                ));
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
        }

        void updatePercent(int value) {
            if (value == SystemStatusSnapshot.UNAVAILABLE) {
                updateValue("—", 0);
            } else {
                updateValue(String.format(Locale.getDefault(), "%d%%", value), value);
            }
        }

        void updateLiters(int liters, int percent) {
            if (liters == SystemStatusSnapshot.UNAVAILABLE
                    || percent == SystemStatusSnapshot.UNAVAILABLE) {
                updateValue("—", 0);
            } else {
                updateValue(String.format(Locale.getDefault(), "%d л", liters), percent);
            }
        }

        private void updateValue(String valueText, int valueProgress) {
            label.setText(String.format(
                    Locale.getDefault(), "%s %s", name, valueText));
            progress = Math.max(0, Math.min(100, valueProgress));
            updateFillLength();
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            updateFillLength();
        }

        private void updateFillLength() {
            int trackLength = sideMode ? track.getHeight() : track.getWidth();
            if (trackLength <= 0) {
                return;
            }
            ViewGroup.LayoutParams params = fill.getLayoutParams();
            if (sideMode) {
                params.height = Math.round(trackLength * progress / 100f);
            } else {
                params.width = Math.round(trackLength * progress / 100f);
            }
            fill.setLayoutParams(params);
        }
    }
}
