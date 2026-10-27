package com.crescent.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

/** Themed dialog that shows the text behind an info button. */
final class InfoDialog {
    private InfoDialog() {}

    static void show(Activity activity, String title, String message) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_info, null);
        ((TextView) view.findViewById(R.id.dialogTitle)).setText(title);
        ((TextView) view.findViewById(R.id.dialogMessage)).setText(message);
        view.findViewById(R.id.dialogOk).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);

        Window w = dialog.getWindow();
        if (w != null) {
            int margin = Math.round(24 * activity.getResources().getDisplayMetrics().density);
            w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margin));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }
}