package com.mmwtl.atlasappwidget;

import android.app.Dialog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.text.LineBreaker;
import android.text.Layout;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

final class CompactDialog {
    private static final int MAX_WIDTH_DP = 640;
    private static final int SIDE_MARGIN_DP = 24;
    private static final float MESSAGE_TEXT_SIZE_SP = 12f;
    private static final float MESSAGE_LINE_SPACING_MULTIPLIER = 1.1f;
    private static final int MESSAGE_LINE_SPACING_DP = 2;

    private CompactDialog() {
    }

    static void show(Dialog dialog) {
        dialog.show();
        apply(dialog);
    }

    @SuppressLint("DiscouragedApi")
    private static void apply(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }

        Context context = window.getContext();
        int titleId = context.getResources().getIdentifier(
                "alertTitle", "id", "android");
        if (titleId != 0) {
            configureWrapping(dialog.findViewById(titleId));
        }

        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            configureWrapping(message);
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, MESSAGE_TEXT_SIZE_SP);
            message.setLineSpacing(
                    Ui.dp(context, MESSAGE_LINE_SPACING_DP),
                    MESSAGE_LINE_SPACING_MULTIPLIER
            );
        }

        WindowManager windowManager = context.getSystemService(WindowManager.class);
        if (windowManager == null) {
            return;
        }
        int sideMarginPx = Ui.dp(context, SIDE_MARGIN_DP);
        int availableWidth = Math.max(
                1,
                windowManager.getCurrentWindowMetrics().getBounds().width()
                        - sideMarginPx * 2
        );
        window.setLayout(
                Math.min(availableWidth, Ui.dp(context, MAX_WIDTH_DP)),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static void configureWrapping(TextView textView) {
        if (textView == null) {
            return;
        }
        textView.setSingleLine(false);
        textView.setMaxLines(Integer.MAX_VALUE);
        textView.setHorizontallyScrolling(false);
        textView.setEllipsize(null);
        textView.setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED);
        textView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
    }
}
