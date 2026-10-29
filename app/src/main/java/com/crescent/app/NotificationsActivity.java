package com.crescent.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class NotificationsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        LinearLayout container = findViewById(R.id.notificationsContainer);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            TextView t = new TextView(this);
            t.setText("No notifications yet.");
            container.addView(t);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get().addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        TextView t = new TextView(this);
                        t.setText("No notifications yet.");
                        t.setTextColor(ContextCompat.getColor(this, R.color.sage_text_soft));
                        container.addView(t);
                        return;
                    }
                    for (QueryDocumentSnapshot doc : query) {
                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setBackgroundResource(R.drawable.bg_dash_card);
                        card.setPadding(40, 40, 40, 40);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                        lp.setMargins(0, 0, 0, 24);
                        card.setLayoutParams(lp);

                        TextView title = new TextView(this);
                        title.setText(doc.getString("title"));
                        title.setTypeface(null, Typeface.BOLD);
                        title.setTextSize(16);
                        title.setTextColor(ContextCompat.getColor(this, R.color.sage_text));

                        TextView msg = new TextView(this);
                        msg.setText(doc.getString("message"));
                        msg.setTextSize(14);
                        msg.setTextColor(ContextCompat.getColor(this, R.color.sage_text_soft));
                        msg.setPadding(0, 10, 0, 0);

                        card.addView(title);
                        card.addView(msg);
                        container.addView(card);
                    }
                }).addOnFailureListener(e -> Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show());
    }
}