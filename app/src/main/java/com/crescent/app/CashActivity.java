package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.crescent.app.databinding.ActivityCashBinding;

/** Step 3: cash in hand, in bank, payable (deducted) and receivable (added). Next: Commercial goods. */
public class CashActivity extends AppCompatActivity {
    private ActivityCashBinding b;

    private static double number(EditText e) {
        String s = e.getText().toString().trim().replace(",", "");
        if (s.isEmpty()) return 0;
        try {
            double d = Double.parseDouble(s);
            return (Double.isNaN(d) || Double.isInfinite(d)) ? 0 : Math.max(0, d);
        } catch (Exception ex) { return 0; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityCashBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.insets(this, b.getRoot(), b.headerBar, b.bottomBar);
        b.infoButton.setOnClickListener(v -> InfoDialog.show(this, "About cash",
                "Enter all amounts in Bangladeshi Taka (৳).\n\n"
                        + "Cash payable is money you owe; it is deducted from your total assets.\n"
                        + "Cash receivable is money owed to you that you expect to receive; it is added to your assets."));
        b.previousButton.setOnClickListener(v -> finish());
        b.nextButton.setOnClickListener(v -> {
            Intent i = new Intent(this, CommercialActivity.class);
            Bundle carried = getIntent().getExtras();
            if (carried != null) i.putExtras(carried);
            i.putExtra("cash_hand", number(b.cashHandInput));
            i.putExtra("cash_bank", number(b.cashBankInput));
            i.putExtra("cash_payable", number(b.cashPayableInput));
            i.putExtra("cash_receivable", number(b.cashReceivableInput));
            startActivity(i);
        });
    }
}