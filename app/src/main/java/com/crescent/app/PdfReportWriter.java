package com.crescent.app;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Draws the structured report (see ResultActivity) as a tabular PDF, plus cache / Downloads file helpers. */
final class PdfReportWriter {

    /** Renders the report into the app cache (nothing is written to Downloads yet) and returns the file for previewing. */
    static File generateToCache(Context ctx, String report) throws Exception {
        File dir = new File(ctx.getCacheDir(), "reports");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create cache folder");
        File file = new File(dir, "Crescent_Zakat_Report_" + System.currentTimeMillis() + ".pdf");
        String[] who = identity();
        PdfReportWriter w = new PdfReportWriter(who[0], who[1], loadLogo(ctx));
        try {
            w.render(report);
            try (OutputStream out = new FileOutputStream(file)) { w.doc.writeTo(out); }
        } catch (Exception e) {
            file.delete();
            throw e;
        } finally {
            w.doc.close();
            if (w.logo != null) w.logo.recycle();
        }
        return file;
    }

    /** Name and e-mail printed under the logo. Guests (anonymous sessions) are shown as "Anonymous". */
    static String[] identity() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || u.isAnonymous()) return new String[]{"Anonymous", "Anonymous"};
        String email = u.getEmail() != null ? u.getEmail() : "";
        String name = u.getDisplayName();
        if (name == null || name.isEmpty()) name = email.contains("@") ? email.split("@")[0] : "User";
        return new String[]{name, email.isEmpty() ? "-" : email};
    }

    /** Decodes res/drawable/logo.png at a reduced size (the source image is large). */
    static Bitmap loadLogo(Context ctx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(ctx.getResources(), R.drawable.logo, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 300 && o.outHeight / (sample * 2) >= 300) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeResource(ctx.getResources(), R.drawable.logo, d);
        } catch (Exception | OutOfMemoryError e) {
            return null;   // the report is still generated, just without the logo
        }
    }

    /** Copies a previewed PDF into the public Downloads folder. */
    static void saveToDownloads(Context ctx, File source) throws Exception {
        String fileName = source.getName();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create download file");
            try (InputStream in = new java.io.FileInputStream(source); OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Could not open download file");
                copy(in, out);
            }
        } else {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null && !downloadsDir.exists()) downloadsDir.mkdirs();
            try (InputStream in = new java.io.FileInputStream(source); OutputStream out = new FileOutputStream(new File(downloadsDir, fileName))) {
                copy(in, out);
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    // A4 portrait = 595 x 842 points. Compact sizes so the whole report fits on one page.
    static final int W = 595, H = 842, M = 40, TOP = 30, BOTTOM = 800, ROW = 17;
    // App palette (res/values/colors.xml): sage_button, sage_text, sage_text_soft, sage_card, sage_card_stroke, sage_top, sage_btn
    static final int GREEN = 0xFF8DAA94,      // sage_button: logo ring, table headers
            DARK = 0xFF2F4A3C,                // sage_text
            GRAY = 0xFF5F7566,                // sage_text_soft
            CREAM = 0xFFF6F1E2,               // sage_card
            STROKE = 0xFFE4DCC3,              // sage_card_stroke
            SAGE_LIGHT = 0xFFBCD1BE,          // sage_top
            SAGE_DEEP = 0xFF5A8B70,           // sage_btn
            SAGE_TINT = 0xFFEBF1EB,           // sage_top at 30% on white (totals)
            RED = 0xFFB71C1C;
    final PdfDocument doc = new PdfDocument();
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint shape = new Paint();
    PdfDocument.Page page;
    Canvas c;
    int pageNo = 0;
    float y;
    final String userName, userEmail;
    final Bitmap logo;

    PdfReportWriter(String userName, String userEmail, Bitmap logo) {
        this.userName = userName;
        this.userEmail = userEmail;
        this.logo = logo;
        newPage();
    }

    void newPage() {
        if (page != null) finishPage();
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        c = page.getCanvas();
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(0xFFFFFFFF);                       // pure white page
        c.drawRect(0, 0, W, H, shape);
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

    /** Top of page 1: centred logo, then the CRESCENT name. */
    void header() {
        final float r = 25, cx = W / 2f, cy = y + r + 2;
        if (logo != null) {
            Path clip = new Path();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            int side = Math.min(logo.getWidth(), logo.getHeight());
            Rect src = new Rect((logo.getWidth() - side) / 2, (logo.getHeight() - side) / 2,
                    (logo.getWidth() + side) / 2, (logo.getHeight() + side) / 2);   // centre-crop to a square
            c.save();
            c.clipPath(clip);
            c.drawBitmap(logo, src, new RectF(cx - r, cy - r, cx + r, cy + r), bmpPaint);
            c.restore();
            shape.setStyle(Paint.Style.STROKE);
            shape.setStrokeWidth(1.5f);
            shape.setColor(GREEN);
            c.drawCircle(cx, cy, r + 1.5f, shape);
            shape.setStyle(Paint.Style.FILL);
        }
        y += 2 * r + 12;
        style(24, DARK, true);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        text.setLetterSpacing(0.08f);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("CRESCENT", cx, y + 20, text);
        text.setLetterSpacing(0f);
        text.setTextAlign(Paint.Align.LEFT);
        y += 32;
    }

    /** Name and e-mail of the person the report belongs to. */
    void identityBlock() {
        final float left = M, right = W - M, h = ROW * 2 + 8, valueX = left + 52;
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(CREAM);
        c.drawRect(left, y, right, y + h, shape);
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeWidth(0.8f);
        shape.setColor(STROKE);
        c.drawRect(left, y, right, y + h, shape);
        shape.setStyle(Paint.Style.FILL);

        String[][] rows = {{"Name", userName}, {"Email", userEmail}};
        float ry = y + 4;
        for (String[] row : rows) {
            style(10.5f, DARK, true);
            c.drawText(row[0], left + 10, ry + 12, text);
            style(10.5f, DARK, false);
            c.drawText(fit(row[1], right - 10 - valueX), valueX, ry + 12, text);
            ry += ROW;
        }
        y += h + 12;
    }

    void title(String t) {
        style(15, DARK, true);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(t, W / 2f, y + 15, text);
        text.setTextAlign(Paint.Align.LEFT);
        y += 22;
    }

    void dateLine(String t) {
        style(10, GRAY, false);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(t, W / 2f, y + 10, text);
        text.setTextAlign(Paint.Align.LEFT);
        y += 15;
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(SAGE_LIGHT);
        c.drawRect(M, y, W - M, y + 2, shape);
        y += 12;
    }

    void verdict(boolean due, String headline, String detail) {
        float inner = W - 2 * M - 28;
        style(11, DARK, false);
        List<String> d = wrap(detail, inner);
        float h = 14 + 20 + d.size() * 13 + 6;
        ensure(h + 12);
        shape.setColor(due ? 0xFFDDE8DE : CREAM);
        c.drawRect(M, y, W - M, y + h, shape);
        shape.setColor(due ? SAGE_DEEP : STROKE);
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
            shape.setColor(total ? SAGE_TINT : (alt ? CREAM : 0xFFFFFFFF));
            c.drawRect(left, y, right, y + ROW, shape);
            shape.setColor(STROKE);
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
        shape.setColor(STROKE);
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
        header();
        identityBlock();
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