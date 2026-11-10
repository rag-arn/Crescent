package com.crescent.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Restarts the chat listener after the phone reboots or the app is updated. */
public class ChatBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null && !u.isAnonymous()) ChatNotificationService.start(context);
    }
}