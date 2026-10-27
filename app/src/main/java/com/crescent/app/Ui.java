package com.crescent.app;

import android.app.Activity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Shared window setup so every screen sits clear of the status bar, navigation bar and keyboard. */
final class Ui {
    private Ui() {}

    /**
     * @param header the top bar; gets extra top padding for the status bar
     * @param bottom the bottom bar (may be null); gets extra bottom padding for the navigation bar / keyboard.
     *               When null, the root view gets the bottom padding instead.
     */
    static void insets(Activity activity, View root, View header, View bottom) {
        final int headerTop = header.getPaddingTop();
        final int bottomPad = bottom != null ? bottom.getPaddingBottom() : 0;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets top = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets low = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            header.setPadding(header.getPaddingLeft(), headerTop + top.top, header.getPaddingRight(), header.getPaddingBottom());
            if (bottom != null) {
                bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(), bottom.getPaddingRight(), bottomPad + low.bottom);
            } else {
                root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), low.bottom);
            }
            return insets;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(activity.getWindow(), root);
        c.setAppearanceLightStatusBars(false);       // light icons on the dark teal header
        c.setAppearanceLightNavigationBars(true);    // dark icons on the cream bottom bar
    }
}