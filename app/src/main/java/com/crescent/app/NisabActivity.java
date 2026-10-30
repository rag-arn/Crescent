package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityNisabBinding;
import java.text.NumberFormat;
import java.util.Locale;

/** Step 4: choose gold or silver as the Nisab basis; the threshold is computed from the step-1/2 prices. */
public class NisabActivity extends AppCompatActivity {
    public static final double GOLD_NISAB_GRAMS = 87.48;     // 7.5 bhori
    public static final double SILVER_NISAB_GRAMS = 612.36;  // 52.5 bhori

    private ActivityNisabBinding b;
    private double goldPrice, silverPrice;   // BDT per gram (24K gold / pure silver)

    private static String money(double v, int decimals) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));
        nf.setMinimumFractionDigits(decimals);
        nf.setMaximumFractionDigits(decimals);
        return "৳" + nf.format(v);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityNisabBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.insets(this, b.getRoot(), b.headerBar, b.bottomBar);
        goldPrice = getIntent().getDoubleExtra("gold_price_per_gram", 0);
        silverPrice = getIntent().getDoubleExtra("silver_price_per_gram", 0);

        if (goldPrice > 0) {
            b.goldOption.setText("Gold: 87.48 g × " + money(goldPrice, 2) + " per gram\n= " + money(GOLD_NISAB_GRAMS * goldPrice, 0));
        } else {
            b.goldOption.setText("Gold: no price available. Go back to the Gold step and fetch or enter a price.");
            b.goldOption.setEnabled(false);
        }
        if (silverPrice > 0) {
            b.silverOption.setText("Silver: 612.36 g × " + money(silverPrice, 2) + " per gram\n= " + money(SILVER_NISAB_GRAMS * silverPrice, 0));
        } else {
            b.silverOption.setText("Silver: no price available. Go back to the Silver step and fetch or enter a price.");
            b.silverOption.setEnabled(false);
        }

        b.infoButton.setOnClickListener(v -> InfoDialog.show(this, "About Nisab",
                "Nisab is the minimum wealth at which Zakat becomes due. It equals the value of 87.48 g of gold (7.5 bhori) or 612.36 g of silver (52.5 bhori).\n\n"
                        + "Choose which one to use as your basis. The threshold is calculated from the price you set in the Gold or Silver step.\n\n"
                        + "The silver Nisab is lower, so more people reach it; the gold Nisab is higher. Scholars follow different opinions, so use the basis recommended by the scholar or method you follow."));
        b.backButton.setOnClickListener(v -> finish());
        b.previousButton.setOnClickListener(v -> finish());
        b.calculateButton.setOnClickListener(v -> calculate());
    }

    private void calculate() {
        boolean gold;
        int id = b.nisabGroup.getCheckedRadioButtonId();
        if (id == b.goldOption.getId()) gold = true;
        else if (id == b.silverOption.getId()) gold = false;
        else { Toast.makeText(this, "Please choose gold or silver as the Nisab basis.", Toast.LENGTH_LONG).show(); return; }

        double grams = gold ? GOLD_NISAB_GRAMS : SILVER_NISAB_GRAMS;
        double price = gold ? goldPrice : silverPrice;
        if (!(price > 0)) { Toast.makeText(this, "No price available for the chosen metal.", Toast.LENGTH_LONG).show(); return; }

        Intent i = new Intent(this, ResultActivity.class);
        Bundle carried = getIntent().getExtras();
        if (carried != null) i.putExtras(carried);
        i.putExtra("nisab_basis", gold ? "gold" : "silver");
        i.putExtra("nisab_grams", grams);
        i.putExtra("nisab_price_per_gram", price);
        i.putExtra("nisab_amount", grams * price);
        startActivity(i);
    }
}