package com.mmwtl.atlasappwidget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/**
 * ACTION_APPWIDGET_CONFIGURE as a short dialog over HOME: the grid and labels that matter when
 * placing the widget, then confirm. MainActivity stays the full settings editor.
 *
 * <p>Columns, rows and labels are written immediately so the preview is honest; cancelling
 * restores them. App selection is edited in AppPickerActivity and is not rolled back.
 */
public final class WidgetSetupActivity extends ScaledActivity {
    private static final String STATE_CHANGED = "setup_changed";
    private static final String STATE_COLUMNS = "setup_original_columns";
    private static final String STATE_ROWS = "setup_original_rows";
    private static final String STATE_LABELS = "setup_original_labels";

    private Prefs prefs;
    private int widgetId;
    private boolean reconfigure;
    private boolean confirmed;
    private boolean changed;
    // Original values, or null when the key was unset and the default applied.
    private Integer originalColumns;
    private Integer originalRows;
    private Boolean originalLabels;
    private FrameLayout previewHost;
    private TextView previewCaption;
    private TextView appsSummary;
    private Button chooseAppsButton;
    private TextView columnsValue;
    private TextView rowsValue;
    private Switch labelsSwitch;
    private Switch widgetModeSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, resultIntent());
        if (!AtlasAppWidgetProvider.owns(this, widgetId)) {
            finish();
            return;
        }
        prefs = new Prefs(this);
        reconfigure = prefs.isWidgetConfigured(widgetId);
        if (savedInstanceState == null) {
            originalColumns = storedInt(Prefs.KEY_COLUMNS);
            originalRows = storedInt(Prefs.KEY_ROWS);
            originalLabels = prefs.raw().contains(Prefs.KEY_SHOW_APP_LABELS)
                    ? prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false) : null;
        } else {
            // A recreated dialog must still roll back to the values from before it opened.
            changed = savedInstanceState.getBoolean(STATE_CHANGED);
            originalColumns = savedInstanceState.containsKey(STATE_COLUMNS)
                    ? savedInstanceState.getInt(STATE_COLUMNS) : null;
            originalRows = savedInstanceState.containsKey(STATE_ROWS)
                    ? savedInstanceState.getInt(STATE_ROWS) : null;
            originalLabels = savedInstanceState.containsKey(STATE_LABELS)
                    ? savedInstanceState.getBoolean(STATE_LABELS) : null;
        }
        setContentView(buildContent());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        getWindow().setLayout(Math.min(screenWidth - Ui.dp(this, 32), Ui.dp(this, 560)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChoices();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_CHANGED, changed);
        if (originalColumns != null) outState.putInt(STATE_COLUMNS, originalColumns);
        if (originalRows != null) outState.putInt(STATE_ROWS, originalRows);
        if (originalLabels != null) outState.putBoolean(STATE_LABELS, originalLabels);
    }

    @Override
    protected void onDestroy() {
        if (prefs != null && isFinishing() && !confirmed && changed) {
            android.content.SharedPreferences.Editor editor = prefs.raw().edit();
            restoreInt(editor, Prefs.KEY_COLUMNS, originalColumns);
            restoreInt(editor, Prefs.KEY_ROWS, originalRows);
            if (originalLabels == null) {
                editor.remove(Prefs.KEY_SHOW_APP_LABELS);
            } else {
                editor.putBoolean(Prefs.KEY_SHOW_APP_LABELS, originalLabels);
            }
            editor.apply();
            AtlasAppWidgetProvider.updateAll(this);
        }
        super.onDestroy();
    }

    private ScrollView buildContent() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 16)));
        card.setPadding(Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 22));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.heading(this, reconfigure
                        ? R.string.widget_setup_title_edit : R.string.widget_setup_title_add, 20),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT_SECONDARY);
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(Ui.dp(this, 48));
        close.setMinHeight(Ui.dp(this, 48));
        close.setContentDescription(getString(R.string.cancel));
        close.setOnClickListener(view -> finish());
        header.addView(close);
        card.addView(header, fullWrap(0));

        previewHost = new FrameLayout(this);
        previewHost.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        previewHost.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        previewHost.setContentDescription(getString(R.string.preview_title));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 120));
        previewParams.topMargin = Ui.dp(this, 8);
        card.addView(previewHost, previewParams);
        previewCaption = Ui.text(this, "", 12, Ui.TEXT_SECONDARY);
        previewCaption.setGravity(Gravity.CENTER);
        card.addView(previewCaption, fullWrap(6));

        LinearLayout apps = new LinearLayout(this);
        apps.setOrientation(LinearLayout.HORIZONTAL);
        apps.setGravity(Gravity.CENTER_VERTICAL);
        appsSummary = Ui.text(this, "", 15, Ui.TEXT);
        apps.addView(appsSummary, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        chooseAppsButton = Ui.button(this, R.string.widget_setup_choose_apps);
        chooseAppsButton.setOnClickListener(view ->
                startActivity(new Intent(this, AppPickerActivity.class)));
        apps.addView(chooseAppsButton);
        card.addView(apps, fullWrap(16));

        columnsValue = addStepper(card, getString(R.string.columns), Prefs.KEY_COLUMNS, 1, 10, 5);
        rowsValue = addStepper(card, getString(R.string.rows), Prefs.KEY_ROWS, 1, 4, 1);

        labelsSwitch = new Switch(this);
        labelsSwitch.setText(R.string.widget_setup_labels);
        labelsSwitch.setTextColor(Ui.TEXT);
        labelsSwitch.setTextSize(15);
        labelsSwitch.setChecked(prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false));
        labelsSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked == prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false)) {
                return;
            }
            prefs.putBoolean(Prefs.KEY_SHOW_APP_LABELS, checked);
            changed = true;
            refreshChoices();
        });
        card.addView(labelsSwitch, fullWrap(12));

        if (!prefs.isWidgetMode()) {
            widgetModeSwitch = new Switch(this);
            widgetModeSwitch.setText(R.string.widget_setup_switch_mode);
            widgetModeSwitch.setTextColor(Ui.TEXT);
            widgetModeSwitch.setTextSize(15);
            widgetModeSwitch.setChecked(true);
            card.addView(widgetModeSwitch, fullWrap(14));
            TextView modeHint = Ui.text(this, R.string.widget_setup_switch_mode_hint, 13,
                    Ui.TEXT_SECONDARY);
            modeHint.setLineSpacing(0, 1.1f);
            card.addView(modeHint, fullWrap(4));
        }

        TextView note = Ui.text(this, R.string.widget_setup_note, 13, Ui.TEXT_SECONDARY);
        note.setLineSpacing(0, 1.1f);
        card.addView(note, fullWrap(16));

        Button add = Ui.button(this, reconfigure ? R.string.done : R.string.widget_setup_add);
        add.setTextSize(17);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        Ui.setButtonAccent(this, add, true);
        add.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        add.setOnClickListener(view -> confirm(false));
        card.addView(add, fullWrap(20));
        Button addAndOpen = Ui.button(this, reconfigure
                ? R.string.widget_setup_open_all : R.string.widget_setup_add_and_open);
        addAndOpen.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        addAndOpen.setOnClickListener(view -> confirm(true));
        card.addView(addAndOpen, fullWrap(10));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(card);
        return scroll;
    }

    /** Compact "label − N +" row writing an int preference in [min, max]. */
    private TextView addStepper(LinearLayout parent, String label, String key,
            int min, int max, int fallback) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(this, label, 15, Ui.TEXT), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Ui.text(this, "", 17, Ui.TEXT);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(Ui.dp(this, 44));
        Button minus = stepButton("−", getString(R.string.widget_setup_decrease, label));
        Button plus = stepButton("+", getString(R.string.widget_setup_increase, label));
        minus.setOnClickListener(view -> step(key, -1, min, max, fallback));
        plus.setOnClickListener(view -> step(key, 1, min, max, fallback));
        row.addView(minus);
        row.addView(value);
        row.addView(plus);
        parent.addView(row, fullWrap(10));
        return value;
    }

    private Button stepButton(String symbol, String description) {
        Button button = Ui.button(this, symbol);
        button.setTextSize(20);
        button.setMinWidth(Ui.dp(this, 52));
        button.setMinimumWidth(Ui.dp(this, 52));
        button.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        button.setContentDescription(description);
        return button;
    }

    private void step(String key, int delta, int min, int max, int fallback) {
        int current = Math.max(min, Math.min(max, prefs.getInt(key, fallback)));
        int next = Math.max(min, Math.min(max, current + delta));
        if (next == prefs.getInt(key, fallback)) {
            return;
        }
        prefs.putInt(key, next);
        changed = true;
        refreshChoices();
    }

    private void confirm(boolean openSettings) {
        if (!AtlasAppWidgetProvider.owns(this, widgetId)) {
            finish();
            return;
        }
        confirmed = true;
        prefs.setWidgetsConfigured(new int[]{widgetId}, true);
        if (widgetModeSwitch != null && widgetModeSwitch.isChecked()) {
            MainActivity.applyDisplayMode(this, prefs, true);
        } else {
            AtlasAppWidgetProvider.updateAll(this);
        }
        setResult(RESULT_OK, resultIntent());
        if (openSettings) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    private void refreshChoices() {
        PanelConfig config = prefs.panelConfig();
        columnsValue.setText(String.valueOf(config.columns));
        rowsValue.setText(String.valueOf(config.rows));
        if (labelsSwitch.isChecked() != config.showAppLabels) {
            labelsSwitch.setChecked(config.showAppLabels);
        }
        List<AppEntry> entries = AppRepository.loadSelectedActivities(this, prefs);
        appsSummary.setText(getString(R.string.widget_setup_apps, entries.size()));
        Ui.setButtonAccent(this, chooseAppsButton, entries.isEmpty());
        renderPreview(entries);
    }

    private void renderPreview(List<AppEntry> entries) {
        Bundle options = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId);
        previewCaption.setText(getString(R.string.widget_preview_size,
                AtlasAppWidgetProvider.widthDp(getApplicationContext(), options),
                AtlasAppWidgetProvider.heightDp(getApplicationContext(), options)));
        if (entries.isEmpty()) {
            previewHost.removeAllViews();
            TextView hint = Ui.text(this, R.string.widget_setup_no_apps, 14, Ui.TEXT_SECONDARY);
            hint.setGravity(Gravity.CENTER);
            previewHost.addView(hint, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            ViewGroup.LayoutParams params = previewHost.getLayoutParams();
            params.height = Ui.dp(this, 120);
            previewHost.setLayoutParams(params);
            return;
        }
        if (previewHost.getWidth() <= 0) {
            // The first render waits for the dialog's own layout, which follows setLayout().
            previewHost.post(() -> {
                if (!isFinishing() && previewHost.getWidth() > 0) {
                    renderPreview(AppRepository.loadSelectedActivities(this, prefs));
                }
            });
            return;
        }
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.28f);
        WidgetPreview.render(getApplicationContext(), previewHost, prefs, entries, options,
                maxHeight);
    }

    private Integer storedInt(String key) {
        return prefs.raw().contains(key) ? prefs.getInt(key, 0) : null;
    }

    private static void restoreInt(android.content.SharedPreferences.Editor editor, String key,
            Integer value) {
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putInt(key, value);
        }
    }

    private Intent resultIntent() {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    }

    private LinearLayout.LayoutParams fullWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, topMarginDp);
        return params;
    }
}
