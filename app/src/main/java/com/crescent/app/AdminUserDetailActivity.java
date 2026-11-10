package com.crescent.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** One user: disable / enable the account, or delete all of the user's data. */
public class AdminUserDetailActivity extends AppCompatActivity {

    private DocumentReference userRef;
    private DocumentSnapshot doc;
    private String uid;

    private TextView nameText, emailText, roleText, statusText, uidText, disableInfo;
    private Button toggleBtn, deleteBtn;
    private View progress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Admin.isAdmin(this, FirebaseAuth.getInstance().getCurrentUser())) { finish(); return; }
        uid = getIntent().getStringExtra("uid");
        if (uid == null) { finish(); return; }

        setContentView(R.layout.activity_admin_user_detail);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        nameText = findViewById(R.id.detailName);
        emailText = findViewById(R.id.detailEmail);
        roleText = findViewById(R.id.detailRole);
        statusText = findViewById(R.id.detailStatus);
        uidText = findViewById(R.id.detailUid);
        disableInfo = findViewById(R.id.disableInfo);
        toggleBtn = findViewById(R.id.toggleDisableBtn);
        deleteBtn = findViewById(R.id.deleteDataBtn);
        progress = findViewById(R.id.detailProgress);

        userRef = FirebaseFirestore.getInstance().collection("users").document(uid);
        toggleBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        toggleBtn.setOnClickListener(v -> confirmToggle());
        deleteBtn.setOnClickListener(v -> confirmDelete());
        load();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        toggleBtn.setEnabled(!busy && doc != null);
        deleteBtn.setEnabled(!busy && doc != null);
    }

    private void load() {
        setBusy(true);
        userRef.get().addOnSuccessListener(d -> {
            doc = d;
            render();
            setBusy(false);
        }).addOnFailureListener(e -> {
            setBusy(false);
            Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private boolean isDisabled() { return doc != null && Boolean.TRUE.equals(doc.getBoolean("disabled")); }

    private void render() {
        String name = doc.getString("name");
        nameText.setText(name != null && !name.isEmpty() ? name : "Unnamed user");
        emailText.setText(doc.getString("email"));
        uidText.setText("ID: " + uid);

        if (Boolean.TRUE.equals(doc.getBoolean("isExpert"))) {
            Double r = doc.getDouble("rating");
            Long c = doc.getLong("ratingCount");
            roleText.setText(String.format(Locale.US, "Role: Expert  (★ %.1f, %d ratings)", r != null ? r : 0.0, c != null ? c : 0));
        } else if ("pending".equals(doc.getString("expertStatus"))) {
            roleText.setText("Role: General user (expert application pending)");
        } else {
            roleText.setText("Role: General user");
        }

        boolean disabled = isDisabled();
        statusText.setText(disabled ? "Account disabled" : "Account active");
        statusText.setTextColor(disabled ? 0xFFE53935 : getColor(R.color.sage_text));
        toggleBtn.setText(disabled ? "Enable account" : "Disable account");
        disableInfo.setText(disabled
                ? "This person cannot sign in or use the app until the account is enabled again."
                : "A disabled account is signed out, cannot sign in, and is hidden from the expert list.");
    }

    // ----- disable / enable -----

    private void confirmToggle() {
        final boolean disable = !isDisabled();
        new ThemedDialog(this)
                .title(disable ? "Disable account" : "Enable account")
                .message(disable
                        ? "Disable " + nameText.getText() + "'s account? They will be signed out and cannot sign in."
                        : "Enable " + nameText.getText() + "'s account again?")
                .negative("Cancel", null)
                .positive(disable ? "Disable" : "Enable", d -> {
                    d.dismiss();
                    setBusy(true);
                    Map<String, Object> update = new HashMap<>();
                    update.put("disabled", disable);
                    update.put(disable ? "disabledAt" : "enabledAt", System.currentTimeMillis());
                    userRef.update(update).addOnSuccessListener(a -> {
                        Toast.makeText(this, disable ? "Account disabled" : "Account enabled", Toast.LENGTH_SHORT).show();
                        load();
                    }).addOnFailureListener(e -> {
                        setBusy(false);
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                })
                .show();
    }

    // ----- delete data -----

    private void confirmDelete() {
        new ThemedDialog(this)
                .title("Delete user data")
                .message("Permanently delete everything stored for " + nameText.getText()
                        + "?\n\nThis removes their history, notifications, profile details and all of their chats "
                        + "(also for the people they chatted with). This cannot be undone.")
                .negative("Cancel", null)
                .positive("Delete", d -> {
                    d.dismiss();
                    setBusy(true);
                    AdminUserData.deleteAll(uid, ok -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, ok ? "User data deleted"
                                : "Some data could not be deleted. Check the admin access rules.", Toast.LENGTH_LONG).show();
                        load();
                    });
                })
                .show();
    }
}
