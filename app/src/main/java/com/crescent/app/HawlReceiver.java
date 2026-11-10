package com.crescent.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Runs with the app closed. Fires on the due day (alarm), after a reboot or app update (which clear
 * alarms), and when the date, time or time zone changes. Each time it shows the "Zakat is due"
 * notification if the countdown has finished, and re-arms the alarm if not.
 */
public class HawlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        HawlTracker.notifyIfDue(context);
        HawlTracker.schedule(context);
    }
}