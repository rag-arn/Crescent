package com.crescent.app;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sends one notification (title + description) to every registered user. */
public class AdminNotifyActivity extends AppCompatActivity {

    private EditText titleInput, messageInput;
    private View sendBtn, sendProgress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Admin.isAdmin(this, FirebaseAuth.getInstance().getCurrentUser())) { finish(); return; }

        setContentView(R.layout.activity_admin_notify);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        titleInput = findViewById(R.id.notifTitleInput);
        messageInput = findViewById(R.id.notifMessageInput);
        sendBtn = findViewById(R.id.sendNotifBtn);
        sendProgress = findViewById(R.id.sendProgress);

        sendBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String message = messageInput.getText().toString().trim();
            if (title.isEmpty()) { titleInput.setError("Enter a title"); return; }
            if (message.isEmpty()) { messageInput.setError("Enter a description"); return; }

            new ThemedDialog(this)
                    .title("Send to all users")
                    .message("Send this notification to every registered user?\n\n" + title + "\n" + message)
                    .negative("Cancel", null)
                    .positive("Send", d -> {
                        d.dismiss();
                        send(title, message);
                    })
                    .show();
        });
    }

    private void setSending(boolean sending) {
        sendBtn.setEnabled(!sending);
        sendProgress.setVisibility(sending ? View.VISIBLE : View.GONE);
    }

    private void send(String title, String message) {
        setSending(true);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        final String adminUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        db.collection("users").get().addOnSuccessListener(users -> {
            long now = System.currentTimeMillis();
            List<Task<Void>> commits = new ArrayList<>();
            WriteBatch batch = db.batch();
            int inBatch = 0, total = 0;

            for (DocumentSnapshot u : users.getDocuments()) {
                String email = u.getString("email");
                if (email == null || email.isEmpty()) continue;               // guests have no notifications screen
                if (u.getId().equals(adminUid)) continue;                      // not to the admin himself

                Map<String, Object> notif = new HashMap<>();
                notif.put("title", title);
                notif.put("message", message);
                notif.put("type", "admin");
                notif.put("timestamp", now);
                notif.put("read", false);
                batch.set(u.getReference().collection("notifications").document(), notif);
                total++;

                if (++inBatch == 400) {                                       // Firestore allows 500 writes per batch
                    commits.add(batch.commit());
                    batch = db.batch();
                    inBatch = 0;
                }
            }
            if (inBatch > 0) commits.add(batch.commit());

            final int sent = total;
            if (commits.isEmpty()) {
                setSending(false);
                Toast.makeText(this, "There are no registered users to notify.", Toast.LENGTH_LONG).show();
                return;
            }
            Tasks.whenAllComplete(commits).addOnSuccessListener(done -> {
                boolean ok = true;
                for (Task<?> t : done) if (!t.isSuccessful()) ok = false;
                setSending(false);
                if (ok) {
                    titleInput.setText("");
                    messageInput.setText("");
                    Toast.makeText(this, "Sent to " + sent + " users", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Some notifications could not be sent. Check your Firestore rules.", Toast.LENGTH_LONG).show();
                }
            });
        }).addOnFailureListener(e -> {
            setSending(false);
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
