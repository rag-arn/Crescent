package com.crescent.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrescentRepository {
    public interface Result<T> { void onResult(T value); }
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Context app;
    private final String uid;

    // One shared background thread for the local (Room) copy that ZakatDataProvider serves.
    private static final ExecutorService LOCAL = Executors.newSingleThreadExecutor();

    // On a weak connection a server read can stall for many seconds. After this long we show what is
    // already stored on the phone (which already reflects the user's own saves and deletes).
    private static final long SERVER_WAIT_MS = 2000;
    private final Handler main = new Handler(Looper.getMainLooper());

    public CrescentRepository(Context context) {
        app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = (user != null && !user.isAnonymous()) ? user.getUid() : null;
    }

    public void saveSnapshot(Snapshot snapshot) {
        if (uid == null) return;
        // Save to cloud. Offline? Firestore caches it instantly and uploads it when internet returns.
        db.collection("users").document(uid).collection("snapshots")
                .document(String.valueOf(snapshot.createdAt)).set(snapshot);

        // Also keep a local copy so the Content Provider can share it with other apps straight away.
        final Snapshot local = copyOf(snapshot);
        LOCAL.execute(() -> {
            try {
                CrescentDatabase.get(app).dao().insertSnapshot(local);
                app.getContentResolver().notifyChange(ZakatDataProvider.CONTENT_URI, null);
            } catch (Exception ignored) { /* the cloud copy is the source of truth */ }
        });
    }

    public void snapshots(Result<List<Snapshot>> result) {
        if (uid == null) { result.onResult(new ArrayList<>()); return; }
        final Query query = db.collection("users").document(uid).collection("snapshots")
                .orderBy("createdAt", Query.Direction.DESCENDING);
        final AtomicBoolean delivered = new AtomicBoolean(false);

        query.get().addOnSuccessListener(q -> {
            List<Snapshot> list = toList(q);
            mirrorLocally(list);
            if (delivered.compareAndSet(false, true)) result.onResult(list);
        }).addOnFailureListener(e -> {
            if (delivered.compareAndSet(false, true)) result.onResult(new ArrayList<>());
        });

        // Slow network: fall back to the on-device copy instead of keeping the screen waiting.
        main.postDelayed(() -> {
            if (delivered.get()) return;
            query.get(Source.CACHE).addOnSuccessListener(q -> {
                if (q.isEmpty()) return;                       // nothing cached yet: keep waiting for the server
                if (delivered.compareAndSet(false, true)) result.onResult(toList(q));
            });
        }, SERVER_WAIT_MS);
    }

    private static List<Snapshot> toList(QuerySnapshot q) {
        List<Snapshot> list = new ArrayList<>();
        for (QueryDocumentSnapshot doc : q) list.add(doc.toObject(Snapshot.class));
        return list;
    }

    /**
     * Permanently deletes one saved calculation from Firestore (and from the local copy that
     * ZakatDataProvider serves). The callback reports false only if the cloud rejects the delete.
     * Offline, Firestore keeps the delete queued and completes it when the connection returns.
     */
    public void deleteSnapshot(Snapshot snapshot, Result<Boolean> result) {
        if (uid == null) { result.onResult(false); return; }
        final long createdAt = snapshot.createdAt;

        LOCAL.execute(() -> {
            try {
                CrescentDatabase.get(app).dao().deleteSnapshotByCreatedAt(createdAt);
                app.getContentResolver().notifyChange(ZakatDataProvider.CONTENT_URI, null);
            } catch (Exception ignored) { }
        });

        db.collection("users").document(uid).collection("snapshots")
                .document(String.valueOf(createdAt)).delete()
                .addOnSuccessListener(v -> result.onResult(true))
                .addOnFailureListener(e -> result.onResult(false));
    }

    /** Replaces the local Room copy with the cloud history, so the provider always matches History. */
    private void mirrorLocally(List<Snapshot> cloud) {
        final List<Snapshot> copies = new ArrayList<>();
        for (Snapshot s : cloud) copies.add(copyOf(s));
        LOCAL.execute(() -> {
            try {
                final CrescentDatabase room = CrescentDatabase.get(app);
                room.runInTransaction(() -> {
                    room.dao().clearSnapshots();
                    room.dao().insertSnapshots(copies);
                });
                app.getContentResolver().notifyChange(ZakatDataProvider.CONTENT_URI, null);
            } catch (Exception ignored) { }
        });
    }

    private static Snapshot copyOf(Snapshot s) {
        return new Snapshot(s.createdAt, s.year, s.assets, s.liabilities, s.netWealth, s.nisab, s.zakat, s.breakdown);
    }

    public void saveAsset(String category, double amount) {
        if (uid == null) return;
        Asset a = new Asset(category, amount, System.currentTimeMillis());
        db.collection("users").document(uid).collection("assets")
                .document(category).set(a);
    }

    public void assets(Result<List<Asset>> result) {
        if (uid == null) { result.onResult(new ArrayList<>()); return; }
        db.collection("users").document(uid).collection("assets")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get().addOnSuccessListener(q -> {
                    List<Asset> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : q) list.add(doc.toObject(Asset.class));
                    result.onResult(list);
                }).addOnFailureListener(e -> result.onResult(new ArrayList<>()));
    }
}