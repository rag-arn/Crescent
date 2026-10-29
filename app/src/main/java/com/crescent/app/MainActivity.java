package com.crescent.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
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
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard: shows the latest saved calculation and links to the calculator, history and info. */
public class MainActivity extends AppCompatActivity {
    private static final float MENU_WIDTH_FRACTION = 0.78f; // > 2/3rd of the screen
    private static final long ANIM_DURATION_MS = 320L;

    private ActivityMainBinding b;
    private CrescentRepository repo;
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));

    private boolean menuOpen = false;
    private float menuProgress = 0f; // 0f = closed, 1f = open
    private int menuWidthPx = 0;
    private ValueAnimator menuAnimator;
    private OnBackPressedCallback backCallback;

    private String taka(double v) { return "৳" + nf.format(Math.round(v)); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new CrescentRepository(getApplicationContext());

        setupSystemBars();
        setupSlideMenu();
        setupClicks();
        makeLogoRound();
    }

    @Override protected void onResume() {
        super.onResume();
        loadDashboard();   // refresh after returning from a calculation
    }

    /** Keeps the headers and bottom bars clear of the status/navigation bars (edge-to-edge on Android 15+). */
    private void setupSystemBars() {
        final int headerTop = b.brandHeader.getPaddingTop();
        final int navBottom = b.bottomNav.getPaddingBottom();
        final int menuHeaderTop = b.menuHeader.getPaddingTop();
        final int menuFooterBottom = b.menuFooter.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(b.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            b.brandHeader.setPadding(b.brandHeader.getPaddingLeft(), headerTop + bars.top, b.brandHeader.getPaddingRight(), b.brandHeader.getPaddingBottom());
            b.bottomNav.setPadding(b.bottomNav.getPaddingLeft(), b.bottomNav.getPaddingTop(), b.bottomNav.getPaddingRight(), navBottom + bars.bottom);
            b.menuHeader.setPadding(b.menuHeader.getPaddingLeft(), menuHeaderTop + bars.top, b.menuHeader.getPaddingRight(), b.menuHeader.getPaddingBottom());
            b.menuFooter.setPadding(b.menuFooter.getPaddingLeft(), b.menuFooter.getPaddingTop(), b.menuFooter.getPaddingRight(), menuFooterBottom + bars.bottom);
            return insets;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), b.getRoot());
        c.setAppearanceLightStatusBars(false);       // light icons on the dark teal header
        c.setAppearanceLightNavigationBars(true);    // dark icons on the cream bottom bar
    }

    /** Configures the >2/3 screen slide menu opening from the LEFT side and back-button handling. */
    private void setupSlideMenu() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        menuWidthPx = Math.round(screenW * MENU_WIDTH_FRACTION);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.sideMenu.getLayoutParams();
        lp.width = menuWidthPx;
        lp.gravity = Gravity.START;
        b.sideMenu.setLayoutParams(lp);

        applyMenuProgress(0f);

        b.drawerRoot.post(() -> {
            int measuredW = b.drawerRoot.getWidth();
            if (measuredW > 0) {
                int newWidth = Math.round(measuredW * MENU_WIDTH_FRACTION);
                if (newWidth != menuWidthPx) {
                    menuWidthPx = newWidth;
                    ViewGroup.LayoutParams params = b.sideMenu.getLayoutParams();
                    params.width = menuWidthPx;
                    b.sideMenu.setLayoutParams(params);
                    applyMenuProgress(menuProgress);
                }
            }
        });

        backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                closeMenu(null);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    /** Slides the menu in from the left (-menuWidthPx -> 0) and pushes the activity to the right (0 -> +menuWidthPx). */
    private void applyMenuProgress(float p) {
        menuProgress = p;
        float mainTx = menuWidthPx * p;
        float menuTx = -menuWidthPx * (1f - p);

        b.mainContent.setTranslationX(mainTx);
        b.drawerScrim.setTranslationX(mainTx);
        b.sideMenu.setTranslationX(menuTx);

        b.drawerScrim.setAlpha(p);
        b.drawerScrim.setVisibility(p > 0.001f ? View.VISIBLE : View.GONE);
        b.sideMenu.setVisibility(p > 0.001f ? View.VISIBLE : View.INVISIBLE);
    }

    private void toggleMenu() {
        if (menuOpen) closeMenu(null);
        else openMenu();
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
        if (menuAnimator != null && menuAnimator.isRunning()) {
            menuAnimator.cancel();
        }
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

    /** Clips the logo image into a circle, whatever shape the PNG itself has. */
    private void makeLogoRound() {
        b.logoImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        b.logoImage.setClipToOutline(true);
    }

    private void soon(String what) { Toast.makeText(this, what + " is coming soon.", Toast.LENGTH_SHORT).show(); }

    private void setupClicks() {
        b.recalcButton.setOnClickListener(v -> startActivity(new Intent(this, GoldActivity.class)));
        b.navHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        b.navHome.setOnClickListener(v -> { /* already on the dashboard */ });

        // Placeholders for features that are not available yet
        b.profileButton.setOnClickListener(v -> soon("Profile"));
        b.payButton.setOnClickListener(v -> soon("Zakat payment tracking"));
        b.addAssetText.setOnClickListener(v -> soon("Adding assets"));
        b.navAssets.setOnClickListener(v -> soon("Assets"));
        b.navSettings.setOnClickListener(v -> soon("Settings"));

        // Three-bar menu open / close triggers
        b.menuButton.setOnClickListener(v -> toggleMenu());
        b.menuCloseButton.setOnClickListener(v -> closeMenu(null));
        b.drawerScrim.setOnClickListener(v -> closeMenu(null));

        // Slide-out menu items (smoothly closes menu then performs action)
        b.menuAboutZakat.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, InfoActivity.class))));
        b.menuCalculate.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, GoldActivity.class))));
        b.menuHistory.setOnClickListener(v -> closeMenu(() -> startActivity(new Intent(this, HistoryActivity.class))));
        b.menuProfileCard.setOnClickListener(v -> closeMenu(() -> soon("Profile")));
        b.menuSettings.setOnClickListener(v -> closeMenu(() -> soon("Settings")));
    }

    private void loadDashboard() {
        repo.snapshots(snaps -> repo.assets(assets -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            render(snaps.isEmpty() ? null : snaps.get(0), assets);
        })));
    }

    private void render(Snapshot s, List<Asset> assets) {
        // Latest saved value per category (assets come newest first)
        Map<String, Double> latest = new HashMap<>();
        for (Asset a : assets) if (!latest.containsKey(a.category)) latest.put(a.category, a.amount);
        double cash = val(latest, "Cash") + val(latest, "Bank savings") + val(latest, "Cash receivable");
        double metals = val(latest, "Gold") + val(latest, "Silver");
        double commercial = val(latest, "Commercial goods");

        if (s == null) {
            b.lastCalculatedText.setText("—");
            b.zakatAmount.setText(taka(0));
            b.paidText.setText("Paid: —");
            b.targetText.setText("Target: " + taka(0));
            b.zakatProgress.setProgress(0);
            b.nisabSub.setText("Complete a calculation to see it");
            b.nisabStatus.setText("—");
            b.recalcText.setText("Calculate Zakat");
            setAsset(b.cashAmount, b.cashSub, 0, 0);
            setAsset(b.metalAmount, b.metalSub, 0, 0);
            setAsset(b.commercialAmount, b.commercialSub, 0, 0);
            b.liabilityAmount.setText(taka(0));
            b.liabilitySub.setText("Deducted");
            return;
        }

        b.lastCalculatedText.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(s.createdAt)));
        b.zakatAmount.setText(taka(s.zakat));
        b.paidText.setText("Paid: —");                       // payment tracking is not available yet
        b.targetText.setText("Target: " + taka(s.zakat));
        b.zakatProgress.setProgress(0);
        b.nisabSub.setText(taka(s.nisab) + "  (net wealth " + taka(s.netWealth) + ")");
        b.nisabStatus.setText(s.netWealth >= s.nisab ? "Above Nisab" : "Below Nisab");
        b.recalcText.setText("Recalculate");
        setAsset(b.cashAmount, b.cashSub, cash, s.assets);
        setAsset(b.metalAmount, b.metalSub, metals, s.assets);
        setAsset(b.commercialAmount, b.commercialSub, commercial, s.assets);
        b.liabilityAmount.setText("-" + taka(s.liabilities));
        b.liabilitySub.setText("Deducted");
    }

    private static double val(Map<String, Double> m, String k) { Double d = m.get(k); return d == null ? 0 : d; }

    private void setAsset(android.widget.TextView amount, android.widget.TextView sub, double v, double total) {
        amount.setText(taka(v));
        sub.setText(total > 0 ? Math.round(v * 100 / total) + "% of assets" : "—");
    }
}