package com.crescent.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
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

        View formLayout = findViewById(R.id.formLayout);
        View alreadyExpertLayout = findViewById(R.id.alreadyExpertLayout);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (user != null) {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
                if (!doc.exists()) return;
                if (Boolean.TRUE.equals(doc.getBoolean("isExpert"))) {
                    formLayout.setVisibility(View.GONE);
                    alreadyExpertLayout.setVisibility(View.VISIBLE);
                } else if ("pending".equals(doc.getString("expertStatus"))) {
                    // Applied already: waiting for the admin
                    formLayout.setVisibility(View.GONE);
                    alreadyExpertLayout.setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.alreadyTitle)).setText("Application under review");
                    ((TextView) findViewById(R.id.alreadyMessage)).setText(
                            "Your application was sent to the admin. You will get a notification as soon as it is approved.");
                }
            });
        }

        EditText bioInput = findViewById(R.id.bioInput);
        Button submitBtn = findViewById(R.id.submitBtn);

        submitBtn.setOnClickListener(v -> {
            String bio = bioInput.getText().toString().trim();
            if (bio.isEmpty()) {
                Toast.makeText(this, "Please enter your qualifications", Toast.LENGTH_SHORT).show();
                return;
            }
            if (user == null || user.isAnonymous()) {
                Toast.makeText(this, "Guests cannot become experts. Please sign up.", Toast.LENGTH_LONG).show();
                return;
            }

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

                String uid = user.getUid();
                Map<String, Object> data = new HashMap<>();
                data.put("expertStatus", "pending");      // not an expert until the admin approves
                data.put("bio", bio);
                data.put("expertRequestedAt", System.currentTimeMillis());

                db.collection("users").document(uid).set(data, SetOptions.merge()).addOnSuccessListener(a -> {
                    Toast.makeText(this, "Application sent. The admin will review it.", Toast.LENGTH_LONG).show();
                    finish();
                }).addOnFailureListener(e -> {
                    submitBtn.setEnabled(true);
                    submitBtn.setText("Register as Expert");
                });
            });
            dialog.show();
        });
    }
}