package mmwtl.atlaswidget;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class MainActivity extends Activity
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
    private FrameLayout previewContainer;
    private Button backgroundColorButton;
    private boolean updatingSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);
        setContentView(buildContent());
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
            refreshStatus();
            if (!Prefs.KEY_POSITION_X.equals(key) && !Prefs.KEY_POSITION_Y.equals(key)) {
                refreshPreviewSoon();
            }
        });
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 42));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = Ui.heading(this, "Atlas Widget", 30);
        content.addView(title);
        TextView subtitle = Ui.text(this,
                "Плавающая панель launchable activity для Android 11. Вне панели экран остаётся полностью интерактивным.",
                15,
                Ui.TEXT_SECONDARY
        );
        subtitle.setLineSpacing(0, 1.12f);
        Ui.topMargin(subtitle, 6);
        content.addView(subtitle);

        LinearLayout.LayoutParams firstCard = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        firstCard.topMargin = Ui.dp(this, 22);

        LinearLayout permissions = Ui.card(this);
        permissions.setLayoutParams(firstCard);
        permissions.addView(Ui.heading(this, "1. Системные разрешения", 20));
        TextView permissionHint = Ui.text(this,
                "Оба специальных доступа обязательны: первый создаёт окно, второй определяет, открыт ли лаунчер.",
                14,
                Ui.TEXT_SECONDARY
        );
        permissionHint.setLineSpacing(0, 1.1f);
        Ui.topMargin(permissionHint, 6);
        permissions.addView(permissionHint);

        overlayStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(overlayStatus, 16);
        permissions.addView(overlayStatus);
        Button overlayButton = Ui.button(this, "Разрешить поверх других окон");
        Ui.topMargin(overlayButton, 8);
        overlayButton.setOnClickListener(view -> openOverlaySettings());
        permissions.addView(overlayButton);

        usageStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(usageStatus, 16);
        permissions.addView(usageStatus);
        Button usageButton = Ui.button(this, "Разрешить доступ к статистике");
        Ui.topMargin(usageButton, 8);
        usageButton.setOnClickListener(view -> openUsageSettings());
        permissions.addView(usageButton);

        notificationStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(notificationStatus, 16);
        permissions.addView(notificationStatus);
        Button notificationButton = Ui.button(this, "Разрешить уведомления");
        Ui.topMargin(notificationButton, 8);
        notificationButton.setOnClickListener(view -> requestNotificationPermission());
        permissions.addView(notificationButton);
        content.addView(permissions);

        LinearLayout control = Ui.card(this);
        control.addView(Ui.heading(this, "2. Панель и приложения", 20));
        selectedSummary = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(selectedSummary, 8);
        control.addView(selectedSummary);

        Button appsButton = Ui.button(this, "Выбрать приложения, activity и иконки");
        Ui.topMargin(appsButton, 12);
        appsButton.setOnClickListener(view -> startActivity(new Intent(this, AppPickerActivity.class)));
        control.addView(appsButton);

        dragHandleSwitch = new Switch(this);
        dragHandleSwitch.setText("Показывать ручку перетаскивания ⋮");
        dragHandleSwitch.setTextColor(Ui.TEXT);
        dragHandleSwitch.setTextSize(15);
        dragHandleSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        dragHandleSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) {
                prefs.putBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, checked);
            }
        });
        control.addView(dragHandleSwitch);

        TextView dragHandleHint = Ui.text(this,
                "Если ручка скрыта, удерживайте пустое место панели 1 секунду, затем перетаскивайте.",
                13,
                Ui.TEXT_SECONDARY
        );
        dragHandleHint.setLineSpacing(0, 1.1f);
        control.addView(dragHandleHint);

        autoStartSwitch = new Switch(this);
        autoStartSwitch.setText("Автозапуск после загрузки ГУ");
        autoStartSwitch.setTextColor(Ui.TEXT);
        autoStartSwitch.setTextSize(15);
        autoStartSwitch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        autoStartSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSwitch) {
                return;
            }
            prefs.putBoolean(Prefs.KEY_AUTO_START, checked);
            if (checked && (!Settings.canDrawOverlays(this)
                    || !ForegroundAppDetector.hasUsageAccess(this))) {
                Toast.makeText(this,
                        "Автозапуск сохранён, но без двух специальных разрешений он не сработает",
                        Toast.LENGTH_LONG).show();
            }
        });
        control.addView(autoStartSwitch);

        serviceStatus = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        control.addView(serviceStatus);

        LinearLayout serviceButtons = new LinearLayout(this);
        serviceButtons.setOrientation(LinearLayout.HORIZONTAL);
        serviceButtons.setGravity(Gravity.START);
        Ui.topMargin(serviceButtons, 10);
        Button startButton = Ui.button(this, "Запустить панель");
        startButton.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 8)));
        startButton.setOnClickListener(view -> startPanel());
        serviceButtons.addView(startButton);
        Button stopButton = Ui.button(this, "Остановить");
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stopParams.leftMargin = Ui.dp(this, 10);
        serviceButtons.addView(stopButton, stopParams);
        stopButton.setOnClickListener(view -> stopPanel());
        control.addView(serviceButtons);

        Button resetPosition = Ui.button(this, "Сбросить позицию панели");
        Ui.topMargin(resetPosition, 10);
        resetPosition.setOnClickListener(view -> {
            prefs.putInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
            prefs.putInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
            Toast.makeText(this, "Позиция сброшена", Toast.LENGTH_SHORT).show();
        });
        control.addView(resetPosition);
        content.addView(control);

        LinearLayout preview = Ui.card(this);
        preview.addView(Ui.heading(this, "Предпросмотр", 20));
        TextView previewHint = Ui.text(this,
                "Фактический размер задаётся в процентах ширины экрана ГУ. Ручку ⋮ можно скрыть в настройках выше.",
                13,
                Ui.TEXT_SECONDARY
        );
        Ui.topMargin(previewHint, 5);
        preview.addView(previewHint);
        previewContainer = new FrameLayout(this);
        previewContainer.setClipChildren(false);
        previewContainer.setClipToPadding(false);
        previewContainer.setPadding(Ui.dp(this, 8), Ui.dp(this, 12),
                Ui.dp(this, 8), Ui.dp(this, 12));
        previewContainer.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        Ui.topMargin(previewContainer, 12);
        preview.addView(previewContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 130)
        ));
        content.addView(preview);

        LinearLayout geometry = Ui.card(this);
        geometry.addView(Ui.heading(this, "3. Геометрия", 20));
        addSlider(geometry, "Ширина панели", 25, 100,
                prefs.getInt(Prefs.KEY_WIDTH_PERCENT, 72),
                value -> value + "% экрана",
                value -> prefs.putInt(Prefs.KEY_WIDTH_PERCENT, value));
        addSlider(geometry, "Столбцы", 1, 10,
                prefs.getInt(Prefs.KEY_COLUMNS, 5),
                String::valueOf,
                value -> prefs.putInt(Prefs.KEY_COLUMNS, value));
        addSlider(geometry, "Ряды", 1, 4,
                prefs.getInt(Prefs.KEY_ROWS, 1),
                String::valueOf,
                value -> prefs.putInt(Prefs.KEY_ROWS, value));
        addSlider(geometry, "Размер иконок", 40, 140,
                prefs.getInt(Prefs.KEY_ICON_SIZE_DP, 72),
                value -> value + " dp",
                value -> prefs.putInt(Prefs.KEY_ICON_SIZE_DP, value));
        addSlider(geometry, "Форма иконок", 0, 50,
                prefs.getInt(Prefs.KEY_ICON_CORNER_PERCENT, 12),
                value -> value == 0 ? "квадрат" : value == 50 ? "круг" : value + "%",
                value -> prefs.putInt(Prefs.KEY_ICON_CORNER_PERCENT, value));
        addSlider(geometry, "Внутренний отступ", 4, 40,
                prefs.getInt(Prefs.KEY_PADDING_DP, 14),
                value -> value + " dp",
                value -> prefs.putInt(Prefs.KEY_PADDING_DP, value));
        addSlider(geometry, "Интервал между иконками", 0, 40,
                prefs.getInt(Prefs.KEY_GAP_DP, 12),
                value -> value + " dp",
                value -> prefs.putInt(Prefs.KEY_GAP_DP, value));
        content.addView(geometry);

        LinearLayout background = Ui.card(this);
        background.addView(Ui.heading(this, "4. Фон панели", 20));
        TextView shapeLabel = Ui.text(this, "Форма", 14, Ui.TEXT);
        Ui.topMargin(shapeLabel, 15);
        background.addView(shapeLabel);
        Spinner shape = new Spinner(this);
        String[] shapes = {"Прямоугольник", "Настраиваемое скругление", "Капсула"};
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                shapes
        );
        shapeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shape.setAdapter(shapeAdapter);
        shape.setSelection(prefs.getInt(Prefs.KEY_PANEL_SHAPE, 1));
        shape.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.putInt(Prefs.KEY_PANEL_SHAPE, position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        background.addView(shape);

        addSlider(background, "Радиус панели", 0, 80,
                prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP, 8),
                value -> value + " dp",
                value -> prefs.putInt(Prefs.KEY_PANEL_RADIUS_DP, value));
        addSlider(background, "Прозрачность фона", 0, 255,
                prefs.getInt(Prefs.KEY_BACKGROUND_ALPHA, 235),
                value -> Math.round(value * 100f / 255f) + "%",
                value -> prefs.putInt(Prefs.KEY_BACKGROUND_ALPHA, value));

        backgroundColorButton = Ui.button(this, "Изменить цвет фона");
        Ui.topMargin(backgroundColorButton, 12);
        backgroundColorButton.setOnClickListener(view -> ColorPickerDialog.show(
                this,
                prefs.getInt(Prefs.KEY_BACKGROUND_COLOR, 0xFF262626),
                color -> prefs.putInt(Prefs.KEY_BACKGROUND_COLOR, color)
        ));
        background.addView(backgroundColorButton);
        content.addView(background);

        LinearLayout notes = Ui.card(this);
        notes.addView(Ui.heading(this, "Важно для автомобильной ГУ", 18));
        TextView note = Ui.text(this,
                "Некоторые прошивки отдельно блокируют автозапуск и фоновые сервисы. Если после перезагрузки панель не стартует, разрешите автозапуск и работу без ограничений в менеджере питания самой ГУ. Android API не может выдать эти OEM-разрешения автоматически.",
                14,
                Ui.TEXT_SECONDARY
        );
        note.setLineSpacing(0, 1.15f);
        Ui.topMargin(note, 7);
        notes.addView(note);
        content.addView(notes);

        return scroll;
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

    private void refreshStatus() {
        boolean overlayAllowed = Settings.canDrawOverlays(this);
        boolean usageAllowed = ForegroundAppDetector.hasUsageAccess(this);
        setStatus(overlayStatus,
                overlayAllowed ? "✓ Наложение разрешено" : "! Наложение не разрешено",
                overlayAllowed);
        setStatus(usageStatus,
                usageAllowed ? "✓ Статистика использования доступна" : "! Нет доступа к foreground-приложению",
                usageAllowed);

        boolean notificationAllowed = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        setStatus(notificationStatus,
                notificationAllowed ? "✓ Уведомления разрешены" : "! Уведомления отключены (панель всё равно может работать)",
                notificationAllowed);

        boolean serviceEnabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        setStatus(serviceStatus,
                serviceEnabled ? "● Панель запущена; вне лаунчера она скрыта" : "○ Панель остановлена",
                serviceEnabled);
        updatingSwitch = true;
        autoStartSwitch.setChecked(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
        dragHandleSwitch.setChecked(prefs.getBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, true));
        updatingSwitch = false;

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

        int color = prefs.getInt(Prefs.KEY_BACKGROUND_COLOR, 0xFF262626);
        backgroundColorButton.setText(String.format("Цвет фона  #%06X", color & 0xFFFFFF));
        backgroundColorButton.setBackground(Ui.rounded(color, Ui.dp(this, 8)));
        double luminance = (0.2126 * Color.red(color)
                + 0.7152 * Color.green(color)
                + 0.0722 * Color.blue(color)) / 255.0;
        backgroundColorButton.setTextColor(luminance > 0.62 ? Color.BLACK : Color.WHITE);
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
        int available = Math.max(Ui.dp(this, 240),
                previewContainer.getWidth() - previewContainer.getPaddingLeft() - previewContainer.getPaddingRight());
        PanelView panelPreview = new PanelView(
                this,
                prefs,
                prefs.panelConfig(),
                AppRepository.loadSelectedActivities(this, prefs),
                true,
                available,
                null
        );
        previewContainer.removeAllViews();
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelPreview.panelWidth(),
                panelPreview.panelHeight(),
                Gravity.CENTER
        );
        previewContainer.addView(panelPreview, panelParams);
        ViewGroup.LayoutParams containerParams = previewContainer.getLayoutParams();
        containerParams.height = panelPreview.panelHeight() + Ui.dp(this, 32);
        previewContainer.setLayoutParams(containerParams);
    }

    private void startPanel() {
        if (prefs.selectedComponents().isEmpty()) {
            Toast.makeText(this, "Сначала выберите хотя бы одно приложение", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AppPickerActivity.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Сначала разрешите наложение", Toast.LENGTH_SHORT).show();
            openOverlaySettings();
            return;
        }
        if (!ForegroundAppDetector.hasUsageAccess(this)) {
            Toast.makeText(this, "Сначала разрешите статистику использования", Toast.LENGTH_SHORT).show();
            openUsageSettings();
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
            Toast.makeText(this, "Панель запущена", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            Toast.makeText(this, "Система не разрешила запуск фонового сервиса", Toast.LENGTH_LONG).show();
        }
    }

    private void stopPanel() {
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "Панель остановлена", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "В прошивке нет системного экрана Usage Access", Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
        } else {
            Toast.makeText(this, "На Android 11 отдельное разрешение не требуется", Toast.LENGTH_SHORT).show();
        }
    }
}
