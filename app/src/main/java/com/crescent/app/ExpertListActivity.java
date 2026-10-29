package com.crescent.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Locale;

public class ExpertListActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expert_list);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        LinearLayout container = findViewById(R.id.expertsContainer);
        View progress = findViewById(R.id.loadingProgress);
        TextView emptyText = findViewById(R.id.emptyText);

        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("isExpert", true)
                .get()
                .addOnSuccessListener(query -> {
                    progress.setVisibility(View.GONE);
                    if (query.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        return;
                    }
                    LayoutInflater inflater = LayoutInflater.from(this);
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        View item = inflater.inflate(R.layout.item_expert, container, false);

                        String name = doc.getString("name");
                        String bio = doc.getString("bio");
                        Double rating = doc.getDouble("rating");
                        Long count = doc.getLong("ratingCount");

                        ((TextView) item.findViewById(R.id.expertName)).setText(name != null ? name : "Expert");
                        ((TextView) item.findViewById(R.id.expertBio)).setText(bio != null ? bio : "Verified Zakat Expert");

                        double r = rating != null ? rating : 5.0;
                        long c = count != null ? count : 0;
                        ((TextView) item.findViewById(R.id.expertRating)).setText(String.format(Locale.US, "★ %.1f (%d reviews)", r, c));

                        item.setOnClickListener(v -> {
                            // TODO: Open ChatRoomActivity (Phase 3)
                            Toast.makeText(this, "Opening chat with " + name + " (Coming in Phase 3)", Toast.LENGTH_SHORT).show();
                        });

                        container.addView(item);
                    }
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load experts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}