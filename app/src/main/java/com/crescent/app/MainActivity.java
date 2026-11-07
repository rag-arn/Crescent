package com.crescent.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.crescent.app.databinding.ActivityMainBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final float MENU_WIDTH_FRACTION = 0.78f;
    private static final long ANIM_DURATION_MS = 320L;
    private static final float GAUGE_CALCULATED = 0.75f;
    private static final int NOTIF_PERMISSION_CODE = 101;

    private ActivityMainBinding b;
    private CrescentRepository repo;
    private FirebaseAuth mAuth;
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private boolean menuOpen = false;
    private float menuProgress = 0f;
    private int menuWidthPx = 0;
    private ValueAnimator menuAnimator;
    private OnBackPressedCallback backCallback;
    private WindowInsetsControllerCompat insetsController;

    public static String activeRoomId = null;
    private boolean isFirstNotifLoad = true;
    private boolean isFirstChatLoad = true;

    private String taka(double v) { return nf.format(Math.round(v)); }

    private final android.content.BroadcastReceiver dynamicSyncReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if ("com.crescent.app.SYNC_COMPLETE".equals(intent.getAction())) {
                fetchRandomHadith();
                Toast.makeText(context, "Market data updated in background", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestStartupPermissions();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Intent login = new Intent(this, LoginActivity.class);
            if (getIntent().getData() != null) login.setData(getIntent().getData());
            startActivity(login);
            finish();
            return;
        }

        if (savedInstanceState == null) handleDeepLink(getIntent());

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new CrescentRepository(getApplicationContext());

        String name = "Guest";
        String email = "";

        if (!currentUser.isAnonymous()) {
            name = currentUser.getDisplayName();
            email = currentUser.getEmail() != null ? currentUser.getEmail() : "";
            if (name == null || name.isEmpty()) {
                name = email.contains("@") ? email.split("@")[0] : "User";
            }
        }

        b.slideMenu.profileNameText.setText(name);
        b.slideMenu.profileEmailText.setText(email.isEmpty() ? "Anonymous Session" : email);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("uid", currentUser.getUid());
        data.put("name", name);
        data.put("email", email);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(currentUser.getUid()).set(data, com.google.firebase.firestore.SetOptions.merge());

        db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String base64Img = doc.getString("profileImageBase64");
                if (base64Img != null && !base64Img.isEmpty()) {
                    try {
                        byte[] decoded = Base64.decode(base64Img, Base64.DEFAULT);
                        android.graphics.Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                        b.slideMenu.slideProfileImage.setPadding(0, 0, 0, 0);
                        b.slideMenu.slideProfileImage.setImageTintList(null);
                        b.slideMenu.slideProfileImage.setImageBitmap(bmp);
                    } catch (Exception ignored) {}
                }
            }
        });

        setupSystemBars();
        setupSlideMenu();
        setupClicks();
        makeLogoRound();
        setDates();
        fetchRandomHadith();
        listenForNotifications();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(DataSyncWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("DailyCrescentSync", ExistingPeriodicWorkPolicy.KEEP, syncRequest);
    }

    @Override protected void onStart() {
        super.onStart();
        android.content.IntentFilter filter = new android.content.IntentFilter("com.crescent.app.SYNC_COMPLETE");
        ContextCompat.registerReceiver(this, dynamicSyncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(dynamicSyncReceiver); } catch (IllegalArgumentException ignored) {}
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    private static String expertUidFromLink(android.net.Uri data) {
        if (data == null) return null;
        String scheme = data.getScheme();
        String host = data.getHost();
        boolean httpsLink = "https".equals(scheme) && "crescent.app".equals(host)
                && data.getPathSegments().size() >= 2 && "expert".equals(data.getPathSegments().get(0));
        boolean customLink = "crescent".equals(scheme) && "expert".equals(host)
                && !data.getPathSegments().isEmpty();
        if (!httpsLink && !customLink) return null;
        String uid = data.getLastPathSegment();
        return (uid == null || uid.isEmpty()) ? null : uid;
    }

    private void handleDeepLink(Intent intent) {
        final String expertUid = expertUidFromLink(intent.getData());
        if (expertUid == null) return;
        intent.setData(null);

        FirebaseFirestore.getInstance().collection("users").document(expertUid).get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (doc.exists() && Boolean.TRUE.equals(doc.getBoolean("isExpert"))) {
                        String name = doc.getString("name");
                        Intent chatIntent = new Intent(this, ChatActivity.class);
                        chatIntent.putExtra("expertUid", expertUid);
                        chatIntent.putExtra("expertName", name != null ? name : "Expert");
                        chatIntent.putExtra("expertImage", doc.getString("profileImageBase64"));
                        startActivity(chatIntent);
                    } else {
                        Toast.makeText(this, "Expert profile not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not open the expert profile. Check your connection.", Toast.LENGTH_SHORT).show());
    }

    private void requestStartupPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_CODE);
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mAuth != null && mAuth.getCurrentUser() != null) {
            loadDashboard();
            HawlTracker.syncFromCloud(this, () -> {
                HawlTracker.schedule(this);
                HawlTracker.notifyIfDue(this);
                updateHawl();
            });
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void setupSystemBars() {
        final int headerTop = b.dashboard.brandHeader.getPaddingTop();
        final int navBottom = b.dashboard.bottomNav.getPaddingBottom();
        final int menuHeaderTop = b.slideMenu.menuHeader.getPaddingTop();
        final int menuFooterBottom = b.slideMenu.menuFooter.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(b.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            b.dashboard.brandHeader.setPadding(b.dashboard.brandHeader.getPaddingLeft(), headerTop + bars.top, b.dashboard.brandHeader.getPaddingRight(), b.dashboard.brandHeader.getPaddingBottom());
            b.dashboard.bottomNav.setPadding(b.dashboard.bottomNav.getPaddingLeft(), b.dashboard.bottomNav.getPaddingTop(), b.dashboard.bottomNav.getPaddingRight(), navBottom + bars.bottom);
            b.slideMenu.menuHeader.setPadding(b.slideMenu.menuHeader.getPaddingLeft(), menuHeaderTop + bars.top, b.slideMenu.menuHeader.getPaddingRight(), b.slideMenu.menuHeader.getPaddingBottom());
            b.slideMenu.menuFooter.setPadding(b.slideMenu.menuFooter.getPaddingLeft(), b.slideMenu.menuFooter.getPaddingTop(), b.slideMenu.menuFooter.getPaddingRight(), menuFooterBottom + bars.bottom);
            return insets;
        });
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        insetsController = WindowCompat.getInsetsController(getWindow(), b.getRoot());
        insetsController.setAppearanceLightStatusBars(true);
        insetsController.setAppearanceLightNavigationBars(true);
    }

    private void setupSlideMenu() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        menuWidthPx = Math.round(screenW * MENU_WIDTH_FRACTION);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.slideMenu.getRoot().getLayoutParams();
        lp.width = menuWidthPx;
        lp.gravity = Gravity.START;
        b.slideMenu.getRoot().setLayoutParams(lp);
        applyMenuProgress(0f);
        b.drawerRoot.post(() -> {
            int measuredW = b.drawerRoot.getWidth();
            if (measuredW > 0) {
                int newWidth = Math.round(measuredW * MENU_WIDTH_FRACTION);
                if (newWidth != menuWidthPx) {
                    menuWidthPx = newWidth;
                    ViewGroup.LayoutParams params = b.slideMenu.getRoot().getLayoutParams();
                    params.width = menuWidthPx;
                    b.slideMenu.getRoot().setLayoutParams(params);
                    applyMenuProgress(menuProgress);
                }
            }
        });
        backCallback = new OnBackPressedCallback(false) { @Override public void handleOnBackPressed() { closeMenu(null); } };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    private void applyMenuProgress(float p) {
        menuProgress = p;
        float mainTx = menuWidthPx * p;
        float menuTx = -menuWidthPx * (1f - p);
        b.dashboard.getRoot().setTranslationX(mainTx);
        b.drawerScrim.setTranslationX(mainTx);
        b.slideMenu.getRoot().setTranslationX(menuTx);
        b.drawerScrim.setAlpha(p);
        b.drawerScrim.setVisibility(p > 0.001f ? View.VISIBLE : View.GONE);
        b.slideMenu.getRoot().setVisibility(p > 0.001f ? View.VISIBLE : View.INVISIBLE);
        if (insetsController != null) insetsController.setAppearanceLightStatusBars(p < 0.5f);
    }

    private void toggleMenu() { if (menuOpen) closeMenu(null); else openMenu(); }
    private void openMenu() { menuOpen = true; if (backCallback != null) backCallback.setEnabled(true); animateMenuTo(1f, null); }
    private void closeMenu(Runnable onClosed) { menuOpen = false; if (backCallback != null) backCallback.setEnabled(false); animateMenuTo(0f, onClosed); }

    private void animateMenuTo(float target, Runnable onEnd) {
        if (menuAnimator != null && menuAnimator.isRunning()) menuAnimator.cancel();
        menuAnimator = ValueAnimator.ofFloat(menuProgress, target);
        menuAnimator.setDuration(ANIM_DURATION_MS);
        menuAnimator.setInterpolator(new FastOutSlowInInterpolator());
        menuAnimator.addUpdateListener(anim -> applyMenuProgress((float) anim.getAnimatedValue()));
        menuAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) { if (!cancelled) { applyMenuProgress(target); if (onEnd != null) onEnd.run(); } }
        });
        menuAnimator.start();
    }

    private void setDates() {
        String engDate = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(new Date());
        b.dashboard.englishDateText.setText(engDate);
        try {
            HijrahDate hijri = HijrahDate.now();
            String[] months = {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"};
            b.dashboard.arabicDateText.setText(hijri.get(ChronoField.DAY_OF_MONTH) + " " + months[hijri.get(ChronoField.MONTH_OF_YEAR) - 1] + " " + hijri.get(ChronoField.YEAR) + " AH");
        } catch (Exception e) { b.dashboard.arabicDateText.setVisibility(View.GONE); }
    }

    private void makeLogoRound() {
        ViewOutlineProvider roundProvider = new ViewOutlineProvider() { @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); } };
        b.dashboard.logoImage.setOutlineProvider(roundProvider);
        b.dashboard.logoImage.setClipToOutline(true);
        b.slideMenu.slideProfileImage.setOutlineProvider(roundProvider);
        b.slideMenu.slideProfileImage.setClipToOutline(true);
    }

    private void setupClicks() {
        b.dashboard.payNowBtn.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://assunnahfoundation.org/"))); } catch (Exception e) { Toast.makeText(this, "Could not open browser.", Toast.LENGTH_SHORT).show(); }
        });
        b.dashboard.detailedBreakdownBtn.setOnClickListener(v -> startActivity(new Intent(this, AssetBreakdownActivity.class)));
        b.dashboard.gridCalculate.setOnClickListener(v -> startActivity(new Intent(this, GoldActivity.class)));
        b.dashboard.gridHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        b.dashboard.gridAssets.setOnClickListener(v -> startActivity(new Intent(this, AssetBreakdownActivity.class)));
        b.dashboard.gridChat.setOnClickListener(v -> startActivity(new Intent(this, ExpertListActivity.class)));
        b.dashboard.navCalculate.setOnClickListener(v -> startActivity(new Intent(this, GoldActivity.class)));
        b.dashboard.navChat.setOnClickListener(v -> startActivity(new Intent(this, ChatListActivity.class)));
        b.dashboard.navSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        b.dashboard.notifButton.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        b.dashboard.menuButton.setOnClickListener(v -> toggleMenu());
        b.slideMenu.menuCloseButton.setOnClickListener(v -> closeMenu(null));
        b.drawerScrim.setOnClickListener(v -> closeMenu(null));
        b.slideMenu.menuProfileCard.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, ProfileActivity.class))));
        b.slideMenu.menuAboutZakat.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, InfoActivity.class))));
        b.slideMenu.menuCalculate.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, GoldActivity.class))));
        b.slideMenu.menuHistory.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, HistoryActivity.class))));
        b.slideMenu.menuBecomeExpert.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, ExpertRegistrationActivity.class))));
        b.slideMenu.menuSettings.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, SettingsActivity.class))));

        b.slideMenu.menuSignOut.setOnClickListener(v -> closeMenu(() -> {
            Toast.makeText(this, "Signing out...", Toast.LENGTH_SHORT).show();
            FirebaseUser user = mAuth.getCurrentUser();
            boolean isGuest = (user != null && user.isAnonymous());
            String uid = user != null ? user.getUid() : null;

            io.execute(() -> {
                if (isGuest) {
                    CrescentDatabase.get(MainActivity.this).clearAllTables();
                    getSharedPreferences("crescent_hawl", MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("crescent_prices", MODE_PRIVATE).edit().clear().apply();
                }

                runOnUiThread(() -> {
                    if (isGuest && uid != null) {
                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        db.collection("users").document(uid).delete();

                        // RTDB Clean Up for Guests
                        FirebaseDatabase.getInstance().getReference("user_chats").child(uid).removeValue();

                        user.delete().addOnCompleteListener(task -> completeSignOut());
                    } else {
                        completeSignOut();
                    }
                });
            });
        }));
    }

    private void completeSignOut() {
        mAuth.signOut();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignIn.getClient(MainActivity.this, gso).signOut().addOnCompleteListener(task -> {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void listenForNotifications() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            String uid = user.getUid();

            // 1. System Notifications (Firestore)
            FirebaseFirestore.getInstance().collection("users").document(uid).collection("notifications")
                    .whereEqualTo("read", false).addSnapshotListener((value, error) -> {
                        if (value == null) return;
                        b.dashboard.notifDot.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
                        if (!isFirstNotifLoad) {
                            for (com.google.firebase.firestore.DocumentChange dc : value.getDocumentChanges()) {
                                if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                    String title = dc.getDocument().getString("title");
                                    String msg = dc.getDocument().getString("message");
                                    showSystemNotification(title != null ? title : "Alert", msg != null ? msg : "New update.", dc.getDocument().getId().hashCode());
                                }
                            }
                        }
                        isFirstNotifLoad = false;
                    });

            // 2. Chat Notifications (Realtime Database)
            FirebaseDatabase.getInstance().getReference("user_chats").child(uid)
                    .addValueEventListener(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean hasUnread = false;
                            for (DataSnapshot doc : snapshot.getChildren()) {
                                Boolean unread = doc.child("hasUnread").getValue(Boolean.class);
                                if (Boolean.TRUE.equals(unread)) {
                                    hasUnread = true;
                                    if (!isFirstChatLoad) {
                                        String roomId = doc.getKey();
                                        if (!roomId.equals(activeRoomId)) {
                                            String msg = doc.child("lastMessage").getValue(String.class);
                                            showSystemNotification("New Message", msg != null ? msg : "You received a new message.", roomId.hashCode());
                                        }
                                    }
                                }
                            }
                            b.dashboard.chatDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
                            isFirstChatLoad = false;
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }
    }

    private void showSystemNotification(String title, String message, int notifId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        String channelId = "crescent_alerts";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Crescent Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteViews customView = new RemoteViews(getPackageName(), R.layout.custom_notification);
        customView.setTextViewText(R.id.notifTitle, title);
        customView.setTextViewText(R.id.notifText, message);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_bell).setStyle(new NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(customView)
                .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(pendingIntent);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notificationManager.notify(notifId, builder.build());
    }

    private void updateHawl() {
        HawlTracker.State h = HawlTracker.state(this);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault());

        if (!h.active) {
            b.dashboard.hawlDaysText.setText("Not started");
            b.dashboard.hawlProgress.setProgress(0);

            // Clear the placeholder text and completely hide the TextView so it takes up no space
            b.dashboard.hawlDueText.setText("");
            b.dashboard.hawlDueText.setVisibility(View.GONE);

        } else if (h.complete) {
            b.dashboard.hawlDaysText.setText("Zakat is due now");
            b.dashboard.hawlProgress.setProgress(1000, true);
            b.dashboard.hawlDueText.setText("One lunar year was completed on " + LocalDate.ofEpochDay(h.dueDay).format(f) + ".");
            b.dashboard.hawlDueText.setVisibility(View.VISIBLE);

        } else {
            b.dashboard.hawlDaysText.setText(h.remainingDays + (h.remainingDays == 1 ? " day remaining" : " days remaining"));
            b.dashboard.hawlProgress.setProgress(Math.round(h.progress * 1000f), true);
            b.dashboard.hawlDueText.setText("Zakat becomes due on " + LocalDate.ofEpochDay(h.dueDay).format(f) + ".");
            b.dashboard.hawlDueText.setVisibility(View.VISIBLE);
        }
    }

    private void loadDashboard() {
        repo.snapshots(snaps -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Snapshot s = snaps.isEmpty() ? null : snaps.get(0);
            if (s == null) {
                b.dashboard.zakatYearText.setText("Your Zakat for " + Calendar.getInstance().get(Calendar.YEAR));
                b.dashboard.zakatAmountCircle.setText("0 BDT"); b.dashboard.zakatStateText.setText("Not calculated"); b.dashboard.zakatGauge.setProgress(0f);
            } else {
                b.dashboard.zakatYearText.setText("Your Zakat for " + s.year);
                b.dashboard.zakatAmountCircle.setText(taka(s.zakat) + " BDT"); b.dashboard.zakatStateText.setText("Calculated"); b.dashboard.zakatGauge.setProgress(s.zakat > 0 ? GAUGE_CALCULATED : 0f);
            }
        }));
    }

    private void fetchRandomHadith() {
        String cachedHadith = getSharedPreferences("crescent_ui", MODE_PRIVATE).getString("daily_hadith", null);
        if (cachedHadith != null) { b.dashboard.hadithText.setText(cachedHadith); return; }
        final String[] fallback = { "\"Establish prayer and give zakat...\"\n(Al-Baqarah 2:110)" };
        b.dashboard.hadithText.setText(fallback[new Random().nextInt(fallback.length)]);
    }
}