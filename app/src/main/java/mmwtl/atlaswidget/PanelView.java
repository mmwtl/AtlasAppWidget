package mmwtl.atlaswidget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
    private EmptySpaceDragTouchListener emptySpaceDragTouchListener;

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
        int handleWidth = config.showDragHandle ? Ui.dp(context, 34) : 0;
        int handleGap = config.showDragHandle ? Ui.dp(context, 4) : 0;
        int configuredIconSize = Ui.dp(context, config.iconSizeDp);
        int labelHeight = config.showAppLabels ? Ui.dp(context, 20) : 0;
        int labelGap = config.showAppLabels ? Ui.dp(context, 4) : 0;
        int cellHeight = configuredIconSize + labelGap + labelHeight;
        int rows = Math.max(1, config.rows);
        int columns = Math.max(1, config.columns);

        panelWidth = Math.max(Ui.dp(context, 180),
                Math.round(availableWidthPixels * Math.max(25, Math.min(100, config.widthPercent)) / 100f));
        int gridHeight = rows * cellHeight + Math.max(0, rows - 1) * gap;
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
        GradientDrawable panelBackground = Ui.rounded(background, panelRadius);
        if (config.backgroundStrokeEnabled) {
            int strokeAlpha = Math.max(0, Math.min(255, config.backgroundStrokeAlpha));
            int strokeColor = Color.argb(
                    strokeAlpha,
                    Color.red(Ui.ACCENT),
                    Color.green(Ui.ACCENT),
                    Color.blue(Ui.ACCENT)
            );
            int strokeWidth = Ui.dp(context,
                    Math.max(1, Math.min(20, config.backgroundStrokeWidthDp)));
            panelBackground.setStroke(strokeWidth, strokeColor);
        }
        setBackground(panelBackground);

        if (config.showDragHandle) {
            DragHandleView handle = new DragHandleView(context);
            handle.setText("⋮");
            handle.setTextSize(28);
            handle.setTextColor(Ui.TEXT_SECONDARY);
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
        } else if (listener != null) {
            setClickable(true);
            emptySpaceDragTouchListener = new EmptySpaceDragTouchListener(listener);
            setOnTouchListener(emptySpaceDragTouchListener);
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
            cellParams.height = cellHeight;
            if (column > 0) {
                cellParams.leftMargin = gap;
            }
            if (row > 0) {
                cellParams.topMargin = gap;
            }
            grid.addView(cell, cellParams);

            AppEntry entry = index < entries.size() ? entries.get(index) : null;
            if (entry != null) {
                addAppIcon(cell, prefs, config, entry, actualIconSize,
                        configuredIconSize, labelHeight, listener);
            } else if (preview && index < Math.min(4, slotCount)) {
                addPlaceholder(cell, config, actualIconSize, configuredIconSize, index);
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
