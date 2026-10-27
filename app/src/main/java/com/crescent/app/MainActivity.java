package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.crescent.app.databinding.ActivityMainBinding;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard: shows the latest saved calculation and links to the calculator, history and info. */
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding b;
    private CrescentRepository repo;
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));

    private String taka(double v) { return "৳" + nf.format(Math.round(v)); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new CrescentRepository(getApplicationContext());

        setupSystemBars();
        setupClicks();
        makeLogoRound();
    }

    @Override protected void onResume() {
        super.onResume();
        loadDashboard();   // refresh after returning from a calculation
    }

    /** Keeps the header and bottom bar clear of the status/navigation bars (edge-to-edge on Android 15+). */
    private void setupSystemBars() {
        final int headerTop = b.brandHeader.getPaddingTop();
        final int navBottom = b.bottomNav.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(b.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            b.brandHeader.setPadding(b.brandHeader.getPaddingLeft(), headerTop + bars.top, b.brandHeader.getPaddingRight(), b.brandHeader.getPaddingBottom());
            b.bottomNav.setPadding(b.bottomNav.getPaddingLeft(), b.bottomNav.getPaddingTop(), b.bottomNav.getPaddingRight(), navBottom + bars.bottom);
            return insets;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), b.getRoot());
        c.setAppearanceLightStatusBars(false);       // light icons on the dark teal header
        c.setAppearanceLightNavigationBars(true);    // dark icons on the cream bottom bar
    }

    /** Clips the logo image into a circle, whatever shape the PNG itself has. */
    private void makeLogoRound() {
        b.logoImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        b.logoImage.setClipToOutline(true);
    }

    private void soon(String what) { Toast.makeText(this, what + " is coming soon.", Toast.LENGTH_SHORT).show(); }

    private void setupClicks() {
        b.recalcButton.setOnClickListener(v -> startActivity(new Intent(this, GoldActivity.class)));
        b.navHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        b.navHome.setOnClickListener(v -> { /* already on the dashboard */ });

        // Placeholders for features that are not available yet
        b.profileButton.setOnClickListener(v -> soon("Profile"));
        b.payButton.setOnClickListener(v -> soon("Zakat payment tracking"));
        b.addAssetText.setOnClickListener(v -> soon("Adding assets"));
        b.navAssets.setOnClickListener(v -> soon("Assets"));
        b.navSettings.setOnClickListener(v -> soon("Settings"));

        b.menuButton.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, b.menuButton);
            Menu m = pm.getMenu();
            m.add(0, 1, 0, "About Zakat");
            m.add(0, 2, 1, "Profile (coming soon)").setEnabled(false);
            m.add(0, 3, 2, "Settings (coming soon)").setEnabled(false);
            pm.setOnMenuItemClickListener((MenuItem item) -> {
                if (item.getItemId() == 1) startActivity(new Intent(this, InfoActivity.class));
                return true;
            });
            pm.show();
        });
    }

    private void loadDashboard() {
        repo.snapshots(snaps -> repo.assets(assets -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            render(snaps.isEmpty() ? null : snaps.get(0), assets);
        })));
    }

    private void render(Snapshot s, List<Asset> assets) {
        // Latest saved value per category (assets come newest first)
        Map<String, Double> latest = new HashMap<>();
        for (Asset a : assets) if (!latest.containsKey(a.category)) latest.put(a.category, a.amount);
        double cash = val(latest, "Cash") + val(latest, "Bank savings") + val(latest, "Cash receivable");
        double metals = val(latest, "Gold") + val(latest, "Silver");
        double commercial = val(latest, "Commercial goods");

        if (s == null) {
            b.lastCalculatedText.setText("—");
            b.zakatAmount.setText(taka(0));
            b.paidText.setText("Paid: —");
            b.targetText.setText("Target: " + taka(0));
            b.zakatProgress.setProgress(0);
            b.nisabSub.setText("Complete a calculation to see it");
            b.nisabStatus.setText("—");
            b.recalcText.setText("Calculate Zakat");
            setAsset(b.cashAmount, b.cashSub, 0, 0);
            setAsset(b.metalAmount, b.metalSub, 0, 0);
            setAsset(b.commercialAmount, b.commercialSub, 0, 0);
            b.liabilityAmount.setText(taka(0));
            b.liabilitySub.setText("Deducted");
            return;
        }

        b.lastCalculatedText.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(s.createdAt)));
        b.zakatAmount.setText(taka(s.zakat));
        b.paidText.setText("Paid: —");                       // payment tracking is not available yet
        b.targetText.setText("Target: " + taka(s.zakat));
        b.zakatProgress.setProgress(0);
        b.nisabSub.setText(taka(s.nisab) + "  (net wealth " + taka(s.netWealth) + ")");
        b.nisabStatus.setText(s.netWealth >= s.nisab ? "Above Nisab" : "Below Nisab");
        b.recalcText.setText("Recalculate");
        setAsset(b.cashAmount, b.cashSub, cash, s.assets);
        setAsset(b.metalAmount, b.metalSub, metals, s.assets);
        setAsset(b.commercialAmount, b.commercialSub, commercial, s.assets);
        b.liabilityAmount.setText("-" + taka(s.liabilities));
        b.liabilitySub.setText("Deducted");
    }

    private static double val(Map<String, Double> m, String k) { Double d = m.get(k); return d == null ? 0 : d; }

    private void setAsset(android.widget.TextView amount, android.widget.TextView sub, double v, double total) {
        amount.setText(taka(v));
        sub.setText(total > 0 ? Math.round(v * 100 / total) + "% of assets" : "—");
    }
}