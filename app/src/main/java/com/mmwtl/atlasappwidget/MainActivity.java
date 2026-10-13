package com.mmwtl.atlasappwidget;

import android.Manifest;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends ScaledActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private interface ValueFormatter {
        String format(int value);
    }

    private static final int REQUEST_NOTIFICATIONS = 301;
    private static final int REQUEST_IMPORT_SETTINGS = 303;
    private static final String STATE_TAB = "settings_tab";
    private static final int TAB_PANEL = 0;
    private static final int TAB_SHORTCUTS = 1;
    private static final int TAB_LOOK = 2;
    private static final int TAB_SYSTEM = 3;
    private static final int[] TAB_LABELS = {R.string.tab_panel, R.string.tab_shortcuts,
            R.string.tab_look, R.string.tab_system};
    private static final long ACCESSIBILITY_STATUS_REFRESH_DELAY_MS = 1_000L;
    private static final long POSITION_SAVE_DEBOUNCE_DELAY_MS = 400L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private Prefs prefs;
    private final LinearLayout[] tabPages = new LinearLayout[TAB_LABELS.length];
    private final TextView[] tabButtons = new TextView[TAB_LABELS.length];
    private int selectedTab;
    private ScrollView settingsScroll;
    private TextView overlayModeSegment;
    private TextView widgetModeSegment;
    private TextView displayModeHint;
    private TextView widgetPlacementStatus;
    private Button overlayPermissionButton;
    private Button usagePermissionButton;
    private Button accessibilityPermissionButton;
    private Button notificationPermissionButton;
    private TextView serviceStatus;
    private TextView selectedSummary;
    private Button startPanelButton;
    private Button stopPanelButton;
    private Button addWidgetButton;
    private Switch autoStartSwitch;
    private Switch dragHandleSwitch;
    private Switch appLabelsSwitch;
    private Switch appLabelOutlineSwitch;
    private Switch showOnlyInAppListSwitch;
    private Switch systemStatusSwitch;
    private Switch cpuStatusSwitch;
    private Switch ramStatusSwitch;
    private Switch fuelStatusSwitch;
    private Spinner systemStatusPositionSpinner;
    private LinearLayout systemStatusOptions;
    private LinearLayout systemStatusFineTune;
    private TextView fuelFormulaSummary;
    private Switch fuelCustomFormulaSwitch;
    private Button fuelFormulaButton;
    private LinearLayout dragHandleOptions;
    private Spinner positionCornerSpinner;
    private EditText positionX;
    private EditText positionY;
    private OverlayCorner displayedPositionCorner;
    private boolean refreshingPosition;
    private boolean suppressPositionWatchers;
    private boolean positionDirty;
    private int lastValidPositionX;
    private int lastValidPositionY;
    private boolean hasLastValidPosition;
    private boolean savingPosition;
    private Switch backgroundStrokeSwitch;
    private FrameLayout previewContainer;
    private TextView previewTitle;
    private final List<View> overlayOnlyViews = new ArrayList<>();
    private final List<View> widgetOnlyViews = new ArrayList<>();
    private TextView widgetGeometryHint;
    private Button backgroundColorButton;
    private Button backgroundStrokeColorButton;
    private Switch manualHeightSwitch;
    private SeekBar manualHeightSlider;
    private EditText manualHeightInput;
    private TextView manualHeightRange;
    private boolean updatingManualHeight;
    private Button exportSettingsButton;
    private Button importSettingsButton;
    private SeekBar climateTransitionDurationSlider;
    private TextView climateTransitionDurationValue;
    private boolean updatingSwitch;
    private volatile boolean applyingSettings;
    private final Runnable delayedAccessibilityStatusRefresh = this::refreshStatus;
    private final Runnable delayedPositionSave = this::savePosition;
    private final Runnable delayedWidgetRefresh = () -> AtlasAppWidgetProvider.updateAll(this);
    private final Runnable accessibilityStateRefresh = () ->
            main.post(delayedAccessibilityStatusRefresh);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        View content = buildContent(savedInstanceState == null
                ? TAB_PANEL : savedInstanceState.getInt(STATE_TAB, TAB_PANEL));
        setContentView(content);
        Ui.applySystemBarInsets(content);
        prefs.raw().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AccessibilityWindowState.setStateListener(accessibilityStateRefresh);
        refreshStatus();
        refreshPositionControls();
        main.removeCallbacks(delayedAccessibilityStatusRefresh);
        main.postDelayed(delayedAccessibilityStatusRefresh,
                ACCESSIBILITY_STATUS_REFRESH_DELAY_MS);
        refreshPreviewSoon();
        AtlasAppWidgetProvider.updateAll(this);
    }

    @Override
    protected void onPause() {
        AccessibilityWindowState.clearStateListener(accessibilityStateRefresh);
        main.removeCallbacks(delayedAccessibilityStatusRefresh);
        main.removeCallbacks(delayedPositionSave);
        main.removeCallbacks(delayedWidgetRefresh);
        savePosition();
        AtlasAppWidgetProvider.updateAll(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        prefs.raw().unregisterOnSharedPreferenceChangeListener(this);
        main.removeCallbacksAndMessages(null);
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_SETTINGS && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            readSettingsForImport(data.getData());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (applyingSettings) {
            return;
        }
        runOnUiThread(() -> {
            if (Prefs.KEY_APP_UI_SCALE_TENTHS.equals(key)) {
                recreate();
                return;
            }
            refreshStatus();
            if (isManualHeightKey(key)) {
                updateManualHeightControls();
            }
            if (!Prefs.KEY_POSITION_X.equals(key) && !Prefs.KEY_POSITION_Y.equals(key)
                    && !Prefs.KEY_POSITION_CORNER.equals(key)) {
                refreshPreviewSoon();
            }
            if (isPositionGeometryKey(key)) {
                if (isPositionKey(key) && savingPosition) {
                    return;
                }
                refreshPositionControls();
            }
            main.removeCallbacks(delayedWidgetRefresh);
            main.postDelayed(delayedWidgetRefresh, 300L);
        });
    }

    private View buildContent(int initialTab) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout stickyHeader = new LinearLayout(this);
        stickyHeader.setOrientation(LinearLayout.VERTICAL);
        stickyHeader.setClipChildren(false);
        stickyHeader.setPadding(
                Ui.dp(this, 24),
                Ui.dp(this, 16),
                Ui.dp(this, 24),
                Ui.dp(this, 12)
        );
        stickyHeader.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.heading(this, R.string.app_name, 24);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        previewTitle = Ui.text(this,
                R.string.preview_title,
                13,
                Ui.TEXT_SECONDARY
        );
        titleRow.addView(previewTitle);
        ImageView donate = new ImageView(this);
        donate.setImageResource(R.drawable.ic_heart);
        donate.setColorFilter(Ui.ACCENT);
        int heartPad = Ui.dp(this, 8);
        donate.setPadding(heartPad, heartPad, heartPad, heartPad);
        donate.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 8)));
        donate.setContentDescription(getString(R.string.donate_title));
        donate.setOnClickListener(view -> DonateDialog.show(this));
        LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40));
        heartParams.leftMargin = Ui.dp(this, 12);
        titleRow.addView(donate, heartParams);
        stickyHeader.addView(titleRow);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < TAB_LABELS.length; index++) {
            int tab = index;
            TextView button = Ui.segment(this, getString(TAB_LABELS[index]));
            button.setTextSize(15);
            button.setOnClickListener(view -> selectTab(tab));
            tabButtons[index] = button;
            Ui.addSegment(tabs, button);
        }
        // Tabs sit above the preview so they stay put when a tab hides the preview.
        Ui.topMargin(tabs, 12);
        stickyHeader.addView(tabs);

        previewContainer = new FrameLayout(this);
        previewContainer.setClipChildren(false);
        previewContainer.setClipToPadding(false);
        previewContainer.setPadding(Ui.dp(this, 8), Ui.dp(this, 10),
                Ui.dp(this, 8), Ui.dp(this, 10));
        previewContainer.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        previewContainer.setContentDescription(getString(R.string.preview_title));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 110)
        );
        previewParams.topMargin = Ui.dp(this, 10);
        stickyHeader.addView(previewContainer, previewParams);

        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 42));
        settingsScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (int index = 0; index < tabPages.length; index++) {
            tabPages[index] = new LinearLayout(this);
            tabPages[index].setOrientation(LinearLayout.VERTICAL);
            content.addView(tabPages[index], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        buildPanelTab(tabPages[TAB_PANEL]);
        buildShortcutsTab(tabPages[TAB_SHORTCUTS]);
        buildLookTab(tabPages[TAB_LOOK]);
        buildSystemTab(tabPages[TAB_SYSTEM]);

        root.addView(stickyHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(settingsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        selectTab(initialTab >= 0 && initialTab < tabPages.length ? initialTab : TAB_PANEL);
        return root;
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        for (int index = 0; index < tabPages.length; index++) {
            tabPages[index].setVisibility(index == tab ? View.VISIBLE : View.GONE);
            Ui.setSegmentSelected(this, tabButtons[index], index == tab);
        }
        boolean preview = tab == TAB_SHORTCUTS || tab == TAB_LOOK;
        previewContainer.setVisibility(preview ? View.VISIBLE : View.GONE);
        previewTitle.setVisibility(preview ? View.VISIBLE : View.GONE);
        settingsScroll.scrollTo(0, 0);
        // A hidden preview is not redrawn on preference changes; catch up on return.
        refreshPreviewSoon();
    }

    private void buildPanelTab(LinearLayout page) {
        page.addView(buildDisplayModeCard());
        LinearLayout placement = buildWidgetPlacementCard();
        page.addView(placement);
        widgetOnlyViews.add(placement);
        LinearLayout overlay = buildOverlayCard();
        page.addView(overlay);
        overlayOnlyViews.add(overlay);
        LinearLayout movement = buildMovementCard();
        page.addView(movement);
        overlayOnlyViews.add(movement);
        LinearLayout visibility = buildWindowVisibilityCard();
        page.addView(visibility);
        overlayOnlyViews.add(visibility);
    }

    private void buildShortcutsTab(LinearLayout page) {
        LinearLayout apps = Ui.card(this);
        apps.addView(Ui.heading(this, R.string.panel_apps_title, 20));
        selectedSummary = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(selectedSummary, 8);
        apps.addView(selectedSummary);
        Button appsButton = Ui.button(this, R.string.choose_apps);
        Ui.topMargin(appsButton, 12);
        appsButton.setOnClickListener(view -> startActivity(new Intent(this, AppPickerActivity.class)));
        apps.addView(appsButton);
        page.addView(apps);

        page.addView(buildAppLabelsCard());
        addClimateTransitionDurationSlider(collapsibleCard(page,
                R.string.climate_transition_title, R.string.climate_transition_summary));
        page.addView(buildFuelCard());
    }

    private void buildLookTab(LinearLayout page) {
        LinearLayout preset = Ui.card(this);
        preset.addView(Ui.heading(this, R.string.oneos_preset_title, 20));
        TextView presetHint = Ui.text(this, R.string.oneos_preset_hint, 13, Ui.TEXT_SECONDARY);
        presetHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(presetHint, 6);
        preset.addView(presetHint);
        Button oneOsPresetButton = Ui.button(this, R.string.apply_oneos_preset);
        Ui.topMargin(oneOsPresetButton, 12);
        oneOsPresetButton.setOnClickListener(view -> {
            prefs.applyOneOsPreset();
            recreate();
        });
        preset.addView(oneOsPresetButton);
        page.addView(preset);

        page.addView(buildGridCard());
        LinearLayout panelSize = buildPanelSizeCard();
        page.addView(panelSize);
        overlayOnlyViews.add(panelSize);
        page.addView(buildBackgroundCard());
        page.addView(buildSystemStatusCard());
    }

    private void buildSystemTab(LinearLayout page) {
        LinearLayout notifications = Ui.card(this);
        notifications.addView(Ui.heading(this, R.string.notifications_title, 20));
        TextView notificationsHint = Ui.text(this,
                R.string.notifications_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        notificationsHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(notificationsHint, 6);
        notifications.addView(notificationsHint);
        notificationPermissionButton = permissionButton(notifications,
                R.string.allow_notifications, view -> requestNotificationPermission());
        page.addView(notifications);

        LinearLayout settingsBackup = Ui.card(this);
        settingsBackup.addView(Ui.heading(this, R.string.settings_backup_title, 20));
        TextView settingsBackupHint = Ui.text(this,
                R.string.settings_backup_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        settingsBackupHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(settingsBackupHint, 8);
        settingsBackup.addView(settingsBackupHint);
        exportSettingsButton = Ui.button(this, R.string.export_settings);
        Ui.topMargin(exportSettingsButton, 12);
        exportSettingsButton.setOnClickListener(view -> exportSettings());
        settingsBackup.addView(exportSettingsButton);
        importSettingsButton = Ui.button(this, R.string.import_settings);
        Ui.topMargin(importSettingsButton, 10);
        importSettingsButton.setOnClickListener(view -> chooseSettingsImport());
        settingsBackup.addView(importSettingsButton);
        page.addView(settingsBackup);

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
        page.addView(scale);

        LinearLayout donate = Ui.card(this);
        donate.addView(Ui.heading(this, R.string.donate_title, 20));
        TextView donateHint = Ui.text(this,
                R.string.donate_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        donateHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(donateHint, 6);
        donate.addView(donateHint);
        Button donateButton = Ui.button(this, R.string.donate_button);
        Ui.topMargin(donateButton, 12);
        donateButton.setOnClickListener(view -> DonateDialog.show(this));
        donate.addView(donateButton);
        page.addView(donate);
    }

    private LinearLayout buildDisplayModeCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.display_mode_title, 20));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        overlayModeSegment = Ui.segment(this, getString(R.string.display_mode_overlay));
        overlayModeSegment.setOnClickListener(view -> changeDisplayMode(false));
        Ui.addSegment(modes, overlayModeSegment);
        widgetModeSegment = Ui.segment(this, getString(R.string.display_mode_widget));
        widgetModeSegment.setOnClickListener(view -> changeDisplayMode(true));
        Ui.addSegment(modes, widgetModeSegment);
        Ui.topMargin(modes, 12);
        card.addView(modes);
        displayModeHint = Ui.text(this, "", 13, Ui.TEXT_SECONDARY);
        displayModeHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(displayModeHint, 8);
        card.addView(displayModeHint);
        return card;
    }

    private void changeDisplayMode(boolean widget) {
        if (widget == prefs.isWidgetMode()) {
            return;
        }
        applyDisplayMode(this, prefs, widget);
        refreshStatus();
    }

    /**
     * Switches between the overlay and the launcher widget. Widget mode stops the overlay
     * service so the two never show the same panel twice. Shared with the setup dialog.
     */
    static void applyDisplayMode(Context context, Prefs prefs, boolean widget) {
        prefs.setWidgetMode(widget);
        if (widget) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            context.stopService(new Intent(context, OverlayService.class));
        }
        AtlasAppWidgetProvider.updateAll(context);
    }

    private LinearLayout buildWidgetPlacementCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.widget_placement_title, 20));
        widgetPlacementStatus = Ui.text(this, "", 15, Ui.TEXT);
        Ui.topMargin(widgetPlacementStatus, 6);
        card.addView(widgetPlacementStatus);
        addWidgetButton = Ui.button(this, R.string.add_android_widget);
        Ui.topMargin(addWidgetButton, 12);
        addWidgetButton.setOnClickListener(view -> requestWidgetPlacement());
        card.addView(addWidgetButton);
        TextView hint = Ui.text(this, R.string.widget_placement_hint, 13, Ui.TEXT_SECONDARY);
        hint.setLineSpacing(0, 1.1f);
        Ui.topMargin(hint, 10);
        card.addView(hint);
        return card;
    }

    private LinearLayout buildOverlayCard() {
        LinearLayout service = Ui.card(this);
        service.addView(Ui.heading(this, R.string.overlay_title, 20));
        TextView permissionsLabel = Ui.text(this, R.string.permissions_title, 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(permissionsLabel, 10);
        service.addView(permissionsLabel);
        overlayPermissionButton = permissionButton(service, R.string.allow_overlay,
                view -> openOverlaySettings());
        usagePermissionButton = permissionButton(service, R.string.allow_usage,
                view -> openUsageSettings());
        accessibilityPermissionButton = permissionButton(service, R.string.allow_accessibility,
                view -> openAccessibilitySettings());

        serviceStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(serviceStatus, 16);
        service.addView(serviceStatus);

        LinearLayout serviceButtons = new LinearLayout(this);
        serviceButtons.setOrientation(LinearLayout.HORIZONTAL);
        serviceButtons.setGravity(Gravity.START);
        Ui.topMargin(serviceButtons, 10);
        startPanelButton = Ui.button(this, R.string.start_panel);
        Ui.setButtonAccent(this, startPanelButton, true);
        startPanelButton.setOnClickListener(view -> startPanel());
        serviceButtons.addView(startPanelButton);
        stopPanelButton = Ui.button(this, R.string.stop);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stopParams.leftMargin = Ui.dp(this, 10);
        serviceButtons.addView(stopPanelButton, stopParams);
        stopPanelButton.setOnClickListener(view -> stopPanel());
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
            if (checked && (!Settings.canDrawOverlays(this)
                    || !ForegroundAppDetector.hasUsageAccess(this)
                    || !AccessibilityWindowState.isEnabled(this))) {
                Toast.makeText(this,
                        R.string.auto_start_permission_warning,
                        Toast.LENGTH_LONG).show();
            }
        });
        service.addView(autoStartSwitch);

        LinearLayout notes = collapsible(service, R.string.head_unit_note_title,
                R.string.head_unit_note_summary, 16, 8);
        TextView note = Ui.text(this,
                R.string.head_unit_note,
                14,
                Ui.TEXT_SECONDARY
        );
        note.setLineSpacing(0, 1.15f);
        Ui.topMargin(note, 7);
        notes.addView(note);
        return service;
    }

    private LinearLayout buildMovementCard() {
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

        TextView positionTitle = Ui.text(this, R.string.panel_position, 14, Ui.TEXT);
        Ui.topMargin(positionTitle, 14);
        movement.addView(positionTitle);
        positionCornerSpinner = new Spinner(this);
        positionCornerSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, cornerLabels()));
        positionCornerSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent,
                            View view, int position, long id) {
                        if (!refreshingPosition && positionX != null && positionY != null
                                && position >= 0 && position < OverlayCorner.values().length
                                && OverlayCorner.values()[position] != displayedPositionCorner) {
                            reanchorPositionFields(OverlayCorner.values()[position]);
                        }
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    }
                });
        FrameLayout positionCornerField = new FrameLayout(this);
        positionCornerField.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        positionCornerSpinner.setBackgroundColor(0x00000000);
        positionCornerSpinner.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 34), 0);
        positionCornerField.addView(positionCornerSpinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView positionCornerArrow = Ui.text(this, "▾", 16, Ui.ACCENT);
        positionCornerArrow.setGravity(Gravity.CENTER);
        positionCornerArrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams positionCornerArrowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        positionCornerArrowParams.rightMargin = Ui.dp(this, 10);
        positionCornerField.addView(positionCornerArrow, positionCornerArrowParams);
        positionCornerField.setOnClickListener(view -> positionCornerSpinner.performClick());

        LinearLayout positionGrid = new LinearLayout(this);
        positionGrid.setOrientation(LinearLayout.VERTICAL);
        Ui.topMargin(positionGrid, 6);
        LinearLayout positionHeader = new LinearLayout(this);
        positionHeader.setOrientation(LinearLayout.HORIZONTAL);
        positionHeader.addView(Ui.text(this, R.string.position_corner, 13, Ui.TEXT_SECONDARY),
                positionColumnParams(1.25f, 0));
        positionHeader.addView(Ui.text(this, R.string.position_x, 13, Ui.TEXT_SECONDARY),
                positionColumnParams(1, 8));
        positionHeader.addView(Ui.text(this, R.string.position_y, 13, Ui.TEXT_SECONDARY),
                positionColumnParams(1, 8));
        positionGrid.addView(positionHeader);

        LinearLayout positionRow = new LinearLayout(this);
        positionRow.setOrientation(LinearLayout.HORIZONTAL);
        positionRow.setGravity(Gravity.CENTER_VERTICAL);
        positionRow.addView(positionCornerField, positionColumnParams(1.25f, 0));
        positionX = numberInput();
        positionX.setHint("X");
        positionX.setContentDescription(getString(R.string.position_x_content_description));
        LinearLayout xCell = new LinearLayout(this);
        xCell.setOrientation(LinearLayout.HORIZONTAL);
        xCell.addView(positionX, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        xCell.addView(Ui.text(this, "px", 13, Ui.TEXT_SECONDARY),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        positionRow.addView(xCell, positionColumnParams(1, 8));
        positionY = numberInput();
        positionY.setHint("Y");
        positionY.setContentDescription(getString(R.string.position_y_content_description));
        LinearLayout yCell = new LinearLayout(this);
        yCell.setOrientation(LinearLayout.HORIZONTAL);
        yCell.addView(positionY, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        yCell.addView(Ui.text(this, "px", 13, Ui.TEXT_SECONDARY),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        positionRow.addView(yCell, positionColumnParams(1, 8));
        positionGrid.addView(positionRow);
        movement.addView(positionGrid);

        TextWatcher positionWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence source, int start, int count,
                    int after) {
            }

            @Override
            public void onTextChanged(CharSequence source, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                onPositionTextChanged();
            }
        };
        positionX.addTextChangedListener(positionWatcher);
        positionY.addTextChangedListener(positionWatcher);
        View.OnFocusChangeListener positionFocusListener = (view, hasFocus) -> {
            if (!hasFocus && !suppressPositionWatchers) {
                main.removeCallbacks(delayedPositionSave);
                savePosition();
            }
        };
        positionX.setOnFocusChangeListener(positionFocusListener);
        positionY.setOnFocusChangeListener(positionFocusListener);

        TextView positionHint = Ui.text(this, R.string.panel_position_hint, 13,
                Ui.TEXT_SECONDARY);
        positionHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(positionHint, 6);
        movement.addView(positionHint);

        Button resetPosition = Ui.button(this, R.string.reset_panel_position);
        Ui.topMargin(resetPosition, 12);
        resetPosition.setOnClickListener(view -> {
            main.removeCallbacks(delayedPositionSave);
            positionDirty = false;
            savingPosition = true;
            try {
                prefs.raw().edit()
                        .putInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET)
                        .putInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET)
                        .remove(Prefs.KEY_POSITION_CORNER)
                        .apply();
            } finally {
                savingPosition = false;
            }
            refreshPositionControls();
            Toast.makeText(this, R.string.position_reset, Toast.LENGTH_SHORT).show();
        });
        movement.addView(resetPosition);
        return movement;
    }

    private LinearLayout buildAppLabelsCard() {
        LinearLayout labels = Ui.card(this);
        labels.addView(Ui.heading(this, R.string.app_labels_title, 20));
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
        labels.addView(appLabelsSwitch);
        appLabelOutlineSwitch = new Switch(this);
        appLabelOutlineSwitch.setText(R.string.app_label_outline);
        appLabelOutlineSwitch.setTextColor(Ui.TEXT);
        appLabelOutlineSwitch.setTextSize(15);
        appLabelOutlineSwitch.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        appLabelOutlineSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_APP_LABEL_OUTLINE_ENABLED, checked);
            }
        });
        labels.addView(appLabelOutlineSwitch);
        LinearLayout fineTune = collapsible(labels, R.string.app_labels_fine_title,
                R.string.app_labels_fine_summary, 16, 10);
        addSlider(fineTune, getString(R.string.app_label_text_size),
                PanelConfig.APP_LABEL_TEXT_SIZE_MIN_SP,
                PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP,
                prefs.getInt(Prefs.KEY_APP_LABEL_TEXT_SIZE_SP,
                        PanelConfig.APP_LABEL_TEXT_SIZE_DEFAULT_SP),
                value -> getString(R.string.sp_value, value),
                value -> prefs.putInt(Prefs.KEY_APP_LABEL_TEXT_SIZE_SP, value));
        addSlider(fineTune, getString(R.string.app_label_gap),
                PanelConfig.APP_LABEL_GAP_MIN_DP,
                PanelConfig.APP_LABEL_GAP_MAX_DP,
                prefs.getInt(Prefs.KEY_APP_LABEL_GAP_DP,
                        PanelConfig.APP_LABEL_GAP_DEFAULT_DP),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_APP_LABEL_GAP_DP, value));
        return labels;
    }

    private LinearLayout buildFuelCard() {
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

        LinearLayout fuelFormulaOptions = collapsible(fuelSettings,
                R.string.fuel_formula_title, R.string.fuel_formula_summary, 16, 10);
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
        return fuelSettings;
    }

    private LinearLayout buildSystemStatusCard() {
        LinearLayout systemStatus = Ui.card(this);
        systemStatus.addView(Ui.heading(this, R.string.system_status_title, 20));
        systemStatusSwitch = new Switch(this);
        systemStatusSwitch.setText(R.string.show_system_status);
        systemStatusSwitch.setTextColor(Ui.TEXT);
        systemStatusSwitch.setTextSize(15);
        systemStatusSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        systemStatusSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, checked);
            }
            if (systemStatusOptions != null && systemStatusFineTune != null) {
                setSettingsGroupEnabled(systemStatusOptions, checked);
                setSettingsGroupEnabled(systemStatusFineTune, checked);
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

        systemStatusFineTune = collapsible(systemStatus, R.string.system_status_fine_title,
                R.string.system_status_fine_summary, 16, 10);
        TextView systemStatusHint = Ui.text(this,
                R.string.system_status_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        systemStatusHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(systemStatusHint, 6);
        systemStatusFineTune.addView(systemStatusHint);
        addSlider(systemStatusFineTune, getString(R.string.system_status_line_height),
                PanelConfig.STATUS_LINE_HEIGHT_MIN_DP,
                PanelConfig.STATUS_LINE_HEIGHT_MAX_DP,
                prefs.getInt(Prefs.KEY_SYSTEM_STATUS_LINE_HEIGHT_DP,
                        PanelConfig.STATUS_LINE_HEIGHT_DEFAULT_DP),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_SYSTEM_STATUS_LINE_HEIGHT_DP, value));
        addSlider(systemStatusFineTune, getString(R.string.system_status_text_size),
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
        addSlider(systemStatusFineTune, getString(R.string.system_status_text_weight),
                PanelConfig.STATUS_TEXT_WEIGHT_MIN / 100,
                PanelConfig.STATUS_TEXT_WEIGHT_MAX / 100,
                Math.max(PanelConfig.STATUS_TEXT_WEIGHT_MIN,
                        Math.min(PanelConfig.STATUS_TEXT_WEIGHT_MAX,
                                configuredStatusWeight)) / 100,
                value -> formatSystemStatusTextWeight(value * 100),
                value -> prefs.putInt(Prefs.KEY_SYSTEM_STATUS_TEXT_WEIGHT, value * 100));
        return systemStatus;
    }

    /** Adds a card whose body starts collapsed and returns the body for its controls. */
    private LinearLayout collapsibleCard(LinearLayout page, int titleRes, int summaryRes) {
        LinearLayout card = Ui.card(this);
        LinearLayout body = collapsible(card, titleRes, summaryRes, 20, 0);
        page.addView(card);
        return body;
    }

    /**
     * Adds a collapsed section to {@code parent} and returns its body. The header stays
     * outside the body so {@link #setSettingsGroupEnabled} on the body never disables it.
     */
    private LinearLayout collapsible(LinearLayout parent, int titleRes, int summaryRes,
            float titleSizeSp, int topMarginDp) {
        String title = getString(titleRes);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setMinimumHeight(Ui.dp(this, 40));
        header.setClickable(true);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(Ui.heading(this, title, titleSizeSp), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = Ui.heading(this, "▸", 26);
        chevron.setTextColor(Ui.ACCENT);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        titleRow.addView(chevron);
        header.addView(titleRow);
        TextView summary = Ui.text(this, summaryRes, 13, Ui.TEXT_SECONDARY);
        summary.setLineSpacing(0, 1.1f);
        header.addView(summary);
        Ui.topMargin(header, topMarginDp);
        parent.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        parent.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        header.setContentDescription(getString(R.string.collapsed_section, title));
        header.setOnClickListener(view -> {
            boolean expand = body.getVisibility() != View.VISIBLE;
            body.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.setText(expand ? "▾" : "▸");
            header.setContentDescription(getString(expand
                    ? R.string.expanded_section : R.string.collapsed_section, title));
        });
        return body;
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
        if (view == systemStatusOptions || view == systemStatusFineTune
                || view == dragHandleOptions) {
            view.setAlpha(enabled ? 1f : 0.42f);
        }
    }

    private LinearLayout buildGridCard() {
        LinearLayout grid = Ui.card(this);
        grid.addView(Ui.heading(this, R.string.grid_title, 20));
        widgetGeometryHint = Ui.text(this, R.string.widget_geometry_hint, 13, Ui.TEXT_SECONDARY);
        widgetGeometryHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(widgetGeometryHint, 6);
        grid.addView(widgetGeometryHint);
        widgetOnlyViews.add(widgetGeometryHint);
        addSlider(grid, getString(R.string.columns), 1, 10,
                prefs.getInt(Prefs.KEY_COLUMNS, 5), String::valueOf,
                value -> prefs.putInt(Prefs.KEY_COLUMNS, value));
        addSlider(grid, getString(R.string.rows), 1, 4,
                prefs.getInt(Prefs.KEY_ROWS, 1), String::valueOf,
                value -> prefs.putInt(Prefs.KEY_ROWS, value));
        addSlider(grid, getString(R.string.icon_size), 40, 240,
                prefs.getInt(Prefs.KEY_ICON_SIZE_DP, 72),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_ICON_SIZE_DP, value));
        addSlider(grid, getString(R.string.icon_shape), 0, 50,
                prefs.getInt(Prefs.KEY_ICON_CORNER_PERCENT, 12),
                value -> value == 0
                        ? getString(R.string.shape_square)
                        : value == 50
                        ? getString(R.string.shape_circle)
                        : getString(R.string.percent_value, value),
                value -> prefs.putInt(Prefs.KEY_ICON_CORNER_PERCENT, value));
        LinearLayout spacing = collapsible(grid, R.string.spacing_title,
                R.string.spacing_summary, 16, 14);
        addSlider(spacing, getString(R.string.padding), 4, 40,
                prefs.getInt(Prefs.KEY_PADDING_DP, 14),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_PADDING_DP, value));
        addSlider(spacing, getString(R.string.icon_gap), 0, 40,
                prefs.getInt(Prefs.KEY_GAP_DP, 12),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_GAP_DP, value));
        return grid;
    }

    private LinearLayout buildPanelSizeCard() {
        LinearLayout sizeControls = Ui.card(this);
        sizeControls.addView(Ui.heading(this, R.string.panel_size_title, 20));
        addSlider(sizeControls, getString(R.string.panel_width),
                PanelConfig.WIDTH_MIN_PIXELS,
                PanelConfig.WIDTH_MAX_PIXELS,
                prefs.getInt(Prefs.KEY_WIDTH_PIXELS, PanelConfig.WIDTH_DEFAULT_PIXELS),
                value -> getString(R.string.px_value, value),
                value -> prefs.putInt(Prefs.KEY_WIDTH_PIXELS, value));
        manualHeightSwitch = new Switch(this);
        manualHeightSwitch.setText(R.string.automatic_panel_height);
        manualHeightSwitch.setTextColor(Ui.TEXT);
        manualHeightSwitch.setTextSize(15);
        manualHeightSwitch.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        manualHeightSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingManualHeight) return;
            if (!checked && !prefs.getBoolean(Prefs.KEY_MANUAL_HEIGHT_ENABLED, false)) {
                PanelView current = positionPanel(availableBoundsForPosition());
                prefs.putInt(Prefs.KEY_HEIGHT_PIXELS,
                        Math.max(PanelConfig.HEIGHT_MIN_PIXELS, current.backgroundHeight()));
            }
            prefs.putBoolean(Prefs.KEY_MANUAL_HEIGHT_ENABLED, !checked);
        });
        sizeControls.addView(manualHeightSwitch);
        manualHeightRange = Ui.text(this, "", 13, Ui.TEXT_SECONDARY);
        manualHeightRange.setLineSpacing(0, 1.1f);
        sizeControls.addView(manualHeightRange);
        manualHeightSlider = new SeekBar(this);
        manualHeightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && manualHeightInput != null) {
                    manualHeightInput.clearFocus();
                    setManualHeightInput(value);
                } else if (manualHeightInput != null && !manualHeightInput.hasFocus()) {
                    setManualHeightInput(value);
                }
                if (fromUser && !updatingManualHeight) {
                    prefs.putInt(Prefs.KEY_HEIGHT_PIXELS, value);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        sizeControls.addView(manualHeightSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        manualHeightInput = numberInput();
        manualHeightInput.setHint(R.string.panel_height);
        manualHeightInput.setContentDescription(getString(R.string.panel_height));
        manualHeightInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updatingManualHeight) return;
                Integer value = parseManualHeight(s.toString());
                if (value != null && manualHeightSlider != null
                        && value >= manualHeightSlider.getMin()
                        && value <= manualHeightSlider.getMax()) {
                    manualHeightSlider.setProgress(value);
                    prefs.putInt(Prefs.KEY_HEIGHT_PIXELS, value);
                    manualHeightInput.setError(null);
                } else if (!s.toString().trim().isEmpty() && manualHeightSlider != null) {
                    manualHeightInput.setError(getString(R.string.panel_height_invalid,
                            manualHeightSlider.getMin(), manualHeightSlider.getMax()));
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        manualHeightInput.setOnFocusChangeListener((view, focused) -> {
            if (!focused) updateManualHeightControls();
        });
        sizeControls.addView(manualHeightInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateManualHeightControls();
        return sizeControls;
    }

    private boolean isManualHeightKey(String key) {
        return Prefs.KEY_MANUAL_HEIGHT_ENABLED.equals(key)
                || Prefs.KEY_HEIGHT_PIXELS.equals(key)
                || isPositionGeometryKey(key);
    }

    private Integer parseManualHeight(String value) {
        try {
            if (value.trim().isEmpty()) return null;
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private void setManualHeightInput(int value) {
        if (manualHeightInput == null) return;
        boolean previous = updatingManualHeight;
        updatingManualHeight = true;
        manualHeightInput.setText(Integer.toString(value));
        manualHeightInput.setError(null);
        updatingManualHeight = previous;
    }

    private void updateManualHeightControls() {
        if (manualHeightSwitch == null || manualHeightSlider == null
                || manualHeightInput == null || manualHeightRange == null) return;
        PanelView current = positionPanel(availableBoundsForPosition());
        int minimum = current.minimumBackgroundHeight();
        int maximum = current.maximumBackgroundHeight();
        int configured = prefs.getInt(Prefs.KEY_HEIGHT_PIXELS, current.backgroundHeight());
        int value = Math.max(minimum, Math.min(maximum, configured));
        boolean inputFocused = manualHeightInput.hasFocus();
        boolean previous = updatingManualHeight;
        updatingManualHeight = true;
        boolean manualEnabled = prefs.getBoolean(Prefs.KEY_MANUAL_HEIGHT_ENABLED, false);
        manualHeightSwitch.setChecked(!manualEnabled);
        manualHeightSlider.setMin(minimum);
        manualHeightSlider.setMax(Math.max(minimum, maximum));
        manualHeightSlider.setProgress(value);
        if (!inputFocused) {
            setManualHeightInput(value);
        }
        manualHeightRange.setText(getString(R.string.panel_height_range, minimum, maximum));
        manualHeightSlider.setEnabled(manualEnabled);
        manualHeightInput.setEnabled(manualEnabled);
        manualHeightSlider.setVisibility(manualEnabled ? View.VISIBLE : View.GONE);
        manualHeightInput.setVisibility(manualEnabled ? View.VISIBLE : View.GONE);
        manualHeightRange.setVisibility(manualEnabled ? View.VISIBLE : View.GONE);
        manualHeightRange.setAlpha(manualEnabled ? 1f : 0.55f);
        updatingManualHeight = previous;
    }

    private LinearLayout buildWindowVisibilityCard() {
        LinearLayout visibility = Ui.card(this);
        visibility.addView(Ui.heading(this, R.string.window_visibility_title, 20));
        showOnlyInAppListSwitch = new Switch(this);
        showOnlyInAppListSwitch.setText(R.string.show_only_in_app_list);
        showOnlyInAppListSwitch.setTextColor(Ui.TEXT);
        showOnlyInAppListSwitch.setTextSize(15);
        showOnlyInAppListSwitch.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        showOnlyInAppListSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_SHOW_ONLY_IN_APP_LIST, checked);
            }
        });
        visibility.addView(showOnlyInAppListSwitch);
        TextView appListHint = Ui.text(this,
                R.string.show_only_in_app_list_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        appListHint.setLineSpacing(0, 1.1f);
        visibility.addView(appListHint);
        TextView hint = Ui.text(this,
                R.string.freeform_hide_threshold_hint,
                13,
                Ui.TEXT_SECONDARY
        );
        hint.setLineSpacing(0, 1.1f);
        Ui.topMargin(hint, 7);
        visibility.addView(hint);
        addSlider(
                visibility,
                getString(R.string.freeform_hide_threshold),
                WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT,
                WindowVisibilityPolicy.MAX_HIDE_THRESHOLD_PERCENT,
                prefs.freeformHideThresholdPercent(),
                value -> getString(R.string.percent_value, value),
                value -> prefs.putInt(Prefs.KEY_FREEFORM_HIDE_THRESHOLD_PERCENT, value)
        );
        return visibility;
    }

    private LinearLayout buildBackgroundCard() {
        LinearLayout background = Ui.card(this);
        background.addView(Ui.heading(this, R.string.background_title, 20));
        addSlider(background, getString(R.string.panel_radius), 0,
                PanelConfig.PANEL_RADIUS_FULLY_ROUNDED,
                prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP,
                        PanelConfig.PANEL_RADIUS_DEFAULT_DP),
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

        LinearLayout stroke = collapsible(background, R.string.stroke_title,
                R.string.stroke_summary, 16, 14);
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
        stroke.addView(backgroundStrokeSwitch);

        backgroundStrokeColorButton = Ui.button(this, R.string.change_stroke_color);
        Ui.topMargin(backgroundStrokeColorButton, 8);
        backgroundStrokeColorButton.setOnClickListener(view -> ColorPickerDialog.show(
                this,
                getString(R.string.stroke_color),
                prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, Ui.ACCENT),
                color -> prefs.putInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, color)
        ));
        stroke.addView(backgroundStrokeColorButton);
        addSlider(stroke, getString(R.string.stroke_width), 1, 20,
                prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP, 2),
                value -> getString(R.string.dp_value, value),
                value -> prefs.putInt(Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP, value));
        addSlider(stroke, getString(R.string.stroke_opacity), 0, 255,
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

    private void addClimateTransitionDurationSlider(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.topMargin(header, 15);
        TextView name = Ui.text(this, R.string.climate_transition_duration, 14, Ui.TEXT);
        header.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        climateTransitionDurationValue = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        climateTransitionDurationValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(climateTransitionDurationValue);
        parent.addView(header);

        climateTransitionDurationSlider = new SeekBar(this);
        climateTransitionDurationSlider.setMin(
                Prefs.CLIMATE_TRANSITION_DURATION_MIN_MS
                        / Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS);
        climateTransitionDurationSlider.setMax(
                Prefs.CLIMATE_TRANSITION_DURATION_MAX_MS
                        / Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS);
        climateTransitionDurationSlider.setProgress(
                prefs.climateTransitionDurationMs()
                        / Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS);
        climateTransitionDurationSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                        climateTransitionDurationValue.setText(getString(
                                R.string.milliseconds_value,
                                value * Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        prefs.setClimateTransitionDurationMs(bar.getProgress()
                                * Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS);
                    }
                });
        climateTransitionDurationValue.setText(getString(
                R.string.milliseconds_value,
                climateTransitionDurationSlider.getProgress()
                        * Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS));
        parent.addView(climateTransitionDurationSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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

    private void refreshStatus() {
        boolean overlayAllowed = Settings.canDrawOverlays(this);
        boolean usageAllowed = ForegroundAppDetector.hasUsageAccess(this);
        boolean accessibilityAllowed = AccessibilityWindowState.isEnabled(this);
        boolean accessibilityConfigured = AccessibilityWindowState.isConfigured(this);
        updatePermissionButton(overlayPermissionButton, overlayAllowed,
                "Наложение разрешено", getString(R.string.allow_overlay));
        updatePermissionButton(usagePermissionButton, usageAllowed,
                "Статистика использования доступна", getString(R.string.allow_usage));
        updatePermissionButton(accessibilityPermissionButton, accessibilityAllowed,
                "Контроль окон включён",
                accessibilityConfigured
                        ? getString(R.string.permission_accessibility_not_connected)
                        : getString(R.string.allow_accessibility));

        boolean notificationAllowed = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        updatePermissionButton(notificationPermissionButton, notificationAllowed,
                "Уведомления разрешены", getString(R.string.allow_notifications));

        boolean serviceEnabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        boolean serviceRunning = OverlayService.isRunning();
        boolean widgetMode = prefs.isWidgetMode();
        for (View view : overlayOnlyViews) {
            view.setVisibility(widgetMode ? View.GONE : View.VISIBLE);
        }
        for (View view : widgetOnlyViews) {
            view.setVisibility(widgetMode ? View.VISIBLE : View.GONE);
        }
        Ui.setSegmentSelected(this, overlayModeSegment, !widgetMode);
        Ui.setSegmentSelected(this, widgetModeSegment, widgetMode);
        displayModeHint.setText(widgetMode
                ? R.string.display_mode_widget_hint : R.string.display_mode_overlay_hint);
        int placedWidgets = AtlasAppWidgetProvider.ids(this).length;
        widgetPlacementStatus.setText(placedWidgets == 0
                ? getString(R.string.widget_not_placed)
                : getString(R.string.widget_placed_count, placedWidgets));
        Ui.setButtonAccent(this, addWidgetButton, placedWidgets == 0);
        setStatus(serviceStatus,
                serviceEnabled && serviceRunning
                        ? getString(R.string.status_service_running)
                        : serviceEnabled
                        ? getString(R.string.status_service_waiting)
                        : getString(R.string.status_service_stopped),
                serviceEnabled && serviceRunning);
        updatingSwitch = true;
        autoStartSwitch.setChecked(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
        boolean showDragHandle = prefs.getBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, true);
        dragHandleSwitch.setChecked(showDragHandle);
        appLabelsSwitch.setChecked(prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false));
        appLabelOutlineSwitch.setChecked(
                prefs.getBoolean(Prefs.KEY_APP_LABEL_OUTLINE_ENABLED, true));
        if (climateTransitionDurationSlider != null) {
            climateTransitionDurationSlider.setProgress(
                    prefs.climateTransitionDurationMs()
                            / Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS);
        }
        showOnlyInAppListSwitch.setChecked(
                prefs.getBoolean(Prefs.KEY_SHOW_ONLY_IN_APP_LIST, false));
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
        setSettingsGroupEnabled(systemStatusFineTune, showSystemStatus);
        setSettingsGroupEnabled(dragHandleOptions, showDragHandle);
        updateFuelFormulaControls(useCustomFuelFormula);
        refreshFuelFormulaSummary();

        List<String> selected = prefs.selectedComponents();
        PanelConfig config = prefs.panelConfig();
        int capacity = config.rows * config.columns;
        selectedSummary.setText(getString(selected.size() > capacity
                                ? R.string.selected_overflow_summary
                                : R.string.selected_summary,
                        selected.size(), capacity));

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

    /** As GInputBridge shares .gibb: a dated file in the cache, sent through the share sheet. */
    private void exportSettings() {
        setSettingsTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date())
                + "_" + SettingsBackup.FILE_NAME;
        ioExecutor.execute(() -> {
            try {
                Uri uri = BackupProvider.publish(appContext, name,
                        SettingsBackup.encode(appContext, prefs));
                main.post(() -> {
                    if (isDestroyed()) return;
                    setSettingsTransferEnabled(true);
                    shareSettings(name, uri);
                });
            } catch (Exception error) {
                AppLog.warn("Cannot export settings", error);
                showSettingsTransferError(R.string.settings_export_failed, error);
            }
        });
    }

    private void shareSettings(String name, Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(SettingsBackup.MIME)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(name, uri));
        try {
            startActivity(Intent.createChooser(send, getString(R.string.settings_share_title))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.settings_share_no_target, Toast.LENGTH_LONG).show();
        }
    }

    /** As GInputBridge's import: the system document picker, any type, since JSON has none. */
    @SuppressWarnings("deprecation")
    private void chooseSettingsImport() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{SettingsBackup.MIME,
                        "application/octet-stream", "text/plain", "*/*"});
        try {
            startActivityForResult(picker, REQUEST_IMPORT_SETTINGS);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    private void readSettingsForImport(Uri uri) {
        setSettingsTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        ioExecutor.execute(() -> {
            try {
                SettingsBackup.Data imported = SettingsBackup.read(appContext, uri);
                main.post(() -> {
                    if (isDestroyed()) return;
                    setSettingsTransferEnabled(true);
                    confirmSettingsImport(imported);
                });
            } catch (Exception error) {
                AppLog.warn("Cannot read settings backup", error);
                showSettingsTransferError(R.string.settings_import_read_failed, error);
            }
        });
    }

    private void confirmSettingsImport(SettingsBackup.Data imported) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_import_confirm_title)
                .setMessage(R.string.settings_import_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.import_settings_confirm,
                        (ignoredDialog, which) -> applySettings(imported))
                .create();
        CompactDialog.show(dialog);
    }

    private void applySettings(SettingsBackup.Data imported) {
        setSettingsTransferEnabled(false);
        applyingSettings = true;
        ioExecutor.execute(() -> {
            boolean saved;
            try {
                saved = prefs.replacePortableSettings(getApplicationContext(), imported);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot apply imported settings", error);
                main.post(() -> {
                    applyingSettings = false;
                    if (isDestroyed()) return;
                    setSettingsTransferEnabled(true);
                    Toast.makeText(this, R.string.settings_import_save_failed,
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            main.post(() -> {
                applyingSettings = false;
                if (isDestroyed()) return;
                setSettingsTransferEnabled(true);
                if (!saved) {
                    Toast.makeText(this, R.string.settings_import_save_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, R.string.settings_imported,
                        Toast.LENGTH_LONG).show();
                recreate();
            });
        });
    }

    private void showSettingsTransferError(int fallbackResource, Exception error) {
        String detail = error.getMessage() == null || error.getMessage().isBlank()
                ? getString(fallbackResource) : error.getMessage();
        main.post(() -> {
            if (isDestroyed()) return;
            setSettingsTransferEnabled(true);
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
        });
    }

    private void setSettingsTransferEnabled(boolean enabled) {
        if (exportSettingsButton != null) exportSettingsButton.setEnabled(enabled);
        if (importSettingsButton != null) importSettingsButton.setEnabled(enabled);
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
        CompactDialog.show(dialog);
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

    private EditText numberInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(16);
        input.setSelectAllOnFocus(true);
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12),
                Ui.dp(this, 8));
        input.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        return input;
    }

    private String[] cornerLabels() {
        String[] labels = new String[OverlayCorner.values().length];
        for (OverlayCorner corner : OverlayCorner.values()) {
            labels[corner.ordinal()] = corner.label;
        }
        return labels;
    }

    private LinearLayout.LayoutParams positionColumnParams(float weight, int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = Ui.dp(this, leftMarginDp);
        return params;
    }

    private void refreshPositionControls() {
        if (positionCornerSpinner == null || positionX == null || positionY == null) return;
        boolean previous = refreshingPosition;
        refreshingPosition = true;
        suppressPositionWatchers = true;
        positionDirty = false;
        Rect bounds = availableBoundsForPosition();
        PanelView size = positionPanel(bounds);
        int width = size.panelWidth();
        int height = size.panelHeight();
        int maxX = Math.max(0, bounds.width() - width);
        int maxY = Math.max(0, bounds.height() - height);
        OverlayCorner corner = OverlayCorner.fromPreference(
                prefs.raw().getString(Prefs.KEY_POSITION_CORNER, null));
        int storedX = prefs.getInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
        int storedY = prefs.getInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
        if (corner == null) {
            corner = OverlayCorner.TOP_START;
            int defaultX = bounds.left + Math.max(0,
                    (bounds.width() - size.panelWidth()) / 2);
            int defaultY = bounds.top + Math.max(0,
                    Math.round((bounds.height() - size.panelHeight()) * 0.72f));
            int absoluteX = storedX == Prefs.POSITION_UNSET
                    ? defaultX : storedX + bounds.left - size.outlineInset();
            int absoluteY = storedY == Prefs.POSITION_UNSET
                    ? defaultY : storedY + bounds.top - size.outlineInset();
            OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(corner,
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    width, height, absoluteX, absoluteY);
            storedX = offsets.x();
            storedY = offsets.y();
        }
        displayedPositionCorner = corner;
        positionCornerSpinner.setSelection(corner.ordinal());
        int displayedX = Math.min(maxX, Math.max(0,
                storedX == Prefs.POSITION_UNSET ? 0 : storedX));
        int displayedY = Math.min(maxY, Math.max(0,
                storedY == Prefs.POSITION_UNSET ? 0 : storedY));
        setPositionFields(displayedX, displayedY);
        lastValidPositionX = displayedX;
        lastValidPositionY = displayedY;
        hasLastValidPosition = true;
        suppressPositionWatchers = false;
        refreshingPosition = previous;
    }

    private boolean isPositionGeometryKey(String key) {
        return Prefs.KEY_POSITION_X.equals(key)
                || Prefs.KEY_POSITION_Y.equals(key)
                || Prefs.KEY_POSITION_CORNER.equals(key)
                || Prefs.KEY_WIDTH_PIXELS.equals(key)
                || Prefs.KEY_MANUAL_HEIGHT_ENABLED.equals(key)
                || Prefs.KEY_HEIGHT_PIXELS.equals(key)
                || Prefs.KEY_COLUMNS.equals(key)
                || Prefs.KEY_ROWS.equals(key)
                || Prefs.KEY_ICON_SIZE_DP.equals(key)
                || Prefs.KEY_PADDING_DP.equals(key)
                || Prefs.KEY_GAP_DP.equals(key)
                || Prefs.KEY_SHOW_DRAG_HANDLE.equals(key)
                || Prefs.KEY_DRAG_HANDLE_POSITION.equals(key)
                || Prefs.KEY_SHOW_APP_LABELS.equals(key)
                || Prefs.KEY_APP_LABEL_TEXT_SIZE_SP.equals(key)
                || Prefs.KEY_APP_LABEL_GAP_DP.equals(key)
                || Prefs.KEY_SHOW_SYSTEM_STATUS.equals(key)
                || Prefs.KEY_SHOW_CPU_STATUS.equals(key)
                || Prefs.KEY_SHOW_RAM_STATUS.equals(key)
                || Prefs.KEY_SHOW_FUEL_STATUS.equals(key)
                || Prefs.KEY_SYSTEM_STATUS_POSITION.equals(key)
                || Prefs.KEY_SYSTEM_STATUS_LINE_HEIGHT_DP.equals(key)
                || Prefs.KEY_SYSTEM_STATUS_TEXT_SIZE_SP.equals(key)
                || Prefs.KEY_SYSTEM_STATUS_TEXT_WEIGHT.equals(key)
                || Prefs.KEY_BACKGROUND_STROKE_ENABLED.equals(key)
                || Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP.equals(key)
                || Prefs.KEY_SELECTED_COMPONENTS.equals(key)
                || Prefs.KEY_SHORTCUT_CATALOG.equals(key);
    }

    private void reanchorPositionFields(OverlayCorner newCorner) {
        Rect bounds = availableBoundsForPosition();
        PanelView size = positionPanel(bounds);
        int width = size.panelWidth();
        int height = size.panelHeight();
        int maxX = Math.max(0, bounds.width() - width);
        int maxY = Math.max(0, bounds.height() - height);
        OverlayCorner oldCorner = displayedPositionCorner == null
                ? OverlayCorner.TOP_START : displayedPositionCorner;
        Integer inputX = parsePositionInput(positionX, 0, maxX);
        Integer inputY = parsePositionInput(positionY, 0, maxY);
        boolean currentInputValid = inputX != null && inputY != null;
        int currentX = currentInputValid
                ? inputX : hasLastValidPosition ? lastValidPositionX : nonNegativeInput(positionX);
        int currentY = currentInputValid
                ? inputY : hasLastValidPosition ? lastValidPositionY : nonNegativeInput(positionY);
        OverlayGeometry.Position absolute = OverlayGeometry.positionFor(oldCorner,
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                width, height, currentX, currentY);
        OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(newCorner,
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                width, height, absolute.x(), absolute.y());
        displayedPositionCorner = newCorner;
        setPositionFields(offsets.x(), offsets.y());
        lastValidPositionX = offsets.x();
        lastValidPositionY = offsets.y();
        hasLastValidPosition = true;
        positionDirty = false;
        main.removeCallbacks(delayedPositionSave);
        persistPosition(newCorner, offsets.x(), offsets.y());
    }

    private void onPositionTextChanged() {
        if (suppressPositionWatchers || positionX == null || positionY == null) {
            return;
        }
        positionDirty = true;
        main.removeCallbacks(delayedPositionSave);
        main.postDelayed(delayedPositionSave, POSITION_SAVE_DEBOUNCE_DELAY_MS);
    }

    private void savePosition() {
        if (!positionDirty || positionX == null || positionY == null) {
            return;
        }
        Rect bounds = availableBoundsForPosition();
        PanelView size = positionPanel(bounds);
        int maxX = Math.max(0, bounds.width() - size.panelWidth());
        int maxY = Math.max(0, bounds.height() - size.panelHeight());
        Integer x = validatedPositionInput(positionX, 0, maxX,
                getString(R.string.position_x_range, maxX));
        Integer y = validatedPositionInput(positionY, 0, maxY,
                getString(R.string.position_y_range, maxY));
        if (x == null || y == null) {
            return;
        }
        int selected = positionCornerSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= OverlayCorner.values().length) {
            return;
        }
        OverlayCorner corner = OverlayCorner.values()[selected];
        lastValidPositionX = x;
        lastValidPositionY = y;
        hasLastValidPosition = true;
        displayedPositionCorner = corner;
        positionDirty = false;
        persistPosition(corner, x, y);
    }

    private void setPositionFields(int x, int y) {
        boolean previous = suppressPositionWatchers;
        suppressPositionWatchers = true;
        positionX.setText(Integer.toString(Math.max(0, x)));
        positionY.setText(Integer.toString(Math.max(0, y)));
        positionX.setError(null);
        positionY.setError(null);
        suppressPositionWatchers = previous;
    }

    private void persistPosition(OverlayCorner corner, int x, int y) {
        savingPosition = true;
        try {
            prefs.putPosition(corner, x, y);
        } finally {
            savingPosition = false;
        }
    }

    private boolean isPositionKey(String key) {
        return Prefs.KEY_POSITION_X.equals(key)
                || Prefs.KEY_POSITION_Y.equals(key)
                || Prefs.KEY_POSITION_CORNER.equals(key);
    }

    private int nonNegativeInput(EditText input) {
        try {
            return Math.max(0, Integer.parseInt(input.getText().toString().trim()));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private Integer validatedPositionInput(EditText input, int min, int max, String message) {
        try {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) throw new NumberFormatException();
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            input.setError(null);
            return parsed;
        } catch (NumberFormatException error) {
            input.setError(message);
            return null;
        }
    }

    private Integer parsePositionInput(EditText input, int min, int max) {
        try {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) throw new NumberFormatException();
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Rect availableBoundsForPosition() {
        WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
        Rect full = metrics.getBounds();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect safe = new Rect(full.left + insets.left, full.top + insets.top,
                full.right - insets.right, full.bottom - insets.bottom);
        return safe.width() > 0 && safe.height() > 0 ? safe : new Rect(full);
    }

    private PanelView positionPanel(Rect bounds) {
        List<AppEntry> entries = AppRepository.loadSelectedActivities(this, prefs);
        if (entries.isEmpty()) {
            entries = AppRepository.placeholderSelectedActivities(this, prefs);
        }
        return new PanelView(getApplicationContext(), prefs, prefs.panelConfig(), entries, true,
                bounds.width(), bounds.height(), null, false);
    }

    private void updateColorButton(Button button, String label, int color) {
        button.setText(String.format("%s  #%06X", label, color & 0xFFFFFF));
        button.setBackground(Ui.rounded(color, Ui.dp(this, 8)));
        double luminance = (0.2126 * Color.red(color)
                + 0.7152 * Color.green(color)
                + 0.0722 * Color.blue(color)) / 255.0;
        button.setTextColor(luminance > 0.62 ? Color.BLACK : Color.WHITE);
    }

    private Button permissionButton(LinearLayout parent, int requestLabel,
            View.OnClickListener listener) {
        Button button = Ui.button(this, requestLabel);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 12);
        parent.addView(button, params);
        return button;
    }

    private void updatePermissionButton(Button button, boolean granted,
            String grantedText, String requestText) {
        if (button == null) return;
        button.setText((granted ? "✓ " : "✕ ") + (granted ? grantedText : requestText));
        Ui.setButtonAccent(this, button, granted);
    }

    private void setStatus(TextView view, String text, boolean good) {
        view.setText(text);
        view.setTextColor(good ? Ui.SUCCESS : Ui.WARNING);
    }

    private void refreshPreviewSoon() {
        if (previewContainer == null || previewContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        previewContainer.post(this::refreshPreview);
    }

    private void refreshPreview() {
        if (isFinishing() || previewContainer.getVisibility() != View.VISIBLE
                || previewContainer.getWidth() <= 0) {
            return;
        }
        int available = Math.max(1,
                previewContainer.getWidth() - previewContainer.getPaddingLeft() - previewContainer.getPaddingRight());
        if (prefs.isWidgetMode()) {
            refreshWidgetPreview();
            return;
        }
        previewTitle.setText(R.string.preview_title);
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

    private void refreshWidgetPreview() {
        Context app = getApplicationContext();
        int[] ids = AtlasAppWidgetProvider.ids(app);
        int requestedId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        int widgetId = ids.length == 0 ? AppWidgetManager.INVALID_APPWIDGET_ID : ids[0];
        for (int id : ids) {
            if (id == requestedId) widgetId = id;
        }
        Bundle options = widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                ? new Bundle() : AppWidgetManager.getInstance(app).getAppWidgetOptions(widgetId);
        previewTitle.setText(getString(widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                        ? R.string.widget_preview_example : R.string.widget_preview_size,
                AtlasAppWidgetProvider.widthDp(app, options),
                AtlasAppWidgetProvider.heightDp(app, options)));
        int maxHeight = Math.round(getWindowManager().getCurrentWindowMetrics().getBounds().height() * 0.30f);
        WidgetPreview.render(app, previewContainer, prefs,
                AppRepository.loadSelectedActivities(app, prefs), options, maxHeight);
    }

    private void startPanel() {
        if (prefs.isWidgetMode()) return;
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
        if (!AccessibilityWindowState.isEnabled(this)) {
            Toast.makeText(this, R.string.allow_accessibility_first, Toast.LENGTH_SHORT).show();
            openAccessibilitySettings();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission();
        }
        try {
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
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, R.string.panel_stopped, Toast.LENGTH_SHORT).show();
    }

    private void requestWidgetPlacement() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager.isRequestPinAppWidgetSupported()) {
            boolean requested = manager.requestPinAppWidget(
                    new ComponentName(this, AtlasAppWidgetProvider.class), null, null);
            if (requested) return;
        }
        Toast.makeText(this, R.string.widget_add_manually, Toast.LENGTH_LONG).show();
    }

    private void openOverlaySettings() {
        Intent perApp = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        openSettingsWithFallback(
                perApp,
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                R.string.no_overlay_settings
        );
    }

    private void openUsageSettings() {
        Intent perApp = new Intent(
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        );
        openSettingsWithFallback(
                perApp,
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                R.string.no_usage_settings
        );
    }

    private void openAccessibilitySettings() {
        Intent settings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        openSettingsWithFallback(
                settings,
                new Intent(Settings.ACTION_SETTINGS),
                R.string.no_accessibility_settings
        );
    }

    private void openSettingsWithFallback(Intent preferred, Intent fallback, int errorMessage) {
        try {
            startActivity(preferred);
            return;
        } catch (ActivityNotFoundException | SecurityException error) {
            AppLog.warn("Preferred system settings screen is unavailable", error);
        }
        try {
            startActivity(fallback);
        } catch (ActivityNotFoundException | SecurityException error) {
            AppLog.warn("Fallback system settings screen is unavailable", error);
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
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
