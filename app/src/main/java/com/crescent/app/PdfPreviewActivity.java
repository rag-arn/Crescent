package com.crescent.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityPdfPreviewBinding;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows the generated PDF before anything is saved. Download hands it to PdfDownloadService (a foreground
 * service that saves it to Downloads with a progress notification); Discard deletes it and returns.
 */
public class PdfPreviewActivity extends AppCompatActivity {
    public static final String EXTRA_PDF_PATH = "pdf_path";

    private ActivityPdfPreviewBinding b;
    private File pdf;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Bitmap> bitmaps = new ArrayList<>();
    private boolean handedOff;   // the foreground service now owns the temp file and deletes it after saving

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityPdfPreviewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.insets(this, b.getRoot(), b.headerBar, b.bottomBar);

        String path = getIntent().getStringExtra(EXTRA_PDF_PATH);
        pdf = path == null ? null : new File(path);
        if (pdf == null || !pdf.exists()) {
            Toast.makeText(this, "The report is no longer available. Please generate it again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        b.backButton.setOnClickListener(v -> finish());
        b.discardButton.setOnClickListener(v -> finish());          // onDestroy removes the temp file
        b.downloadButton.setOnClickListener(v -> download());
        renderPages();
    }

    private void renderPages() {
        // Page width = screen width minus the container padding (12dp each side).
        final int targetW = getResources().getDisplayMetrics().widthPixels - 2 * Math.round(12 * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            List<Bitmap> rendered = new ArrayList<>();
            String error = null;
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(fd)) {
                for (int i = 0; i < renderer.getPageCount(); i++) {
                    try (PdfRenderer.Page p = renderer.openPage(i)) {
                        int h = Math.round(targetW * (p.getHeight() / (float) p.getWidth()));
                        Bitmap bmp = Bitmap.createBitmap(targetW, h, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);                // PdfRenderer draws on transparent
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        rendered.add(bmp);
                    }
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) { for (Bitmap bm : rendered) bm.recycle(); return; }
                b.progress.setVisibility(View.GONE);
                if (err != null || rendered.isEmpty()) {
                    Toast.makeText(this, "Could not preview the PDF" + (err == null ? "." : ": " + err), Toast.LENGTH_LONG).show();
                    b.downloadButton.setEnabled(false);
                    return;
                }
                int gap = Math.round(12 * getResources().getDisplayMetrics().density);
                for (Bitmap bm : rendered) {
                    bitmaps.add(bm);
                    ImageView iv = new ImageView(this);
                    iv.setImageBitmap(bm);
                    iv.setAdjustViewBounds(true);
                    iv.setContentDescription("Report page " + bitmaps.size());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    if (bitmaps.size() > 1) lp.topMargin = gap;
                    b.pagesContainer.addView(iv, lp);
                }
            });
        });
    }

    private void download() {
        if (handedOff) return;
        handedOff = true;
        b.downloadButton.setEnabled(false);
        b.discardButton.setEnabled(false);
        try {
            PdfDownloadService.saveReport(getApplicationContext(), pdf);
        } catch (RuntimeException e) {                              // e.g. the system refused to start the service
            handedOff = false;
            b.downloadButton.setEnabled(true);
            b.discardButton.setEnabled(true);
            Toast.makeText(this, "Could not start saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Saving report to Downloads. Check the notification for progress.", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && !handedOff) {                          // discarded: the temp file is no longer needed
            final File f = pdf;
            io.execute(() -> { if (f != null) f.delete(); });
        }
        io.shutdown();
        if (isFinishing()) for (Bitmap bm : bitmaps) bm.recycle();
    }
}