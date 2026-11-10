package com.crescent.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Who is the admin.
 *
 * The password is NOT stored in the app. The admin signs in like any other user.
 *
 *  - The account whose VERIFIED e-mail is ADMIN_EMAIL is always the admin. Signing in with it "claims" the
 *    admin role (stored in Firestore config/admin and on the phone).
 *  - After claiming, the admin is remembered by the Firebase uid, so the admin can change the e-mail address
 *    later (Admin account screen) and still be the admin.
 *  - A claim only counts while it was made under the current ADMIN_EMAIL. So if ADMIN_EMAIL is changed in the
 *    code, an old admin account stops being admin and the new address takes over at its next sign-in.
 */
final class Admin {
    private Admin() {}

    static final String ADMIN_EMAIL = "arnob4669@icloud.com";

    interface Result { void done(boolean isAdmin); }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("crescent_admin", Context.MODE_PRIVATE);
    }

    private static boolean basic(FirebaseUser u) {
        return u != null && !u.isAnonymous() && u.isEmailVerified();
    }

    private static boolean hasAdminEmail(FirebaseUser u) {
        return u.getEmail() != null && ADMIN_EMAIL.equalsIgnoreCase(u.getEmail());
    }

    /** Fast check without network (used by the admin screens). */
    static boolean isAdmin(Context c, FirebaseUser user) {
        if (!basic(user)) return false;
        if (hasAdminEmail(user)) return true;
        SharedPreferences p = prefs(c);
        return ADMIN_EMAIL.equalsIgnoreCase(p.getString("claimEmail", null))
                && user.getUid().equals(p.getString("uid", null));
    }

    private static void remember(Context c, String uid) {
        prefs(c).edit().putString("uid", uid).putString("claimEmail", ADMIN_EMAIL).apply();
    }

    /** Full check at sign-in. Claims the admin role when the admin e-mail signs in. */
    static void verify(Context c, FirebaseUser user, Result cb) {
        final Context app = c.getApplicationContext();
        if (!basic(user)) { cb.done(false); return; }

        final FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (hasAdminEmail(user)) {                       // the designated admin address always wins
            Map<String, Object> claim = new HashMap<>();
            claim.put("uid", user.getUid());
            claim.put("email", ADMIN_EMAIL);
            claim.put("claimedAt", System.currentTimeMillis());
            db.collection("config").document("admin").set(claim);
            remember(app, user.getUid());
            cb.done(true);
            return;
        }

        // Another e-mail: admin only if it is the uid that claimed the role under the current ADMIN_EMAIL
        db.collection("config").document("admin").get().addOnCompleteListener(t -> {
            if (!t.isSuccessful() || t.getResult() == null) {
                cb.done(isAdmin(app, user));             // offline or no access: use what the phone remembers
                return;
            }
            String adminUid = t.getResult().exists() ? t.getResult().getString("uid") : null;
            String claimEmail = t.getResult().exists() ? t.getResult().getString("email") : null;
            boolean valid = adminUid != null && ADMIN_EMAIL.equalsIgnoreCase(claimEmail);
            boolean me = valid && adminUid.equals(user.getUid());
            if (valid) remember(app, adminUid); else prefs(app).edit().clear().apply();
            cb.done(me);
        });
    }
}