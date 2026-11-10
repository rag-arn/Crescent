package com.crescent.app;

import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private final SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "BD"));

    private CrescentRepository repo;
    private HistoryAdapter adapter;
    private View emptyCard, hint;
    private RecyclerView list;
    private int revealPx;   // width of the delete action revealed by the swipe

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
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        repo = new CrescentRepository(this);
        revealPx = dp(80);
        emptyCard = findViewById(R.id.emptyCard);
        hint = findViewById(R.id.historyHint);

        list = findViewById(R.id.historyList);
        list.setLayoutManager(new LinearLayoutManager(this));
        RecyclerView.ItemAnimator animator = list.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        adapter = new HistoryAdapter();
        list.setAdapter(adapter);
        new ItemTouchHelper(new SwipeToRevealCallback()).attachToRecyclerView(list);

        loadHistory();
    }

    private void loadHistory() {
        repo.snapshots(items -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            adapter.setItems(items == null ? new ArrayList<>() : items);
            updateEmptyState();
        }));
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        hint.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyCard.setVisibility(empty ? View.VISIBLE : View.GONE);
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

    // ---------------- Delete ----------------

    private void confirmDelete(Snapshot s) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete this calculation?")
                .setMessage("The calculation from " + df.format(new Date(s.createdAt))
                        + " (Zakat " + taka(s.zakat) + ") will be permanently deleted from this device and from "
                        + "the cloud. This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteSnapshot(s))
                .setNegativeButton("Cancel", (d, w) -> adapter.closeAll())
                .setOnCancelListener(d -> adapter.closeAll())
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFC9605A);
    }

    private void deleteSnapshot(Snapshot s) {
        int pos = adapter.indexOf(s.createdAt);
        if (pos >= 0) adapter.removeAt(pos);
        updateEmptyState();

        // The Hawl (lunar-year countdown) belongs to the latest calculation. If nothing eligible is left,
        // stop the countdown right now: local state, reminder alarm, cloud flag and the home-screen widget.
        if (!hasEligibleLatest()) HawlTracker.onCalculation(getApplicationContext(), false);

        // Removes it from Firestore (and the local copy). If the phone is offline, Firestore keeps the
        // delete queued and completes it as soon as the connection returns.
        repo.deleteSnapshot(s, ok -> {
            if (ok) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, "Could not delete this calculation. Please try again.", Toast.LENGTH_LONG).show();
                loadHistory();
            });
        });
    }

    /** True when the newest remaining calculation has wealth at or above the Nisab (Zakat due). */
    private boolean hasEligibleLatest() {
        Snapshot latest = adapter.latest();
        return latest != null && latest.nisab > 0 && latest.netWealth >= latest.nisab;
    }

    // ---------------- Adapter ----------------

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private final List<Snapshot> items = new ArrayList<>();
        private long openId = -1;   // createdAt of the card whose delete action is showing

        HistoryAdapter() { setHasStableIds(true); }

        class Holder extends RecyclerView.ViewHolder {
            final View front, back;
            final ImageButton delete;
            final TextView date, assets;
            Holder(View v) {
                super(v);
                front = v.findViewById(R.id.historyFront);
                back = v.findViewById(R.id.historyBack);
                delete = v.findViewById(R.id.historyDelete);
                date = v.findViewById(R.id.historyDate);
                assets = v.findViewById(R.id.historyAssets);
            }
        }

        void setItems(List<Snapshot> newItems) {
            items.clear();
            items.addAll(newItems);
            openId = -1;
            notifyDataSetChanged();
        }

        Snapshot latest() { return items.isEmpty() ? null : items.get(0); }   // list is newest first

        int indexOf(long createdAt) {
            for (int i = 0; i < items.size(); i++) if (items.get(i).createdAt == createdAt) return i;
            return -1;
        }

        boolean isOpen(Holder h) {
            int p = h.getAdapterPosition();
            return p != RecyclerView.NO_POSITION && items.get(p).createdAt == openId;
        }

        void open(int pos) {
            int prev = indexOf(openId);
            openId = items.get(pos).createdAt;
            notifyItemChanged(pos);
            if (prev >= 0 && prev != pos) notifyItemChanged(prev);
        }

        void closeAll() {
            int p = indexOf(openId);
            openId = -1;
            if (p >= 0) notifyItemChanged(p);
        }

        void removeAt(int pos) {
            items.remove(pos);
            openId = -1;
            notifyItemRemoved(pos);
        }

        @Override public long getItemId(int position) { return items.get(position).createdAt; }
        @Override public int getItemCount() { return items.size(); }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Snapshot s = items.get(position);
            boolean open = s.createdAt == openId;

            h.date.setText(df.format(new Date(s.createdAt)));
            h.assets.setText("Total assets: " + taka(s.assets));
            h.front.setTranslationX(open ? -revealPx : 0f);
            h.back.setVisibility(open ? View.VISIBLE : View.INVISIBLE);

            h.front.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                if (openId != -1) closeAll();           // first tap just closes the delete action
                else openSnapshotDetail(items.get(p));
            });
            h.delete.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) confirmDelete(items.get(p));
            });
        }
    }

    // ---------------- Swipe: card slides left and reveals the delete icon ----------------

    private class SwipeToRevealCallback extends ItemTouchHelper.SimpleCallback {
        SwipeToRevealCallback() { super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT); }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            // Closed card: swipe left to reveal. Open card: swipe right to hide again.
            return adapter.isOpen((HistoryAdapter.Holder) vh) ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT;
        }

        @Override
        public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder vh) {
            int w = Math.max(1, vh.itemView.getWidth());
            return Math.min(0.5f, (revealPx * 0.5f) / w);
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            int pos = vh.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (direction == ItemTouchHelper.LEFT) adapter.open(pos);
            else adapter.closeAll();
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) return;
            HistoryAdapter.Holder h = (HistoryAdapter.Holder) vh;
            float base = adapter.isOpen(h) ? -revealPx : 0f;
            float tx = Math.max(-revealPx, Math.min(0f, base + dX));
            h.front.setTranslationX(tx);                       // only the card moves; the delete icon stays put
            h.back.setVisibility(tx < 0f ? View.VISIBLE : View.INVISIBLE);
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            HistoryAdapter.Holder h = (HistoryAdapter.Holder) vh;
            boolean open = adapter.isOpen(h);
            h.front.setTranslationX(open ? -revealPx : 0f);
            h.back.setVisibility(open ? View.VISIBLE : View.INVISIBLE);
        }
    }
}