package com.mmwtl.atlasappwidget;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppPickerActivity extends ScaledActivity {
    private static final int REQUEST_ICON = 401;
    private static final String STATE_PENDING_ICON_COMPONENT = "pending_icon_component";

    private final ExecutorService loader = Executors.newFixedThreadPool(2);
    private final ExecutorService importer = Executors.newSingleThreadExecutor();
    private Prefs prefs;
    private ListView listView;
    private ProgressBar progress;
    private TextView resultSummary;
    private AppAdapter adapter;
    private String pendingIconComponent;
    private final Set<String> iconLoads = Collections.synchronizedSet(new HashSet<>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        if (savedInstanceState != null) {
            pendingIconComponent = savedInstanceState.getString(STATE_PENDING_ICON_COMPONENT);
        }
        View content = buildContent();
        setContentView(content);
        Ui.applySystemBarInsets(content);
        loadApplications();
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        importer.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_ICON_COMPONENT, pendingIconComponent);
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 12));
        root.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Ui.button(this, R.string.picker_back);
        back.setOnClickListener(view -> finish());
        toolbar.addView(back);
        TextView title = Ui.heading(this, R.string.picker_title, 24);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        titleParams.leftMargin = Ui.dp(this, 16);
        toolbar.addView(title, titleParams);
        root.addView(toolbar);

        TextView explanation = Ui.text(this,
                R.string.picker_hint,
                14,
                Ui.TEXT_SECONDARY
        );
        explanation.setLineSpacing(0, 1.12f);
        Ui.topMargin(explanation, 12);
        root.addView(explanation);

        EditText search = new EditText(this);
        search.setHint(R.string.picker_search);
        search.setHintTextColor(Ui.TEXT_SECONDARY);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        search.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 8)));
        Ui.topMargin(search, 16);
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (adapter != null) {
                    adapter.setQuery(text.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        resultSummary = Ui.text(this, R.string.picker_loading, 13, Ui.TEXT_SECONDARY);
        Ui.topMargin(resultSummary, 10);
        root.addView(resultSummary);

        FrameLayout listFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        frameParams.topMargin = Ui.dp(this, 8);
        root.addView(listFrame, frameParams);

        listView = new ListView(this);
        listView.setDividerHeight(Ui.dp(this, 8));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        listView.setSelector(android.R.color.transparent);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, Ui.dp(this, 20));
        listFrame.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        listFrame.addView(progress, progressParams);
        return root;
    }

    private void loadApplications() {
        loader.execute(() -> {
            List<AppEntry> entries = AppRepository.loadLaunchableActivities(this);
            Set<String> available = new HashSet<>();
            boolean hasApplicationEntry = false;
            for (AppEntry entry : entries) {
                available.add(entry.componentKey);
                hasApplicationEntry |= !entry.isFuel();
            }
            if (hasApplicationEntry) {
                prefs.retainSelectedComponents(available);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                adapter = new AppAdapter(entries);
                listView.setAdapter(adapter);
                adapter.refresh();
            });
        });
    }

    private void showIconOptions(AppEntry entry) {
        boolean hasCustom = prefs.customIcon(entry.componentKey) != null;
        String[] options = hasCustom
                ? new String[]{getString(R.string.choose_image),
                getString(R.string.restore_system_icon), getString(R.string.cancel)}
                : new String[]{getString(R.string.choose_image), getString(R.string.cancel)};
        new AlertDialog.Builder(this)
                .setTitle(entry.label + " — " + entry.activityLabel)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        chooseIcon(entry);
                    } else if (hasCustom && which == 1) {
                        CustomIconStore.delete(this, prefs.customIcon(entry.componentKey));
                        prefs.setCustomIcon(entry.componentKey, null);
                        IconLoader.clearComponent(entry.componentKey);
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                })
                .show();
    }

    private void chooseIcon(AppEntry entry) {
        pendingIconComponent = entry.componentKey;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_ICON);
        } catch (android.content.ActivityNotFoundException ignored) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ICON || resultCode != RESULT_OK
                || data == null || data.getData() == null || pendingIconComponent == null) {
            return;
        }
        Uri uri = data.getData();
        String component = pendingIconComponent;
        pendingIconComponent = null;
        Toast.makeText(this, R.string.icon_importing, Toast.LENGTH_SHORT).show();
        importer.execute(() -> {
            try {
                String previous = prefs.customIcon(component);
                String stored = CustomIconStore.importIcon(
                        getApplicationContext(),
                        uri,
                        component
                );
                prefs.setCustomIcon(component, stored);
                if (!stored.equals(previous)) {
                    CustomIconStore.delete(getApplicationContext(), previous);
                }
                IconLoader.clearComponent(component);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, R.string.icon_saved, Toast.LENGTH_SHORT).show();
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            } catch (IOException | SecurityException error) {
                AppLog.warn("Custom icon import failed for " + component, error);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this,
                                R.string.icon_save_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> all;
        private final List<AppEntry> visible = new ArrayList<>();
        private String query = "";

        AppAdapter(List<AppEntry> entries) {
            all = new ArrayList<>(entries);
        }

        void setQuery(String value) {
            query = value == null ? "" : value.trim().toLowerCase(Locale.getDefault());
            refresh();
        }

        void refresh() {
            List<String> selected = prefs.selectedComponents();
            Map<String, Integer> positions = new HashMap<>();
            for (int index = 0; index < selected.size(); index++) {
                positions.put(selected.get(index), index);
            }
            all.sort((left, right) -> {
                Integer leftIndex = positions.get(left.componentKey);
                Integer rightIndex = positions.get(right.componentKey);
                if (leftIndex != null && rightIndex != null) {
                    return Integer.compare(leftIndex, rightIndex);
                }
                if (leftIndex != null) {
                    return -1;
                }
                if (rightIndex != null) {
                    return 1;
                }
                if (left.isFuel() != right.isFuel()) {
                    return left.isFuel() ? -1 : 1;
                }
                int label = left.label.compareToIgnoreCase(right.label);
                return label != 0 ? label : left.activityLabel.compareToIgnoreCase(right.activityLabel);
            });

            visible.clear();
            for (AppEntry entry : all) {
                if (query.isEmpty() || entry.searchText.contains(query)) {
                    visible.add(entry);
                }
            }
            resultSummary.setText(getString(
                    R.string.picker_summary,
                    visible.size(),
                    all.size(),
                    selected.size()
            ));
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).componentKey.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                holder = createRow();
                convertView = holder.root;
                convertView.setTag(holder);
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            AppEntry entry = getItem(position);
            List<String> selected = prefs.selectedComponents();
            int selectedIndex = selected.indexOf(entry.componentKey);
            boolean isSelected = selectedIndex >= 0;
            holder.root.setBackground(Ui.rounded(
                    isSelected ? Ui.SURFACE_RAISED : Ui.SURFACE,
                    Ui.dp(AppPickerActivity.this, 8)
            ));
            holder.appLabel.setText(entry.label);
            holder.activityLabel.setText(entry.activityLabel);
            holder.componentLabel.setText(entry.isFuel()
                    ? getString(R.string.fuel_tile_component_label)
                    : getString(
                    R.string.component_lines,
                    entry.componentName.getPackageName(),
                    entry.componentName.getClassName()
            ));
            bindIcon(holder, entry);
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(isSelected);
            holder.check.setContentDescription(getString(
                    isSelected ? R.string.remove_app : R.string.add_app,
                    entry.label
            ));
            holder.check.setOnCheckedChangeListener((button, checked) -> {
                prefs.setComponentSelected(entry.componentKey, checked);
                refresh();
            });

            holder.up.setVisibility(isSelected && selectedIndex > 0 ? View.VISIBLE : View.INVISIBLE);
            holder.down.setVisibility(isSelected && selectedIndex < selected.size() - 1
                    ? View.VISIBLE : View.INVISIBLE);
            holder.up.setOnClickListener(view -> {
                prefs.moveSelected(entry.componentKey, -1);
                refresh();
            });
            holder.down.setOnClickListener(view -> {
                prefs.moveSelected(entry.componentKey, 1);
                refresh();
            });
            holder.iconButton.setVisibility(entry.isFuel() ? View.GONE : View.VISIBLE);
            if (!entry.isFuel()) {
                holder.iconButton.setText(prefs.customIcon(entry.componentKey) != null
                        ? R.string.custom_icon : R.string.icon);
                holder.iconButton.setOnClickListener(view -> showIconOptions(entry));
            } else {
                holder.iconButton.setOnClickListener(null);
            }
            return convertView;
        }

        private void bindIcon(RowHolder holder, AppEntry entry) {
            int targetPixels = Ui.dp(AppPickerActivity.this, 56);
            if (entry.isFuel()) {
                FuelTileDrawable fuel = new FuelTileDrawable(
                        getString(R.string.fuel_free_short),
                        getString(R.string.fuel_filled_short)
                );
                fuel.showPreview();
                holder.icon.setTag(entry.componentKey);
                holder.icon.setImageDrawable(fuel);
                holder.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                return;
            }
            String loadKey = entry.componentKey + "|"
                    + prefs.customIcon(entry.componentKey) + "|" + targetPixels;
            holder.icon.setTag(loadKey);
            holder.icon.setImageDrawable(getPackageManager().getDefaultActivityIcon());
            holder.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (!iconLoads.add(loadKey)) {
                return;
            }
            loader.execute(() -> {
                IconLoader.Result icon;
                try {
                    icon = IconLoader.load(
                            getApplicationContext(),
                            prefs,
                            entry,
                            targetPixels
                    );
                } catch (RuntimeException error) {
                    AppLog.warnRateLimited(
                            "picker-icon-" + entry.componentKey,
                            "App-picker icon load failed",
                            error
                    );
                    iconLoads.remove(loadKey);
                    return;
                }
                iconLoads.remove(loadKey);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || !loadKey.equals(holder.icon.getTag())) {
                        return;
                    }
                    holder.icon.setImageDrawable(icon.drawable);
                    holder.icon.setScaleType(icon.custom
                            ? ImageView.ScaleType.CENTER_CROP
                            : ImageView.ScaleType.FIT_CENTER);
                });
            });
        }

        private RowHolder createRow() {
            RowHolder holder = new RowHolder();
            LinearLayout root = new LinearLayout(AppPickerActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(Ui.dp(AppPickerActivity.this, 14), Ui.dp(AppPickerActivity.this, 12),
                    Ui.dp(AppPickerActivity.this, 10), Ui.dp(AppPickerActivity.this, 10));
            holder.root = root;

            LinearLayout top = new LinearLayout(AppPickerActivity.this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(top, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            holder.icon = new ImageView(AppPickerActivity.this);
            int iconSize = Ui.dp(AppPickerActivity.this, 56);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.rightMargin = Ui.dp(AppPickerActivity.this, 14);
            top.addView(holder.icon, iconParams);

            LinearLayout labels = new LinearLayout(AppPickerActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            top.addView(labels, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));
            holder.appLabel = Ui.heading(AppPickerActivity.this, "", 16);
            labels.addView(holder.appLabel);
            holder.activityLabel = Ui.text(AppPickerActivity.this, "", 13, Ui.TEXT_SECONDARY);
            labels.addView(holder.activityLabel);
            holder.componentLabel = Ui.text(AppPickerActivity.this, "", 10, Ui.TEXT_SECONDARY);
            holder.componentLabel.setMaxLines(2);
            labels.addView(holder.componentLabel);

            holder.check = new CheckBox(AppPickerActivity.this);
            top.addView(holder.check, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            LinearLayout actions = new LinearLayout(AppPickerActivity.this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            actionsParams.topMargin = Ui.dp(AppPickerActivity.this, 6);
            root.addView(actions, actionsParams);
            View spacer = new View(AppPickerActivity.this);
            actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            holder.up = compactButton("↑");
            holder.down = compactButton("↓");
            holder.iconButton = Ui.button(AppPickerActivity.this, R.string.icon);
            actions.addView(holder.up, compactParams());
            actions.addView(holder.down, compactParams());
            LinearLayout.LayoutParams iconButtonParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.dp(AppPickerActivity.this, 38)
            );
            iconButtonParams.leftMargin = Ui.dp(AppPickerActivity.this, 7);
            actions.addView(holder.iconButton, iconButtonParams);
            return holder;
        }

        private Button compactButton(String text) {
            Button button = Ui.button(AppPickerActivity.this, text);
            button.setTextSize(18);
            button.setPadding(0, 0, 0, 0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            return button;
        }

        private LinearLayout.LayoutParams compactParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.dp(AppPickerActivity.this, 42),
                    Ui.dp(AppPickerActivity.this, 38)
            );
            params.leftMargin = Ui.dp(AppPickerActivity.this, 7);
            return params;
        }
    }

    private static final class RowHolder {
        LinearLayout root;
        ImageView icon;
        TextView appLabel;
        TextView activityLabel;
        TextView componentLabel;
        CheckBox check;
        Button up;
        Button down;
        Button iconButton;
    }
}
