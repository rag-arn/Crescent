package com.crescent.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityAssetBreakdownBinding;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows the latest saved calculation split into Cash & Savings, Gold & Silver,
 * Commercial Goods and Liabilities. Opened from "Asset Breakdown" and "Detailed Breakdown" on the dashboard.
 */
public class AssetBreakdownActivity extends AppCompatActivity {
    private static final double ZAKAT_RATE = 0.025;

    private ActivityAssetBreakdownBinding b;
    private CrescentRepository repo;
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));

    private String taka(double v) { return "৳" + nf.format(Math.round(v)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityAssetBreakdownBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.insets(this, b.getRoot(), b.headerBar, null);
        repo = new CrescentRepository(getApplicationContext());

        b.backButton.setOnClickListener(v -> finish());
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        repo.assets(assets -> repo.snapshots(snaps -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            render(assets, snaps.isEmpty() ? null : snaps.get(0));
        })));
    }

    private void render(List<Asset> assets, Snapshot s) {
        // Assets are saved newest-first; keep only the latest value per category.
        Map<String, Double> latest = new HashMap<>();
        for (Asset a : assets) if (!latest.containsKey(a.category)) latest.put(a.category, a.amount);

        double cash = val(latest, "Cash") + val(latest, "Bank savings") + val(latest, "Cash receivable");
        double metals = val(latest, "Gold") + val(latest, "Silver");
        double commercial = val(latest, "Commercial goods");
        double liabilities = s == null ? 0 : s.liabilities;

        // Zakat is only payable when the Nisab was reached in the last calculation.
        boolean due = s != null && s.zakat > 0;

        b.cashAmount.setText(taka(cash));
        b.cashSub.setText("Zakat: " + taka(due ? cash * ZAKAT_RATE : 0));
        b.goldAmount.setText(taka(metals));
        b.goldSub.setText("Zakat: " + taka(due ? metals * ZAKAT_RATE : 0));
        b.commercialAmount.setText(taka(commercial));
        b.commercialSub.setText("Zakat: " + taka(due ? commercial * ZAKAT_RATE : 0));
        b.liabilitiesAmount.setText("-" + taka(liabilities));
        b.liabilitiesSub.setText("Deductible");

        if (s == null) {
            b.statusText.setText("No calculation saved yet. Calculate Zakat from the dashboard to see your breakdown.");
        } else {
            String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(s.createdAt));
            b.statusText.setText("Last calculated " + date + "\nTotal Zakat payable: " + taka(s.zakat)
                    + (due ? " (2.5% of net wealth after liabilities)" : " (below Nisab)"));
        }
    }

    private static double val(Map<String, Double> m, String k) {
        Double d = m.get(k);
        return d == null ? 0 : d;
    }
}