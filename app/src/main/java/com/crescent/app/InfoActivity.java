package com.crescent.app;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
public class InfoActivity extends AppCompatActivity {
 @Override protected void onCreate(Bundle state) { super.onCreate(state); setContentView(R.layout.activity_info); Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null); findViewById(R.id.backButton).setOnClickListener(v -> finish()); }
}