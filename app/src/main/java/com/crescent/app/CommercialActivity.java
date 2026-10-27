package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityCommercialBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Step 4: market value of commercial goods held for sale. Next: Nisab. */
public class CommercialActivity extends AppCompatActivity {
    private static final String INFO_TEXT =
            "Enter the current market value of goods you hold for sale or trade, such as shop inventory and stock for resale.\n\n"
                    + "Include only items you intend to sell. Do not include fixed assets used to run your business or daily life, "
                    + "such as shop rent, vehicles, furniture (tables, chairs), computers, machinery or tools.";

    private ActivityCommercialBinding b;

    private static double number(EditText e) {
        String s = e.getText().toString().trim().replace(",", "");
        if (s.isEmpty()) return 0;
        try {
            double d = Double.parseDouble(s);
            return (Double.isNaN(d) || Double.isInfinite(d)) ? 0 : Math.max(0, d);
        } catch (Exception ex) { return 0; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityCommercialBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.infoButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Commercial goods")
                .setMessage(INFO_TEXT)
                .setPositiveButton("Got it", null)
                .show());

        b.previousButton.setOnClickListener(v -> finish());
        b.nextButton.setOnClickListener(v -> {
            Intent i = new Intent(this, NisabActivity.class);
            Bundle carried = getIntent().getExtras();
            if (carried != null) i.putExtras(carried);
            i.putExtra("commercial_goods", number(b.commercialInput));
            startActivity(i);
        });
    }
}