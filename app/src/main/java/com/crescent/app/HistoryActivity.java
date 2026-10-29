package com.crescent.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private final SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));

    private String taka(double v) {
        return "৳" + nf.format(Math.round(v));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_history);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        ListView listView = findViewById(R.id.historyList);
        View emptyCard = findViewById(R.id.emptyCard);

        new CrescentRepository(this).snapshots(items -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (items == null || items.isEmpty()) {
                listView.setVisibility(View.GONE);
                emptyCard.setVisibility(View.VISIBLE);
                return;
            }
            emptyCard.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            listView.setAdapter(new SnapshotAdapter(items));
            listView.setOnItemClickListener((parent, view, position, id) -> openSnapshotDetail(items.get(position)));
        }));
    }

    private void openSnapshotDetail(Snapshot s) {
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra("from_history", true);
        i.putExtra("snapshot_created_at", s.createdAt);
        i.putExtra("snapshot_assets", s.assets);
        i.putExtra("snapshot_liabilities", s.liabilities);
        i.putExtra("snapshot_net_wealth", s.netWealth);
        i.putExtra("snapshot_nisab", s.nisab);
        i.putExtra("snapshot_zakat", s.zakat);
        i.putExtra("snapshot_breakdown", s.breakdown);
        startActivity(i);
    }

    private static class ViewHolder {
        TextView dateText;
        TextView assetText;
    }

    private class SnapshotAdapter extends BaseAdapter {
        private final List<Snapshot> items;

        SnapshotAdapter(List<Snapshot> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Snapshot getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(HistoryActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setBackgroundResource(R.drawable.bg_asset_card);
                card.setPadding(dp(16), dp(14), dp(16), dp(14));

                TypedValue outValue = new TypedValue();
                if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                    card.setForeground(ContextCompat.getDrawable(HistoryActivity.this, outValue.resourceId));
                }

                FrameLayout iconBox = new FrameLayout(HistoryActivity.this);
                iconBox.setBackgroundResource(R.drawable.bg_icon_box);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
                iconLp.setMarginEnd(dp(14));
                card.addView(iconBox, iconLp);

                ImageView icon = new ImageView(HistoryActivity.this);
                icon.setImageResource(R.drawable.ic_history);
                icon.setColorFilter(ContextCompat.getColor(HistoryActivity.this, R.color.sage_button));
                FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER);
                iconBox.addView(icon, imgLp);

                LinearLayout textCol = new LinearLayout(HistoryActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                card.addView(textCol, colLp);

                TextView dateTv = new TextView(HistoryActivity.this);
                dateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                dateTv.setTypeface(dateTv.getTypeface(), Typeface.BOLD);
                dateTv.setTextColor(ContextCompat.getColor(HistoryActivity.this, R.color.sage_text));
                textCol.addView(dateTv);

                TextView assetTv = new TextView(HistoryActivity.this);
                assetTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                assetTv.setTextColor(ContextCompat.getColor(HistoryActivity.this, R.color.sage_text_soft));
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                subLp.topMargin = dp(4);
                textCol.addView(assetTv, subLp);

                holder = new ViewHolder();
                holder.dateText = dateTv;
                holder.assetText = assetTv;
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Snapshot s = getItem(position);
            holder.dateText.setText(df.format(new Date(s.createdAt)));
            holder.assetText.setText("Total assets: " + taka(s.assets));
            return convertView;
        }
    }
}