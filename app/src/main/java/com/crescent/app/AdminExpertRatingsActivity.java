package com.crescent.app;

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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Every rating one expert has received in the current rating period, with the person who gave it. */
public class AdminExpertRatingsActivity extends AppCompatActivity {

    /** One row: who rated and how. stars = 0 means an old rating whose stars were not recorded. */
    private static class Row {
        String name, email;
        int stars;
        long time;
    }

    private final List<Row> rows = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Admin.isAdmin(this, FirebaseAuth.getInstance().getCurrentUser())) { finish(); return; }

        final String expertUid = getIntent().getStringExtra("expertUid");
        final String expertName = getIntent().getStringExtra("expertName");
        if (expertUid == null) { finish(); return; }

        setContentView(R.layout.activity_admin_list);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.adminTitle)).setText(expertName != null ? expertName : "Expert");
        TextView subtitle = findViewById(R.id.adminSubtitle);
        subtitle.setVisibility(View.VISIBLE);

        View progress = findViewById(R.id.adminProgress);
        TextView empty = findViewById(R.id.adminEmpty);
        empty.setText("Nobody has rated this expert yet.");

        RecyclerView rv = findViewById(R.id.adminRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        Adapter adapter = new Adapter();
        rv.setAdapter(adapter);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(expertUid).get().addOnSuccessListener(expert -> {
            Double avg = expert.getDouble("rating");
            Long count = expert.getLong("ratingCount");
            Long ep = expert.getLong("ratingEpoch");
            final long epoch = ep != null ? ep : 0L;
            subtitle.setText(String.format(Locale.US, "★ %.1f  (%d ratings)", avg != null ? avg : 0.0, count != null ? count : 0));

            // New-style records (with stars) and old records (only "has rated")
            Task<QuerySnapshot> detailed = db.collection("users")
                    .whereGreaterThan(FieldPath.of("ratingsGiven", expertUid, "stars"), 0).get();
            Task<QuerySnapshot> legacy = db.collection("users")
                    .whereEqualTo(FieldPath.of("ratedExperts", expertUid), true).get();

            Tasks.whenAllComplete(detailed, legacy).addOnSuccessListener(done -> {
                progress.setVisibility(View.GONE);
                Set<String> seen = new HashSet<>();

                if (detailed.isSuccessful()) {
                    for (DocumentSnapshot d : detailed.getResult().getDocuments()) {
                        Object all = d.get("ratingsGiven");
                        if (!(all instanceof Map)) continue;
                        Object mine = ((Map<?, ?>) all).get(expertUid);
                        if (!(mine instanceof Map)) continue;
                        Map<?, ?> m = (Map<?, ?>) mine;
                        Object e = m.get("epoch");
                        long rowEpoch = e instanceof Number ? ((Number) e).longValue() : 0L;
                        if (rowEpoch != epoch) continue;                  // belongs to an earlier rating period
                        Object s = m.get("stars");
                        Object t = m.get("timestamp");
                        addRow(d, s instanceof Number ? Math.round(((Number) s).floatValue()) : 0,
                                t instanceof Number ? ((Number) t).longValue() : 0L);
                        seen.add(d.getId());
                    }
                }
                if (legacy.isSuccessful() && epoch == 0L) {
                    for (DocumentSnapshot d : legacy.getResult().getDocuments()) {
                        if (seen.add(d.getId())) addRow(d, 0, 0L);
                    }
                }

                // newest first; old records without a date go last
                Collections.sort(rows, (a, b) -> Long.compare(b.time, a.time));
                adapter.notifyDataSetChanged();
                empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                if (!detailed.isSuccessful()) {
                    Toast.makeText(this, "Could not load all ratings.", Toast.LENGTH_SHORT).show();
                }
            });
        }).addOnFailureListener(e -> {
            progress.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void addRow(DocumentSnapshot user, int stars, long time) {
        Row r = new Row();
        String n = user.getString("name");
        r.name = n != null && !n.isEmpty() ? n : "Unnamed user";
        String e = user.getString("email");
        r.email = e != null ? e : "";
        r.stars = stars;
        r.time = time;
        rows.add(r);
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_rater, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            Row r = rows.get(position);
            h.name.setText(r.name);
            h.email.setText(r.email);
            if (r.stars > 0) {
                StringBuilder s = new StringBuilder();
                for (int i = 1; i <= 5; i++) s.append(i <= r.stars ? "\u2605" : "\u2606");
                h.stars.setText(s + "  " + r.stars);
            } else {
                h.stars.setText("Stars not recorded");
                h.stars.setTextSize(12);
            }
            h.date.setText(r.time > 0 ? fmt.format(new Date(r.time)) : "Rated before this feature was added");
        }

        @Override public int getItemCount() { return rows.size(); }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView name, email, stars, date;
        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.raterName);
            email = v.findViewById(R.id.raterEmail);
            stars = v.findViewById(R.id.raterStars);
            date = v.findViewById(R.id.raterDate);
        }
    }
}
