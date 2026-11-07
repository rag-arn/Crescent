package com.crescent.app;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Foreground service (type dataSync) that saves a PDF into the public Downloads folder while an
 * ongoing notification shows its progress. A finished/failed notification is left in the tray.
 *
 * Two sources are supported:
 *  - EXTRA_PATH: a local file (the generated Zakat report in the app cache); it is deleted after a successful save.
 *  - EXTRA_URL:  a remote PDF to download.
 * Use {@link #saveReport(Context, File)} to start it for a local report.
 */
public class PdfDownloadService extends Service {

    static final String EXTRA_URL = "pdfUrl";
    static final String EXTRA_PATH = "pdfPath";
    static final String EXTRA_NAME = "fileName";

    private static final String CHANNEL_PROGRESS = "crescent_downloads";
    private static final String CHANNEL_DONE = "crescent_downloads_done";
    private static final int NOTIFICATION_ID = 202;          // the ongoing foreground notification

    private NotificationManager notificationManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger active = new AtomicInteger();

    /** Starts the service to copy a generated report from the cache into Downloads. */
    static void saveReport(Context ctx, File pdf) {
        Intent i = new Intent(ctx, PdfDownloadService.class)
                .putExtra(EXTRA_PATH, pdf.getAbsolutePath())
                .putExtra(EXTRA_NAME, pdf.getName());
        ContextCompat.startForegroundService(ctx, i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String name = intent != null ? intent.getStringExtra(EXTRA_NAME) : null;
        final String path = intent != null ? intent.getStringExtra(EXTRA_PATH) : null;
        final String url = intent != null ? intent.getStringExtra(EXTRA_URL) : null;

        // A service started with startForegroundService() must call startForeground() promptly.
        Notification starting = progressBuilder(name != null ? name : "report")
                .setContentText("Starting…").setProgress(100, 0, true).build();
        ServiceCompat.startForeground(this, NOTIFICATION_ID, starting, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);

        if (name == null || (path == null && url == null)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        active.incrementAndGet();
        executor.execute(() -> run(name, path, url));
        return START_NOT_STICKY;
    }

    private void run(String name, String path, String url) {
        boolean ok = false;
        Uri savedUri = null;
        try {
            NotificationCompat.Builder nb = progressBuilder(name);
            if (path != null) {
                File src = new File(path);
                try (InputStream in = new FileInputStream(src)) {
                    savedUri = writeToDownloads(name, in, src.length(), nb);
                }
                //noinspection ResultOfMethodCallIgnored
                src.delete();                                   // the cached copy is no longer needed
            } else {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", "CrescentApp/1.0");
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                try {
                    if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                    try (InputStream in = c.getInputStream()) {
                        savedUri = writeToDownloads(name, in, c.getContentLength(), nb);
                    }
                } finally {
                    c.disconnect();
                }
            }
            ok = true;
        } catch (Exception ignored) {
            // reported through the notification below
        } finally {
            showDownloadComplete(name, ok, savedUri);
            if (active.decrementAndGet() == 0) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);   // remove the ongoing notification
                stopSelf();
            }
        }
    }

    /** Streams into Downloads (MediaStore on Android 10+), updating the progress notification. Returns the Uri, or null on old Android. */
    private Uri writeToDownloads(String fileName, InputStream in, long length, NotificationCompat.Builder nb) throws IOException {
        final ContentResolver cr = getContentResolver();
        Uri uri = null;
        OutputStream out;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);        // hidden from other apps until fully written
            uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Could not create download file");
            out = cr.openOutputStream(uri);
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && !dir.exists()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            out = new FileOutputStream(new File(dir, fileName));
        }
        if (out == null) throw new IOException("Could not open download file");

        boolean done = false;
        try (OutputStream o = out) {
            byte[] buf = new byte[4096];
            long total = 0;
            int n, lastProgress = -1;
            while ((n = in.read(buf)) != -1) {
                o.write(buf, 0, n);
                total += n;
                if (length > 0) {
                    int p = (int) Math.min(100, total * 100 / length);
                    if (p != lastProgress) {
                        lastProgress = p;
                        notificationManager.notify(NOTIFICATION_ID, nb.setProgress(100, p, false)
                                .setContentText("Saving to Downloads… " + p + "%").build());
                    }
                }
            }
            o.flush();
            done = true;
        } finally {
            if (!done && uri != null) cr.delete(uri, null, null);   // don't leave a half-written file behind
        }
        if (uri != null) {
            ContentValues finished = new ContentValues();
            finished.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, finished, null, null);
        }
        return uri;
    }

    private NotificationCompat.Builder progressBuilder(String fileName) {
        return new NotificationCompat.Builder(this, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_crescent_small)
                .setColor(ContextCompat.getColor(this, R.color.sage_btn))
                .setContentTitle("Saving " + fileName)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);   // Android 12+ would delay it ~10 s otherwise
    }

    private void showDownloadComplete(String fileName, boolean ok, @Nullable Uri uri) {
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_crescent_small)
                .setColor(ContextCompat.getColor(this, R.color.sage_btn))
                .setContentTitle(fileName)
                .setContentText(ok ? (uri != null ? "Saved to Downloads. Tap to open." : "Saved to Downloads.")
                        : "Could not save the report. Please generate it again.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (ok) {
            Intent open;
            if (uri != null) {
                open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                open = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            nb.setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        // One tray entry per file; stays until tapped or swiped.
        notificationManager.notify(2000 + Math.abs(fileName.hashCode() % 1000), nb.build());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel progress = new NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW);
            progress.setDescription("Shows progress while a report is being saved");
            NotificationChannel done = new NotificationChannel(CHANNEL_DONE, "Downloads", NotificationManager.IMPORTANCE_DEFAULT);
            done.setDescription("Tells you when a report has been saved");
            notificationManager.createNotificationChannel(progress);
            notificationManager.createNotificationChannel(done);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}