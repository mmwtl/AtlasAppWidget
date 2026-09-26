package com.mmwtl.atlasappwidget;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

/** Native floating fuel details, with no overlay or foreground-window monitoring. */
public final class WidgetFuelActivity extends Activity {
    private static final long AUTO_HIDE_MS = 10_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FuelDetailsView details;
    private final Runnable autoHide = this::finish;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            details.update(WidgetFuelService.reading());
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = new Prefs(this);
        if (!prefs.isWidgetMode()
                || !prefs.selectedComponents().contains(AppEntry.FUEL_COMPONENT_KEY)) {
            finish();
            return;
        }
        details = new FuelDetailsView(this, prefs, this::finish);
        setContentView(details);
        getWindow().setLayout(Math.min(Ui.dp(this, 600),
                        Math.round(getWindowManager().getCurrentWindowMetrics()
                                .getBounds().width() * 0.9f)),
                WindowManager.LayoutParams.WRAP_CONTENT);
        setFinishOnTouchOutside(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (details == null) {
            return;
        }
        WidgetFuelService.sync(this);
        refresh.run();
        handler.postDelayed(autoHide, AUTO_HIDE_MS);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refresh);
        handler.removeCallbacks(autoHide);
        super.onStop();
    }
}
