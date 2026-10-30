package com.crescent.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires on the due day (alarm) and after a reboot, which clears scheduled alarms. */
public class HawlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) HawlTracker.schedule(context);
        HawlTracker.notifyIfDue(context);
    }
}