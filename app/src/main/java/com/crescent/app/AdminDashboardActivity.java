package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/** Home screen of the admin: applications, ratings and broadcast notifications. */
public class AdminDashboardActivity extends AppCompatActivity {

    private TextView pendingCountText;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (!Admin.isAdmin(this, user)) {               // never show this screen to anybody else
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_dashboard);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        Admin.verify(this, user, ok -> { });                  // claims the admin account the first time
        ((TextView) findViewById(R.id.adminEmailText)).setText(user.getEmail());
        pendingCountText = findViewById(R.id.pendingCountText);

        findViewById(R.id.cardApplications).setOnClickListener(v -> startActivity(new Intent(this, AdminApplicationsActivity.class)));
        findViewById(R.id.cardRatings).setOnClickListener(v -> startActivity(new Intent(this, AdminRatingsActivity.class)));
        findViewById(R.id.cardUsers).setOnClickListener(v -> startActivity(new Intent(this, AdminUsersActivity.class)));
        findViewById(R.id.cardAccount).setOnClickListener(v -> startActivity(new Intent(this, AdminAccountActivity.class)));
        findViewById(R.id.cardNotify).setOnClickListener(v -> startActivity(new Intent(this, AdminNotifyActivity.class)));
        findViewById(R.id.adminSignOutBtn).setOnClickListener(v -> confirmSignOut());
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingCountText == null) return;
        FirebaseFirestore.getInstance().collection("users").whereEqualTo("expertStatus", "pending").get()
                .addOnSuccessListener(q -> {
                    int n = q.size();
                    pendingCountText.setText(n == 0 ? "No pending applications" : n + " pending");
                })
                .addOnFailureListener(e -> pendingCountText.setText(""));
    }

    private void confirmSignOut() {
        new ThemedDialog(this)
                .title("Sign out")
                .message("Sign out of the admin account?")
                .negative("Cancel", null)
                .positive("Sign out", d -> {
                    d.dismiss();
                    FirebaseAuth.getInstance().signOut();
                    Intent login = new Intent(this, LoginActivity.class);
                    login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(login);
                    finish();
                })
                .show();
    }
}
