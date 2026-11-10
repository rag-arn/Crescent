package com.crescent.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.time.Period;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Home-screen widget: Zakat (Hawl) countdown shown in boxes (months / weeks / days), the Islamic and
 * Gregorian date, Hawl progress and the due date, using the same values as the dashboard.
 * Tapping it opens the app.
 */
public class ZakatWidgetProvider extends AppWidgetProvider {

    private static final String[] HIJRI_MONTHS = {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal",
            "Dhu al-Qi'dah", "Dhu al-Hijjah"};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        RemoteViews views = buildViews(context);
        for (int id : appWidgetIds) manager.updateAppWidget(id, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);   // handles APPWIDGET_UPDATE -> onUpdate()
        String action = intent.getAction();
        // The day (and so the countdown and Islamic date) can change without a normal update tick.
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            updateAll(context);
        }
    }

    /** Refreshes every placed widget. Call this whenever the Hawl data changes. */
    public static void updateAll(Context context) {
        try {
            Context app = context.getApplicationContext();
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            int[] ids = manager.getAppWidgetIds(new ComponentName(app, ZakatWidgetProvider.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews views = buildViews(app);
            for (int id : ids) manager.updateAppWidget(id, views);
        } catch (Exception ignored) { /* a widget refresh must never crash the app */ }
    }

    private static String unit(long n, String singular, String plural) { return n == 1 ? singular : plural; }

    private static RemoteViews buildViews(Context context) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_zakat);
        Locale loc = Locale.getDefault();
        LocalDate today = LocalDate.now();

        // ---- Dates: Islamic (same format as the dashboard) and Gregorian ----
        String hijriDay, hijriYear;
        try {
            HijrahDate d = HijrahDate.now();
            hijriDay = d.get(ChronoField.DAY_OF_MONTH) + " " + HIJRI_MONTHS[d.get(ChronoField.MONTH_OF_YEAR) - 1];
            hijriYear = d.get(ChronoField.YEAR) + " AH";
        } catch (Exception e) {
            hijriDay = "—";
            hijriYear = "";
        }
        final String hijri = (hijriDay + " " + hijriYear).trim();
        final String gregorian = today.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", loc));
        v.setTextViewText(R.id.widgetHijri, hijriDay);
        v.setTextViewText(R.id.widgetHijriYear, hijriYear);
        v.setTextViewText(R.id.widgetGregorian, gregorian);

        // ---- Zakat (Hawl) countdown ----
        HawlTracker.State h = HawlTracker.state(context);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM yyyy", loc);
        String months, weeks, days, chip, summary, due, description;
        int chipBg, chipText, progress;

        if (!h.active) {
            months = weeks = days = "—";
            chip = "NOT STARTED";
            chipBg = R.drawable.bg_widget_chip_idle;
            chipText = R.color.sage_text_soft;
            summary = "No active Hawl";
            due = "Calculate Zakat to begin";
            progress = 0;
            description = "Zakat countdown not started. Islamic date " + hijri + ". " + gregorian;
        } else if (h.complete) {
            months = weeks = days = "0";
            chip = "ZAKAT DUE";
            chipBg = R.drawable.bg_widget_chip_due;
            chipText = R.color.sage_text;
            summary = "Lunar year completed";
            due = "Due since " + LocalDate.ofEpochDay(h.dueDay).format(f);
            progress = 1000;
            description = "Zakat is due now. Islamic date " + hijri + ". " + gregorian;
        } else {
            LocalDate dueDate = LocalDate.ofEpochDay(h.dueDay);
            Period p = today.isBefore(dueDate) ? Period.between(today, dueDate) : Period.ZERO;
            long m = p.getYears() * 12L + p.getMonths();
            long w = p.getDays() / 7;
            long d = p.getDays() % 7;
            months = String.valueOf(m);
            weeks = String.valueOf(w);
            days = String.valueOf(d);
            v.setTextViewText(R.id.widgetMonthsLabel, unit(m, "MONTH", "MONTHS"));
            v.setTextViewText(R.id.widgetWeeksLabel, unit(w, "WEEK", "WEEKS"));
            v.setTextViewText(R.id.widgetDaysLabel, unit(d, "DAY", "DAYS"));
            chip = "IN PROGRESS";
            chipBg = R.drawable.bg_widget_chip;
            chipText = R.color.sage_btn_pressed;
            progress = Math.round(h.progress * 1000f);
            summary = Math.round(h.progress * 100f) + "% complete · " + h.remainingDays + " " + unit(h.remainingDays, "day", "days") + " left";
            due = "Due " + dueDate.format(f);
            description = h.remainingDays + " " + unit(h.remainingDays, "day", "days") + " remaining until Zakat, due "
                    + dueDate.format(f) + ". Islamic date " + hijri + ". " + gregorian;
        }

        v.setTextViewText(R.id.widgetMonths, months);
        v.setTextViewText(R.id.widgetWeeks, weeks);
        v.setTextViewText(R.id.widgetDays, days);
        if (!h.active || h.complete) {   // labels stay at their plural defaults outside the running countdown
            v.setTextViewText(R.id.widgetMonthsLabel, "MONTHS");
            v.setTextViewText(R.id.widgetWeeksLabel, "WEEKS");
            v.setTextViewText(R.id.widgetDaysLabel, "DAYS");
        }
        v.setTextViewText(R.id.widgetChip, chip);
        v.setInt(R.id.widgetChip, "setBackgroundResource", chipBg);
        v.setTextColor(R.id.widgetChip, androidx.core.content.ContextCompat.getColor(context, chipText));
        v.setTextViewText(R.id.widgetSummary, summary);
        v.setTextViewText(R.id.widgetDue, due);
        v.setProgressBar(R.id.widgetProgress, 1000, progress, false);
        v.setContentDescription(R.id.widgetRoot, description);

        // ---- Tap anywhere to open the app ----
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widgetRoot, pi);
        return v;
    }
}