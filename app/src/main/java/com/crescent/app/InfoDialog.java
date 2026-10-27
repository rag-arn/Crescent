package com.crescent.app;

import android.app.Activity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Small helper that shows the text behind an ⓘ button. */
final class InfoDialog {
    private InfoDialog() {}

    static void show(Activity activity, String title, String message) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got it", null)
                .show();
    }
}