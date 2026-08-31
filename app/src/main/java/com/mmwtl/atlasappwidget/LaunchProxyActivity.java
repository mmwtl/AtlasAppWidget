package com.mmwtl.atlasappwidget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.ViewTreeObserver;
import android.widget.Toast;

/**
 * A transparent, short-lived full-screen bridge for launchers that need to observe an activity
 * transition before the selected application takes focus.
 */
public final class LaunchProxyActivity extends Activity {
    private boolean targetLaunchScheduled;
    private ViewTreeObserver.OnPreDrawListener firstFrameListener;
    private Runnable launchTargetRunnable;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
        }
        setContentView(new View(this));
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (targetLaunchScheduled) {
            return;
        }
        targetLaunchScheduled = true;
        View decor = getWindow().getDecorView();
        firstFrameListener = () -> {
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(firstFrameListener);
            }
            firstFrameListener = null;
            // Let this pre-draw proceed and wait for the next frame before changing activities.
            launchTargetRunnable = this::launchTarget;
            decor.postOnAnimation(launchTargetRunnable);
            return true;
        };
        decor.getViewTreeObserver().addOnPreDrawListener(firstFrameListener);
    }

    @Override
    protected void onDestroy() {
        if (firstFrameListener != null) {
            View decor = getWindow().getDecorView();
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(firstFrameListener);
            }
            firstFrameListener = null;
        }
        if (launchTargetRunnable != null) {
            getWindow().getDecorView().removeCallbacks(launchTargetRunnable);
            launchTargetRunnable = null;
        }
        super.onDestroy();
    }

    private void launchTarget() {
        launchTargetRunnable = null;
        String component = getIntent().getStringExtra(LaunchProxyIntents.EXTRA_TARGET_COMPONENT);
        try {
            Intent target = LaunchProxyIntents.target(component);
            if (target == null) {
                failLaunch(component, null);
                return;
            }
            startActivity(target);
        } catch (RuntimeException error) {
            failLaunch(component, error);
        } finally {
            finishWithoutAnimation();
        }
    }

    private void failLaunch(String component, Throwable error) {
        String targetLabel = getIntent().getStringExtra(LaunchProxyIntents.EXTRA_TARGET_LABEL);
        String displayName = targetLabel == null || targetLabel.isEmpty()
                ? component == null ? getString(R.string.app_name) : component
                : targetLabel;
        if (error == null) {
            error = new IllegalArgumentException("Invalid target component: " + component);
        }
        AppLog.warn("Cannot launch selected activity through proxy " + component, error);
        Toast.makeText(this, getString(R.string.launch_failed, displayName),
                Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }
}
