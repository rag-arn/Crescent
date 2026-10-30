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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoUnit;

/**
 * Tracks the hawl (one lunar year) that must pass while wealth stays at or above the Nisab.
 *  - A calculation that reaches the Nisab starts the count (if one is not already running).
 *  - A calculation below the Nisab cancels it, because the wealth did not stay above the Nisab.
 *  - When the lunar year is complete, a notification tells the user that Zakat is due.
 */
final class HawlTracker {
    private HawlTracker() {}

    static final String ACTION_DUE = "com.crescent.app.HAWL_DUE";
    private static final String PREFS = "crescent_hawl";
    private static final String KEY_START = "start_epoch_day";
    private static final String KEY_NOTIFIED = "due_notified";
    private static final String CHANNEL_ID = "crescent_alerts";   // same channel as the other alerts
    private static final int NOTIF_ID = 7001;
    private static final long FALLBACK_LUNAR_YEAR_DAYS = 354;

    /** Snapshot of the current hawl, ready for the dashboard. */
    static final class State {
        boolean active, complete;
        long startDay, dueDay, totalDays, elapsedDays, remainingDays;
        float progress;   // 0..1
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Call after every new calculation. eligible = net wealth reached the Nisab. */
    static void onCalculation(Context ctx, boolean eligible) {
        SharedPreferences p = prefs(ctx);
        if (eligible) {
            if (!p.contains(KEY_START)) {
                p.edit().putLong(KEY_START, LocalDate.now().toEpochDay()).putBoolean(KEY_NOTIFIED, false).apply();
            }
        } else {
            p.edit().clear().apply();
            cancelAlarm(ctx);
        }
        schedule(ctx);
    }

    /** Starts a fresh lunar year from today. Call this once Zakat has been paid. */
    static void startNewCycle(Context ctx) {
        prefs(ctx).edit().putLong(KEY_START, LocalDate.now().toEpochDay()).putBoolean(KEY_NOTIFIED, false).apply();
        schedule(ctx);
    }

    /** Same Hijri date one lunar year later (about 354 days). */
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

    // ---------- alarm (delivers the notification even if the app is closed) ----------

    private static PendingIntent alarmIntent(Context ctx) {
        Intent i = new Intent(ctx, HawlReceiver.class).setAction(ACTION_DUE);
        return PendingIntent.getBroadcast(ctx.getApplicationContext(), 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void cancelAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(ctx));
    }

    /** (Re)schedules the reminder for 9:00 on the due day. Safe to call repeatedly. */
    static void schedule(Context ctx) {
        State s = state(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (!s.active || prefs(ctx).getBoolean(KEY_NOTIFIED, false)) { cancelAlarm(ctx); return; }
        long trigger = LocalDate.ofEpochDay(s.dueDay).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (trigger <= System.currentTimeMillis()) { cancelAlarm(ctx); return; }   // already due: notifyIfDue() handles it
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(ctx));  // inexact, needs no special permission
    }

    // ---------- notification ----------

    /** Posts the "Zakat is due" notification once, when the lunar year is complete. */
    static void notifyIfDue(Context ctx) {
        Context app = ctx.getApplicationContext();
        State s = state(app);
        SharedPreferences p = prefs(app);
        if (!s.active || !s.complete || p.getBoolean(KEY_NOTIFIED, false)) return;

        if (Build.VERSION.SDK_INT >= 33
                && ActivityCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;   // try again next time the app opens
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