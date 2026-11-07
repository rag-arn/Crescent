package com.crescent.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExpertListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ExpertAdapter adapter;
    private List<DocumentSnapshot> expertList = new ArrayList<>();
    private ProgressBar progress;
    private TextView emptyText;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expert_list);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        progress = findViewById(R.id.loadingProgress);
        emptyText = findViewById(R.id.emptyText);

        // 1. Setup the RecyclerView
        recyclerView = findViewById(R.id.expertsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpertAdapter();
        recyclerView.setAdapter(adapter);

        // 2. Fetch the data from Firestore
        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("isExpert", true)
                .get()
                .addOnSuccessListener(query -> {
                    progress.setVisibility(View.GONE);
                    if (query.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        return;
                    }
                    // Load the documents into our list and tell the adapter to refresh
                    expertList.addAll(query.getDocuments());
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load experts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // --- RECYCLER VIEW ADAPTER & VIEWHOLDER ---

    private class ExpertAdapter extends RecyclerView.Adapter<ExpertViewHolder> {

        @NonNull
        @Override
        public ExpertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_expert, parent, false);
            return new ExpertViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ExpertViewHolder holder, int position) {
            DocumentSnapshot doc = expertList.get(position);

            String name = doc.getString("name");
            String bio = doc.getString("bio");
            Double rating = doc.getDouble("rating");
            Long count = doc.getLong("ratingCount");
            String base64Img = doc.getString("profileImageBase64");

            holder.nameText.setText(name != null ? name : "Expert");
            holder.bioText.setText(bio != null ? bio : "Verified Zakat Expert");

            double r = rating != null ? rating : 5.0;
            long c = count != null ? count : 0;
            holder.ratingText.setText(String.format(Locale.US, "★ %.1f (%d reviews)", r, c));

            // Reset image view state before loading to prevent recycled view glitches
            holder.expertImage.setImageBitmap(null);
            holder.expertImage.setImageResource(R.drawable.ic_person);
            holder.expertImage.setImageTintList(getColorStateList(R.color.sage_text));
            holder.expertImage.setPadding(10, 10, 10, 10);

            if (base64Img != null && !base64Img.isEmpty()) {
                try {
                    byte[] decoded = Base64.decode(base64Img, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    holder.expertImage.setPadding(0, 0, 0, 0);
                    holder.expertImage.setImageTintList(null);
                    holder.expertImage.setImageBitmap(bmp);
                } catch (Exception ignored) {}
            }

            // Standard HTTPS deep link sharing
            holder.itemView.setOnLongClickListener(v -> {
                String link = "https://crescent.app/expert/" + doc.getId();
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, "Zakat Expert on Crescent");
                share.putExtra(Intent.EXTRA_TEXT, "Consult with this verified Zakat expert on the Crescent app:\n\n" + link
                        + "\n\nIf the link opens in your browser, use this one instead:\ncrescent://expert/" + doc.getId());
                startActivity(Intent.createChooser(share, "Share Expert Profile"));
                return true;
            });

            holder.itemView.setOnClickListener(v -> {
                String currentUid = FirebaseAuth.getInstance().getUid();
                if (doc.getId().equals(currentUid)) {
                    Toast.makeText(ExpertListActivity.this, "You cannot message yourself.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(ExpertListActivity.this, "Long press to share this profile", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(ExpertListActivity.this, ChatActivity.class);
                intent.putExtra("expertUid", doc.getId());
                intent.putExtra("expertName", name != null ? name : "Expert");
                intent.putExtra("expertImage", base64Img);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return expertList.size();
        }
    }

    private static class ExpertViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, bioText, ratingText;
        ImageView expertImage;

        ExpertViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.expertName);
            bioText = itemView.findViewById(R.id.expertBio);
            ratingText = itemView.findViewById(R.id.expertRating);
            expertImage = itemView.findViewById(R.id.expertImage);

            expertImage.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            expertImage.setClipToOutline(true);
        }
    }
}