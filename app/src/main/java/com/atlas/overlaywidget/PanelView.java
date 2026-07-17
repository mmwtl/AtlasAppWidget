package com.atlas.overlaywidget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

@SuppressLint("ViewConstructor")
final class PanelView extends LinearLayout {
    interface Listener {
        boolean onHandleTouch(View view, MotionEvent event);

        void onAppClicked(AppEntry entry);
    }

    private static final int[] PLACEHOLDER_COLORS = {
            0xFF7C6CFF, 0xFF31BFA3, 0xFFFF8A65, 0xFF4E9BFF, 0xFFE05CA8
    };

    private final int panelWidth;
    private final int panelHeight;

    PanelView(
            Context context,
            Prefs prefs,
            PanelConfig config,
            List<AppEntry> entries,
            boolean preview,
            int availableWidthPixels,
            Listener listener
    ) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int outerPadding = Ui.dp(context, config.paddingDp);
        int gap = Ui.dp(context, config.gapDp);
        int handleWidth = Ui.dp(context, 34);
        int handleGap = Ui.dp(context, 4);
        int configuredIconSize = Ui.dp(context, config.iconSizeDp);
        int rows = Math.max(1, config.rows);
        int columns = Math.max(1, config.columns);

        panelWidth = Math.max(Ui.dp(context, 180),
                Math.round(availableWidthPixels * Math.max(25, Math.min(100, config.widthPercent)) / 100f));
        int gridHeight = rows * configuredIconSize + Math.max(0, rows - 1) * gap;
        panelHeight = Math.max(Ui.dp(context, 54), gridHeight + outerPadding * 2);
        setPadding(outerPadding, outerPadding, outerPadding, outerPadding);

        int background = Color.argb(
                Math.max(0, Math.min(255, config.backgroundAlpha)),
                Color.red(config.backgroundColor),
                Color.green(config.backgroundColor),
                Color.blue(config.backgroundColor)
        );
        float panelRadius;
        if (config.panelShape == 0) {
            panelRadius = 0;
        } else if (config.panelShape == 2) {
            panelRadius = panelHeight / 2f;
        } else {
            panelRadius = Ui.dp(context, config.panelRadiusDp);
        }
        setBackground(Ui.rounded(background, panelRadius));
        setElevation(Ui.dp(context, 10));

        DragHandleView handle = new DragHandleView(context);
        handle.setText("⋮");
        handle.setTextSize(28);
        handle.setTextColor(0x99FFFFFF);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Перетащить панель");
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(handleWidth,
                ViewGroup.LayoutParams.MATCH_PARENT);
        handleParams.rightMargin = handleGap;
        addView(handle, handleParams);
        if (listener != null) {
            handle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return listener.onHandleTouch(view, event);
            });
        }

        int gridWidth = Math.max(1, panelWidth - outerPadding * 2 - handleWidth - handleGap);
        int totalHorizontalGaps = Math.max(0, columns - 1) * gap;
        int cellWidth = Math.max(1, (gridWidth - totalHorizontalGaps) / columns);
        int actualIconSize = Math.max(1, Math.min(configuredIconSize, cellWidth));

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(columns);
        grid.setRowCount(rows);
        grid.setOrientation(GridLayout.HORIZONTAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(gridWidth, gridHeight);
        addView(grid, gridParams);

        int slotCount = rows * columns;
        for (int index = 0; index < slotCount; index++) {
            int row = index / columns;
            int column = index % columns;
            FrameLayout cell = new FrameLayout(context);
            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams(
                    GridLayout.spec(row), GridLayout.spec(column)
            );
            cellParams.width = cellWidth;
            cellParams.height = configuredIconSize;
            if (column > 0) {
                cellParams.leftMargin = gap;
            }
            if (row > 0) {
                cellParams.topMargin = gap;
            }
            grid.addView(cell, cellParams);

            AppEntry entry = index < entries.size() ? entries.get(index) : null;
            if (entry != null) {
                addAppIcon(cell, prefs, config, entry, actualIconSize, listener);
            } else if (preview && index < Math.min(4, slotCount)) {
                addPlaceholder(cell, config, actualIconSize, index);
            }
        }

        setLayoutParams(new ViewGroup.LayoutParams(panelWidth, panelHeight));
    }

    int panelWidth() {
        return panelWidth;
    }

    int panelHeight() {
        return panelHeight;
    }

    private void addAppIcon(
            FrameLayout cell,
            Prefs prefs,
            PanelConfig config,
            AppEntry entry,
            int iconSize,
            Listener listener
    ) {
        FrameLayout mask = iconMask(config, iconSize, 0x16FFFFFF);
        FrameLayout.LayoutParams maskParams = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        cell.addView(mask, maskParams);

        IconLoader.Result icon = IconLoader.load(getContext(), prefs, entry, iconSize);
        ImageView image = new ImageView(getContext());
        image.setImageDrawable(icon.drawable);
        image.setScaleType(icon.custom ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(entry.label + ", " + entry.activityLabel);
        mask.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        if (listener != null) {
            mask.setClickable(true);
            mask.setFocusable(true);
            mask.setOnClickListener(view -> listener.onAppClicked(entry));
        }
    }

    private void addPlaceholder(FrameLayout cell, PanelConfig config, int iconSize, int index) {
        FrameLayout mask = iconMask(config, iconSize,
                PLACEHOLDER_COLORS[index % PLACEHOLDER_COLORS.length]);
        FrameLayout.LayoutParams maskParams = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        cell.addView(mask, maskParams);
        TextView dot = Ui.heading(getContext(), "•", 28);
        dot.setGravity(Gravity.CENTER);
        mask.addView(dot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private FrameLayout iconMask(PanelConfig config, int iconSize, int color) {
        FrameLayout mask = new FrameLayout(getContext());
        GradientDrawable background = Ui.rounded(color,
                iconSize * Math.max(0, Math.min(50, config.iconCornerPercent)) / 100f);
        mask.setBackground(background);
        mask.setClipToOutline(true);
        mask.setElevation(Ui.dp(getContext(), 2));
        return mask;
    }
}
