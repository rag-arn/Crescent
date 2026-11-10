package com.crescent.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a lightweight Realtime Database listener alive in the background so a chat message
 * notification appears even when the app is closed. It needs no server, no Cloud Functions and no
 * paid plan: the phone listens to its own inbox (user_chats/<uid>) directly.
 *
 * A notification is suppressed only while that person's chat is open and on screen
 * (MainActivity.activeRoomId, set by ChatActivity in onResume / cleared in onPause).
 */
public class ChatNotificationService extends Service {

    private static final String CH_SERVICE = "crescent_chat_service";
    private static final String CH_MESSAGES = "crescent_chat_messages";
    private static final String CH_ALERTS = "crescent_account_alerts";
    private static final String PREFS_ALERTS = "crescent_alerts_notified";   // ids of account notifications already shown
    private static final int FOREGROUND_ID = 7100;
    private static final String PREFS_NOTIFIED = "crescent_chat_notified";   // roomId -> last notified timestamp
    private static final String PREFS_SERVICE = "crescent_chat_service";

    private SharedPreferences notified;
    private FirebaseAuth.AuthStateListener authListener;
    private DatabaseReference inboxRef;
    private ChildEventListener inboxListener;
    private ListenerRegistration alertsReg;
    private ListenerRegistration selfReg;
    private final Map<String, String> nameCache = new HashMap<>();

    // ---------- public helpers ----------

    /** Starts (or keeps running) the background chat listener. Safe to call repeatedly. */
    public static void start(Context ctx) {
        try {
            ContextCompat.startForegroundService(ctx.getApplicationContext(), new Intent(ctx.getApplicationContext(), ChatNotificationService.class));
        } catch (Exception ignored) {
            // The system refused a background start; the next time the app is opened it starts again.
        }
    }

    /** Stops the listener and forgets what was notified (used on sign out). */
    public static void stop(Context ctx) {
        Context app = ctx.getApplicationContext();
        app.stopService(new Intent(app, ChatNotificationService.class));
        app.getSharedPreferences(PREFS_NOTIFIED, MODE_PRIVATE).edit().clear().apply();
    }

    /** Same id for one chat room everywhere, so ChatActivity can clear the notification when it opens. */
    public static int notificationId(String roomId) {
        return 10000 + Math.abs(roomId.hashCode() % 100000);
    }

    /**
     * Asks once for "Don't optimize battery" so Android's Doze mode does not pause the connection
     * while the screen is off. This is a system dialog; the person can say no.
     */
    public static void askBatteryExemptionOnce(Activity a) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        SharedPreferences p = a.getSharedPreferences(PREFS_SERVICE, MODE_PRIVATE);
        if (p.getBoolean("battery_asked", false)) return;
        PowerManager pm = (PowerManager) a.getSystemService(POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(a.getPackageName())) return;
        p.edit().putBoolean("battery_asked", true).apply();
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.getPackageName())));
        } catch (Exception ignored) {}
    }

    // ---------- service lifecycle ----------

    @Override public void onCreate() {
        super.onCreate();
        notified = getSharedPreferences(PREFS_NOTIFIED, MODE_PRIVATE);
        createChannels();
        try {
            goForeground();
        } catch (Exception e) {
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            goForeground();
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        HawlTracker.notifyIfDue(this);   // belt and braces: also re-arms the Zakat countdown alarm
        HawlTracker.schedule(this);
        if (authListener == null) {
            authListener = auth -> {
                detachInbox();
                FirebaseUser u = auth.getCurrentUser();
                if (u == null || u.isAnonymous()) {
                    stopSelf();
                } else {
                    attachInbox(u.getUid());
                }
            };
            FirebaseAuth.getInstance().addAuthStateListener(authListener);
        }
        return START_STICKY;   // ask Android to bring it back if it is ever killed
    }

    @Override public void onDestroy() {
        if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        authListener = null;
        detachInbox();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    // ---------- listening ----------

    private void attachInbox(String uid) {
        inboxRef = FirebaseDatabase.getInstance().getReference("user_chats").child(uid);
        inboxListener = new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot s, String prev) { handle(s); }
            @Override public void onChildChanged(@NonNull DataSnapshot s, String prev) { handle(s); }
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {
                if (s.getKey() != null) {
                    notified.edit().remove(s.getKey()).apply();
                    NotificationManagerCompat.from(ChatNotificationService.this).cancel(notificationId(s.getKey()));
                }
            }
            @Override public void onChildMoved(@NonNull DataSnapshot s, String prev) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        inboxRef.addChildEventListener(inboxListener);

        // A disabled account is signed out and stops receiving notifications
        selfReg = FirebaseFirestore.getInstance().collection("users").document(uid)
                .addSnapshotListener((snap, err) -> {
                    if (snap != null && Boolean.TRUE.equals(snap.getBoolean("disabled"))) {
                        FirebaseAuth.getInstance().signOut();   // the auth listener then stops this service
                    }
                });

        // Account notifications (for example "New rating received"). The first snapshot also contains
        // anything that arrived while this person was signed out, so it is shown as soon as they sign in.
        alertsReg = FirebaseFirestore.getInstance().collection("users").document(uid).collection("notifications")
                .whereEqualTo("read", false)
                .addSnapshotListener((snap, err) -> {
                    if (snap == null) return;
                    for (DocumentChange dc : snap.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) handleAlert(dc.getDocument());
                    }
                });
    }

    private void handleAlert(QueryDocumentSnapshot d) {
        if (Boolean.TRUE.equals(d.getBoolean("silent"))) return;   // already shown locally (e.g. Zakat due); list-only record
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences p = getSharedPreferences(PREFS_ALERTS, MODE_PRIVATE);
        Set<String> seen = new HashSet<>(p.getStringSet("ids", new HashSet<>()));
        if (seen.contains(d.getId())) return;                  // already shown once
        if (seen.size() > 300) seen.clear();
        seen.add(d.getId());
        p.edit().putStringSet("ids", seen).apply();

        String title = d.getString("title");
        String msg = d.getString("message");
        if (title == null) title = "Alert";
        if (msg == null) msg = "New update.";

        int id = 200000 + Math.abs(d.getId().hashCode() % 700000);
        PendingIntent pi = TaskStackBuilder.create(this)
                .addNextIntent(new Intent(this, MainActivity.class))
                .addNextIntent(new Intent(this, NotificationsActivity.class))
                .getPendingIntent(id, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_crescent_small)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        NotificationManagerCompat.from(this).notify(id, n);
    }

    private void detachInbox() {
        if (alertsReg != null) alertsReg.remove();
        alertsReg = null;
        if (selfReg != null) selfReg.remove();
        selfReg = null;
        if (inboxRef != null && inboxListener != null) inboxRef.removeEventListener(inboxListener);
        inboxRef = null;
        inboxListener = null;
    }

    private void handle(DataSnapshot room) {
        final String roomId = room.getKey();
        Boolean unread = room.child("hasUnread").getValue(Boolean.class);
        Long ts = room.child("lastTimestamp").getValue(Long.class);
        final String otherUid = room.child("otherUid").getValue(String.class);
        final String text = room.child("lastMessage").getValue(String.class);

        if (roomId == null || ts == null || otherUid == null || !Boolean.TRUE.equals(unread)) return;
        if (ts <= notified.getLong(roomId, 0L)) return;            // already notified for this message

        notified.edit().putLong(roomId, ts).apply();
        if (roomId.equals(MainActivity.activeRoomId)) return;     // that chat is open on screen: it is being read now

        final String msg = (text != null && !text.isEmpty()) ? text : "You received a new message.";
        final long when = ts;

        String cached = nameCache.get(otherUid);
        if (cached != null) {
            postMessageNotification(roomId, otherUid, cached, msg, when);
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(otherUid).get()
                .addOnSuccessListener(doc -> {
                    String n = doc.getString("name");
                    if (n == null || n.isEmpty()) n = "New message";
                    else nameCache.put(otherUid, n);
                    postMessageNotification(roomId, otherUid, n, msg, when);
                })
                .addOnFailureListener(e -> postMessageNotification(roomId, otherUid, "New message", msg, when));
    }

    // ---------- notifications ----------

    private void postMessageNotification(String roomId, String otherUid, String name, String text, long when) {
        if (roomId.equals(MainActivity.activeRoomId)) return;   // the chat was opened while the name was loading
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent main = new Intent(this, MainActivity.class);
        Intent chat = new Intent(this, ChatActivity.class)
                .putExtra("expertUid", otherUid)
                .putExtra("expertName", "New message".equals(name) ? "User" : name);
        int id = notificationId(roomId);
        PendingIntent pi = TaskStackBuilder.create(this)
                .addNextIntent(main)
                .addNextIntent(chat)
                .getPendingIntent(id, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new NotificationCompat.Builder(this, CH_MESSAGES)
                .setSmallIcon(R.drawable.ic_crescent_small)
                .setContentTitle(name)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(when)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        NotificationManagerCompat.from(this).notify(id, n);
    }

    private void goForeground() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CH_SERVICE)
                .setSmallIcon(R.drawable.ic_crescent_small)
                .setContentTitle("Crescent")
                .setContentText("Listening for new messages")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pi)
                .build();
        int type = Build.VERSION.SDK_INT >= 34
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                : ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST;
        ServiceCompat.startForeground(this, FOREGROUND_ID, n, type);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel svc = new NotificationChannel(CH_SERVICE, "Background connection", NotificationManager.IMPORTANCE_MIN);
        svc.setDescription("Keeps Crescent connected so chat messages arrive when the app is closed.");
        svc.setShowBadge(false);
        NotificationChannel msgs = new NotificationChannel(CH_MESSAGES, "Chat messages", NotificationManager.IMPORTANCE_HIGH);
        msgs.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel alerts = new NotificationChannel(CH_ALERTS, "Account alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(svc);
        nm.createNotificationChannel(msgs);
        nm.createNotificationChannel(alerts);
    }
}
