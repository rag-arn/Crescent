package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityResultBinding;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Final step: calculation table, verdict, snapshot saving and PDF download. */
public class ResultActivity extends AppCompatActivity {
    public static final String DISCLAIMER = "Check the calculation with an expert. This may not be 100% correct.";
    private static final String NOTE = "This estimate is 2.5% of net wealth and assumes your wealth has stayed at or above the Nisab for one full lunar year (hawl) and that all entered assets are zakatable. Rules vary by school of thought and circumstances.";

    private ActivityResultBinding b;
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));
    /** Table lines shared by the screen and the PDF: {type, label, screenValue, pdfValue}; type = H (section), R (row), T (total row). */
    private final List<String[]> lines = new ArrayList<>();

    private String taka(double v) { return "৳" + nf.format(Math.round(v)); }
    private String bdt(double v) { return "BDT " + nf.format(Math.round(v)); }

    private void section(String title) { lines.add(new String[]{"H", title, "", ""}); }
    private void row(String label, double v) { lines.add(new String[]{"R", label, taka(v), bdt(v)}); }
    private void total(String label, double v) { lines.add(new String[]{"T", label, taka(v), bdt(v)}); }
    private void rowText(String type, String label, String value) { lines.add(new String[]{type, label, value, value}); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityResultBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        Intent in = getIntent();
        double gold = in.getDoubleExtra("gold_value_bdt", 0), goldGrams = in.getDoubleExtra("gold_pure_grams", 0);
        double silver = in.getDoubleExtra("silver_value_bdt", 0), silverGrams = in.getDoubleExtra("silver_pure_grams", 0);
        double cashHand = in.getDoubleExtra("cash_hand", 0), cashBank = in.getDoubleExtra("cash_bank", 0);
        double cashPayable = in.getDoubleExtra("cash_payable", 0), cashReceivable = in.getDoubleExtra("cash_receivable", 0);
        String basis = in.getStringExtra("nisab_basis");
        double nisab = in.getDoubleExtra("nisab_amount", 0);
        double nisabGrams = in.getDoubleExtra("nisab_grams", 0), nisabPrice = in.getDoubleExtra("nisab_price_per_gram", 0);

        if (basis == null || !(nisab > 0)) {
            Toast.makeText(this, "Nisab was not calculated. Please go back and choose gold or silver.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        b.previousButton.setOnClickListener(v -> finish());
        b.homeButton.setOnClickListener(v -> {
            Intent h = new Intent(this, MainActivity.class);
            h.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(h);
            finish();
        });

        // ----- calculation -----
        double assets = gold + silver + cashHand + cashBank + cashReceivable;
        double net = Math.max(0, assets - cashPayable);          // cash payable is deducted from total assets
        boolean due = net >= nisab;
        double zakat = due ? net * 0.025 : 0;
        String basisName = basis.equals("gold") ? "Gold" : "Silver";

        section("Assets");
        row(goldGrams > 0 ? String.format(Locale.US, "Gold (%.3f g pure)", goldGrams) : "Gold", gold);
        row(silverGrams > 0 ? String.format(Locale.US, "Silver (%.3f g pure)", silverGrams) : "Silver", silver);
        row("Cash in hand", cashHand);
        row("Cash in bank", cashBank);
        row("Cash receivable", cashReceivable);
        total("Total assets", assets);
        section("Deductions");
        row("Cash payable", cashPayable);
        total("Net wealth (assets − payable)", net);
        section("Nisab (" + basisName + " basis)");
        rowText("R", "Weight", String.format(Locale.US, "%.2f g of %s", nisabGrams, basis));
        rowText("R", "Price per gram", "৳" + String.format(Locale.US, "%,.2f", nisabPrice));
        lines.get(lines.size() - 1)[3] = "BDT " + String.format(Locale.US, "%,.2f", nisabPrice);
        total("Nisab threshold", nisab);
        section("Zakat");
        row("Net wealth", net);
        row("Nisab threshold", nisab);
        rowText("R", "Net wealth vs Nisab", due ? "At or above Nisab" : "Below Nisab");
        rowText("R", "Zakat rate", "2.5%");
        total("Zakat payable", zakat);

        // ----- verdict -----
        String headline = due ? "Zakat is due: " + taka(zakat) : "Zakat is not due";
        String detail = due
                ? "Your net wealth (" + taka(net) + ") is at or above the Nisab (" + taka(nisab) + ")."
                : "Your net wealth (" + taka(net) + ") is below the Nisab (" + taka(nisab) + ").";
        b.verdictText.setText(headline + "\n\n" + detail);
        b.verdictCard.setCardBackgroundColor(due ? 0x33176B52 : 0x33888888);
        b.noteText.setText(NOTE);
        renderTable();

        // ----- PDF -----
        final String pdfHeadline = due ? "Zakat is due: " + bdt(zakat) : "Zakat is not due";
        final String pdfDetail = due
                ? "Net wealth (" + bdt(net) + ") is at or above the Nisab (" + bdt(nisab) + ")."
                : "Net wealth (" + bdt(net) + ") is below the Nisab (" + bdt(nisab) + ").";
        final boolean dueFinal = due;
        b.pdfButton.setOnClickListener(v -> {
            StringBuilder r = new StringBuilder();
            r.append("TITLE|Zakat Calculation Report\n");
            r.append("DATE|Generated: ").append(DateFormat.getDateTimeInstance().format(new Date())).append("\n");
            r.append("VERDICT|").append(dueFinal ? "1" : "0").append("|").append(pdfHeadline).append("|").append(pdfDetail).append("\n");
            for (String[] l : lines) r.append(l[0]).append("|").append(l[1]).append("|").append(l[3]).append("\n");
            r.append("NOTE|").append(NOTE).append("\n");
            r.append("WARN|").append(DISCLAIMER);          // always the last, bold red line
            Intent i = new Intent(this, PdfGenerationService.class);
            i.putExtra("report", r.toString());
            androidx.core.content.ContextCompat.startForegroundService(this, i);
        });

        // ----- save snapshot once (not again after rotation) -----
        if (state == null) {
            CrescentRepository repo = new CrescentRepository(getApplicationContext());
            StringBuilder plain = new StringBuilder("CRESCENT — ZAKAT ESTIMATE\n");
            for (String[] l : lines) {
                if (l[0].equals("H")) plain.append("\n").append(l[1].toUpperCase(Locale.US)).append("\n");
                else plain.append(l[1]).append(": ").append(l[3]).append("\n");
            }
            plain.append("\n").append(headline).append("\n").append(DISCLAIMER);
            repo.saveSnapshot(new Snapshot(System.currentTimeMillis(), Calendar.getInstance().get(Calendar.YEAR),
                    assets, cashPayable, net, nisab, zakat, plain.toString()));
            repo.saveAsset("Cash", cashHand); repo.saveAsset("Bank savings", cashBank); repo.saveAsset("Cash receivable", cashReceivable);
            repo.saveAsset("Gold", gold); repo.saveAsset("Silver", silver);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView cell(String text, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setPadding(dp(8), dp(7), dp(8), dp(7));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private void renderTable() {
        for (String[] l : lines) {
            TableRow tr = new TableRow(this);
            if (l[0].equals("H")) {
                TextView h = cell(l[1], true);
                h.setTextColor(0xFFFFFFFF);
                h.setBackgroundColor(0xFF176B52);
                TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
                lp.span = 2;
                lp.topMargin = dp(12);
                tr.addView(h, lp);
            } else {
                boolean bold = l[0].equals("T");
                TextView a = cell(l[1], bold), v = cell(l[2], bold);
                v.setGravity(Gravity.END);
                if (bold) { a.setBackgroundColor(0x1A176B52); v.setBackgroundColor(0x1A176B52); }
                tr.addView(a);
                tr.addView(v);
            }
            b.resultTable.addView(tr);
        }
    }
}