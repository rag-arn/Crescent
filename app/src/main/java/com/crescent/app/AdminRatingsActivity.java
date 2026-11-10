package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** All active experts with their average rating. Tap one to see every rating and who gave it. */
public class AdminRatingsActivity extends AppCompatActivity {

    private final List<DocumentSnapshot> experts = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Admin.isAdmin(this, FirebaseAuth.getInstance().getCurrentUser())) { finish(); return; }

        setContentView(R.layout.activity_admin_list);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.adminTitle)).setText("Expert Ratings");

        View progress = findViewById(R.id.adminProgress);
        TextView empty = findViewById(R.id.adminEmpty);
        empty.setText("There are no active experts yet.");

        RecyclerView rv = findViewById(R.id.adminRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        Adapter adapter = new Adapter();
        rv.setAdapter(adapter);

        FirebaseFirestore.getInstance().collection("users").whereEqualTo("isExpert", true).get()
                .addOnSuccessListener(q -> {
                    progress.setVisibility(View.GONE);
                    experts.addAll(q.getDocuments());
                    Collections.sort(experts, (a, b) -> {
                        int c = Double.compare(ratingOf(b), ratingOf(a));
                        return c != 0 ? c : Long.compare(countOf(b), countOf(a));
                    });
                    adapter.notifyDataSetChanged();
                    empty.setVisibility(experts.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load experts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private static double ratingOf(DocumentSnapshot d) {
        Double r = d.getDouble("rating");
        return r != null ? r : 0.0;
    }

    private static long countOf(DocumentSnapshot d) {
        Long c = d.getLong("ratingCount");
        return c != null ? c : 0;
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_expert, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            DocumentSnapshot doc = experts.get(position);
            String name = doc.getString("name");
            String email = doc.getString("email");
            h.name.setText(name != null && !name.isEmpty() ? name : "Expert");
            h.email.setText(email != null ? email : "");
            h.rating.setText(String.format(Locale.US, "★ %.1f  (%d ratings)", ratingOf(doc), countOf(doc)));
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(AdminRatingsActivity.this, AdminExpertRatingsActivity.class);
                i.putExtra("expertUid", doc.getId());
                i.putExtra("expertName", name != null ? name : "Expert");
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return experts.size(); }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView name, email, rating;
        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.adminExpertName);
            email = v.findViewById(R.id.adminExpertEmail);
            rating = v.findViewById(R.id.adminExpertRating);
        }
    }
}
