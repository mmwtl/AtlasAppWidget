package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

/** A short-lived, transparent overlay used to bridge a launcher activity transition. */
final class LaunchTransitionOverlay {
    static final int WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

    private final Context context;
    private final WindowManager windowManager;
    private View transitionView;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private Runnable nextFrameRunnable;
    private Runnable launchRunnable;

    LaunchTransitionOverlay(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    boolean start(Runnable launchAction) {
        clear();
        if (launchAction == null || windowManager == null) {
            return false;
        }

        View view = new View(context);
        view.setBackgroundColor(Color.TRANSPARENT);
        transitionView = view;
        preDrawListener = () -> {
            removePreDrawListener(view);
            if (transitionView != view) {
                return true;
            }
            launchRunnable = () -> {
                launchRunnable = null;
                if (transitionView != view) {
                    return;
                }
                try {
                    launchAction.run();
                } finally {
                    clear();
                }
            };
            nextFrameRunnable = () -> {
                nextFrameRunnable = null;
                if (transitionView == view) {
                    view.postOnAnimation(launchRunnable);
                }
            };
            // Let two complete overlay frames reach the window manager before launching. This
            // stays tied to display frames instead of adding a fixed user-visible delay.
            view.postOnAnimation(nextFrameRunnable);
            return true;
        };
        view.getViewTreeObserver().addOnPreDrawListener(preDrawListener);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT
        );
        try {
            windowManager.addView(view, params);
            return true;
        } catch (SecurityException | WindowManager.BadTokenException
                 | IllegalArgumentException | IllegalStateException error) {
            AppLog.warn("Cannot attach launch transition overlay", error);
            clear();
            return false;
        }
    }

    void clear() {
        View view = transitionView;
        transitionView = null;
        if (view == null) {
            preDrawListener = null;
            nextFrameRunnable = null;
            launchRunnable = null;
            return;
        }
        removePreDrawListener(view);
        if (nextFrameRunnable != null) {
            view.removeCallbacks(nextFrameRunnable);
            nextFrameRunnable = null;
        }
        if (launchRunnable != null) {
            view.removeCallbacks(launchRunnable);
            launchRunnable = null;
        }
        view.setVisibility(View.GONE);
        try {
            windowManager.removeViewImmediate(view);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The view was already detached, or WindowManager is finishing its removal traversal.
        }
    }

    private void removePreDrawListener(View view) {
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        preDrawListener = null;
        if (listener == null) {
            return;
        }
        ViewTreeObserver observer = view.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(listener);
        }
    }
}
