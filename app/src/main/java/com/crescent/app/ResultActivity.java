package com.crescent.app;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityResultBinding;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ResultActivity extends AppCompatActivity {
    public static final String DISCLAIMER = "Check the calculation with an expert. This may not be 100% correct.";
    private static final String NOTE = "This estimate is 2.5% of net wealth and assumes your wealth has stayed at or above the Nisab for one full lunar year (hawl) and that all entered assets are zakatable. Rules vary by school of thought and circumstances.";

    private ActivityResultBinding b;
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));
    private final List<String[]> lines = new ArrayList<>();
    private long calculatedAt = System.currentTimeMillis();   // history mode overrides this with the saved time

    // The contact picker returns the chosen contact's name and phone number.
    private final ActivityResultLauncher<Intent> contactPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                Intent d = r.getData();
                if (r.getResultCode() == RESULT_OK && d != null) {
                    showShareConfirm(d.getStringExtra(ContactPickerActivity.EXTRA_NAME), d.getStringExtra(ContactPickerActivity.EXTRA_NUMBER));
                }
            });

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
        Ui.insets(this, b.getRoot(), b.headerBar, b.bottomBar);

        b.infoButton.setOnClickListener(v -> InfoDialog.show(this, "About this estimate", NOTE));
        b.backButton.setOnClickListener(v -> finish());
        b.shareContactButton.setOnClickListener(v -> contactPicker.launch(new Intent(this, ContactPickerActivity.class)));
        b.previousButton.setOnClickListener(v -> finish());
        b.homeButton.setOnClickListener(v -> {
            Intent h = new Intent(this, MainActivity.class);
            h.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(h);
            finish();
        });

        Intent in = getIntent();
        if (in.getBooleanExtra("from_history", false)) {
            renderFromHistory(in);
            return;
        }

        double gold = in.getDoubleExtra("gold_value_bdt", 0), goldGrams = in.getDoubleExtra("gold_pure_grams", 0);
        double silver = in.getDoubleExtra("silver_value_bdt", 0), silverGrams = in.getDoubleExtra("silver_pure_grams", 0);
        double cashHand = in.getDoubleExtra("cash_hand", 0), cashBank = in.getDoubleExtra("cash_bank", 0);
        double cashPayable = in.getDoubleExtra("cash_payable", 0), cashReceivable = in.getDoubleExtra("cash_receivable", 0);
        double commercial = in.getDoubleExtra("commercial_goods", 0);
        String basis = in.getStringExtra("nisab_basis");
        double nisab = in.getDoubleExtra("nisab_amount", 0);
        double nisabGrams = in.getDoubleExtra("nisab_grams", 0), nisabPrice = in.getDoubleExtra("nisab_price_per_gram", 0);

        if (basis == null || !(nisab > 0)) {
            Toast.makeText(this, "Nisab was not calculated. Please go back and choose gold or silver.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        double assets = gold + silver + cashHand + cashBank + cashReceivable + commercial;
        double net = Math.max(0, assets - cashPayable);
        boolean due = net >= nisab;
        double zakat = due ? net * 0.025 : 0;
        String basisName = basis.equals("gold") ? "Gold" : "Silver";

        section("Assets");
        row(goldGrams > 0 ? String.format(Locale.US, "Gold (%.3f g pure)", goldGrams) : "Gold", gold);
        row(silverGrams > 0 ? String.format(Locale.US, "Silver (%.3f g pure)", silverGrams) : "Silver", silver);
        row("Cash in hand", cashHand);
        row("Cash in bank", cashBank);
        row("Cash receivable", cashReceivable);
        row("Commercial goods", commercial);
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

        String headline = due ? "Zakat is due: " + taka(zakat) : "Zakat is not due";
        String detail = due
                ? "Your net wealth (" + taka(net) + ") is at or above the Nisab (" + taka(nisab) + ")."
                : "Your net wealth (" + taka(net) + ") is below the Nisab (" + taka(nisab) + ").";
        b.verdictText.setText(headline + "\n\n" + detail);
        b.verdictCard.setCardBackgroundColor(due ? 0x80BCD1BE : 0x80E4DCC3);
        renderTable();

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
            r.append("WARN|").append(DISCLAIMER);
            generatePdfAndPreview(r.toString());
        });

        if (state == null) {
            HawlTracker.onCalculation(getApplicationContext(), due);
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
            repo.saveAsset("Gold", gold); repo.saveAsset("Silver", silver); repo.saveAsset("Commercial goods", commercial);
        }
    }

    // ---------------- Share with a contact ----------------

    /** Shows exactly what will be sent, lets the person choose how much to include, then opens their messages app. */
    private void showShareConfirm(String name, String number) {
        if (isFinishing() || isDestroyed()) return;
        if (number == null || number.trim().isEmpty()) {
            Toast.makeText(this, "That contact has no phone number.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String who = (name == null || name.trim().isEmpty()) ? number : name;

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_share_confirm);
        Window w = dialog.getWindow();
        if (w != null) {
            int margin = Math.round(24 * getResources().getDisplayMetrics().density);
            w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margin));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ((TextView) dialog.findViewById(R.id.shareTitle)).setText("Share with " + who);
        ((TextView) dialog.findViewById(R.id.shareNumber)).setText(number);
        final TextView preview = dialog.findViewById(R.id.sharePreview);
        final CheckBox full = dialog.findViewById(R.id.includeBreakdownCheckbox);   // off by default: share the summary only
        preview.setText(buildShareMessage(false));
        full.setOnCheckedChangeListener((v, checked) -> preview.setText(buildShareMessage(checked)));

        dialog.findViewById(R.id.shareCancelBtn).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.shareConfirmBtn).setOnClickListener(v -> {
            dialog.dismiss();
            openMessagesApp(number, buildShareMessage(full.isChecked()));
        });
        dialog.show();
    }

    /** Plain-text (ASCII) message so it stays a normal SMS: the taka sign and fancy dashes are replaced. */
    private String buildShareMessage(boolean fullBreakdown) {
        StringBuilder m = new StringBuilder("Zakat estimate from the Crescent app\n");
        m.append("Calculated: ").append(new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date(calculatedAt))).append("\n\n");
        m.append(plain(b.verdictText.getText().toString().replace("\n\n", "\n")));
        if (fullBreakdown) {
            for (String[] l : lines) {
                if (l[0].equals("H")) m.append("\n\n").append(plain(l[1]).toUpperCase(Locale.US));
                else m.append("\n").append(plain(l[1])).append(": ").append(plain(l[3]));
            }
        }
        m.append("\n\n").append(DISCLAIMER);
        return m.toString();
    }

    private static String plain(String s) {
        return s.replace("৳", "BDT ").replace("−", "-").replace("—", "-").replace("–", "-")
                .replace("’", "'").replace("“", "\"").replace("”", "\"");
    }

    /** Opens the default messaging app with the number and text filled in. The person reviews and taps send; no SMS permission is needed. */
    private void openMessagesApp(String number, String body) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)));
        i.putExtra("sms_body", body);
        i.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No messaging app found on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private void renderFromHistory(Intent in) {
        long createdAt = in.getLongExtra("snapshot_created_at", System.currentTimeMillis());
        calculatedAt = createdAt;
        double assets = in.getDoubleExtra("snapshot_assets", 0);
        double liabilities = in.getDoubleExtra("snapshot_liabilities", 0);
        double net = in.getDoubleExtra("snapshot_net_wealth", 0);
        double nisab = in.getDoubleExtra("snapshot_nisab", 0);
        double zakat = in.getDoubleExtra("snapshot_zakat", 0);
        String breakdown = in.getStringExtra("snapshot_breakdown");

        boolean due = net >= nisab && nisab > 0;
        parseBreakdownLines(breakdown, assets, liabilities, net, nisab, zakat, due);

        String headline = due ? "Zakat is due: " + taka(zakat) : "Zakat is not due";
        String detail = due
                ? "Your net wealth (" + taka(net) + ") is at or above the Nisab (" + taka(nisab) + ")."
                : "Your net wealth (" + taka(net) + ") is below the Nisab (" + taka(nisab) + ").";
        b.verdictText.setText(headline + "\n\n" + detail);
        b.verdictCard.setCardBackgroundColor(due ? 0x80BCD1BE : 0x80E4DCC3);
        renderTable();

        final String pdfHeadline = due ? "Zakat is due: " + bdt(zakat) : "Zakat is not due";
        final String pdfDetail = due
                ? "Net wealth (" + bdt(net) + ") is at or above the Nisab (" + bdt(nisab) + ")."
                : "Net wealth (" + bdt(net) + ") is below the Nisab (" + bdt(nisab) + ").";
        final boolean dueFinal = due;
        final String dateStr = DateFormat.getDateTimeInstance().format(new Date(createdAt));
        b.pdfButton.setOnClickListener(v -> {
            StringBuilder r = new StringBuilder();
            r.append("TITLE|Zakat Calculation Report\n");
            r.append("DATE|Calculated: ").append(dateStr).append("\n");
            r.append("VERDICT|").append(dueFinal ? "1" : "0").append("|").append(pdfHeadline).append("|").append(pdfDetail).append("\n");
            for (String[] l : lines) r.append(l[0]).append("|").append(l[1]).append("|").append(l[3]).append("\n");
            r.append("NOTE|").append(NOTE).append("\n");
            r.append("WARN|").append(DISCLAIMER);
            generatePdfAndPreview(r.toString());
        });
    }

    private void parseBreakdownLines(String breakdown, double assets, double liabilities, double net, double nisab, double zakat, boolean due) {
        if (breakdown != null && !breakdown.trim().isEmpty()) {
            for (String raw : breakdown.split("\\n")) {
                String s = raw.trim();
                if (s.isEmpty()
                        || s.startsWith("CRESCENT")
                        || s.startsWith("Generated:")
                        || s.startsWith("Zakat is ")
                        || s.equals(DISCLAIMER)
                        || s.startsWith("1.") || s.startsWith("2.") || s.startsWith("3.") || s.startsWith("4.")) {
                    continue;
                }
                if (s.equals("METHOD")) break;

                if (!s.contains(":")) {
                    section(formatSectionHeader(s));
                } else {
                    int idx = s.indexOf(':');
                    String label = s.substring(0, idx).trim();
                    String val = s.substring(idx + 1).trim();
                    String screenVal = val.startsWith("BDT ") ? "৳" + val.substring(4) : val;
                    String pdfVal = val.startsWith("৳") ? "BDT " + val.substring(1) : val;
                    boolean isTotal = label.equalsIgnoreCase("Total assets")
                            || label.equalsIgnoreCase("Total liabilities")
                            || label.startsWith("Net wealth (")
                            || label.equalsIgnoreCase("Nisab threshold")
                            || label.equalsIgnoreCase("Zakat payable")
                            || label.equalsIgnoreCase("Estimated Zakat");
                    lines.add(new String[]{isTotal ? "T" : "R", label, screenVal, pdfVal});
                }
            }
        }
        if (lines.isEmpty()) {
            section("Summary");
            total("Total assets", assets);
            row("Liabilities", liabilities);
            total("Net wealth", net);
            row("Nisab threshold", nisab);
            rowText("R", "Net wealth vs Nisab", due ? "At or above Nisab" : "Below Nisab");
            rowText("R", "Zakat rate", "2.5%");
            total("Zakat payable", zakat);
        }
    }

    private static String formatSectionHeader(String upper) {
        if (upper.equalsIgnoreCase("ASSETS")) return "Assets";
        if (upper.equalsIgnoreCase("DEDUCTIONS")) return "Deductions";
        if (upper.equalsIgnoreCase("LIABILITIES")) return "Liabilities";
        if (upper.equalsIgnoreCase("ZAKAT")) return "Zakat";
        if (upper.toUpperCase(Locale.US).startsWith("NISAB")) {
            return "Nisab" + upper.substring(5).toLowerCase(Locale.US);
        }
        return upper.substring(0, 1).toUpperCase(Locale.US) + upper.substring(1).toLowerCase(Locale.US);
    }

    private void generatePdfAndPreview(String report) {
        b.pdfButton.setEnabled(false);
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            java.io.File file = null;
            String error = null;
            try {
                file = PdfReportWriter.generateToCache(app, report);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final java.io.File out = file;
            final String err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) { if (out != null) out.delete(); return; }
                b.pdfButton.setEnabled(true);
                if (out == null) {
                    Toast.makeText(this, "Could not generate PDF" + (err == null ? "." : ": " + err), Toast.LENGTH_LONG).show();
                    return;
                }
                Intent i = new Intent(this, PdfPreviewActivity.class);
                i.putExtra(PdfPreviewActivity.EXTRA_PDF_PATH, out.getAbsolutePath());
                startActivity(i);
            });
        }).start();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView cell(String text, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(0xFF2F4A3C); // sage_text
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
                h.setBackgroundColor(0xFF8DAA94); // sage_button
                TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
                lp.span = 2;
                lp.topMargin = dp(12);
                tr.addView(h, lp);
            } else {
                boolean bold = l[0].equals("T");
                TextView a = cell(l[1], bold), v = cell(l[2], bold);
                v.setGravity(Gravity.END);
                if (bold) { a.setBackgroundColor(0x4DBCD1BE); v.setBackgroundColor(0x4DBCD1BE); }
                tr.addView(a);
                tr.addView(v);
            }
            b.resultTable.addView(tr);
        }
    }
}