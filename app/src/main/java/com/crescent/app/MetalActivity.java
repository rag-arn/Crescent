package com.crescent.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityMetalBinding;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared wizard step for precious metals (gold, silver): holdings in any unit/purity,
 * a live (or manually overridden) price, and conversion to BDT.
 */
public abstract class MetalActivity extends AppCompatActivity {
    // ----- hooks implemented by GoldActivity / SilverActivity -----
    protected abstract String metalKey();            // "gold" | "silver" (used for intent extras)
    protected abstract String metalTitle();          // heading
    protected abstract String apiSymbol();           // "XAU" | "XAG"
    protected abstract int stepNumber();
    protected abstract String priceBasisText();      // "24K gold" | "pure silver"
    protected abstract String[] purityNames();
    protected abstract double[] purityFractions();   // same length as purityNames()
    protected abstract Class<?> nextActivity();

    protected static final int TOTAL_STEPS = 4;
    private static final String PRICE_URL = "https://api.gold-api.com/price/";
    private static final String FX_URL = "https://open.er-api.com/v6/latest/USD";
    private static final double TROY_OUNCE_GRAMS = 31.1034768;
    private static final double BHORI_GRAMS = 11.664;

    private static final String[] UNIT_NAMES = {
            "Gram (g)", "Milligram (mg)", "Kilogram (kg)", "Metric ton",
            "Bhori / Tola", "Ana", "Roti",
            "Troy ounce (oz t)", "Troy pound", "Ounce (regular)", "Pound (lb)"
    };
    private static final double[] UNIT_GRAMS = {
            1, 0.001, 1000, 1000000,
            BHORI_GRAMS, 0.729, 0.1215,
            TROY_OUNCE_GRAMS, 373.2417, 28.3495, 453.5924
    };

    private class Row {
        View view; EditText amount; Spinner unit, purity; TextView value;
        double pureGrams() {
            return parse(amount) * UNIT_GRAMS[unit.getSelectedItemPosition()] * purityFractions()[purity.getSelectedItemPosition()];
        }
    }

    private ActivityMetalBinding b;
    private final List<Row> rows = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private double liveBdtPerGram = 0;   // price from the API / cache (0 = none)
    private double bdtPerGram = 0;       // effective price used for calculation (live or user-entered)
    private boolean manualOverride = false;
    private boolean priceUnitUserChosen = false;  // false: price unit follows the first row's unit
    private boolean settingPrice = false;         // true while we write the price field ourselves
    private boolean fetchRunning = false;
    private String sourceText = "Price not loaded yet.";

    private static double parse(EditText e) {
        String s = e.getText().toString().trim().replace(",", "");
        if (s.isEmpty()) return 0;
        try {
            double d = Double.parseDouble(s);
            return (Double.isNaN(d) || Double.isInfinite(d)) ? 0 : Math.max(0, d);
        } catch (Exception ex) { return 0; }
    }
    private String taka(double v) { return "৳" + NumberFormat.getNumberInstance(new Locale("en", "BD")).format(Math.round(v)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityMetalBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = getSharedPreferences("crescent_prices", MODE_PRIVATE);

        b.stepText.setText("Step " + stepNumber() + " of " + TOTAL_STEPS);
        b.titleText.setText(metalTitle());
        b.introText.setText("Enter the " + metalTitle().toLowerCase(Locale.US) + " you own. Choose the unit and purity for each entry. Values are converted to Taka (৳) using the price below, which you can edit.");
        b.priceLabel.setText("Price of " + priceBasisText() + " (৳) per:");
        b.priceUnitSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, UNIT_NAMES));

        b.priceUnitSpinner.setOnTouchListener((v, e) -> { priceUnitUserChosen = true; return false; });
        b.priceUnitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refreshPriceField(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        b.priceInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(Editable s) {
                if (settingPrice) return;
                priceUnitUserChosen = true;
                manualOverride = true;
                bdtPerGram = parse(b.priceInput) / UNIT_GRAMS[b.priceUnitSpinner.getSelectedItemPosition()];
                refreshStatus();
                updateValues();
            }
        });

        loadCachedPrice();
        addRow();
        syncPriceUnit();
        refreshPriceField();
        refreshStatus();

        b.addMoreButton.setOnClickListener(v -> addRow());
        b.refreshPriceButton.setOnClickListener(v -> fetchPrice());
        b.useLivePriceButton.setOnClickListener(v -> useLivePrice());
        b.previousButton.setOnClickListener(v -> finish());
        b.nextButton.setOnClickListener(v -> next());
        fetchPrice();
    }

    @Override protected void onDestroy() { super.onDestroy(); executor.shutdownNow(); }

    // ---------- rows ----------
    private void addRow() {
        final Row r = new Row();
        r.view = LayoutInflater.from(this).inflate(R.layout.item_metal_row, b.rowsContainer, false);
        r.amount = r.view.findViewById(R.id.amountInput);
        r.unit = r.view.findViewById(R.id.unitSpinner);
        r.purity = r.view.findViewById(R.id.puritySpinner);
        r.value = r.view.findViewById(R.id.rowValue);
        r.unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, UNIT_NAMES));
        r.purity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, purityNames()));
        r.amount.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(Editable s) { updateValues(); }
        });
        r.unit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateValues();
                if (!rows.isEmpty() && rows.get(0) == r) syncPriceUnit();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        r.purity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { updateValues(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        r.view.findViewById(R.id.removeButton).setOnClickListener(v -> {
            if (rows.size() > 1) { rows.remove(r); b.rowsContainer.removeView(r.view); syncPriceUnit(); }
            else r.amount.setText("");
            updateValues();
        });
        rows.add(r);
        b.rowsContainer.addView(r.view);
        updateValues();
    }

    private void updateValues() {
        double totalGrams = 0;
        for (Row r : rows) {
            double g = r.pureGrams();
            totalGrams += g;
            if (g <= 0) r.value.setText("—");
            else if (bdtPerGram > 0) r.value.setText(String.format(Locale.US, "%.3f g pure %s ≈ %s", g, metalKey(), taka(g * bdtPerGram)));
            else r.value.setText(String.format(Locale.US, "%.3f g pure %s (no price yet)", g, metalKey()));
        }
        if (bdtPerGram > 0) b.totalText.setText(String.format(Locale.US, "Total: %.3f g pure %s ≈ %s", totalGrams, metalKey(), taka(totalGrams * bdtPerGram)));
        else b.totalText.setText(String.format(Locale.US, "Total: %.3f g pure %s (enter or fetch a price)", totalGrams, metalKey()));
    }

    // ---------- price ----------
    private static String formatPrice(double v) {
        if (!(v > 0)) return "";
        return new BigDecimal(v).round(new MathContext(7)).stripTrailingZeros().toPlainString();
    }

    /** Re-renders the price box from the stored per-gram price, in the selected price unit. */
    private void refreshPriceField() {
        int u = b.priceUnitSpinner.getSelectedItemPosition();
        if (u < 0) u = 0;
        settingPrice = true;
        b.priceInput.setText(formatPrice(bdtPerGram * UNIT_GRAMS[u]));
        settingPrice = false;
    }

    /** Until the user picks their own unit, the price unit follows the first entry's unit. */
    private void syncPriceUnit() {
        if (priceUnitUserChosen || rows.isEmpty()) return;
        int idx = rows.get(0).unit.getSelectedItemPosition();
        if (idx >= 0 && idx != b.priceUnitSpinner.getSelectedItemPosition()) b.priceUnitSpinner.setSelection(idx);
        refreshPriceField();
    }

    private void refreshStatus() {
        b.priceStatus.setText((manualOverride ? "✎ Using your own price.\n" : "") + sourceText);
    }

    private void useLivePrice() {
        if (liveBdtPerGram <= 0) { Toast.makeText(this, "No live price available yet. Tap Refresh price.", Toast.LENGTH_LONG).show(); return; }
        manualOverride = false;
        priceUnitUserChosen = false;
        bdtPerGram = liveBdtPerGram;
        syncPriceUnit();
        refreshPriceField();
        refreshStatus();
        updateValues();
    }

    private String cacheKey() { return apiSymbol().toLowerCase(Locale.US) + "_usd"; }

    private void loadCachedPrice() {
        double usd = Double.parseDouble(prefs.getString(cacheKey(), "0"));
        double fx = Double.parseDouble(prefs.getString("usd_bdt", "0"));
        long t = prefs.getLong(cacheKey() + "_at", 0);
        if (usd > 0 && fx > 0) {
            liveBdtPerGram = usd / TROY_OUNCE_GRAMS * fx;
            bdtPerGram = liveBdtPerGram;
            sourceText = "Saved price (offline) from " + DateFormat.getDateTimeInstance().format(new Date(t));
        }
    }

    private void fetchPrice() {
        if (fetchRunning) return;
        fetchRunning = true;
        b.refreshPriceButton.setEnabled(false);
        b.refreshPriceButton.setText("Fetching…");
        final String symbol = apiSymbol();
        final String key = cacheKey();
        executor.execute(() -> {
            try {
                double usd = new JSONObject(httpGet(PRICE_URL + symbol)).getDouble("price");
                double fx = new JSONObject(httpGet(FX_URL)).getJSONObject("rates").getDouble("BDT");
                if (!(usd > 0) || !(fx > 0)) throw new IOException("Invalid price data");
                final long now = System.currentTimeMillis();
                prefs.edit().putString(key, String.valueOf(usd)).putString("usd_bdt", String.valueOf(fx)).putLong(key + "_at", now).apply();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    liveBdtPerGram = usd / TROY_OUNCE_GRAMS * fx;
                    sourceText = String.format(Locale.US, "Live: $%.2f/oz t × ৳%.2f/USD, updated %s", usd, fx, DateFormat.getTimeInstance().format(new Date(now)));
                    if (!manualOverride) { bdtPerGram = liveBdtPerGram; syncPriceUnit(); refreshPriceField(); }
                    refreshStatus();
                    fetchDone();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (bdtPerGram <= 0) { sourceText = "Could not fetch the price. Check your connection and tap Refresh price, or enter a price yourself."; refreshStatus(); }
                    else Toast.makeText(this, "Could not refresh the live price.", Toast.LENGTH_LONG).show();
                    fetchDone();
                });
            }
        });
    }

    private void fetchDone() {
        fetchRunning = false;
        b.refreshPriceButton.setEnabled(true);
        b.refreshPriceButton.setText("Refresh price");
        updateValues();
    }

    private static String httpGet(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } finally { c.disconnect(); }
    }

    // ---------- navigation ----------
    private void next() {
        double totalGrams = 0;
        for (Row r : rows) totalGrams += r.pureGrams();
        if (totalGrams > 0 && bdtPerGram <= 0) {
            Toast.makeText(this, "No price available. Refresh the price or enter one.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, nextActivity());
        Bundle carried = getIntent().getExtras();
        if (carried != null) i.putExtras(carried);       // keep data from earlier steps
        i.putExtra(metalKey() + "_pure_grams", totalGrams);
        i.putExtra(metalKey() + "_value_bdt", totalGrams * bdtPerGram);
        i.putExtra(metalKey() + "_price_per_gram", bdtPerGram);   // used by the Nisab step
        startActivity(i);
    }
}