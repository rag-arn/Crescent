package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Every registered user, with search. Tap one to disable the account or delete the user's data. */
public class AdminUsersActivity extends AppCompatActivity {

    private final List<DocumentSnapshot> all = new ArrayList<>();
    private final List<DocumentSnapshot> shown = new ArrayList<>();
    private Adapter adapter;
    private View progress;
    private TextView emptyText;
    private EditText search;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Admin.isAdmin(this, FirebaseAuth.getInstance().getCurrentUser())) { finish(); return; }

        setContentView(R.layout.activity_admin_list);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.adminTitle)).setText("Manage Users");

        progress = findViewById(R.id.adminProgress);
        emptyText = findViewById(R.id.adminEmpty);
        emptyText.setText("No users found.");

        search = findViewById(R.id.adminSearch);
        search.setVisibility(View.VISIBLE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        RecyclerView rv = findViewById(R.id.adminRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
    }

    @Override protected void onResume() {
        super.onResume();
        load();     // reload so a change made on the detail screen shows up
    }

    private void load() {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        final String myUid = me != null ? me.getUid() : "";
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(q -> {
                    progress.setVisibility(View.GONE);
                    all.clear();
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String email = d.getString("email");
                        if (email == null || email.isEmpty()) continue;     // guests
                        if (d.getId().equals(myUid)) continue;              // not the admin himself
                        all.add(d);
                    }
                    Collections.sort(all, (a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load users: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private static String nameOf(DocumentSnapshot d) {
        String n = d.getString("name");
        return n != null && !n.isEmpty() ? n : "Unnamed user";
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (DocumentSnapshot d : all) {
            String email = d.getString("email") != null ? d.getString("email") : "";
            if (q.isEmpty() || nameOf(d).toLowerCase(Locale.ROOT).contains(q) || email.toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(d);
            }
        }
        adapter.notifyDataSetChanged();
        emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            DocumentSnapshot d = shown.get(position);
            h.name.setText(nameOf(d));
            h.email.setText(d.getString("email"));

            boolean disabled = Boolean.TRUE.equals(d.getBoolean("disabled"));
            StringBuilder s = new StringBuilder();
            if (disabled) s.append("Disabled");
            if (Boolean.TRUE.equals(d.getBoolean("isExpert"))) s.append(s.length() > 0 ? "  •  " : "").append("Expert");
            else if ("pending".equals(d.getString("expertStatus"))) s.append(s.length() > 0 ? "  •  " : "").append("Application pending");
            h.status.setText(s.length() > 0 ? s.toString() : "Active");
            h.status.setTextColor(disabled ? 0xFFE53935 : getColor(R.color.sage_text_soft));

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(AdminUsersActivity.this, AdminUserDetailActivity.class);
                i.putExtra("uid", d.getId());
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return shown.size(); }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView name, email, status;
        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.userItemName);
            email = v.findViewById(R.id.userItemEmail);
            status = v.findViewById(R.id.userItemStatus);
        }
    }
}
