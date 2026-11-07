package com.crescent.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/** Themed dialog shown when airplane mode turns on while the person is entering a metal price. */
final class OfflineDialog {
    private OfflineDialog() {}

    static Dialog show(Activity activity, boolean airplane) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_offline, null);

        ((ImageView) view.findViewById(R.id.dialogIcon)).setImageResource(airplane ? R.drawable.ic_airplane : R.drawable.ic_info);
        ((TextView) view.findViewById(R.id.dialogTitle)).setText(airplane ? "Airplane mode is on" : "You're offline");
        ((TextView) view.findViewById(R.id.dialogMessage)).setText(
                "Live prices can't be loaded right now. You can continue with the saved price, or type your own price in the price box."
                        + (airplane ? "\n\nTurn off airplane mode and the price will refresh by itself."
                        : "\n\nReconnect and the price will refresh by itself."));

        Button settings = view.findViewById(R.id.dialogSettings);
        settings.setText(airplane ? "Airplane settings" : "Network settings");
        settings.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                activity.startActivity(new Intent(airplane ? Settings.ACTION_AIRPLANE_MODE_SETTINGS : Settings.ACTION_WIRELESS_SETTINGS));
            } catch (ActivityNotFoundException e) {
                try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (ActivityNotFoundException ignored) {}
            }
        });
        view.findViewById(R.id.dialogOk).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);

        Window w = dialog.getWindow();
        if (w != null) {
            int margin = Math.round(24 * activity.getResources().getDisplayMetrics().density);
            w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margin));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        return dialog;
    }
}