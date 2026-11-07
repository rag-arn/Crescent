package com.crescent.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Watches the two things that decide whether live prices can be loaded:
 *  - the system broadcast ACTION_AIRPLANE_MODE_CHANGED (airplane mode toggled), and
 *  - the default network's state (via ConnectivityManager.NetworkCallback).
 * Every callback is delivered on the main thread. Call start() in onStart() and stop() in onStop().
 */
final class ConnectivityMonitor {
    interface Listener { void onChanged(boolean airplaneOn, boolean online); }

    private final Context app;
    private final ConnectivityManager cm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean running;
    private boolean online;     // main thread only

    ConnectivityMonitor(Context context) {
        app = context.getApplicationContext();
        cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // The state is re-read from the system on every event instead of trusting the broadcast's extras.
    private final BroadcastReceiver airplaneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) notifyListener();
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
            update(hasInternet(caps));
        }
        @Override public void onLost(@NonNull Network network) { update(false); }
    };

    void start(Listener l) {
        if (running) return;
        running = true;
        listener = l;
        online = isOnline(app);
        // Protected system broadcast: only the system can send it, so exporting the receiver is safe.
        ContextCompat.registerReceiver(app, airplaneReceiver,
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_EXPORTED);
        try {
            if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) { /* the airplane-mode receiver still works */ }
        notifyListener();   // report the current state immediately
    }

    void stop() {
        if (!running) return;
        running = false;
        listener = null;
        try { app.unregisterReceiver(airplaneReceiver); } catch (IllegalArgumentException ignored) {}
        try { if (cm != null) cm.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) {}
    }

    private void update(boolean nowOnline) {
        main.post(() -> {
            if (!running) return;
            online = nowOnline;
            notifyListener();
        });
    }

    private void notifyListener() {
        if (listener != null) listener.onChanged(isAirplaneOn(app), online);
    }

    private static boolean hasInternet(NetworkCapabilities caps) {
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    static boolean isAirplaneOn(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    static boolean isOnline(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            return caps != null && hasInternet(caps);
        } catch (RuntimeException e) {
            return true;    // can't tell: don't block the user
        }
    }
}