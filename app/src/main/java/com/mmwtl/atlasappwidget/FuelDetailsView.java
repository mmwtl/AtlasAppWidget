package com.mmwtl.atlasappwidget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressLint("ViewConstructor")
final class FuelDetailsView extends LinearLayout {
    private final Prefs prefs;
    private final FuelTileView tank;
    private final TextView primary;
    private final TextView filled;
    private final TextView free;
    private final TextView formula;
    private final TextView received;

    FuelDetailsView(Context context, Prefs prefs, Runnable onClose) {
        super(context);
        this.prefs = prefs;
        setOrientation(VERTICAL);
        setPadding(Ui.dp(context, 22), Ui.dp(context, 20),
                Ui.dp(context, 22), Ui.dp(context, 18));
        GradientDrawable background = Ui.rounded(0xFA262626, Ui.dp(context, 14));
        background.setStroke(Ui.dp(context, 2), Ui.ACCENT);
        setBackground(background);
        setElevation(Ui.dp(context, 12));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.heading(context, R.string.fuel_details_title, 21);
        header.addView(title, new LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        Button close = Ui.button(context, R.string.close);
        close.setContentDescription(context.getString(R.string.close_fuel_details));
        close.setOnClickListener(view -> onClose.run());
        header.addView(close);
        addView(header);

        LinearLayout summary = new LinearLayout(context);
        summary.setOrientation(HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams summaryParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = Ui.dp(context, 16);
        addView(summary, summaryParams);

        tank = new FuelTileView(context);
        int tankSize = Ui.dp(context, 116);
        LayoutParams tankParams = new LayoutParams(tankSize, tankSize);
        tankParams.rightMargin = Ui.dp(context, 20);
        summary.addView(tank, tankParams);

        LinearLayout values = new LinearLayout(context);
        values.setOrientation(VERTICAL);
        values.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(values, new LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        primary = Ui.heading(context, "", 24);
        values.addView(primary);
        filled = detailLine();
        values.addView(filled);
        free = detailLine();
        values.addView(free);

        formula = detailLine();
        formula.setLineSpacing(0, 1.1f);
        LayoutParams formulaParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        formulaParams.topMargin = Ui.dp(context, 16);
        addView(formula, formulaParams);

        received = detailLine();
        LayoutParams receivedParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        receivedParams.topMargin = Ui.dp(context, 7);
        addView(received, receivedParams);
    }

    void update(FuelLevelProvider.Reading reading) {
        tank.update(reading);
        if (reading == null || !reading.isAvailable()) {
            primary.setText(R.string.fuel_data_unavailable);
            filled.setText(getContext().getString(
                    R.string.fuel_filled_detail,
                    getContext().getString(R.string.value_unavailable)
            ));
            free.setText(getContext().getString(
                    R.string.fuel_free_detail,
                    getContext().getString(R.string.value_unavailable)
            ));
            formula.setText(getContext().getString(
                    R.string.fuel_formula_waiting,
                    formatNumber(prefs.fuelMultiplier()),
                    formatSignedOffset(prefs.fuelOffset())
            ));
            received.setText(R.string.fuel_waiting_for_ginputbridge);
            setContentDescription(getContext().getString(R.string.fuel_data_unavailable));
            return;
        }

        primary.setText(getContext().getString(
                R.string.fuel_primary_detail,
                reading.liters,
                FuelLevelProvider.TANK_CAPACITY_LITERS
        ));
        filled.setText(getContext().getString(
                R.string.fuel_filled_detail,
                getContext().getString(R.string.liters_and_percent,
                        reading.liters, reading.percent)
        ));
        free.setText(getContext().getString(
                R.string.fuel_free_detail,
                getContext().getString(R.string.liters_value, reading.freeLiters)
        ));
        formula.setText(getContext().getString(
                R.string.fuel_formula_detail,
                formatNumber(reading.sensorValue),
                formatNumber(reading.multiplier),
                formatSignedOffset(reading.offset),
                formatNumber(reading.calculatedLiters)
        ));
        String time = reading.receivedAtMillis > 0
                ? DateFormat.getTimeInstance(DateFormat.MEDIUM)
                .format(new Date(reading.receivedAtMillis))
                : getContext().getString(R.string.value_unavailable);
        received.setText(getContext().getString(R.string.fuel_received_detail, time));
        setContentDescription(String.format(
                Locale.getDefault(),
                "%s. %s. %s. %s",
                primary.getText(),
                filled.getText(),
                free.getText(),
                formula.getText()
        ));
    }

    private TextView detailLine() {
        TextView line = Ui.text(getContext(), "", 15, Ui.TEXT_SECONDARY);
        line.setTextIsSelectable(false);
        return line;
    }

    static String formatNumber(float value) {
        if (!Float.isFinite(value)) {
            return "—";
        }
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return String.format(Locale.getDefault(), "%d", Math.round(value));
        }
        String valueText = String.format(Locale.getDefault(), "%.3f", value);
        return valueText.replaceAll("0+$", "").replaceAll("[.,]$", "");
    }

    static String formatSignedOffset(float value) {
        return value < 0
                ? "− " + formatNumber(Math.abs(value))
                : "+ " + formatNumber(value);
    }
}
