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
import com.crescent.app.databinding.ActivityMainBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestStartupPermissions();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

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
    }

    private void requestStartupPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications Enabled", Toast.LENGTH_SHORT).show();
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

        backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { closeMenu(null); }
        };
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

    private void toggleMenu() {
        if (menuOpen) closeMenu(null); else openMenu();
    }

    private void openMenu() {
        menuOpen = true;
        if (backCallback != null) backCallback.setEnabled(true);
        animateMenuTo(1f, null);
    }

    private void closeMenu(Runnable onClosed) {
        menuOpen = false;
        if (backCallback != null) backCallback.setEnabled(false);
        animateMenuTo(0f, onClosed);
    }

    private void animateMenuTo(float target, Runnable onEnd) {
        if (menuAnimator != null && menuAnimator.isRunning()) menuAnimator.cancel();
        menuAnimator = ValueAnimator.ofFloat(menuProgress, target);
        menuAnimator.setDuration(ANIM_DURATION_MS);
        menuAnimator.setInterpolator(new FastOutSlowInInterpolator());
        menuAnimator.addUpdateListener(anim -> applyMenuProgress((float) anim.getAnimatedValue()));
        menuAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    applyMenuProgress(target);
                    if (onEnd != null) onEnd.run();
                }
            }
        });
        menuAnimator.start();
    }

    private void setDates() {
        String engDate = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(new Date());
        b.dashboard.englishDateText.setText(engDate);

        try {
            HijrahDate hijri = HijrahDate.now();
            String[] months = {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
                    "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
                    "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"};
            int m = hijri.get(ChronoField.MONTH_OF_YEAR);
            int d = hijri.get(ChronoField.DAY_OF_MONTH);
            int y = hijri.get(ChronoField.YEAR);
            b.dashboard.arabicDateText.setText(d + " " + months[m - 1] + " " + y + " AH");
        } catch (Exception e) {
            b.dashboard.arabicDateText.setVisibility(View.GONE);
        }
    }

    private void makeLogoRound() {
        ViewOutlineProvider roundProvider = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        };

        b.dashboard.logoImage.setOutlineProvider(roundProvider);
        b.dashboard.logoImage.setClipToOutline(true);

        b.slideMenu.slideProfileImage.setOutlineProvider(roundProvider);
        b.slideMenu.slideProfileImage.setClipToOutline(true);
    }

    private void soon(String what) { Toast.makeText(this, what + " is coming soon.", Toast.LENGTH_SHORT).show(); }

    private void setupClicks() {
        b.dashboard.payNowBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://assunnahfoundation.org/donate/zakat")));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open browser.", Toast.LENGTH_SHORT).show();
            }
        });

        b.dashboard.detailedBreakdownBtn.setOnClickListener(v -> startActivity(new Intent(this, AssetBreakdownActivity.class)));

        b.dashboard.gridCalculate.setOnClickListener(v -> startActivity(new Intent(this, GoldActivity.class)));
        b.dashboard.gridHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        b.dashboard.gridAssets.setOnClickListener(v -> startActivity(new Intent(this, AssetBreakdownActivity.class)));
        b.dashboard.gridChat.setOnClickListener(v -> startActivity(new Intent(this, ExpertListActivity.class)));

        b.dashboard.navHome.setOnClickListener(v -> { /* already here */ });
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

        // Wipes local preferences and deletes Firebase Guest accounts & their chats
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
                        db.collection("chats").whereArrayContains("users", uid).get().addOnSuccessListener(query -> {
                            for (DocumentSnapshot doc : query) doc.getReference().delete();
                        });
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
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String uid = user.getUid();

            db.collection("users").document(uid).collection("notifications")
                    .whereEqualTo("read", false).addSnapshotListener((value, error) -> {
                        if (value == null) return;
                        b.dashboard.notifDot.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);

                        if (!isFirstNotifLoad) {
                            for (com.google.firebase.firestore.DocumentChange dc : value.getDocumentChanges()) {
                                if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                    String title = dc.getDocument().getString("title");
                                    String msg = dc.getDocument().getString("message");
                                    showSystemNotification(title != null ? title : "Crescent Alert", msg != null ? msg : "You have a new update.", dc.getDocument().getId().hashCode());
                                }
                            }
                        }
                        isFirstNotifLoad = false;
                    });

            db.collection("chats")
                    .whereArrayContains("users", uid)
                    .addSnapshotListener((value, error) -> {
                        if (value == null || error != null) return;

                        // 1. Check ALL active chats to see if the red dot should be visible
                        boolean hasUnread = false;
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            if (Boolean.TRUE.equals(doc.getBoolean("hasUnread_" + uid))) {
                                hasUnread = true;
                                break;
                            }
                        }
                        b.dashboard.chatDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);

                        // 2. Only show the pop-up notification if a message arrived JUST NOW
                        if (!isFirstChatLoad) {
                            for (com.google.firebase.firestore.DocumentChange dc : value.getDocumentChanges()) {
                                DocumentSnapshot doc = dc.getDocument();
                                boolean roomHasUnread = Boolean.TRUE.equals(doc.getBoolean("hasUnread_" + uid));

                                if (roomHasUnread && !doc.getId().equals(activeRoomId)) {
                                    if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.MODIFIED || dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                        String msg = doc.getString("lastMessage");
                                        showSystemNotification("New Message", msg != null ? msg : "You received a new message.", doc.getId().hashCode());
                                    }
                                }
                            }
                        }
                        isFirstChatLoad = false;
                    });
        }
    }

    private void showSystemNotification(String title, String message, int notifId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        String channelId = "crescent_alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Crescent Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews customView = new RemoteViews(getPackageName(), R.layout.custom_notification);
        customView.setTextViewText(R.id.notifTitle, title);
        customView.setTextViewText(R.id.notifText, message);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(customView)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notifId, builder.build());
        }
    }

    private void updateHawl() {
        HawlTracker.State h = HawlTracker.state(this);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault());
        if (!h.active) {
            b.dashboard.hawlDaysText.setText("Not started");
            b.dashboard.hawlProgress.setProgress(0);
            b.dashboard.hawlDueText.setText("The lunar-year count begins once a calculation shows your wealth is at or above the Nisab.");
        } else if (h.complete) {
            b.dashboard.hawlDaysText.setText("Zakat is due now");
            b.dashboard.hawlProgress.setProgress(1000, true);
            b.dashboard.hawlDueText.setText("One lunar year was completed on " + LocalDate.ofEpochDay(h.dueDay).format(f) + ".");
        } else {
            b.dashboard.hawlDaysText.setText(h.remainingDays + (h.remainingDays == 1 ? " day remaining" : " days remaining"));
            b.dashboard.hawlProgress.setProgress(Math.round(h.progress * 1000f), true);
            b.dashboard.hawlDueText.setText("Zakat becomes due on " + LocalDate.ofEpochDay(h.dueDay).format(f) + ".");
        }
    }

    private void loadDashboard() {
        repo.snapshots(snaps -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Snapshot s = snaps.isEmpty() ? null : snaps.get(0);

            if (s == null) {
                Calendar c = Calendar.getInstance();
                b.dashboard.zakatYearText.setText("Your Zakat for " + c.get(Calendar.YEAR));
                b.dashboard.zakatAmountCircle.setText("0 BDT");
                b.dashboard.zakatStateText.setText("Not calculated");
                b.dashboard.zakatGauge.setProgress(0f);
            } else {
                b.dashboard.zakatYearText.setText("Your Zakat for " + s.year);
                b.dashboard.zakatAmountCircle.setText(taka(s.zakat) + " BDT");
                b.dashboard.zakatStateText.setText("Calculated");
                b.dashboard.zakatGauge.setProgress(s.zakat > 0 ? GAUGE_CALCULATED : 0f);
            }
        }));
    }

    private void fetchRandomHadith() {
        final String[] fallback = {
                "\"Establish prayer and give zakat...\"\n(Al-Baqarah 2:110)",
                "\"Charity does not decrease wealth.\"\n(Sahih Muslim)",
                "\"Protect yourself from hell-fire even by giving a piece of date as charity.\"\n(Al-Bukhari)",
                "\"The believer's shade on the Day of Resurrection will be his charity.\"\n(Al-Tirmidhi)"
        };
        b.dashboard.hadithText.setText(fallback[new Random().nextInt(fallback.length)]);

        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://random-hadith-generator.vercel.app/bukhari/").openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                if (c.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);

                    JSONObject json = new JSONObject(sb.toString());
                    String hadith = json.getJSONObject("data").getString("hadith_english").trim();

                    if (hadith.length() > 160) {
                        hadith = hadith.substring(0, 155) + "...";
                    }
                    final String finalHadith = "\"" + hadith + "\"\n(Sahih al-Bukhari)";

                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            b.dashboard.hadithText.setText(finalHadith);
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }
}