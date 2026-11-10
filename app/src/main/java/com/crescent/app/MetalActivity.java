package com.crescent.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
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
import java.io.InputStream;
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
    protected abstract String metalKey();
    protected abstract String metalTitle();
    protected abstract String apiSymbol();
    protected abstract int stepNumber();
    protected abstract String priceBasisText();
    protected abstract String[] purityNames();
    protected abstract double[] purityFractions();
    protected abstract Class<?> nextActivity();

    protected static final int TOTAL_STEPS = 5;
    private static final double TROY_OUNCE_GRAMS = 31.1034768;
    private static final double BHORI_GRAMS = 11.664;

    private static final String[] UNIT_NAMES = {"Gram (g)", "Kilogram (kg)", "Bhori / Tola", "Ana", "Roti", "Troy ounce"};
    private static final double[] UNIT_GRAMS = {1, 1000, BHORI_GRAMS, 0.729, 0.1215, TROY_OUNCE_GRAMS};

    private class Row {
        View view; EditText amount; Spinner unit, purity; TextView value, title;
        double pureGrams() {
            return parse(amount) * UNIT_GRAMS[unit.getSelectedItemPosition()] * purityFractions()[purity.getSelectedItemPosition()];
        }
    }

    private ActivityMetalBinding b;
    private final List<Row> rows = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private double liveBdtPerGram = 0;
    private double bdtPerGram = 0;
    private boolean manualOverride = false;
    private boolean priceUnitUserChosen = false;
    private boolean settingPrice = false;
    private boolean fetchRunning = false;
    private ObjectAnimator spin;
    private String sourceText = "Price not loaded yet.";

    // Connectivity (airplane-mode broadcast + network state)
    private ConnectivityMonitor monitor;
    private boolean offline = false;
    private boolean airplane = false;
    private Dialog offlineDialog;

    // Keys for the state saved across rotation (the dynamic rows and the price field are restored by hand)
    private static final String S_AMOUNTS = "rowAmounts", S_UNITS = "rowUnits", S_PURITIES = "rowPurities";
    private static final String S_MANUAL = "manualOverride", S_UNIT_CHOSEN = "priceUnitUserChosen";
    private static final String S_PRICE_UNIT = "priceUnit", S_PRICE = "bdtPerGram", S_SOURCE = "sourceText", S_FETCHING = "fetching";

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
        Ui.insets(this, b.getRoot(), b.headerBar, b.bottomBar);
        prefs = getSharedPreferences("crescent_prices", MODE_PRIVATE);
        PriceFetcher.purgeLegacyMockCache(prefs);   // drop placeholder prices saved by older versions
        monitor = new ConnectivityMonitor(this);
        airplane = ConnectivityMonitor.isAirplaneOn(this);
        offline = !ConnectivityMonitor.isOnline(this);

        b.stepText.setText("Step " + stepNumber() + " of " + TOTAL_STEPS);
        b.titleText.setText(metalTitle());
        final String purityWord = metalKey().equals("gold") ? "karat" : "purity";
        b.titleInfoButton.setOnClickListener(v -> InfoDialog.show(this, metalTitle(),
                "Enter the " + metalTitle().toLowerCase(Locale.US) + " you own. For each entry, choose the unit and " + purityWord
                        + ", then tap “+ Add more” for another entry. Values are converted to Taka (৳) using the price shown."));
        b.priceInfoButton.setOnClickListener(v -> InfoDialog.show(this, "About the price",
                "The live price is the international spot price (USD per troy ounce) converted to BDT, so it can differ from local jeweller (BAJUS) rates.\n\n"
                        + "A troy ounce is 31.1035 g, which is the unit international gold and silver prices are quoted in (a regular ounce is 28.3495 g).\n\n"
                        + "Edit the price in any unit to use your local rate; the other units update automatically. Tap the refresh icon to load the latest live price (this replaces a price you typed).\n\n"
                        + "The price is for " + priceBasisText() + "; each entry is adjusted for its " + purityWord + "."));
        b.priceLabel.setText("Price of " + priceBasisText() + " (৳) per:");
        b.priceUnitSpinner.setAdapter(spinnerAdapter(UNIT_NAMES));
        b.priceUnitSpinner.setSaveEnabled(false);
        b.priceInput.setSaveEnabled(false);

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
        restoreOrInit(state);

        b.addMoreButton.setOnClickListener(v -> addRow());
        b.refreshPriceButton.setOnClickListener(v -> refreshLivePrice());
        b.backButton.setOnClickListener(v -> finish());
        b.previousButton.setOnClickListener(v -> finish());
        b.nextButton.setOnClickListener(v -> next());
        // After a rotation the price is already on screen; fetch again only if it was still loading or is missing.
        final boolean refetch = state == null || state.getBoolean(S_FETCHING) || liveBdtPerGram <= 0;
        if (!offline && refetch) fetchPrice();   // offline: the banner explains, the saved or typed price is used
    }

    /** Restores the rows and price after a rotation, or starts with one empty row on a fresh launch. */
    private void restoreOrInit(Bundle state) {
        final String[] amounts = state == null ? null : state.getStringArray(S_AMOUNTS);
        if (amounts == null || amounts.length == 0) {
            addRow();
            syncPriceUnit();
            refreshPriceField();
            refreshStatus();
            return;
        }
        manualOverride = state.getBoolean(S_MANUAL);
        priceUnitUserChosen = state.getBoolean(S_UNIT_CHOSEN);
        if (manualOverride) bdtPerGram = state.getDouble(S_PRICE);        // otherwise loadCachedPrice() already set the live value
        sourceText = state.getString(S_SOURCE, sourceText);

        final int[] units = state.getIntArray(S_UNITS), purities = state.getIntArray(S_PURITIES);
        for (int i = 0; i < amounts.length; i++) {
            addRow();
            Row r = rows.get(i);
            r.amount.setText(amounts[i]);
            if (units != null && i < units.length) r.unit.setSelection(units[i]);
            if (purities != null && i < purities.length) r.purity.setSelection(purities[i]);
        }
        b.priceUnitSpinner.setSelection(state.getInt(S_PRICE_UNIT, 0));
        refreshPriceField();
        refreshStatus();
        updateValues();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        final int n = rows.size();
        final String[] amounts = new String[n];
        final int[] units = new int[n], purities = new int[n];
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            amounts[i] = r.amount.getText().toString();
            units[i] = r.unit.getSelectedItemPosition();
            purities[i] = r.purity.getSelectedItemPosition();
        }
        out.putStringArray(S_AMOUNTS, amounts);
        out.putIntArray(S_UNITS, units);
        out.putIntArray(S_PURITIES, purities);
        out.putBoolean(S_MANUAL, manualOverride);
        out.putBoolean(S_UNIT_CHOSEN, priceUnitUserChosen);
        out.putInt(S_PRICE_UNIT, b.priceUnitSpinner.getSelectedItemPosition());
        out.putDouble(S_PRICE, bdtPerGram);
        out.putString(S_SOURCE, sourceText);
        out.putBoolean(S_FETCHING, fetchRunning);
    }

    @Override protected void onStart() {
        super.onStart();
        monitor.start(this::onConnectivity);
    }

    @Override protected void onStop() {
        super.onStop();
        monitor.stop();
        dismissOfflineDialog();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        dismissOfflineDialog();
        if (spin != null) spin.cancel();
        executor.shutdownNow();
    }

    /** Called on the main thread whenever airplane mode or the network state changes (and once on start). */
    private void onConnectivity(boolean airplaneNow, boolean onlineNow) {
        final boolean offlineNow = !onlineNow;
        final boolean wasOffline = offline, wasAirplane = airplane;
        offline = offlineNow;
        airplane = airplaneNow;
        updateBanner();

        if (offlineNow && airplaneNow && (!wasOffline || !wasAirplane)) {
            showOfflineDialog();
        } else if (!offlineNow && wasOffline) {
            dismissOfflineDialog();
            Toast.makeText(this, "Back online. Refreshing price…", Toast.LENGTH_SHORT).show();
            fetchPrice();
        }
    }

    private void updateBanner() {
        final View root = b.offlineBanner.getRoot();
        if (!offline) { root.setVisibility(View.GONE); return; }
        b.offlineBanner.bannerIcon.setImageResource(airplane ? R.drawable.ic_airplane : R.drawable.ic_info);
        b.offlineBanner.bannerTitle.setText(airplane ? "Airplane mode is on" : "No internet connection");
        b.offlineBanner.bannerMessage.setText(bdtPerGram > 0
                ? "Live prices can't be loaded. The saved or your own price is used until you're back online."
                : "Live prices can't be loaded. Enter a price manually, or reconnect to load the live price.");
        root.setVisibility(View.VISIBLE);
    }

    private void showOfflineDialog() {
        if (isFinishing() || isDestroyed()) return;
        dismissOfflineDialog();
        offlineDialog = OfflineDialog.show(this, airplane);
    }

    private void dismissOfflineDialog() {
        if (offlineDialog != null) {
            try { offlineDialog.dismiss(); } catch (RuntimeException ignored) {}
            offlineDialog = null;
        }
    }

    private void addRow() {
        final Row r = new Row();
        r.view = LayoutInflater.from(this).inflate(R.layout.item_metal_row, b.rowsContainer, false);
        r.view.setSaveFromParentEnabled(false);   // every row uses the same ids, so the automatic restore would mix them up
        r.amount = r.view.findViewById(R.id.amountInput);
        r.unit = r.view.findViewById(R.id.unitSpinner);
        r.purity = r.view.findViewById(R.id.puritySpinner);
        r.value = r.view.findViewById(R.id.rowValue);
        r.title = r.view.findViewById(R.id.entryTitle);
        ((TextView) r.view.findViewById(R.id.purityLabel)).setText(metalKey().equals("gold") ? "Karat" : "Purity");
        r.unit.setAdapter(spinnerAdapter(UNIT_NAMES));
        r.purity.setAdapter(spinnerAdapter(purityNames()));
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
            if (rows.size() > 1) { rows.remove(r); b.rowsContainer.removeView(r.view); renumberRows(); syncPriceUnit(); }
            else r.amount.setText("");
            updateValues();
        });
        rows.add(r);
        b.rowsContainer.addView(r.view);
        renumberRows();
        updateValues();
    }

    /** Polished spinner adapter: compact selected view, roomy dropdown rows. */
    private ArrayAdapter<String> spinnerAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, R.layout.item_spinner_selected, android.R.id.text1, items);
        a.setDropDownViewResource(R.layout.item_spinner_dropdown);
        return a;
    }

    private void renumberRows() {
        for (int i = 0; i < rows.size(); i++) rows.get(i).title.setText(rows.size() == 1 ? "Entry" : "Entry " + (i + 1));
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

    private static String formatPrice(double v) {
        if (!(v > 0)) return "";
        return new BigDecimal(v).round(new MathContext(7)).stripTrailingZeros().toPlainString();
    }

    private void refreshPriceField() {
        int u = b.priceUnitSpinner.getSelectedItemPosition();
        if (u < 0) u = 0;
        settingPrice = true;
        b.priceInput.setText(formatPrice(bdtPerGram * UNIT_GRAMS[u]));
        settingPrice = false;
    }

    private void syncPriceUnit() {
        if (priceUnitUserChosen || rows.isEmpty()) return;
        int idx = rows.get(0).unit.getSelectedItemPosition();
        if (idx >= 0 && idx != b.priceUnitSpinner.getSelectedItemPosition()) b.priceUnitSpinner.setSelection(idx);
        refreshPriceField();
    }

    private void refreshStatus() {
        b.priceStatus.setText((manualOverride ? "Using your own price.\n" : "") + sourceText);
    }

    /** Refresh icon: drops any typed price, shows the last live price at once, then fetches the latest. */
    private void refreshLivePrice() {
        if (fetchRunning) return;
        if (offline) {
            Toast.makeText(this, "You're offline. Reconnect to refresh, or enter a price manually.", Toast.LENGTH_LONG).show();
            return;
        }
        if (liveBdtPerGram > 0) useLivePrice();
        fetchPrice();
    }

    private void useLivePrice() {
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
        if (offline) {
            Toast.makeText(this, "You're offline. Reconnect to refresh, or enter a price manually.", Toast.LENGTH_LONG).show();
            return;
        }
        fetchRunning = true;
        setRefreshing(true);
        final String symbol = apiSymbol();
        final String key = cacheKey();
        executor.execute(() -> {
            try {
                PriceFetcher.Quote q = PriceFetcher.fetch(symbol);
                final double usd = q.usdPerOunce, fx = q.usdBdt;
                final boolean backup = q.backup;
                final long now = System.currentTimeMillis();
                prefs.edit().putString(key, String.valueOf(usd)).putString("usd_bdt", String.valueOf(fx)).putLong(key + "_at", now).apply();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    liveBdtPerGram = usd / TROY_OUNCE_GRAMS * fx;
                    sourceText = String.format(Locale.US, "Live: $%.2f/oz t × ৳%.2f/USD, updated %s%s", usd, fx,
                            DateFormat.getTimeInstance().format(new Date(now)), backup ? "\n(backup price source)" : "");
                    if (!manualOverride) { bdtPerGram = liveBdtPerGram; syncPriceUnit(); refreshPriceField(); }
                    refreshStatus();
                    fetchDone();
                });
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (bdtPerGram <= 0) {
                        sourceText = "Couldn't fetch the live price. Enter a price manually.\n" + err;
                    } else {
                        int i = sourceText.indexOf("\nRefresh failed");
                        String base = i >= 0 ? sourceText.substring(0, i) : sourceText;
                        sourceText = base + "\nRefresh failed:\n" + err;
                    }
                    refreshStatus();
                    Toast.makeText(this, "Couldn't fetch the live price. See the note under the price.", Toast.LENGTH_LONG).show();
                    fetchDone();
                });
            }
        });
    }

    private void fetchDone() {
        fetchRunning = false;
        setRefreshing(false);
        updateValues();
    }

    /** Spins the refresh icon while a price is being fetched. */
    private void setRefreshing(boolean on) {
        b.refreshPriceButton.setEnabled(!on);
        b.refreshPriceButton.setAlpha(on ? 0.6f : 1f);
        if (on) {
            if (spin == null) {
                spin = ObjectAnimator.ofFloat(b.refreshPriceButton, View.ROTATION, 0f, 360f);
                spin.setDuration(900);
                spin.setInterpolator(new LinearInterpolator());
                spin.setRepeatCount(ValueAnimator.INFINITE);
            }
            if (!spin.isRunning()) spin.start();
        } else {
            if (spin != null) spin.cancel();
            b.refreshPriceButton.setRotation(0f);
        }
    }

    private void next() {
        double totalGrams = 0;
        for (Row r : rows) totalGrams += r.pureGrams();
        if (totalGrams > 0 && bdtPerGram <= 0) {
            Toast.makeText(this, "No price available. Tap the refresh icon or enter a price.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, nextActivity());
        Bundle carried = getIntent().getExtras();
        if (carried != null) i.putExtras(carried);
        i.putExtra(metalKey() + "_pure_grams", totalGrams);
        i.putExtra(metalKey() + "_value_bdt", totalGrams * bdtPerGram);
        i.putExtra(metalKey() + "_price_per_gram", bdtPerGram);
        startActivity(i);
    }
}