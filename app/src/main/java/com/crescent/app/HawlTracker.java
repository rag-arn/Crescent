package com.crescent.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

final class HawlTracker {
    private HawlTracker() {}

    static final String ACTION_DUE = "com.crescent.app.HAWL_DUE";
    private static final String PREFS = "crescent_hawl";
    private static final String KEY_START = "start_epoch_day";
    private static final String KEY_NOTIFIED = "due_notified";
    private static final String CHANNEL_ID = "crescent_alerts";
    private static final int NOTIF_ID = 7001;
    private static final long FALLBACK_LUNAR_YEAR_DAYS = 354;

    static final class State {
        boolean active, complete;
        long startDay, dueDay, totalDays, elapsedDays, remainingDays;
        float progress;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String getUid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return (u != null && !u.isAnonymous()) ? u.getUid() : null;
    }

    static void syncFromCloud(Context ctx, Runnable onComplete) {
        String uid = getUid();
        if (uid == null) { if (onComplete != null) onComplete.run(); return; }

        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                SharedPreferences p = prefs(ctx);
                Boolean active = doc.getBoolean("hawlActive");
                Long startDay = doc.getLong("hawlStartDay");

                if (Boolean.TRUE.equals(active) && startDay != null) {
                    if (p.getLong(KEY_START, 0) != startDay) {
                        p.edit().putLong(KEY_START, startDay).putBoolean(KEY_NOTIFIED, false).apply();
                    }
                } else {
                    p.edit().clear().apply();
                    cancelAlarm(ctx);
                }
            }
            if (onComplete != null) onComplete.run();
        }).addOnFailureListener(e -> {
            if (onComplete != null) onComplete.run();
        });
    }

    static void onCalculation(Context ctx, boolean eligible) {
        SharedPreferences p = prefs(ctx);
        String uid = getUid();

        if (eligible) {
            if (!p.contains(KEY_START)) {
                long today = LocalDate.now().toEpochDay();
                p.edit().putLong(KEY_START, today).putBoolean(KEY_NOTIFIED, false).apply();
                if (uid != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("hawlStartDay", today);
                    data.put("hawlActive", true);
                    FirebaseFirestore.getInstance().collection("users").document(uid).set(data, SetOptions.merge());
                }
            }
        } else {
            p.edit().clear().apply();
            cancelAlarm(ctx);
            if (uid != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("hawlStartDay", null);
                data.put("hawlActive", false);
                FirebaseFirestore.getInstance().collection("users").document(uid).set(data, SetOptions.merge());
            }
        }
        schedule(ctx);
    }

    static void startNewCycle(Context ctx) {
        long today = LocalDate.now().toEpochDay();
        prefs(ctx).edit().putLong(KEY_START, today).putBoolean(KEY_NOTIFIED, false).apply();
        String uid = getUid();
        if (uid != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("hawlStartDay", today);
            data.put("hawlActive", true);
            FirebaseFirestore.getInstance().collection("users").document(uid).set(data, SetOptions.merge());
        }
        schedule(ctx);
    }

    static long dueDay(long startDay) {
        try {
            HijrahDate start = HijrahDate.from(LocalDate.ofEpochDay(startDay));
            long due = start.plus(1, ChronoUnit.YEARS).toEpochDay();
            long span = due - startDay;
            if (span >= 350 && span <= 360) return due;
        } catch (Exception ignored) {}
        return startDay + FALLBACK_LUNAR_YEAR_DAYS;
    }

    static State state(Context ctx) {
        State s = new State();
        SharedPreferences p = prefs(ctx);
        if (!p.contains(KEY_START)) return s;
        s.active = true;
        s.startDay = p.getLong(KEY_START, LocalDate.now().toEpochDay());
        s.dueDay = dueDay(s.startDay);
        s.totalDays = Math.max(1, s.dueDay - s.startDay);
        long today = LocalDate.now().toEpochDay();
        s.elapsedDays = Math.max(0, today - s.startDay);
        s.remainingDays = Math.max(0, s.dueDay - today);
        s.complete = today >= s.dueDay;
        s.progress = s.complete ? 1f : Math.min(1f, s.elapsedDays / (float) s.totalDays);
        return s;
    }

    private static PendingIntent alarmIntent(Context ctx) {
        Intent i = new Intent(ctx, HawlReceiver.class).setAction(ACTION_DUE);
        return PendingIntent.getBroadcast(ctx.getApplicationContext(), 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void cancelAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(ctx));
    }

    static void schedule(Context ctx) {
        State s = state(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (!s.active || prefs(ctx).getBoolean(KEY_NOTIFIED, false)) { cancelAlarm(ctx); return; }
        long trigger = LocalDate.ofEpochDay(s.dueDay).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (trigger <= System.currentTimeMillis()) { cancelAlarm(ctx); return; }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(ctx));
    }

    static void notifyIfDue(Context ctx) {
        Context app = ctx.getApplicationContext();
        State s = state(app);
        SharedPreferences p = prefs(app);
        if (!s.active || !s.complete || p.getBoolean(KEY_NOTIFIED, false)) return;

        if (Build.VERSION.SDK_INT >= 33
                && ActivityCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManagerCompat nm = NotificationManagerCompat.from(app);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Crescent Alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }

        String title = "Zakat is now due";
        String body = "Your wealth has remained above the Nisab for one full lunar year (hawl). "
                + "Zakat is now due on your eligible wealth. Please review your calculation and fulfil your obligation.";
        PendingIntent open = PendingIntent.getActivity(app, 0, new Intent(app, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(open);
        nm.notify(NOTIF_ID, nb.build());
        p.edit().putBoolean(KEY_NOTIFIED, true).apply();
        cancelAlarm(app);
    }
}