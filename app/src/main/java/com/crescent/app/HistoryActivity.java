package com.crescent.app;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_history);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        TextView out=findViewById(R.id.historyText);
        new CrescentRepository(this).snapshots(items -> runOnUiThread(() -> {
            if (items.isEmpty()) { out.setText("No snapshots saved yet. Complete a Zakat calculation to save one."); return; }
            StringBuilder s=new StringBuilder();
            SimpleDateFormat df=new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            NumberFormat nf=NumberFormat.getNumberInstance(new Locale("en","BD"));
            for (Snapshot x:items) s.append("\n").append(df.format(new Date(x.createdAt))).append(" (").append(x.year).append(")\n")
                    .append("Net wealth: ৳").append(nf.format(x.netWealth)).append("\nNisab: ৳").append(nf.format(x.nisab))
                    .append("\nEstimated Zakat: ৳").append(nf.format(x.zakat)).append("\n──────────────\n");
            out.setText(s.toString());
        }));
    }
}