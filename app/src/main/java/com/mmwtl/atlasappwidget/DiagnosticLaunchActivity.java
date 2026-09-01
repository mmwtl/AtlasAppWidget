package com.mmwtl.atlasappwidget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Opaque diagnostic transition surface used to observe a real Activity launch. */
@SuppressWarnings("deprecation")
public final class DiagnosticLaunchActivity extends Activity {
    private static final String EXTRA_TARGET_INTENT =
            "com.mmwtl.atlasappwidget.extra.DIAGNOSTIC_TARGET_INTENT";
    private static final String EXTRA_TARGET_LABEL =
            "com.mmwtl.atlasappwidget.extra.DIAGNOSTIC_TARGET_LABEL";
    private static final long TRANSITION_DURATION_MS = 3_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable launchCallback;
    private boolean launchScheduled;

    static Intent intentFor(Context context, Intent target, String label) {
        return new Intent(context, DiagnosticLaunchActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_TARGET_INTENT, target)
                .putExtra(EXTRA_TARGET_LABEL, label == null ? context.getString(R.string.app_name)
                        : label);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureOpaqueEdgeToEdgeWindow();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        TextView message = Ui.text(this, R.string.diagnostic_launch_message, 20, Ui.TEXT);
        message.setGravity(android.view.Gravity.CENTER);
        root.addView(message, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void configureOpaqueEdgeToEdgeWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Ui.BACKGROUND);
        window.setNavigationBarColor(Ui.BACKGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (launchScheduled) {
            return;
        }
        launchScheduled = true;
        launchCallback = this::launchTarget;
        handler.postDelayed(launchCallback, TRANSITION_DURATION_MS);
    }

    private void launchTarget() {
        Intent target = getIntent().getParcelableExtra(EXTRA_TARGET_INTENT);
        String label = getIntent().getStringExtra(EXTRA_TARGET_LABEL);
        if (target == null || target.getComponent() == null) {
            showLaunchFailure(label);
            return;
        }
        try {
            target.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(target);
            finishWithoutAnimation();
        } catch (RuntimeException error) {
            AppLog.warn("Cannot launch diagnostic target " + target.getComponent(), error);
            showLaunchFailure(label);
        }
    }

    private void showLaunchFailure(String label) {
        Toast.makeText(this, getString(R.string.launch_failed,
                label == null || label.isBlank() ? getString(R.string.app_name) : label),
                Toast.LENGTH_SHORT).show();
        finishWithoutAnimation();
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        if (launchCallback != null) {
            handler.removeCallbacks(launchCallback);
            launchCallback = null;
        }
        super.onDestroy();
    }
}
