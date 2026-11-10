package com.crescent.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Sage-themed replacement for AlertDialog (which renders on a plain white surface).
 * Buttons without a handler simply dismiss; buttons with a handler leave the dialog open
 * so the caller can dismiss it once async work finishes.
 */
public class ThemedDialog {

    public interface OnClick { void onClick(ThemedDialog dialog); }

    private final Activity activity;
    private final Dialog dialog;
    private final TextView titleView, messageView, errorView;
    private final EditText input;
    private final Button positive, negative;

    public ThemedDialog(Activity activity) {
        this.activity = activity;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_themed);

        titleView = dialog.findViewById(R.id.themedTitle);
        messageView = dialog.findViewById(R.id.themedMessage);
        errorView = dialog.findViewById(R.id.themedError);
        input = dialog.findViewById(R.id.themedInput);
        positive = dialog.findViewById(R.id.themedPositive);
        negative = dialog.findViewById(R.id.themedNegative);
        positive.setText("OK");
        positive.setOnClickListener(v -> dialog.dismiss());

        Window w = dialog.getWindow();
        if (w != null) {
            int margin = Math.round(24 * activity.getResources().getDisplayMetrics().density);
            w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margin));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public ThemedDialog title(String t) { titleView.setText(t); return this; }

    public ThemedDialog message(String m) { messageView.setText(m); return this; }

    /** Shows an email field pre-filled with {@code text}. */
    public ThemedDialog emailInput(String hint, String text) {
        input.setVisibility(View.VISIBLE);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setText(text);
        input.setSelection(input.getText().length());
        Window w = dialog.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return this;
    }

    public ThemedDialog positive(String label, OnClick click) {
        positive.setText(label);
        positive.setOnClickListener(v -> { if (click != null) click.onClick(this); else dialog.dismiss(); });
        return this;
    }

    public ThemedDialog negative(String label, OnClick click) {
        negative.setVisibility(View.VISIBLE);
        negative.setText(label);
        negative.setOnClickListener(v -> { if (click != null) click.onClick(this); else dialog.dismiss(); });
        return this;
    }

    public ThemedDialog onDismiss(Runnable r) {
        dialog.setOnDismissListener(d -> r.run());
        return this;
    }

    public String inputText() { return input.getText().toString().trim(); }

    public void setError(String msg) {
        errorView.setText(msg);
        errorView.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    public Button positiveButton() { return positive; }

    public void dismiss() { if (dialog.isShowing()) dialog.dismiss(); }

    public ThemedDialog show() {
        if (!activity.isFinishing() && !activity.isDestroyed()) dialog.show();
        return this;
    }
}