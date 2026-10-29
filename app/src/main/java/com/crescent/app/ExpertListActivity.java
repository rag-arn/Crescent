package com.crescent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
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

                        // Make the image view round and load the Base64 image
                        ImageView expertImage = item.findViewById(R.id.expertImage);
                        expertImage.setOutlineProvider(new ViewOutlineProvider() {
                            @Override public void getOutline(View view, Outline outline) {
                                outline.setOval(0, 0, view.getWidth(), view.getHeight());
                            }
                        });
                        expertImage.setClipToOutline(true);

                        String base64Img = doc.getString("profileImageBase64");
                        if (base64Img != null && !base64Img.isEmpty()) {
                            try {
                                byte[] decoded = Base64.decode(base64Img, Base64.DEFAULT);
                                Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                                expertImage.setPadding(0, 0, 0, 0);
                                expertImage.setImageTintList(null); // <--- ADD THIS LINE
                                expertImage.setImageBitmap(bmp);
                            } catch (Exception ignored) {}
                        }

                        item.setOnClickListener(v -> {
                            String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                            if (doc.getId().equals(currentUid)) {
                                Toast.makeText(this, "You cannot message yourself.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            android.content.Intent intent = new android.content.Intent(ExpertListActivity.this, ChatActivity.class);
                            intent.putExtra("expertUid", doc.getId());
                            intent.putExtra("expertName", name != null ? name : "Expert");
                            intent.putExtra("expertImage", base64Img);
                            startActivity(intent);
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