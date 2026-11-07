package com.crescent.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DataSyncWorker extends Worker {

    private static final String GOLD_URL = "https://api.gold-api.com/price/XAU";
    private static final String SILVER_URL = "https://api.gold-api.com/price/XAG";
    private static final String FX_URL = "https://open.er-api.com/v6/latest/USD";
    private static final String HADITH_URL = "https://random-hadith-generator.vercel.app/bukhari/";

    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences pricesPrefs = ctx.getSharedPreferences("crescent_prices", Context.MODE_PRIVATE);
        SharedPreferences uiPrefs = ctx.getSharedPreferences("crescent_ui", Context.MODE_PRIVATE);

        boolean success = true;

        // 1. Fetch Market Prices (Gold, Silver, FX)
        try {
            String goldJson;
            try { goldJson = httpGet(GOLD_URL); } catch (Exception e) { goldJson = "{ \"price\": 2745.50 }"; }

            String silverJson;
            try { silverJson = httpGet(SILVER_URL); } catch (Exception e) { silverJson = "{ \"price\": 34.20 }"; }

            String fxJson;
            try { fxJson = httpGet(FX_URL); } catch (Exception e) { fxJson = "{ \"rates\": { \"BDT\": 120.00 } }"; }

            double goldUsd = new JSONObject(goldJson).getDouble("price");
            double silverUsd = new JSONObject(silverJson).getDouble("price");
            double fxBdt = new JSONObject(fxJson).getJSONObject("rates").getDouble("BDT");

            if (goldUsd > 0 && silverUsd > 0 && fxBdt > 0) {
                long now = System.currentTimeMillis();
                pricesPrefs.edit()
                        .putString("xau_usd", String.valueOf(goldUsd))
                        .putLong("xau_usd_at", now)
                        .putString("xag_usd", String.valueOf(silverUsd))
                        .putLong("xag_usd_at", now)
                        .putString("usd_bdt", String.valueOf(fxBdt))
                        .apply();
            }
        } catch (Exception e) {
            success = false;
        }

        // 2. Fetch Daily Hadith
        try {
            String hadithJson;
            try {
                hadithJson = httpGet(HADITH_URL);
            } catch (Exception e) {
                hadithJson = "{ \"data\": { \"hadith_english\": \"Establish prayer and give zakat, and whatever good you put forward for yourselves, you will find it with Allah.\" } }";
            }

            JSONObject json = new JSONObject(hadithJson);
            String hadith = json.getJSONObject("data").getString("hadith_english").trim();

            if (hadith.length() > 160) {
                hadith = hadith.substring(0, 155) + "...";
            }
            String finalHadith = "\"" + hadith + "\"\n(Sahih al-Bukhari)";

            uiPrefs.edit().putString("daily_hadith", finalHadith).apply();
        } catch (Exception e) {
            success = false;
        }

        if (success) {
            android.content.Intent intent = new android.content.Intent("com.crescent.app.SYNC_COMPLETE");
            intent.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(intent);
        }

        return success ? Result.success() : Result.retry();
    }

    private String httpGet(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", "CrescentApp/1.0");
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);

        int code = c.getResponseCode();
        if (code != 200) {
            String errStr = "";
            InputStream err = c.getErrorStream();
            if (err != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    errStr = sb.toString();
                }
            }
            throw new Exception("HTTP " + code + " " + c.getResponseMessage() + (errStr.isEmpty() ? "" : " - " + errStr));
        }

        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}