package com.mmwtl.atlasappwidget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import java.util.Locale;

@SuppressLint("ViewConstructor")
final class FuelTileView extends View {
    private final FuelTileDrawable drawable;

    FuelTileView(Context context) {
        super(context);
        drawable = new FuelTileDrawable(
                context.getString(R.string.fuel_free_short),
                context.getString(R.string.fuel_filled_short)
        );
        setBackground(drawable);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        update(FuelLevelProvider.Reading.unavailable());
    }

    void update(FuelLevelProvider.Reading reading) {
        drawable.update(reading);
        if (reading == null || !reading.isAvailable()) {
            setContentDescription(getContext().getString(R.string.fuel_data_unavailable));
        } else {
            setContentDescription(String.format(
                    Locale.getDefault(),
                    getContext().getString(R.string.fuel_tile_content_description),
                    reading.freeLiters,
                    reading.liters
            ));
        }
        invalidate();
    }

    void showPreview() {
        drawable.showPreview();
        setContentDescription(String.format(
                Locale.getDefault(),
                getContext().getString(R.string.fuel_tile_content_description),
                FuelLevelProvider.TANK_CAPACITY_LITERS - 42,
                42
        ));
        invalidate();
    }
}
