package com.mmwtl.atlasappwidget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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

    private static final int[] PLACEHOLDER_COLORS = {0xFF7893A0};
    private static final long EMPTY_SPACE_DRAG_DELAY_MS = 1_000L;

    private final int panelWidth;
    private final int panelHeight;
    private final int outlineInset;
    private final SystemStatusView systemStatusView;
    private EmptySpaceDragTouchListener emptySpaceDragTouchListener;

    PanelView(
            Context context,
            Prefs prefs,
            PanelConfig config,
            List<AppEntry> entries,
            boolean preview,
            int availableWidthPixels,
            int availableHeightPixels,
            Listener listener
    ) {
        super(context);
        int handlePosition = config.dragHandlePosition;
        boolean handleVertical = config.showDragHandle
                && (handlePosition == PanelConfig.HANDLE_TOP
                || handlePosition == PanelConfig.HANDLE_BOTTOM);
        boolean systemStatusSide = config.showSystemStatus
                && (config.systemStatusPosition == PanelConfig.STATUS_LEFT
                || config.systemStatusPosition == PanelConfig.STATUS_RIGHT);
        setOrientation(systemStatusSide ? HORIZONTAL : VERTICAL);
        setGravity(Gravity.CENTER);

        outlineInset = config.backgroundStrokeEnabled
                ? Ui.dp(context, Math.max(1, Math.min(20, config.backgroundStrokeWidthDp)))
                : 0;
        int requestedPadding = Ui.dp(context, config.paddingDp);
        int requestedGap = Ui.dp(context, config.gapDp);
        int handleSize = config.showDragHandle ? Ui.dp(context, 34) : 0;
        int handleGap = config.showDragHandle ? Ui.dp(context, 4) : 0;
        int configuredIconSize = Ui.dp(context, config.iconSizeDp);
        int labelHeight = config.showAppLabels ? Ui.dp(context, 20) : 0;
        int labelGap = config.showAppLabels ? Ui.dp(context, 4) : 0;
        int systemStatusHeight = SystemStatusView.heightPixels(
                context,
                config.systemStatusTextSizeSp,
                config.systemStatusTextWeight,
                config.systemStatusLineHeightDp
        );
        int systemStatusSideWidth = SystemStatusView.sideWidthPixels(
                context,
                config.systemStatusLineHeightDp
        );
        int systemStatusSize = systemStatusSide
                ? systemStatusSideWidth : systemStatusHeight;
        int rows = Math.max(1, config.rows);
        int columns = Math.max(1, config.columns);
        PanelLayout layout = PanelLayout.calculate(
                availableWidthPixels,
                availableHeightPixels,
                config.widthPercent,
                Ui.dp(context, 180),
                Ui.dp(context, 54),
                configuredIconSize,
                labelHeight,
                labelGap,
                rows,
                columns,
                requestedPadding,
                requestedGap,
                config.showDragHandle,
                handleVertical,
                handleSize,
                handleGap,
                config.showSystemStatus,
                systemStatusSide,
                systemStatusSize,
                Ui.dp(context, SystemStatusView.GAP_DP),
                outlineInset
        );
        int backgroundWidth = layout.backgroundWidth;
        int backgroundHeight = layout.backgroundHeight;
        int outerPadding = layout.padding;
        panelWidth = layout.panelWidth;
        panelHeight = layout.panelHeight;
        int contentInset = outerPadding + outlineInset;
        setPadding(contentInset, contentInset, contentInset, contentInset);

        int background = Color.argb(
                Math.max(0, Math.min(255, config.backgroundAlpha)),
                Color.red(config.backgroundColor),
                Color.green(config.backgroundColor),
                Color.blue(config.backgroundColor)
        );
        int configuredPanelRadius = Math.max(0, Math.min(
                PanelConfig.PANEL_RADIUS_FULLY_ROUNDED,
                config.panelRadiusDp
        ));
        float panelRadius = configuredPanelRadius == PanelConfig.PANEL_RADIUS_FULLY_ROUNDED
                ? Math.min(backgroundWidth, backgroundHeight) / 2f
                : Ui.dp(context, configuredPanelRadius);
        GradientDrawable backgroundDrawable = Ui.rounded(background, panelRadius);
        if (config.backgroundStrokeEnabled) {
            int strokeAlpha = Math.max(0, Math.min(255, config.backgroundStrokeAlpha));
            int strokeColor = Color.argb(
                    strokeAlpha,
                    Color.red(config.backgroundStrokeColor),
                    Color.green(config.backgroundStrokeColor),
                    Color.blue(config.backgroundStrokeColor)
            );
            float outlineRadius = panelRadius == 0 ? 0 : panelRadius + outlineInset;
            GradientDrawable outline = Ui.rounded(Color.TRANSPARENT, outlineRadius);
            outline.setStroke(outlineInset, strokeColor);
            LayerDrawable layers = new LayerDrawable(new Drawable[]{outline, backgroundDrawable});
            layers.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
            layers.setLayerInset(1, outlineInset, outlineInset, outlineInset, outlineInset);
            setBackground(layers);
        } else {
            setBackground(backgroundDrawable);
        }

        DragHandleView handle = null;
        LinearLayout.LayoutParams handleParams = null;
        if (config.showDragHandle) {
            handle = new DragHandleView(context);
            handle.setText(handleVertical ? "⋯" : "⋮");
            handle.setTextSize(28);
            handle.setTextColor(Ui.TEXT_SECONDARY);
            handle.setGravity(Gravity.CENTER);
            handle.setContentDescription(context.getString(R.string.drag_panel));
            if (handleVertical) {
                handleParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        handleSize
                );
                if (handlePosition == PanelConfig.HANDLE_TOP) {
                    handleParams.bottomMargin = handleGap;
                } else {
                    handleParams.topMargin = handleGap;
                }
            } else {
                handleParams = new LinearLayout.LayoutParams(
                        handleSize,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                if (handlePosition == PanelConfig.HANDLE_LEFT) {
                    handleParams.rightMargin = handleGap;
                } else {
                    handleParams.leftMargin = handleGap;
                }
            }
            if (listener != null) {
                handle.setOnTouchListener((view, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        view.performClick();
                    }
                    return listener.onHandleTouch(view, event);
                });
            }
        } else if (listener != null) {
            setClickable(true);
            emptySpaceDragTouchListener = new EmptySpaceDragTouchListener(listener);
            setOnTouchListener(emptySpaceDragTouchListener);
        }

        int gridWidth = layout.gridWidth;
        int gridHeight = layout.gridHeight;
        int cellWidth = layout.cellWidth;
        int cellHeight = layout.cellHeight;
        int actualIconSize = layout.iconSize;

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(columns);
        grid.setRowCount(rows);
        grid.setOrientation(GridLayout.HORIZONTAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(gridWidth, gridHeight);
        LinearLayout shortcutArea = new LinearLayout(context);
        shortcutArea.setOrientation(handleVertical ? VERTICAL : HORIZONTAL);
        shortcutArea.setGravity(Gravity.CENTER);
        boolean handleBeforeGrid = handlePosition == PanelConfig.HANDLE_LEFT
                || handlePosition == PanelConfig.HANDLE_TOP;
        if (handle != null && handleBeforeGrid) {
            shortcutArea.addView(handle, handleParams);
        }
        shortcutArea.addView(grid, gridParams);
        if (handle != null && !handleBeforeGrid) {
            shortcutArea.addView(handle, handleParams);
        }

        systemStatusView = config.showSystemStatus
                ? new SystemStatusView(
                        context,
                        config.systemStatusTextSizeSp,
                        config.systemStatusTextWeight,
                        config.systemStatusLineHeightDp,
                        systemStatusSide ? layout.gridHeight : systemStatusHeight,
                        systemStatusSide
                )
                : null;
        if (systemStatusView != null && config.systemStatusPosition == PanelConfig.STATUS_TOP) {
            addSystemStatusView(systemStatusView, systemStatusHeight, false);
        }
        int shortcutHeight = gridHeight + (handleVertical ? handleSize + handleGap : 0);
        int shortcutWidth = gridWidth + (handleVertical ? 0 : handleSize + handleGap);
        if (systemStatusView != null
                && config.systemStatusPosition == PanelConfig.STATUS_LEFT) {
            addSideSystemStatusView(systemStatusView, systemStatusSideWidth, false);
        }
        addView(shortcutArea, new LinearLayout.LayoutParams(
                systemStatusSide ? shortcutWidth : ViewGroup.LayoutParams.MATCH_PARENT,
                shortcutHeight
        ));
        if (systemStatusView != null && config.systemStatusPosition == PanelConfig.STATUS_BOTTOM) {
            addSystemStatusView(systemStatusView, systemStatusHeight, true);
        }
        if (systemStatusView != null
                && config.systemStatusPosition == PanelConfig.STATUS_RIGHT) {
            addSideSystemStatusView(systemStatusView, systemStatusSideWidth, true);
        }
        if (systemStatusView != null) {
            systemStatusView.update(preview
                    ? SystemStatusSnapshot.preview()
                    : SystemStatusSnapshot.unavailable());
        }

        int slotCount = rows * columns;
        for (int index = 0; index < slotCount; index++) {
            int row = index / columns;
            int column = index % columns;
            FrameLayout cell = new FrameLayout(context);
            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams(
                    GridLayout.spec(row), GridLayout.spec(column)
            );
            cellParams.width = cellWidth;
            cellParams.height = cellHeight;
            if (column > 0) {
                cellParams.leftMargin = layout.horizontalGap;
            }
            if (row > 0) {
                cellParams.topMargin = layout.verticalGap;
            }
            grid.addView(cell, cellParams);

            AppEntry entry = index < entries.size() ? entries.get(index) : null;
            if (entry != null) {
                addAppIcon(cell, prefs, config, entry, actualIconSize,
                        actualIconSize, labelHeight, listener);
            } else if (preview && index < Math.min(4, slotCount)) {
                addPlaceholder(cell, config, actualIconSize, actualIconSize, index);
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

    int outlineInset() {
        return outlineInset;
    }

    boolean hasSystemStatus() {
        return systemStatusView != null;
    }

    void updateSystemStatus(SystemStatusSnapshot snapshot) {
        if (systemStatusView != null) {
            systemStatusView.update(snapshot);
        }
    }

    private void addSystemStatusView(
            SystemStatusView status,
            int statusHeight,
            boolean belowShortcuts
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                statusHeight
        );
        if (belowShortcuts) {
            params.topMargin = Ui.dp(getContext(), SystemStatusView.GAP_DP);
        } else {
            params.bottomMargin = Ui.dp(getContext(), SystemStatusView.GAP_DP);
        }
        addView(status, params);
    }

    private void addSideSystemStatusView(
            SystemStatusView status,
            int statusWidth,
            boolean afterShortcuts
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                statusWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        if (afterShortcuts) {
            params.leftMargin = Ui.dp(getContext(), SystemStatusView.GAP_DP);
        } else {
            params.rightMargin = Ui.dp(getContext(), SystemStatusView.GAP_DP);
        }
        addView(status, params);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (emptySpaceDragTouchListener != null) {
            emptySpaceDragTouchListener.cancel();
        }
        super.onDetachedFromWindow();
    }

    private void addAppIcon(
            FrameLayout cell,
            Prefs prefs,
            PanelConfig config,
            AppEntry entry,
            int iconSize,
            int iconAreaHeight,
            int labelHeight,
            Listener listener
    ) {
        FrameLayout mask = iconMask(config, iconSize, Color.TRANSPARENT);
        FrameLayout.LayoutParams maskParams = iconLayoutParams(config, iconSize, iconAreaHeight);
        cell.addView(mask, maskParams);

        IconLoader.Result icon = IconLoader.load(getContext(), prefs, entry, iconSize);
        ImageView image = new ImageView(getContext());
        image.setImageDrawable(icon.drawable);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
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

        if (config.showAppLabels) {
            TextView label = Ui.text(getContext(), entry.label, 12, Ui.TEXT_SECONDARY);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setIncludeFontPadding(false);
            label.setShadowLayer(Ui.dp(getContext(), 2), 0,
                    Ui.dp(getContext(), 1), Color.BLACK);
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    labelHeight,
                    Gravity.BOTTOM
            );
            cell.addView(label, labelParams);
            if (listener != null) {
                label.setClickable(true);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                label.setOnClickListener(view -> listener.onAppClicked(entry));
            }
        }
    }

    private void addPlaceholder(
            FrameLayout cell,
            PanelConfig config,
            int iconSize,
            int iconAreaHeight,
            int index
    ) {
        FrameLayout mask = iconMask(config, iconSize,
                PLACEHOLDER_COLORS[index % PLACEHOLDER_COLORS.length]);
        FrameLayout.LayoutParams maskParams = iconLayoutParams(config, iconSize, iconAreaHeight);
        cell.addView(mask, maskParams);
        TextView dot = Ui.heading(getContext(), "•", 28);
        dot.setGravity(Gravity.CENTER);
        mask.addView(dot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private FrameLayout.LayoutParams iconLayoutParams(
            PanelConfig config,
            int iconSize,
            int iconAreaHeight
    ) {
        int gravity = config.showAppLabels
                ? Gravity.TOP | Gravity.CENTER_HORIZONTAL
                : Gravity.CENTER;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(iconSize, iconSize, gravity);
        if (config.showAppLabels) {
            params.topMargin = Math.max(0, (iconAreaHeight - iconSize) / 2);
        }
        return params;
    }

    private FrameLayout iconMask(PanelConfig config, int iconSize, int color) {
        FrameLayout mask = new FrameLayout(getContext());
        GradientDrawable background = Ui.rounded(color,
                iconSize * Math.max(0, Math.min(50, config.iconCornerPercent)) / 100f);
        mask.setBackground(background);
        mask.setClipToOutline(true);
        return mask;
    }

    private final class EmptySpaceDragTouchListener implements OnTouchListener {
        private final Listener listener;
        private final int touchSlopSquared;
        private final Runnable activateDrag;

        private MotionEvent downEvent;
        private float downRawX;
        private float downRawY;
        private boolean waiting;
        private boolean dragging;

        EmptySpaceDragTouchListener(Listener listener) {
            this.listener = listener;
            int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            touchSlopSquared = touchSlop * touchSlop;
            activateDrag = this::activateDrag;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancel();
                    downEvent = MotionEvent.obtain(event);
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    waiting = true;
                    postDelayed(activateDrag, EMPTY_SPACE_DRAG_DELAY_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        listener.onHandleTouch(view, event);
                    } else if (waiting) {
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        if (deltaX * deltaX + deltaY * deltaY > touchSlopSquared) {
                            cancelPendingDrag();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        listener.onHandleTouch(view, event);
                    } else {
                        view.performClick();
                    }
                    cancel();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        listener.onHandleTouch(view, event);
                    }
                    cancel();
                    return true;
                default:
                    return true;
            }
        }

        void cancel() {
            removeCallbacks(activateDrag);
            recycleDownEvent();
            waiting = false;
            dragging = false;
        }

        private void activateDrag() {
            if (!waiting || downEvent == null || !isAttachedToWindow()) {
                cancel();
                return;
            }
            waiting = false;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            MotionEvent startEvent = downEvent;
            downEvent = null;
            dragging = listener.onHandleTouch(PanelView.this, startEvent);
            startEvent.recycle();
        }

        private void cancelPendingDrag() {
            removeCallbacks(activateDrag);
            recycleDownEvent();
            waiting = false;
        }

        private void recycleDownEvent() {
            if (downEvent != null) {
                downEvent.recycle();
                downEvent = null;
            }
        }
    }
}
