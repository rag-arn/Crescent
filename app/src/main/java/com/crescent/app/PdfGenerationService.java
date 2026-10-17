package com.crescent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfGenerationService extends Service {
    private static final String CHANNEL = "crescent_reports";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Crescent reports", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(c);
            }
            Notification n = new NotificationCompat.Builder(this, CHANNEL)
                    .setContentTitle("Crescent")
                    .setContentText("Generating report…")
                    .setSmallIcon(android.R.drawable.ic_menu_save)
                    .build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(9, n);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String report = intent == null ? "No report data available." : intent.getStringExtra("report");
        if (report == null) report = "No report data available.";
        final String reportText = report;

        new Thread(() -> {
            String status = "PDF generation failed.";
            ReportWriter w = new ReportWriter();
            PdfDocument doc = w.doc;
            try {
                w.render(reportText);

                String fileName = "Crescent_Zakat_Report_" + System.currentTimeMillis() + ".pdf";
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Could not create download file");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        doc.writeTo(out);
                    }
                } else {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (downloadsDir != null && !downloadsDir.exists()) {
                        downloadsDir.mkdirs();
                    }
                    File file = new File(downloadsDir, fileName);
                    try (OutputStream out = new FileOutputStream(file)) {
                        doc.writeTo(out);
                    }
                }
                status = "Saved PDF to Downloads.";
            } catch (Exception e) {
                status = "Could not save PDF: " + e.getMessage();
            } finally {
                doc.close();
            }

            final String msg = status;
            android.os.Handler h = new android.os.Handler(getMainLooper());
            h.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());

            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf(startId);
        }).start();

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Draws the structured report (see ResultActivity) as a clean, tabular PDF. */
    private static class ReportWriter {
        // A4 portrait = 595 x 842 points. Compact sizes so the whole report fits on one page.
        static final int W = 595, H = 842, M = 40, TOP = 34, BOTTOM = 800, ROW = 19;
        static final int GREEN = 0xFF176B52, DARK = 0xFF222222, GRAY = 0xFF666666, RED = 0xFFB71C1C;
        final PdfDocument doc = new PdfDocument();
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint shape = new Paint();
        PdfDocument.Page page;
        Canvas c;
        int pageNo = 0;
        float y;

        ReportWriter() { newPage(); }

        void newPage() {
            if (page != null) finishPage();
            pageNo++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
            c = page.getCanvas();
            y = TOP;
        }

        void finishPage() {
            style(9, GRAY, false);
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("Crescent  |  Page " + pageNo, W / 2f, H - 22, text);
            text.setTextAlign(Paint.Align.LEFT);
            doc.finishPage(page);
        }

        void finish() { finishPage(); }

        void style(float size, int color, boolean bold) {
            text.setTextSize(size);
            text.setColor(color);
            text.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            text.setTextAlign(Paint.Align.LEFT);
        }

        void ensure(float h) { if (y + h > BOTTOM) newPage(); }

        String fit(String s, float maxWidth) {
            if (text.measureText(s) <= maxWidth) return s;
            int n = text.breakText(s, true, maxWidth - text.measureText("…"), null);
            return s.substring(0, Math.max(0, n)) + "…";
        }

        List<String> wrap(String s, float width) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String word : s.split(" ")) {
                String t = cur.length() == 0 ? word : cur + " " + word;
                if (cur.length() == 0 || text.measureText(t) <= width) { cur.setLength(0); cur.append(t); }
                else { out.add(cur.toString()); cur.setLength(0); cur.append(word); }
            }
            if (cur.length() > 0) out.add(cur.toString());
            return out;
        }

        void title(String t) {
            style(22, GREEN, true);
            c.drawText("CRESCENT", M, y + 22, text);
            style(14, DARK, false);
            c.drawText(t, M, y + 41, text);
            y += 50;
        }

        void dateLine(String t) {
            style(10, GRAY, false);
            c.drawText(t, M, y + 10, text);
            y += 15;
            shape.setColor(GREEN);
            c.drawRect(M, y, W - M, y + 2, shape);
            y += 12;
        }

        void verdict(boolean due, String headline, String detail) {
            float inner = W - 2 * M - 28;
            style(11, DARK, false);
            List<String> d = wrap(detail, inner);
            float h = 14 + 20 + d.size() * 13 + 6;
            ensure(h + 12);
            shape.setColor(due ? 0xFFE3F2EA : 0xFFEEEEEE);
            c.drawRect(M, y, W - M, y + h, shape);
            shape.setColor(due ? GREEN : 0xFF888888);
            c.drawRect(M, y, M + 5, y + h, shape);
            style(15, DARK, true);
            c.drawText(fit(headline, inner), M + 18, y + 24, text);
            style(11, DARK, false);
            float ty = y + 41;
            for (String line : d) { c.drawText(line, M + 18, ty, text); ty += 13; }
            y += h + 12;
        }

        void table(String title, List<String[]> rows) {
            ensure(ROW * (rows.size() + 1) + 12);
            float left = M, right = W - M, top = y;
            shape.setStyle(Paint.Style.FILL);
            shape.setColor(GREEN);
            c.drawRect(left, y, right, y + ROW, shape);
            style(10.5f, 0xFFFFFFFF, true);
            c.drawText(title, left + 8, y + 13, text);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText("Value", right - 8, y + 13, text);
            y += ROW;
            boolean alt = false;
            for (String[] r : rows) {
                boolean total = r[0].equals("T");
                shape.setColor(total ? 0xFFD9EBE3 : (alt ? 0xFFF2F6F4 : 0xFFFFFFFF));
                c.drawRect(left, y, right, y + ROW, shape);
                shape.setColor(0xFFDDDDDD);
                c.drawRect(left, y + ROW - 0.5f, right, y + ROW, shape);
                style(10.5f, DARK, total);
                String value = r[2];
                float valueW = text.measureText(value);
                c.drawText(fit(r[1], right - left - 24 - valueW), left + 8, y + 13, text);
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText(value, right - 8, y + 13, text);
                text.setTextAlign(Paint.Align.LEFT);
                y += ROW;
                alt = !alt;
            }
            shape.setStyle(Paint.Style.STROKE);
            shape.setStrokeWidth(0.8f);
            shape.setColor(0xFFB8CFC6);
            c.drawRect(left, top, right, y, shape);
            shape.setStyle(Paint.Style.FILL);
            y += 12;
        }

        void paragraph(String s, float size, int color, boolean bold) {
            style(size, color, bold);
            List<String> wrapped = wrap(s, W - 2 * M);
            for (String line : wrapped) {
                ensure(size + 6);
                style(size, color, bold);   // a page break draws the footer, which changes the paint; restore it
                c.drawText(line, M, y + size, text);
                y += size + 4;
            }
            y += 6;
        }

        private static String get(String[] f, int i) { return i < f.length ? f[i] : ""; }

        void render(String report) {
            String sectionTitle = null;
            List<String[]> rows = new ArrayList<>();
            for (String line : report.split("\\n")) {
                if (line.trim().isEmpty()) continue;
                String[] f = line.split("\\|", 4);
                String k = f[0];
                boolean rowLine = k.equals("R") || k.equals("T");
                if (!rowLine && sectionTitle != null) { table(sectionTitle, rows); sectionTitle = null; rows = new ArrayList<>(); }
                switch (k) {
                    case "TITLE": title(get(f, 1)); break;
                    case "DATE": dateLine(get(f, 1)); break;
                    case "VERDICT": verdict(get(f, 1).equals("1"), get(f, 2), get(f, 3)); break;
                    case "H": sectionTitle = get(f, 1); rows = new ArrayList<>(); break;
                    case "R": case "T": rows.add(new String[]{k, get(f, 1), get(f, 2)}); break;
                    case "NOTE": paragraph(get(f, 1), 10, GRAY, false); break;
                    case "WARN": paragraph(get(f, 1), 12, RED, true); break;   // last sentence: bold red
                    default: break;
                }
            }
            if (sectionTitle != null) table(sectionTitle, rows);
            finish();
        }
    }
}