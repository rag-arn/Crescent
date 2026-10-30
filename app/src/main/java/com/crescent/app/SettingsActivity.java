package com.crescent.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Redirects to Email App
        findViewById(R.id.reportBtn).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:arnob4669@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Crescent App Feedback/Report");
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No email app found on your device.", Toast.LENGTH_SHORT).show();
            }
        });

        // Redirects to LinkedIn
        findViewById(R.id.contactBtn).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/arnob4669"));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open browser.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}