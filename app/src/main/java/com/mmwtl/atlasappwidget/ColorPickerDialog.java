package com.mmwtl.atlasappwidget;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class ColorPickerDialog {
    interface Listener {
        void onColorSelected(int color);
    }

    private ColorPickerDialog() {
    }

    static void show(Context context, String title, int initialColor, Listener listener) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 24), Ui.dp(context, 10),
                Ui.dp(context, 24), Ui.dp(context, 4));

        View preview = new View(context);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(context, 70)
        );
        previewParams.bottomMargin = Ui.dp(context, 14);
        content.addView(preview, previewParams);

        int[] channels = {Color.red(initialColor), Color.green(initialColor), Color.blue(initialColor)};
        TextView[] labels = new TextView[3];
        SeekBar[] sliders = new SeekBar[3];
        String[] names = {
                context.getString(R.string.color_red),
                context.getString(R.string.color_green),
                context.getString(R.string.color_blue)
        };

        Runnable update = () -> {
            int color = Color.rgb(channels[0], channels[1], channels[2]);
            preview.setBackground(Ui.rounded(color, Ui.dp(context, 8)));
            for (int index = 0; index < 3; index++) {
                labels[index].setText(context.getString(
                        R.string.color_channel,
                        names[index],
                        channels[index]
                ));
            }
        };

        for (int index = 0; index < 3; index++) {
            int channelIndex = index;
            labels[index] = Ui.text(context, "", 14, Ui.TEXT);
            content.addView(labels[index]);
            sliders[index] = new SeekBar(context);
            sliders[index].setMax(255);
            sliders[index].setProgress(channels[index]);
            sliders[index].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    channels[channelIndex] = progress;
                    update.run();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            content.addView(sliders[index], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        update.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    listener.onColorSelected(Color.rgb(channels[0], channels[1], channels[2]));
                    dialog.dismiss();
                }));
        dialog.show();
    }
}
