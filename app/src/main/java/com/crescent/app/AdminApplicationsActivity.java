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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lists people who applied to become experts. The admin approves or rejects each one. */
public class AdminApplicationsActivity extends AppCompatActivity {

    private final List<DocumentSnapshot> items = new ArrayList<>();
    private Adapter adapter;
    private View progress;
    private TextView emptyText;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Admin.isAdmin(this, FirebaseAuth.getInstance().getCurrentUser())) { finish(); return; }

        setContentView(R.layout.activity_admin_list);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.adminTitle)).setText("Expert Applications");

        progress = findViewById(R.id.adminProgress);
        emptyText = findViewById(R.id.adminEmpty);
        emptyText.setText("No pending applications.");

        RecyclerView rv = findViewById(R.id.adminRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);

        FirebaseFirestore.getInstance().collection("users").whereEqualTo("expertStatus", "pending").get()
                .addOnSuccessListener(q -> {
                    progress.setVisibility(View.GONE);
                    items.addAll(q.getDocuments());
                    // oldest application first
                    Collections.sort(items, (a, b) -> Long.compare(requestedAt(a), requestedAt(b)));
                    adapter.notifyDataSetChanged();
                    updateEmpty();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load applications: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private static long requestedAt(DocumentSnapshot d) {
        Long t = d.getLong("expertRequestedAt");
        return t != null ? t : 0L;
    }

    private void updateEmpty() {
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDecision(DocumentSnapshot doc, boolean approve) {
        String name = doc.getString("name") != null ? doc.getString("name") : "this user";
        new ThemedDialog(this)
                .title(approve ? "Approve expert" : "Reject application")
                .message(approve
                        ? "Approve " + name + " as a Zakat expert? They will appear in the expert directory."
                        : "Reject " + name + "'s application? They will be told and can apply again.")
                .negative("Cancel", null)
                .positive(approve ? "Approve" : "Reject", d -> {
                    d.positiveButton().setEnabled(false);
                    decide(doc, approve, d);
                })
                .show();
    }

    private void decide(DocumentSnapshot doc, boolean approve, ThemedDialog dialog) {
        long now = System.currentTimeMillis();
        Map<String, Object> update = new HashMap<>();
        if (approve) {
            update.put("isExpert", true);
            update.put("expertStatus", "approved");
            update.put("rating", 0.0);              // every expert starts with a fresh rating
            update.put("ratingCount", 0);
            update.put("ratingEpoch", now);         // new rating period: everybody may rate once
            update.put("expertApprovedAt", now);
        } else {
            update.put("isExpert", false);
            update.put("expertStatus", "rejected");
        }

        doc.getReference().update(update).addOnSuccessListener(a -> {
            Map<String, Object> notif = new HashMap<>();
            notif.put("title", approve ? "Expert application approved" : "Expert application not approved");
            notif.put("message", approve
                    ? "Congratulations! You are now a Zakat expert. Users can now view your profile and message you."
                    : "Your application to become an expert was not approved. You can update your qualifications and apply again.");
            notif.put("type", approve ? "expert_approved" : "expert_rejected");
            notif.put("timestamp", now);
            notif.put("read", false);
            doc.getReference().collection("notifications").add(notif);

            int index = items.indexOf(doc);
            if (index >= 0) {
                items.remove(index);
                adapter.notifyItemRemoved(index);
            }
            dialog.dismiss();
            updateEmpty();
            Toast.makeText(this, approve ? "Approved" : "Rejected", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            dialog.dismiss();
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_application, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            DocumentSnapshot doc = items.get(position);
            String name = doc.getString("name");
            String email = doc.getString("email");
            String bio = doc.getString("bio");
            long t = requestedAt(doc);

            h.name.setText(name != null && !name.isEmpty() ? name : "Unnamed user");
            h.email.setText(email != null ? email : "");
            h.bio.setText(bio != null && !bio.isEmpty() ? bio : "(no qualifications written)");
            h.date.setText(t > 0 ? fmt.format(new Date(t)) : "");
            h.approve.setOnClickListener(v -> confirmDecision(doc, true));
            h.reject.setOnClickListener(v -> confirmDecision(doc, false));
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView name, email, bio, date;
        final View approve, reject;
        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.appName);
            email = v.findViewById(R.id.appEmail);
            bio = v.findViewById(R.id.appBio);
            date = v.findViewById(R.id.appDate);
            approve = v.findViewById(R.id.approveBtn);
            reject = v.findViewById(R.id.rejectBtn);
        }
    }
}
