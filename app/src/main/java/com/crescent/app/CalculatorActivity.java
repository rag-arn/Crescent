package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityCalculatorBinding;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

/** Step 4: combines gold, silver and cash from earlier steps with the remaining items. */
public class CalculatorActivity extends AppCompatActivity {
    private ActivityCalculatorBinding b;
    private CrescentRepository repo;
    private double assets, liabilities, nisab, zakat;
    // values carried from steps 1-3
    private double gold, silver, cashHand, cashBank, cashPayable, cashReceivable;
    private String breakdown = "";

    private static double number(EditText e) {
        String s = e.getText().toString().trim().replace(",", "");
        if (s.isEmpty()) return 0;
        try { double d = Double.parseDouble(s); return (Double.isNaN(d) || Double.isInfinite(d)) ? 0 : Math.max(0, d); } catch (Exception ex) { return 0; }
    }
    private String taka(double v) { return "৳" + NumberFormat.getNumberInstance(new Locale("en", "BD")).format(Math.round(v)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityCalculatorBinding.inflate(getLayoutInflater()); setContentView(b.getRoot());
        repo = new CrescentRepository(getApplicationContext());
        Intent in = getIntent();
        gold = in.getDoubleExtra("gold_value_bdt", 0);
        silver = in.getDoubleExtra("silver_value_bdt", 0);
        cashHand = in.getDoubleExtra("cash_hand", 0);
        cashBank = in.getDoubleExtra("cash_bank", 0);
        cashPayable = in.getDoubleExtra("cash_payable", 0);
        cashReceivable = in.getDoubleExtra("cash_receivable", 0);
        b.summaryText.setText("From earlier steps:\nGold: " + taka(gold) + "\nSilver: " + taka(silver) +
                "\nCash in hand: " + taka(cashHand) + "\nCash in bank: " + taka(cashBank) +
                "\nCash receivable: " + taka(cashReceivable) + "\nCash payable (deducted): " + taka(cashPayable));

        b.backButton.setOnClickListener(v -> finish());
        b.previousButton.setOnClickListener(v -> finish());
        b.calculateButton.setOnClickListener(v -> calculate(true));
        b.pdfButton.setOnClickListener(v -> {
            // Always recalculate so the PDF reflects the current inputs; abort if the Nisab is invalid.
            if (!calculate(false)) return;
            b.pdfButton.setEnabled(false);
            final String report = pdfReport();
            final android.content.Context app = getApplicationContext();
            new Thread(() -> {
                java.io.File file = null;
                String error = null;
                try { file = PdfReportWriter.generateToCache(app, report); } catch (Exception ex) { error = ex.getMessage(); }
                final java.io.File out = file;
                final String err = error;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) { if (out != null) out.delete(); return; }
                    b.pdfButton.setEnabled(true);
                    if (out == null) {
                        android.widget.Toast.makeText(this, "Could not generate PDF" + (err == null ? "." : ": " + err), android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    startActivity(new Intent(this, PdfPreviewActivity.class).putExtra(PdfPreviewActivity.EXTRA_PDF_PATH, out.getAbsolutePath()));
                });
            }).start();
        });
    }

    /** Structured report understood by PdfReportWriter (TITLE / DATE / VERDICT / H / R / T / NOTE / WARN). */
    private String pdfReport() {
        double business = number(b.businessInput), investments = number(b.investmentInput), other = number(b.liabilityInput);
        double net = Math.max(0, assets - liabilities);
        boolean due = net >= nisab;
        StringBuilder r = new StringBuilder();
        r.append("TITLE|Zakat Calculation Report\n");
        r.append("DATE|Generated: ").append(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date())).append("\n");
        r.append("VERDICT|").append(due ? "1" : "0").append("|").append(due ? "Zakat is due: " + taka(zakat) : "Zakat is not due")
                .append("|Net wealth (").append(taka(net)).append(due ? ") is at or above the Nisab (" : ") is below the Nisab (").append(taka(nisab)).append(").\n");
        r.append("H|Assets\n");
        r.append("R|Gold|").append(taka(gold)).append("\nR|Silver|").append(taka(silver)).append("\nR|Cash in hand|").append(taka(cashHand))
                .append("\nR|Cash in bank|").append(taka(cashBank)).append("\nR|Cash receivable|").append(taka(cashReceivable))
                .append("\nR|Business stock|").append(taka(business)).append("\nR|Investments|").append(taka(investments))
                .append("\nT|Total assets|").append(taka(assets)).append("\n");
        r.append("H|Liabilities\n");
        r.append("R|Cash payable|").append(taka(cashPayable)).append("\nR|Other short-term liabilities|").append(taka(other))
                .append("\nT|Total liabilities|").append(taka(liabilities)).append("\n");
        r.append("H|Zakat\n");
        r.append("R|Net wealth|").append(taka(net)).append("\nR|Nisab threshold|").append(taka(nisab)).append("\nR|Zakat rate|2.5%\nT|Zakat payable|").append(taka(zakat)).append("\n");
        r.append("WARN|Check the calculation with an expert. This may not be 100% correct.");
        return r.toString();
    }

    private boolean calculate(boolean save) {
        double business = number(b.businessInput), investments = number(b.investmentInput);
        double otherLiabilities = number(b.liabilityInput);
        nisab = number(b.nisabInput);
        if (nisab <= 0) {
            // Never guess a Nisab: a wrong threshold silently changes whether Zakat is due.
            breakdown = "";
            zakat = 0;
            b.nisabInput.setError("Enter the current Nisab threshold (greater than 0)");
            b.nisabInput.requestFocus();
            b.resultText.setText("Nisab threshold is required. Enter the current Nisab value in BDT (85 g gold or 595 g silver, per the method you follow) and calculate again.");
            return false;
        }
        b.nisabInput.setError(null);

        assets = gold + silver + cashHand + cashBank + cashReceivable + business + investments;
        liabilities = cashPayable + otherLiabilities;           // cash payable is deducted from total assets
        double net = Math.max(0, assets - liabilities);
        boolean isNisabMet = net >= nisab;
        zakat = isNisabMet ? net * 0.025 : 0;
        String nisabStatusMsg = isNisabMet
                ? "Net wealth meets the Nisab threshold."
                : "Net wealth is below the Nisab threshold (short by " + taka(nisab - net) + "), so no Zakat is estimated.";

        breakdown = "CRESCENT — ZAKAT ESTIMATE\nGenerated: " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) +
                "\n\nASSETS\nGold: " + taka(gold) + "\nSilver: " + taka(silver) +
                "\nCash in hand: " + taka(cashHand) + "\nCash in bank: " + taka(cashBank) + "\nCash receivable: " + taka(cashReceivable) +
                "\nBusiness stock: " + taka(business) + "\nInvestments: " + taka(investments) + "\nTotal assets: " + taka(assets) +
                "\n\nLIABILITIES\nCash payable: " + taka(cashPayable) + "\nOther short-term liabilities: " + taka(otherLiabilities) +
                "\nTotal liabilities: " + taka(liabilities) + "\n\nNet wealth: " + taka(net) + "\nNisab threshold: " + taka(nisab) +
                "\n\nMETHOD\n1. Add the entered asset values (including cash receivable).\n2. Subtract cash payable and other eligible liabilities.\n3. Compare net wealth with the Nisab threshold.\n4. If net wealth reaches Nisab, estimate 2.5% of net wealth; otherwise estimate ৳0." +
                "\n\nEstimated Zakat: " + taka(zakat);
        b.resultText.setText("Total assets: " + taka(assets) + "\nLiabilities (incl. cash payable): " + taka(liabilities) + "\nNet wealth: " + taka(net) + "\nNisab: " + taka(nisab) +
                "\n\nCalculation: (assets − liabilities) × 2.5%, if net wealth ≥ Nisab." +
                "\n\nEstimated Zakat: " + taka(zakat) +
                "\n\n" + nisabStatusMsg);
        if (save) {
            Calendar c = Calendar.getInstance();
            repo.saveSnapshot(new Snapshot(System.currentTimeMillis(), c.get(Calendar.YEAR), assets, liabilities, net, nisab, zakat, breakdown));
            repo.saveAsset("Cash", cashHand); repo.saveAsset("Bank savings", cashBank); repo.saveAsset("Cash receivable", cashReceivable);
            repo.saveAsset("Gold", gold); repo.saveAsset("Silver", silver);
            repo.saveAsset("Business stock", business); repo.saveAsset("Investments", investments);
        }
        return true;
    }
}