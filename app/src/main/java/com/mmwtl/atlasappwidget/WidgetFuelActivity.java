package com.mmwtl.atlasappwidget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;

/** Fuel details for a home-screen widget; it does not require overlay permission. */
public final class WidgetFuelActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FuelLevelProvider fuel;
    private FuelDetailsView details;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (fuel != null && details != null) {
                details.update(fuel.reading());
                handler.postDelayed(this, 1_000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = new Prefs(this);
        fuel = new FuelLevelProvider(this, prefs);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(23, 23, 23));
        details = new FuelDetailsView(this, prefs, this::finish);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(Ui.dp(this, 600),
                        Math.round(getResources().getDisplayMetrics().widthPixels * 0.9f)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(details, params);
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        WidgetFuelService.sync(this);
        fuel.start();
        refresh.run();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refresh);
        fuel.stop();
        super.onStop();
    }
}
