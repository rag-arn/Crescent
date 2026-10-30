package com.crescent.app;

import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class CrescentRepository {
    public interface Result<T> { void onResult(T value); }
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String uid;

    public CrescentRepository(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = (user != null && !user.isAnonymous()) ? user.getUid() : null;
    }

    public void saveSnapshot(Snapshot snapshot) {
        if (uid == null) return;
        // Save to cloud. Offline? Firestore caches it instantly and uploads it when internet returns.
        db.collection("users").document(uid).collection("snapshots")
                .document(String.valueOf(snapshot.createdAt)).set(snapshot);
    }

    public void snapshots(Result<List<Snapshot>> result) {
        if (uid == null) { result.onResult(new ArrayList<>()); return; }
        db.collection("users").document(uid).collection("snapshots")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get().addOnSuccessListener(q -> {
                    List<Snapshot> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : q) list.add(doc.toObject(Snapshot.class));
                    result.onResult(list);
                }).addOnFailureListener(e -> result.onResult(new ArrayList<>()));
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