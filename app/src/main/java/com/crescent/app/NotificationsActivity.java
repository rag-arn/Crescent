package com.crescent.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout container;
    private View clearButton;
    private FirebaseFirestore db;
    private String uid;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        container = findViewById(R.id.notificationsContainer);
        clearButton = findViewById(R.id.clearButton);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            showEmpty();
            return;
        }

        uid = user.getUid();
        db = FirebaseFirestore.getInstance();
        clearButton.setOnClickListener(v -> confirmClear());
        load();
    }

    private void showEmpty() {
        container.removeAllViews();
        clearButton.setVisibility(View.GONE);
        TextView t = new TextView(this);
        t.setText("No notifications yet.");
        t.setTextColor(ContextCompat.getColor(this, R.color.sage_text_soft));
        container.addView(t);
    }

    private void load() {
        db.collection("users").document(uid)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get().addOnSuccessListener(query -> {
                    if (isFinishing() || isDestroyed()) return;
                    container.removeAllViews();
                    if (query.isEmpty()) {
                        showEmpty();
                        return;
                    }
                    clearButton.setVisibility(View.VISIBLE);

                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                    float density = getResources().getDisplayMetrics().density;
                    WriteBatch batch = db.batch();

                    for (QueryDocumentSnapshot doc : query) {
                        if (Boolean.FALSE.equals(doc.getBoolean("read"))) {
                            batch.update(doc.getReference(), "read", true);
                        }

                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setBackgroundResource(R.drawable.bg_dash_card);
                        card.setPadding(40, 40, 40, 40);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                        lp.setMargins(0, 0, 0, 24);
                        card.setLayoutParams(lp);

                        // Title on the left, small date on the right
                        LinearLayout titleRow = new LinearLayout(this);
                        titleRow.setOrientation(LinearLayout.HORIZONTAL);
                        titleRow.setGravity(Gravity.CENTER_VERTICAL);

                        TextView title = new TextView(this);
                        title.setText(doc.getString("title"));
                        title.setTypeface(null, Typeface.BOLD);
                        title.setTextSize(16);
                        title.setTextColor(ContextCompat.getColor(this, R.color.sage_text));
                        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

                        Long ts = doc.getLong("timestamp");
                        if (ts != null) {
                            TextView date = new TextView(this);
                            date.setText(dateFormat.format(new Date(ts)));
                            date.setTextSize(11);
                            date.setMaxLines(1);
                            date.setTextColor(ContextCompat.getColor(this, R.color.sage_text_soft));
                            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
                            dlp.setMarginStart(Math.round(12 * density));
                            titleRow.addView(date, dlp);
                        }

                        TextView msg = new TextView(this);
                        msg.setText(doc.getString("message"));
                        msg.setTextSize(14);
                        msg.setTextColor(ContextCompat.getColor(this, R.color.sage_text_soft));
                        msg.setPadding(0, 10, 0, 0);

                        card.addView(titleRow);
                        card.addView(msg);
                        container.addView(card);
                    }
                    batch.commit();
                }).addOnFailureListener(e -> Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show());
    }

    private void confirmClear() {
        new ThemedDialog(this)
                .title("Clear notifications")
                .message("Delete all notifications? This cannot be undone.")
                .negative("Cancel", null)
                .positive("Clear", d -> {
                    d.positiveButton().setEnabled(false);
                    clearAll(d);
                })
                .show();
    }

    /** Deletes every notification (in batches of 400, well under Firestore's 500 limit). */
    private void clearAll(ThemedDialog dialog) {
        db.collection("users").document(uid).collection("notifications").get()
                .addOnSuccessListener(query -> {
                    List<Task<Void>> commits = new ArrayList<>();
                    WriteBatch batch = db.batch();
                    int count = 0;
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        batch.delete(doc.getReference());
                        if (++count == 400) {
                            commits.add(batch.commit());
                            batch = db.batch();
                            count = 0;
                        }
                    }
                    if (count > 0) commits.add(batch.commit());

                    Tasks.whenAllComplete(commits).addOnSuccessListener(done -> {
                        boolean ok = true;
                        for (Task<?> t : done) if (!t.isSuccessful()) ok = false;
                        dialog.dismiss();
                        if (isFinishing() || isDestroyed()) return;
                        if (ok) {
                            showEmpty();
                            Toast.makeText(this, "Notifications cleared", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Could not clear all notifications", Toast.LENGTH_SHORT).show();
                            load();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Could not clear notifications. Check your connection.", Toast.LENGTH_SHORT).show();
                });
    }
}