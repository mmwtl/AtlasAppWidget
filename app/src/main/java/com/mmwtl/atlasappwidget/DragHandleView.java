package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.widget.TextView;

final class DragHandleView extends TextView {
    DragHandleView(Context context) {
        super(context);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
