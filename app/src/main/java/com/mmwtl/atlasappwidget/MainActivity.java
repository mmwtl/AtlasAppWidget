package com.mmwtl.atlasappwidget;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class MainActivity extends ScaledActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private interface ValueFormatter {
        String format(int value);
    }

    private static final int REQUEST_NOTIFICATIONS = 301;

    private Prefs prefs;
    private TextView overlayStatus;
    private TextView usageStatus;
    private TextView notificationStatus;
    private TextView serviceStatus;
    private TextView selectedSummary;
    private Switch autoStartSwitch;
    private Switch dragHandleSwitch;
    private Switch appLabelsSwitch;
    private Switch systemStatusSwitch;
    private Switch cpuStatusSwitch;
    private Switch ramStatusSwitch;
    private Switch fuelStatusSwitch;
    private Spinner systemStatusPositionSpinner;
    private LinearLayout systemStatusOptions;
    private Button fuelFormulaDisclosureButton;
    private LinearLayout fuelFormulaOptions;
    private TextView fuelFormulaSummary;
    private Switch fuelCustomFormulaSwitch;
    private Button fuelFormulaButton;
    private LinearLayout dragHandleOptions;
    private Switch backgroundStrokeSwitch;
    private FrameLayout previewContainer;
    private Button backgroundColorButton;
    private Button backgroundStrokeColorButton;
    private boolean updatingSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        View content = buildContent();
        setContentView(content);
        Ui.applySystemBarInsets(content);
        prefs.raw().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshPreviewSoon();
    }

    @Override
    protected void onDestroy() {
        prefs.raw().unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        runOnUiThread(() -> {
            if (Prefs.KEY_APP_UI_SCALE_TENTHS.equals(key)) {
                recreate();
                return;
            }
            refreshStatus();
            if (!Prefs.KEY_POSITION_X.equals(key) && !Prefs.KEY_POSITION_Y.equals(key)) {
                refreshPreviewSoon();
            }
        });
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 42));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = Ui.text(this,
                R.string.main_subtitle,
                15,
                Ui.TEXT_SECONDARY
        );
        subtitle.setLineSpacing(0, 1.12f);
        Ui.topMargin(subtitle, 6);
        content.addView(subtitle);

        LinearLayout permissions = Ui.card(this);
        permissions.addView(Ui.heading(this, R.string.permissions_title, 20));
        TextView permissionHint = Ui.text(this,
                R.string.permissions_hint,
                14,
                Ui.TEXT_SECONDARY
        );
        permissionHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(permissionHint, 6);
        permissions.addView(permissionHint);

        overlayStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(overlayStatus, 16);
        permissions.addView(overlayStatus);
        Button overlayButton = Ui.button(this, R.string.allow_overlay);
        Ui.topMargin(overlayButton, 8);
        overlayButton.setOnClickListener(view -> openOverlaySettings());
        permissions.addView(overlayButton);

        usageStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(usageStatus, 16);
        permissions.addView(usageStatus);
        Button usageButton = Ui.button(this, R.string.allow_usage);
        Ui.topMargin(usageButton, 8);
        usageButton.setOnClickListener(view -> openUsageSettings());
        permissions.addView(usageButton);

        notificationStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(notificationStatus, 16);
        permissions.addView(notificationStatus);
        Button notificationButton = Ui.button(this, R.string.allow_notifications);
        Ui.topMargin(notificationButton, 8);
        notificationButton.setOnClickListener(view -> requestNotificationPermission());
        permissions.addView(notificationButton);
        LinearLayout apps = Ui.card(this);
        apps.addView(Ui.heading(this, R.string.panel_apps_title, 20));
        selectedSummary = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(selectedSummary, 8);
        apps.addView(selectedSummary);

        Button appsButton = Ui.button(this, R.string.choose_apps);
        Ui.topMargin(appsButton, 12);
        appsButton.setOnClickListener(view -> startActivity(new Intent(this, AppPickerActivity.class)));
        apps.addView(appsButton);

        appLabelsSwitch = new Switch(this);
        appLabelsSwitch.setText(R.string.show_app_labels);
        appLabelsSwitch.setTextColor(Ui.TEXT);
        appLabelsSwitch.setTextSize(15);
        appLabelsSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        appLabelsSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_SHOW_APP_LABELS, checked);
            }
        });
        LinearLayout systemStatus = Ui.card(this);
        systemStatus.addView(Ui.heading(this, R.string.system_status_title, 20));

        systemStatus.addView(appLabelsSwitch);

        systemStatusSwitch = new Switch(this);
        systemStatusSwitch.setText(R.string.show_system_status);
        systemStatusSwitch.setTextColor(Ui.TEXT);
        systemStatusSwitch.setTextSize(15);
        systemStatusSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        systemStatusSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, checked);
            }
            if (systemStatusOptions != null) {
                setSettingsGroupEnabled(systemStatusOptions, checked);
            }
        });
        systemStatus.addView(systemStatusSwitch);

        systemStatusOptions = settingsGroup();
        systemStatus.addView(systemStatusOptions);

        TextView visibleMetrics = Ui.text(this,
                R.string.system_status_visible_metrics,
                14,
                Ui.TEXT
        );
        Ui.topMargin(visibleMetrics, 8);
        systemStatusOptions.addView(visibleMetrics);
        cpuStatusSwitch = addMetricSwitch(
                systemStatusOptions,
                R.string.show_cpu_status,
                Prefs.KEY_SHOW_CPU_STATUS
        );
        ramStatusSwitch = addMetricSwitch(
                systemStatusOptions,
                R.string.show_ram_status,
                Prefs.KEY_SHOW_RAM_STATUS
        );
        fuelStatusSwitch = addMetricSwitch(
                systemStatusOptions,
                R.string.show_fuel_status,
                Prefs.KEY_SHOW_FUEL_STATUS
        );

        TextView systemStatusPositionLabel = Ui.text(this,
                R.string.system_status_position,
                14,
                Ui.TEXT
        );
        Ui.topMargin(systemStatusPositionLabel, 8);
        systemStatusOptions.addView(systemStatusPositionLabel);
        systemStatusPositionSpinner = new Spinner(this);
        String[] systemStatusPositions = getResources().getStringArray(
                R.array.system_status_positions);
        ArrayAdapter<String> systemStatusPositionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                systemStatusPositions
        );
        systemStatusPositionAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        systemStatusPositionSpinner.setAdapter(systemStatusPositionAdapter);
        systemStatusPositionSpinner.setSelection(Math.max(
                PanelConfig.STATUS_TOP,
                Math.min(PanelConfig.STATUS_RIGHT,
                        prefs.getInt(Prefs.KEY_SYSTEM_STATUS_POSITION,
                                PanelConfig.STATUS_BOTTOM))
        ));
        systemStatusPositionSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        if (!updatingSwitch) {
                            prefs.putInt(Prefs.KEY_SYSTEM_STATUS_POSITION, position);
                        }
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    }
                });
        systemStatusOptions.addView(systemStatusPositionSpinner);

        addSlider(systemStatusOptions, getString(R.string.system_status_line_height),
                PanelConfig.STATUS_LINE_HEIGHT_MIN_DP,
                PanelConfig.STATUS_LINE_HEIGHT_MAX_DP,
                prefs.getInt(Prefs.KEY_SYSTEM_STATUS_LINE_HEIGHT_DP,
                        PanelConfig.STATUS_LINE_HEIGHT_DEFAULT_DP),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_SYSTEM_STATUS_LINE_HEIGHT_DP, value));
        addSlider(systemStatusOptions, getString(R.string.system_status_text_size),
                PanelConfig.STATUS_TEXT_SIZE_MIN_SP,
                PanelConfig.STATUS_TEXT_SIZE_MAX_SP,
                prefs.getInt(Prefs.KEY_SYSTEM_STATUS_TEXT_SIZE_SP,
                        PanelConfig.STATUS_TEXT_SIZE_DEFAULT_SP),
                value -> getString(R.string.sp_value, value),
                value -> prefs.putInt(Prefs.KEY_SYSTEM_STATUS_TEXT_SIZE_SP, value));
        int configuredStatusWeight = prefs.getInt(
                Prefs.KEY_SYSTEM_STATUS_TEXT_WEIGHT,
                PanelConfig.STATUS_TEXT_WEIGHT_DEFAULT
        );
        addSlider(systemStatusOptions, getString(R.string.system_status_text_weight),
                PanelConfig.STATUS_TEXT_WEIGHT_MIN / 100,
                PanelConfig.STATUS_TEXT_WEIGHT_MAX / 100,
                Math.max(PanelConfig.STATUS_TEXT_WEIGHT_MIN,
                        Math.min(PanelConfig.STATUS_TEXT_WEIGHT_MAX,
                                configuredStatusWeight)) / 100,
                value -> formatSystemStatusTextWeight(value * 100),
                value -> prefs.putInt(Prefs.KEY_SYSTEM_STATUS_TEXT_WEIGHT, value * 100));

        TextView systemStatusHint = Ui.text(this,
                R.string.system_status_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        systemStatusHint.setLineSpacing(0, 1.1f);
        systemStatusOptions.addView(systemStatusHint);

        LinearLayout fuelSettings = Ui.card(this);
        fuelSettings.addView(Ui.heading(this, R.string.fuel_settings_title, 20));
        TextView fuelTileHint = Ui.text(
                this,
                R.string.fuel_tile_settings_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        fuelTileHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(fuelTileHint, 8);
        fuelSettings.addView(fuelTileHint);

        fuelFormulaDisclosureButton = Ui.button(
                this,
                R.string.show_fuel_formula_settings
        );
        Ui.topMargin(fuelFormulaDisclosureButton, 10);
        fuelFormulaDisclosureButton.setOnClickListener(view ->
                setFuelFormulaOptionsExpanded(
                        fuelFormulaOptions.getVisibility() != View.VISIBLE
                ));
        fuelSettings.addView(fuelFormulaDisclosureButton);

        fuelFormulaOptions = settingsGroup();
        fuelFormulaOptions.setVisibility(View.GONE);
        fuelSettings.addView(fuelFormulaOptions);
        fuelFormulaSummary = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        fuelFormulaSummary.setLineSpacing(0, 1.1f);
        Ui.topMargin(fuelFormulaSummary, 7);
        fuelFormulaOptions.addView(fuelFormulaSummary);

        fuelCustomFormulaSwitch = new Switch(this);
        fuelCustomFormulaSwitch.setText(R.string.use_custom_fuel_formula);
        fuelCustomFormulaSwitch.setTextColor(Ui.TEXT);
        fuelCustomFormulaSwitch.setTextSize(14);
        fuelCustomFormulaSwitch.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 2));
        fuelCustomFormulaSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_USE_CUSTOM_FUEL_FORMULA, checked);
            }
            updateFuelFormulaControls(checked);
        });
        fuelFormulaOptions.addView(fuelCustomFormulaSwitch);

        fuelFormulaButton = Ui.button(this, R.string.change_fuel_formula);
        Ui.topMargin(fuelFormulaButton, 10);
        fuelFormulaButton.setOnClickListener(view -> showFuelFormulaDialog());
        fuelFormulaOptions.addView(fuelFormulaButton);
        LinearLayout movement = Ui.card(this);
        movement.addView(Ui.heading(this, R.string.movement_title, 20));

        dragHandleSwitch = new Switch(this);
        dragHandleSwitch.setText(R.string.show_drag_handle);
        dragHandleSwitch.setTextColor(Ui.TEXT);
        dragHandleSwitch.setTextSize(15);
        dragHandleSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        dragHandleSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, checked);
            }
            if (dragHandleOptions != null) {
                setSettingsGroupEnabled(dragHandleOptions, checked);
            }
        });
        movement.addView(dragHandleSwitch);

        dragHandleOptions = settingsGroup();
        movement.addView(dragHandleOptions);

        TextView dragHandlePositionLabel = Ui.text(this,
                R.string.drag_handle_position,
                14,
                Ui.TEXT
        );
        Ui.topMargin(dragHandlePositionLabel, 8);
        dragHandleOptions.addView(dragHandlePositionLabel);
        Spinner dragHandlePosition = new Spinner(this);
        String[] dragHandlePositions = getResources().getStringArray(R.array.drag_handle_positions);
        ArrayAdapter<String> dragHandlePositionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                dragHandlePositions
        );
        dragHandlePositionAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        dragHandlePosition.setAdapter(dragHandlePositionAdapter);
        dragHandlePosition.setSelection(Math.max(
                PanelConfig.HANDLE_LEFT,
                Math.min(PanelConfig.HANDLE_BOTTOM,
                        prefs.getInt(Prefs.KEY_DRAG_HANDLE_POSITION, PanelConfig.HANDLE_LEFT))
        ));
        dragHandlePosition.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        prefs.putInt(Prefs.KEY_DRAG_HANDLE_POSITION, position);
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    }
                });
        dragHandleOptions.addView(dragHandlePosition);

        TextView dragHandleHint = Ui.text(this,
                R.string.drag_handle_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        dragHandleHint.setLineSpacing(0, 1.1f);
        dragHandleOptions.addView(dragHandleHint);

        Button resetPosition = Ui.button(this, R.string.reset_panel_position);
        Ui.topMargin(resetPosition, 12);
        resetPosition.setOnClickListener(view -> {
            prefs.putInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
            prefs.putInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
            Toast.makeText(this, R.string.position_reset, Toast.LENGTH_SHORT).show();
        });
        movement.addView(resetPosition);
        LinearLayout service = Ui.card(this);
        service.addView(Ui.heading(this, R.string.service_title, 20));

        serviceStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(serviceStatus, 12);
        service.addView(serviceStatus);

        LinearLayout serviceButtons = new LinearLayout(this);
        serviceButtons.setOrientation(LinearLayout.HORIZONTAL);
        serviceButtons.setGravity(Gravity.START);
        Ui.topMargin(serviceButtons, 10);
        Button startButton = Ui.button(this, R.string.start_panel);
        startButton.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 8)));
        startButton.setOnClickListener(view -> startPanel());
        serviceButtons.addView(startButton);
        Button stopButton = Ui.button(this, R.string.stop);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stopParams.leftMargin = Ui.dp(this, 10);
        serviceButtons.addView(stopButton, stopParams);
        stopButton.setOnClickListener(view -> stopPanel());
        service.addView(serviceButtons);

        autoStartSwitch = new Switch(this);
        autoStartSwitch.setText(R.string.auto_start);
        autoStartSwitch.setTextColor(Ui.TEXT);
        autoStartSwitch.setTextSize(15);
        autoStartSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        autoStartSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSwitch) {
                return;
            }
            prefs.putBoolean(Prefs.KEY_AUTO_START, checked);
            if (!checked && prefs.getLong(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS, 0)
                    > System.currentTimeMillis()) {
                BootReceiver.cancelDelayedStart(this);
                prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
                stopService(new Intent(this, OverlayService.class));
            }
            if (checked && (!Settings.canDrawOverlays(this)
                    || !ForegroundAppDetector.hasUsageAccess(this))) {
                Toast.makeText(this,
                        R.string.auto_start_permission_warning,
                        Toast.LENGTH_LONG).show();
            }
        });
        service.addView(autoStartSwitch);

        addSlider(service, getString(R.string.auto_start_delay),
                Prefs.MIN_AUTO_START_DELAY_SECONDS,
                Prefs.MAX_AUTO_START_DELAY_SECONDS,
                Math.max(Prefs.MIN_AUTO_START_DELAY_SECONDS,
                        Math.min(Prefs.MAX_AUTO_START_DELAY_SECONDS,
                                prefs.getInt(
                                        Prefs.KEY_AUTO_START_DELAY_SECONDS,
                                        Prefs.MIN_AUTO_START_DELAY_SECONDS
                                ))),
                this::formatAutoStartDelay,
                value -> prefs.putInt(Prefs.KEY_AUTO_START_DELAY_SECONDS, value));
        TextView autoStartDelayHint = Ui.text(this,
                R.string.auto_start_delay_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        service.addView(autoStartDelayHint);
        LinearLayout notes = settingsGroup();
        notes.addView(Ui.heading(this, R.string.head_unit_note_title, 18));
        TextView note = Ui.text(this,
                R.string.head_unit_note,
                14,
                Ui.TEXT_SECONDARY
        );
        note.setLineSpacing(0, 1.15f);
        Ui.topMargin(note, 7);
        notes.addView(note);
        service.addView(notes);

        LinearLayout stickyPreview = new LinearLayout(this);
        stickyPreview.setOrientation(LinearLayout.VERTICAL);
        stickyPreview.setClipChildren(false);
        stickyPreview.setPadding(
                Ui.dp(this, 24),
                Ui.dp(this, 16),
                Ui.dp(this, 24),
                Ui.dp(this, 12)
        );
        stickyPreview.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout previewTitleRow = new LinearLayout(this);
        previewTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        previewTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.heading(this, R.string.app_name, 24);
        previewTitleRow.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        TextView previewTitle = Ui.text(this,
                R.string.preview_title,
                13,
                Ui.TEXT_SECONDARY
        );
        previewTitleRow.addView(previewTitle);
        stickyPreview.addView(previewTitleRow);

        previewContainer = new FrameLayout(this);
        previewContainer.setClipChildren(false);
        previewContainer.setClipToPadding(false);
        previewContainer.setPadding(Ui.dp(this, 8), Ui.dp(this, 10),
                Ui.dp(this, 8), Ui.dp(this, 10));
        previewContainer.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        Ui.topMargin(previewContainer, 8);
        stickyPreview.addView(previewContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 110)
        ));

        LinearLayout scale = Ui.card(this);
        scale.addView(Ui.heading(this, R.string.scale_title, 20));
        TextView scaleHint = Ui.text(this,
                R.string.scale_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        scaleHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(scaleHint, 6);
        scale.addView(scaleHint);
        addScaleSlider(scale);

        addSectionHeading(content, R.string.settings_section_system, true);
        content.addView(permissions);
        content.addView(service);

        addSectionHeading(content, R.string.settings_section_content, false);
        content.addView(apps);
        content.addView(fuelSettings);
        content.addView(movement);

        addSectionHeading(content, R.string.settings_section_visual, false);
        content.addView(systemStatus);
        content.addView(buildGeometryCard());
        content.addView(buildBackgroundCard());
        content.addView(scale);

        root.addView(stickyPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        return root;
    }

    private void addSectionHeading(LinearLayout content, int titleRes, boolean first) {
        TextView heading = Ui.heading(this, titleRes, 16);
        heading.setTextColor(Ui.ACCENT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Ui.dp(this, first ? 20 : 14);
        params.bottomMargin = Ui.dp(this, 10);
        heading.setLayoutParams(params);
        content.addView(heading);
    }

    private LinearLayout settingsGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(
                Ui.dp(this, 16),
                Ui.dp(this, 4),
                Ui.dp(this, 16),
                Ui.dp(this, 12)
        );
        group.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Ui.dp(this, 8);
        group.setLayoutParams(params);
        return group;
    }

    private void setSettingsGroupEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                setSettingsGroupEnabled(group.getChildAt(index), enabled);
            }
        }
        if (view == systemStatusOptions || view == dragHandleOptions) {
            view.setAlpha(enabled ? 1f : 0.42f);
        }
    }

    private LinearLayout buildGeometryCard() {
        LinearLayout geometry = Ui.card(this);
        geometry.addView(Ui.heading(this, R.string.geometry_title, 20));
        addSlider(geometry, getString(R.string.panel_width), 25, 100,
                prefs.getInt(Prefs.KEY_WIDTH_PERCENT, 72),
                value -> getString(R.string.screen_percent, value),
                value -> prefs.putInt(Prefs.KEY_WIDTH_PERCENT, value));
        addSlider(geometry, getString(R.string.columns), 1, 10,
                prefs.getInt(Prefs.KEY_COLUMNS, 5), String::valueOf,
                value -> prefs.putInt(Prefs.KEY_COLUMNS, value));
        addSlider(geometry, getString(R.string.rows), 1, 4,
                prefs.getInt(Prefs.KEY_ROWS, 1), String::valueOf,
                value -> prefs.putInt(Prefs.KEY_ROWS, value));
        addSlider(geometry, getString(R.string.icon_size), 40, 240,
                prefs.getInt(Prefs.KEY_ICON_SIZE_DP, 72),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_ICON_SIZE_DP, value));
        addSlider(geometry, getString(R.string.icon_shape), 0, 50,
                prefs.getInt(Prefs.KEY_ICON_CORNER_PERCENT, 12),
                value -> value == 0
                        ? getString(R.string.shape_square)
                        : value == 50
                        ? getString(R.string.shape_circle)
                        : getString(R.string.percent_value, value),
                value -> prefs.putInt(Prefs.KEY_ICON_CORNER_PERCENT, value));
        addSlider(geometry, getString(R.string.padding), 4, 40,
                prefs.getInt(Prefs.KEY_PADDING_DP, 14),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_PADDING_DP, value));
        addSlider(geometry, getString(R.string.icon_gap), 0, 40,
                prefs.getInt(Prefs.KEY_GAP_DP, 12),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_GAP_DP, value));
        return geometry;
    }

    private LinearLayout buildBackgroundCard() {
        LinearLayout background = Ui.card(this);
        background.addView(Ui.heading(this, R.string.background_title, 20));
        addSlider(background, getString(R.string.panel_radius), 0,
                PanelConfig.PANEL_RADIUS_FULLY_ROUNDED,
                prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP, 8),
                value -> value == 0
                        ? getString(R.string.radius_rectangle)
                        : value == PanelConfig.PANEL_RADIUS_FULLY_ROUNDED
                        ? getString(R.string.radius_full)
                        : getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_PANEL_RADIUS_DP, value));
        addSlider(background, getString(R.string.background_opacity), 0, 255,
                prefs.getInt(Prefs.KEY_BACKGROUND_ALPHA, 235),
                value -> getString(R.string.percent_value,
                        Math.round(value * 100f / 255f)),
                value -> prefs.putInt(Prefs.KEY_BACKGROUND_ALPHA, value));

        backgroundColorButton = Ui.button(this, R.string.change_background_color);
        Ui.topMargin(backgroundColorButton, 12);
        backgroundColorButton.setOnClickListener(view -> ColorPickerDialog.show(
                this,
                getString(R.string.background_color),
                prefs.getInt(Prefs.KEY_BACKGROUND_COLOR, 0xFF262626),
                color -> prefs.putInt(Prefs.KEY_BACKGROUND_COLOR, color)
        ));
        background.addView(backgroundColorButton);

        backgroundStrokeSwitch = new Switch(this);
        backgroundStrokeSwitch.setText(R.string.show_background_stroke);
        backgroundStrokeSwitch.setTextColor(Ui.TEXT);
        backgroundStrokeSwitch.setTextSize(15);
        backgroundStrokeSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        backgroundStrokeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_BACKGROUND_STROKE_ENABLED, checked);
            }
        });
        background.addView(backgroundStrokeSwitch);

        backgroundStrokeColorButton = Ui.button(this, R.string.change_stroke_color);
        Ui.topMargin(backgroundStrokeColorButton, 8);
        backgroundStrokeColorButton.setOnClickListener(view -> ColorPickerDialog.show(
                this,
                getString(R.string.stroke_color),
                prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, Ui.ACCENT),
                color -> prefs.putInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, color)
        ));
        background.addView(backgroundStrokeColorButton);
        addSlider(background, getString(R.string.stroke_width), 1, 20,
                prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP, 2),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP, value));
        addSlider(background, getString(R.string.stroke_opacity), 0, 255,
                prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_ALPHA, 200),
                value -> getString(R.string.percent_value,
                        Math.round(value * 100f / 255f)),
                value -> prefs.putInt(Prefs.KEY_BACKGROUND_STROKE_ALPHA, value));
        return background;
    }

    private interface IntListener {
        void onValue(int value);
    }

    private void addSlider(
            LinearLayout parent,
            String label,
            int min,
            int max,
            int current,
            ValueFormatter formatter,
            IntListener listener
    ) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.topMargin(header, 15);
        TextView name = Ui.text(this, label, 14, Ui.TEXT);
        header.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueLabel = Ui.text(this, formatter.format(current), 14, Ui.TEXT_SECONDARY);
        valueLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(valueLabel);
        parent.addView(header);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMin(min);
        seekBar.setMax(max);
        seekBar.setProgress(Math.max(min, Math.min(max, current)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                valueLabel.setText(formatter.format(value));
                if (fromUser) {
                    listener.onValue(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addScaleSlider(LinearLayout parent) {
        int current = configuredScaleTenths(this);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.topMargin(header, 15);
        TextView name = Ui.text(this, R.string.scale, 14, Ui.TEXT);
        header.addView(name, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        TextView valueLabel = Ui.text(this, formatScale(current), 14, Ui.TEXT_SECONDARY);
        valueLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(valueLabel);
        parent.addView(header);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMin(MIN_SCALE_TENTHS);
        seekBar.setMax(MAX_SCALE_TENTHS);
        seekBar.setProgress(current);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                valueLabel.setText(formatScale(value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = seekBar.getProgress();
                if (value != configuredScaleTenths(MainActivity.this)) {
                    prefs.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, value);
                }
            }
        });
        parent.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private String formatScale(int tenths) {
        return tenths % 10 == 0
                ? tenths / 10 + "×"
                : tenths / 10 + "." + tenths % 10 + "×";
    }

    private String formatSystemStatusTextWeight(int weight) {
        int label;
        if (weight <= 300) {
            label = R.string.font_weight_thin;
        } else if (weight <= 500) {
            label = R.string.font_weight_normal;
        } else if (weight <= 700) {
            label = R.string.font_weight_semibold;
        } else {
            label = R.string.font_weight_bold;
        }
        return getString(label, weight);
    }

    private String formatAutoStartDelay(int seconds) {
        if (seconds == 0) {
            return getString(R.string.delay_none);
        }
        if (seconds < 60) {
            return getString(R.string.delay_seconds, seconds);
        }
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return remainder == 0
                ? getString(R.string.delay_minutes, minutes)
                : getString(R.string.delay_minutes_seconds, minutes, remainder);
    }

    private void refreshStatus() {
        boolean overlayAllowed = Settings.canDrawOverlays(this);
        boolean usageAllowed = ForegroundAppDetector.hasUsageAccess(this);
        setStatus(overlayStatus,
                getString(overlayAllowed
                        ? R.string.status_overlay_allowed : R.string.status_overlay_denied),
                overlayAllowed);
        setStatus(usageStatus,
                getString(usageAllowed
                        ? R.string.status_usage_allowed : R.string.status_usage_denied),
                usageAllowed);

        boolean notificationAllowed = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        setStatus(notificationStatus,
                getString(notificationAllowed
                        ? R.string.status_notifications_allowed
                        : R.string.status_notifications_denied),
                notificationAllowed);

        boolean serviceEnabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        boolean serviceRunning = OverlayService.isRunning();
        boolean autoStartPending = serviceEnabled
                && prefs.getLong(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS, 0)
                > System.currentTimeMillis();
        setStatus(serviceStatus,
                autoStartPending
                        ? getString(R.string.status_boot_delay)
                        : serviceEnabled && serviceRunning
                        ? getString(R.string.status_service_running)
                        : serviceEnabled
                        ? getString(R.string.status_service_waiting)
                        : getString(R.string.status_service_stopped),
                serviceEnabled && (serviceRunning || autoStartPending));
        updatingSwitch = true;
        autoStartSwitch.setChecked(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
        boolean showDragHandle = prefs.getBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, true);
        dragHandleSwitch.setChecked(showDragHandle);
        appLabelsSwitch.setChecked(prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false));
        boolean showSystemStatus = prefs.getBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, false);
        systemStatusSwitch.setChecked(showSystemStatus);
        cpuStatusSwitch.setChecked(prefs.getBoolean(Prefs.KEY_SHOW_CPU_STATUS, true));
        ramStatusSwitch.setChecked(prefs.getBoolean(Prefs.KEY_SHOW_RAM_STATUS, true));
        fuelStatusSwitch.setChecked(prefs.getBoolean(Prefs.KEY_SHOW_FUEL_STATUS, true));
        boolean useCustomFuelFormula = prefs.getBoolean(
                Prefs.KEY_USE_CUSTOM_FUEL_FORMULA,
                false
        );
        fuelCustomFormulaSwitch.setChecked(useCustomFuelFormula);
        systemStatusPositionSpinner.setSelection(Math.max(
                PanelConfig.STATUS_TOP,
                Math.min(PanelConfig.STATUS_RIGHT,
                        prefs.getInt(Prefs.KEY_SYSTEM_STATUS_POSITION,
                                PanelConfig.STATUS_BOTTOM))
        ));
        backgroundStrokeSwitch.setChecked(
                prefs.getBoolean(Prefs.KEY_BACKGROUND_STROKE_ENABLED, false));
        updatingSwitch = false;
        setSettingsGroupEnabled(systemStatusOptions, showSystemStatus);
        setSettingsGroupEnabled(dragHandleOptions, showDragHandle);
        updateFuelFormulaControls(useCustomFuelFormula);
        refreshFuelFormulaSummary();

        List<String> selected = prefs.selectedComponents();
        PanelConfig config = prefs.panelConfig();
        int capacity = config.rows * config.columns;
        selectedSummary.setText(getString(
                selected.size() > capacity
                        ? R.string.selected_overflow_summary
                        : R.string.selected_summary,
                selected.size(),
                capacity
        ));

        updateColorButton(
                backgroundColorButton,
                getString(R.string.background_color),
                prefs.getInt(Prefs.KEY_BACKGROUND_COLOR, 0xFF262626)
        );
        updateColorButton(
                backgroundStrokeColorButton,
                getString(R.string.stroke_color),
                prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, Ui.ACCENT)
        );
    }

    private Switch addMetricSwitch(
            LinearLayout parent,
            int labelResource,
            String preferenceKey
    ) {
        Switch metric = new Switch(this);
        metric.setText(labelResource);
        metric.setTextColor(Ui.TEXT);
        metric.setTextSize(14);
        metric.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 2));
        metric.setOnCheckedChangeListener((button, checked) ->
                onMetricVisibilityChanged(metric, preferenceKey, checked));
        parent.addView(metric);
        return metric;
    }

    private void onMetricVisibilityChanged(
            Switch source,
            String preferenceKey,
            boolean checked
    ) {
        if (updatingSwitch) {
            return;
        }
        boolean cpu = Prefs.KEY_SHOW_CPU_STATUS.equals(preferenceKey)
                ? checked : prefs.getBoolean(Prefs.KEY_SHOW_CPU_STATUS, true);
        boolean ram = Prefs.KEY_SHOW_RAM_STATUS.equals(preferenceKey)
                ? checked : prefs.getBoolean(Prefs.KEY_SHOW_RAM_STATUS, true);
        boolean fuel = Prefs.KEY_SHOW_FUEL_STATUS.equals(preferenceKey)
                ? checked : prefs.getBoolean(Prefs.KEY_SHOW_FUEL_STATUS, true);
        if (!cpu && !ram && !fuel) {
            updatingSwitch = true;
            source.setChecked(true);
            updatingSwitch = false;
            Toast.makeText(this,
                    R.string.keep_one_system_status,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.putBoolean(preferenceKey, checked);
    }

    private void refreshFuelFormulaSummary() {
        if (fuelFormulaSummary == null) {
            return;
        }
        boolean custom = prefs.getBoolean(Prefs.KEY_USE_CUSTOM_FUEL_FORMULA, false);
        float multiplier = prefs.fuelMultiplier();
        float offset = prefs.fuelOffset();
        fuelFormulaSummary.setText(getString(
                custom
                        ? R.string.fuel_custom_formula_summary
                        : R.string.fuel_default_formula_summary,
                FuelDetailsView.formatNumber(multiplier),
                FuelDetailsView.formatSignedOffset(offset)
        ));
    }

    private void updateFuelFormulaControls(boolean custom) {
        if (fuelFormulaButton != null) {
            fuelFormulaButton.setVisibility(custom ? View.VISIBLE : View.GONE);
        }
        refreshFuelFormulaSummary();
    }

    private void setFuelFormulaOptionsExpanded(boolean expanded) {
        if (fuelFormulaOptions == null || fuelFormulaDisclosureButton == null) {
            return;
        }
        fuelFormulaOptions.setVisibility(expanded ? View.VISIBLE : View.GONE);
        fuelFormulaDisclosureButton.setText(expanded
                ? R.string.hide_fuel_formula_settings
                : R.string.show_fuel_formula_settings);
    }

    private void showFuelFormulaDialog() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(Ui.dp(this, 24), Ui.dp(this, 8),
                Ui.dp(this, 24), 0);

        TextView explanation = Ui.text(
                this,
                R.string.fuel_formula_dialog_hint,
                14,
                Ui.TEXT_SECONDARY
        );
        explanation.setLineSpacing(0, 1.1f);
        fields.addView(explanation);

        TextView multiplierLabel = Ui.text(
                this, R.string.fuel_multiplier, 14, Ui.TEXT);
        Ui.topMargin(multiplierLabel, 12);
        fields.addView(multiplierLabel);
        EditText multiplier = formulaField(
                prefs.getFloat(
                        Prefs.KEY_FUEL_MULTIPLIER,
                        FuelLevelProvider.DEFAULT_MULTIPLIER
                )
        );
        fields.addView(multiplier);
        TextView offsetLabel = Ui.text(
                this, R.string.fuel_offset, 14, Ui.TEXT);
        Ui.topMargin(offsetLabel, 12);
        fields.addView(offsetLabel);
        EditText offset = formulaField(
                prefs.getFloat(
                        Prefs.KEY_FUEL_OFFSET,
                        FuelLevelProvider.DEFAULT_OFFSET
                )
        );
        fields.addView(offset);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.fuel_formula_dialog_title)
                .setView(fields)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    Float parsedMultiplier = parseFormulaValue(multiplier);
                    Float parsedOffset = parseFormulaValue(offset);
                    if (parsedMultiplier == null || parsedOffset == null) {
                        return;
                    }
                    if (Math.abs(parsedMultiplier) > 100f) {
                        multiplier.setError(getString(R.string.fuel_multiplier_range));
                        return;
                    }
                    if (Math.abs(parsedOffset) > 1_000f) {
                        offset.setError(getString(R.string.fuel_offset_range));
                        return;
                    }
                    prefs.putFuelFormula(parsedMultiplier, parsedOffset);
                    dialog.dismiss();
                }));
        dialog.show();
        if (dialog.getWindow() != null) {
            int availableWidth = Math.max(
                    1,
                    getWindowManager().getCurrentWindowMetrics().getBounds().width()
                            - Ui.dp(this, 32)
            );
            dialog.getWindow().setLayout(
                    Math.min(availableWidth, Ui.dp(this, 640)),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private EditText formulaField(float value) {
        EditText field = new EditText(this);
        field.setText(FuelDetailsView.formatNumber(value));
        field.setSelectAllOnFocus(true);
        field.setSingleLine(true);
        field.setTextColor(Ui.TEXT);
        field.setHintTextColor(Ui.TEXT_SECONDARY);
        field.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Ui.dp(this, 2);
        field.setLayoutParams(params);
        return field;
    }

    private Float parseFormulaValue(EditText field) {
        String value = field.getText().toString().trim().replace(',', '.');
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isFinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Error is attached to the corresponding field below.
        }
        field.setError(getString(R.string.fuel_formula_invalid));
        return null;
    }

    private void updateColorButton(Button button, String label, int color) {
        button.setText(String.format("%s  #%06X", label, color & 0xFFFFFF));
        button.setBackground(Ui.rounded(color, Ui.dp(this, 8)));
        double luminance = (0.2126 * Color.red(color)
                + 0.7152 * Color.green(color)
                + 0.0722 * Color.blue(color)) / 255.0;
        button.setTextColor(luminance > 0.62 ? Color.BLACK : Color.WHITE);
    }

    private void setStatus(TextView view, String text, boolean good) {
        view.setText(text);
        view.setTextColor(good ? Ui.SUCCESS : Ui.WARNING);
    }

    private void refreshPreviewSoon() {
        if (previewContainer == null) {
            return;
        }
        previewContainer.post(this::refreshPreview);
    }

    private void refreshPreview() {
        if (isFinishing() || previewContainer.getWidth() <= 0) {
            return;
        }
        int available = Math.max(1,
                previewContainer.getWidth() - previewContainer.getPaddingLeft() - previewContainer.getPaddingRight());
        PanelView panelPreview = new PanelView(
                getApplicationContext(),
                prefs,
                prefs.panelConfig(),
                AppRepository.loadSelectedActivities(this, prefs),
                true,
                available,
                getWindowManager().getCurrentWindowMetrics().getBounds().height(),
                null
        );
        previewContainer.removeAllViews();
        int maxContainerHeight = Math.max(
                Ui.dp(this, 96),
                Math.round(getWindowManager().getCurrentWindowMetrics().getBounds().height() * 0.30f)
        );
        int verticalPadding = previewContainer.getPaddingTop()
                + previewContainer.getPaddingBottom();
        int maxPanelHeight = Math.max(1, maxContainerHeight - verticalPadding);
        float previewScale = Math.min(
                1f,
                maxPanelHeight / (float) Math.max(1, panelPreview.panelHeight())
        );
        panelPreview.setScaleX(previewScale);
        panelPreview.setScaleY(previewScale);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelPreview.panelWidth(),
                panelPreview.panelHeight(),
                Gravity.CENTER
        );
        previewContainer.addView(panelPreview, panelParams);
        ViewGroup.LayoutParams containerParams = previewContainer.getLayoutParams();
        containerParams.height = Math.round(panelPreview.panelHeight() * previewScale)
                + verticalPadding;
        previewContainer.setLayoutParams(containerParams);
    }

    private void startPanel() {
        if (AppRepository.loadSelectedActivities(this, prefs).isEmpty()) {
            Toast.makeText(this, R.string.select_app_first, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AppPickerActivity.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.allow_overlay_first, Toast.LENGTH_SHORT).show();
            openOverlaySettings();
            return;
        }
        if (!ForegroundAppDetector.hasUsageAccess(this)) {
            Toast.makeText(this, R.string.allow_usage_first, Toast.LENGTH_SHORT).show();
            openUsageSettings();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission();
        }
        try {
            BootReceiver.cancelDelayedStart(this);
            prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
            OverlayService.start(this);
            Toast.makeText(this, R.string.panel_started, Toast.LENGTH_SHORT).show();
            serviceStatus.postDelayed(this::refreshStatus, 300L);
        } catch (RuntimeException error) {
            AppLog.warn("Manual foreground service start failed", error);
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            Toast.makeText(this, R.string.service_start_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void stopPanel() {
        BootReceiver.cancelDelayedStart(this);
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, R.string.panel_stopped, Toast.LENGTH_SHORT).show();
    }

    private void openOverlaySettings() {
        Intent perApp = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        try {
            startActivity(perApp);
        } catch (ActivityNotFoundException ignored) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void openUsageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, R.string.no_usage_settings, Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
        } else {
            Toast.makeText(this, R.string.notification_permission_not_needed,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
