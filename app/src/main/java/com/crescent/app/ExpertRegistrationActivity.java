package com.crescent.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public class ExpertRegistrationActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expert_registration);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        EditText bioInput = findViewById(R.id.bioInput);
        Button submitBtn = findViewById(R.id.submitBtn);

        submitBtn.setOnClickListener(v -> {
            String bio = bioInput.getText().toString().trim();
            if (bio.isEmpty()) {
                Toast.makeText(this, "Please enter your qualifications", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null || user.isAnonymous()) {
                Toast.makeText(this, "Guests cannot become experts. Please sign up with an email.", Toast.LENGTH_LONG).show();
                return;
            }

            // Show Confirmation Dialog
            Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_expert_confirm);

            Window w = dialog.getWindow();
            if (w != null) {
                int margin = Math.round(24 * getResources().getDisplayMetrics().density);
                w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margin));
                w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            CheckBox cb = dialog.findViewById(R.id.understandCheckbox);
            Button cancel = dialog.findViewById(R.id.cancelBtn);
            Button confirm = dialog.findViewById(R.id.confirmBtn);

            cancel.setOnClickListener(view -> dialog.dismiss());
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> confirm.setEnabled(isChecked));

            confirm.setOnClickListener(view -> {
                dialog.dismiss();
                submitBtn.setEnabled(false);
                submitBtn.setText("Registering...");

                Toast.makeText(this, "Application submitted. You will be notified when listed.", Toast.LENGTH_LONG).show();

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                String uid = user.getUid();

                // 1. Save Expert Data
                Map<String, Object> data = new HashMap<>();
                data.put("isExpert", true);
                data.put("bio", bio);
                data.put("rating", 0.0); // Starts at 0, golden stars come from ratings!
                data.put("ratingCount", 0);

                db.collection("users").document(uid).set(data, SetOptions.merge()).addOnSuccessListener(a -> {

                    // 2. Generate Notification automatically
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("title", "Congratulations!");
                    notif.put("message", "You are now an expert of Zakat. Users can now view your profile and contact you.");
                    notif.put("timestamp", System.currentTimeMillis());

                    db.collection("users").document(uid).collection("notifications").add(notif);

                    finish();
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    submitBtn.setEnabled(true);
                    submitBtn.setText("Register as Expert");
                });
            });

            dialog.show();
        });
    }
}