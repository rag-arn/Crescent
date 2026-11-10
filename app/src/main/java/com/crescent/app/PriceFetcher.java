package com.crescent.app;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Fetches the spot price of gold/silver (USD per troy ounce) and the USD to BDT rate.
 * Never invents a number: if every source fails it throws an exception that says exactly why,
 * so the screen can show the real reason instead of a generic "network unavailable".
 * Call from a background thread.
 */
public final class PriceFetcher {

    /** A successful quote. {@code backup} is true when any part came from the secondary source. */
    public static final class Quote {
        public final double usdPerOunce, usdBdt;
        public final boolean backup;
        Quote(double usdPerOunce, double usdBdt, boolean backup) {
            this.usdPerOunce = usdPerOunce; this.usdBdt = usdBdt; this.backup = backup;
        }
    }

    private static final String METAL_URL = "https://api.gold-api.com/price/";          // + XAU / XAG
    private static final String FX_URL = "https://open.er-api.com/v6/latest/USD";
    // Backup: one JSON file with every currency/metal quoted against USD (units of X per 1 USD).
    private static final String[] BACKUP_URLS = {
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
            "https://latest.currency-api.pages.dev/v1/currencies/usd.json"
    };

    private PriceFetcher() {}

    public static Quote fetch(String symbol) throws Exception {
        StringBuilder errors = new StringBuilder();
        double metal = 0, fx = 0;
        boolean backup = false;

        try {
            metal = new JSONObject(get(METAL_URL + symbol)).getDouble("price");
        } catch (Exception e) { note(errors, "gold-api.com", e); }
        if (!valid(metal)) metal = 0;

        try {
            fx = new JSONObject(get(FX_URL)).getJSONObject("rates").getDouble("BDT");
        } catch (Exception e) { note(errors, "open.er-api.com", e); }
        if (!valid(fx)) fx = 0;

        if (metal == 0 || fx == 0) {
            JSONObject usd = null;
            for (String u : BACKUP_URLS) {
                try { usd = new JSONObject(get(u)).getJSONObject("usd"); break; }
                catch (Exception e) { note(errors, hostOf(u), e); }
            }
            if (usd != null) {
                if (metal == 0) {
                    double perUsd = usd.optDouble(symbol.toLowerCase(Locale.US), 0);   // ounces per 1 USD
                    if (valid(perUsd)) { metal = 1.0 / perUsd; backup = true; }
                }
                if (fx == 0) {
                    double bdt = usd.optDouble("bdt", 0);
                    if (valid(bdt)) { fx = bdt; backup = true; }
                }
            }
        }

        if (metal == 0 || fx == 0) {
            throw new Exception(errors.length() == 0 ? "No price source returned a usable value." : errors.toString());
        }
        return new Quote(metal, fx, backup);
    }

    /**
     * Older versions saved made-up placeholder prices as if they were live. Remove those once,
     * so they are never shown as a "saved price". Real saved prices are left untouched.
     */
    public static void purgeLegacyMockCache(SharedPreferences prefs) {
        if (prefs.getBoolean("mock_purged_v2", false)) return;
        SharedPreferences.Editor e = prefs.edit();
        purgeIfEquals(prefs, e, "xau_usd", 2745.50);
        purgeIfEquals(prefs, e, "xag_usd", 34.20);
        purgeIfEquals(prefs, e, "usd_bdt", 120.00);
        e.putBoolean("mock_purged_v2", true).apply();
    }

    private static void purgeIfEquals(SharedPreferences p, SharedPreferences.Editor e, String key, double mock) {
        try {
            if (Math.abs(Double.parseDouble(p.getString(key, "0")) - mock) < 1e-9) { e.remove(key); e.remove(key + "_at"); }
        } catch (Exception ignored) { }
    }

    private static boolean valid(double v) { return v > 0 && !Double.isNaN(v) && !Double.isInfinite(v); }

    private static void note(StringBuilder sb, String source, Exception e) {
        String m = e.getMessage();
        String d = e.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
        if (d.length() > 110) d = d.substring(0, 110) + "…";
        if (sb.length() > 0) sb.append("\n");
        sb.append(source).append(" → ").append(d);
    }

    private static String hostOf(String u) {
        try { return new URL(u).getHost(); } catch (Exception e) { return u; }
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        try {
            c.setRequestProperty("User-Agent", "CrescentApp/1.0");
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            int code = c.getResponseCode();
            if (code != 200) {
                String body = read(c.getErrorStream());
                throw new Exception("HTTP " + code + (body.isEmpty() ? "" : " " + body));
            }
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}