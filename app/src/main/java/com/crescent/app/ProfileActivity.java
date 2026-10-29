package com.crescent.app;

import android.os.Bundle;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        TextView nameView = findViewById(R.id.profileName);
        TextView emailView = findViewById(R.id.profileEmail);
        View ratingLayout = findViewById(R.id.expertRatingLayout);
        RatingBar ratingBar = findViewById(R.id.expertRatingBar);
        TextView ratingCount = findViewById(R.id.expertRatingCount);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String email = user.getEmail() != null ? user.getEmail() : "Anonymous Guest";
            String name = user.getDisplayName() != null ? user.getDisplayName() : (email.contains("@") ? email.split("@")[0] : "User");
            nameView.setText(name);
            emailView.setText(email);

            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .get().addOnSuccessListener(doc -> {
                        if (doc.exists() && Boolean.TRUE.equals(doc.getBoolean("isExpert"))) {
                            ratingLayout.setVisibility(View.VISIBLE);
                            Double r = doc.getDouble("rating");
                            Long c = doc.getLong("ratingCount");
                            float rating = r != null ? r.floatValue() : 0f;
                            long count = c != null ? c : 0;
                            ratingBar.setRating(rating);
                            ratingCount.setText(String.format(Locale.US, "(%d ratings)", count));
                        }
                    }).addOnFailureListener(e -> Toast.makeText(this, "Failed to load expert status.", Toast.LENGTH_SHORT).show());
        }
    }
}