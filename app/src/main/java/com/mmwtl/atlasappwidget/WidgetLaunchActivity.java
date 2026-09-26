package com.mmwtl.atlasappwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Opens a selected widget item using the same launch rules as the overlay. */
public final class WidgetLaunchActivity extends Activity {
    static final String EXTRA_ENTRY_KEY = "com.mmwtl.atlasappwidget.extra.WIDGET_ENTRY_KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = new Prefs(this);
        String key = getIntent().getStringExtra(EXTRA_ENTRY_KEY);
        if (!prefs.isWidgetMode() || key == null) {
            finish();
            return;
        }
        AppEntry selected = null;
        for (AppEntry entry : AppRepository.loadSelectedActivities(this, prefs)) {
            if (key.equals(entry.componentKey)) {
                selected = entry;
                break;
            }
        }
        if (selected == null) {
            finish();
            return;
        }
        if (selected.isFuel()) {
            startActivity(new Intent(this, WidgetFuelActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }
        Intent target = LaunchIntents.forEntry(selected);
        if (target == null) {
            Toast.makeText(this, getString(R.string.launch_failed, selected.label),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (prefs.isClimateTransitionEnabled(selected.componentKey)) {
            target = DiagnosticLaunchActivity.intentFor(this, target, selected.label,
                    prefs.climateTransitionDurationMs());
        }
        try {
            startActivity(target);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot launch widget entry " + selected.componentKey, error);
            Toast.makeText(this, getString(R.string.launch_failed, selected.label),
                    Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
