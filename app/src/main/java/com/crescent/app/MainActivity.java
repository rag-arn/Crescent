package com.crescent.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
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

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.chrono.HijrahDate;
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

    private String taka(double v) { return nf.format(Math.round(v)); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(currentUser.getUid())
                .set(data, com.google.firebase.firestore.SetOptions.merge());

        setupSystemBars();
        setupSlideMenu();
        setupClicks();
        makeLogoRound();
        setDates();
        fetchRandomHadith();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mAuth != null && mAuth.getCurrentUser() != null) {
            loadDashboard();
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
        b.dashboard.logoImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        b.dashboard.logoImage.setClipToOutline(true);
    }

    private void soon(String what) { Toast.makeText(this, what + " is coming soon.", Toast.LENGTH_SHORT).show(); }

    private void setupClicks() {
        b.dashboard.payNowBtn.setOnClickListener(v -> soon("Payment Gateway"));
        b.dashboard.detailedBreakdownBtn.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        b.dashboard.gridCalculate.setOnClickListener(v -> startActivity(new Intent(this, GoldActivity.class)));
        b.dashboard.gridHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        b.dashboard.gridAssets.setOnClickListener(v -> soon("Asset Breakdown"));
        b.dashboard.gridChat.setOnClickListener(v -> startActivity(new Intent(this, ExpertListActivity.class)));

        b.dashboard.navHome.setOnClickListener(v -> { /* already here */ });
        b.dashboard.navCalculate.setOnClickListener(v -> startActivity(new Intent(this, GoldActivity.class)));
        b.dashboard.navChat.setOnClickListener(v -> soon("Chat Inbox (Phase 3)"));
        b.dashboard.navSettings.setOnClickListener(v -> soon("Settings"));

        b.dashboard.menuButton.setOnClickListener(v -> toggleMenu());
        b.slideMenu.menuCloseButton.setOnClickListener(v -> closeMenu(null));
        b.drawerScrim.setOnClickListener(v -> closeMenu(null));

        b.slideMenu.menuProfileCard.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, ProfileActivity.class))));
        b.slideMenu.menuNotifications.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, NotificationsActivity.class))));
        b.slideMenu.menuAboutZakat.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, InfoActivity.class))));
        b.slideMenu.menuCalculate.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, GoldActivity.class))));
        b.slideMenu.menuHistory.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, HistoryActivity.class))));

        b.slideMenu.menuBecomeExpert.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, ExpertRegistrationActivity.class))));
        b.slideMenu.menuSettings.setOnClickListener(v -> closeMenu(() -> soon("Settings")));

        b.slideMenu.menuSignOut.setOnClickListener(v -> closeMenu(() -> {
            mAuth.signOut();
            com.google.android.gms.auth.api.signin.GoogleSignInOptions gso = new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build();
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener(task -> {
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            });
        }));
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