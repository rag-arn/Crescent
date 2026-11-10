package com.crescent.app;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deletes everything the app stores about one user (admin tool).
 *
 * Removed: calculation history and assets, notifications, the profile (expert status, rating, picture),
 * and every chat the user took part in (the room itself, so the other person's copy goes too, and the
 * entry in the other person's chat list).
 * Kept: a minimal profile (uid, name, e-mail and the "disabled" flag) so the admin can still find the user
 * and a disabled account stays disabled. The sign-in account itself is not deleted: that needs a server.
 */
final class AdminUserData {
    private AdminUserData() {}

    interface Done { void finished(boolean ok); }

    private static final String[] SUBCOLLECTIONS = {"snapshots", "assets", "notifications"};

    static void deleteAll(String uid, Done done) {
        final FirebaseFirestore fs = FirebaseFirestore.getInstance();
        final DocumentReference userRef = fs.collection("users").document(uid);
        final DatabaseReference chats = FirebaseDatabase.getInstance().getReference("chats");
        final DatabaseReference inboxes = FirebaseDatabase.getInstance().getReference("user_chats");

        userRef.get().addOnCompleteListener(profileTask -> {
            if (!profileTask.isSuccessful() || profileTask.getResult() == null) { done.finished(false); return; }
            final DocumentSnapshot profile = profileTask.getResult();

            inboxes.child(uid).get().addOnCompleteListener(inboxTask -> {
                if (!inboxTask.isSuccessful()) { done.finished(false); return; }

                final List<Task<?>> work = new ArrayList<>();

                // 1. Realtime Database: chat rooms, the other side's inbox entry, then this user's inbox
                for (DataSnapshot room : inboxTask.getResult().getChildren()) {
                    String roomId = room.getKey();
                    if (roomId == null) continue;
                    work.add(chats.child(roomId).removeValue());
                    String other = room.child("otherUid").getValue(String.class);
                    if (other != null) work.add(inboxes.child(other).child(roomId).removeValue());
                }
                work.add(inboxes.child(uid).removeValue());

                // 2. Firestore sub-collections (a collection can only be deleted document by document)
                final List<Task<QuerySnapshot>> reads = new ArrayList<>();
                for (String sub : SUBCOLLECTIONS) reads.add(userRef.collection(sub).get());

                Tasks.whenAllComplete(reads).addOnSuccessListener(readsDone -> {
                    WriteBatch batch = fs.batch();
                    int count = 0;
                    for (Task<QuerySnapshot> r : reads) {
                        if (!r.isSuccessful()) { done.finished(false); return; }
                        for (DocumentSnapshot d : r.getResult().getDocuments()) {
                            batch.delete(d.getReference());
                            if (++count == 400) {                          // Firestore allows 500 writes per batch
                                work.add(batch.commit());
                                batch = fs.batch();
                                count = 0;
                            }
                        }
                    }
                    if (count > 0) work.add(batch.commit());

                    Tasks.whenAllComplete(work).addOnSuccessListener(allDone -> {
                        for (Task<?> t : allDone) {
                            if (!t.isSuccessful()) { done.finished(false); return; }
                        }
                        // 3. Reset the profile to the bare minimum
                        Map<String, Object> keep = new HashMap<>();
                        keep.put("uid", uid);
                        keep.put("name", profile.getString("name") != null ? profile.getString("name") : "");
                        keep.put("email", profile.getString("email") != null ? profile.getString("email") : "");
                        if (Boolean.TRUE.equals(profile.getBoolean("disabled"))) keep.put("disabled", true);
                        keep.put("dataDeletedAt", System.currentTimeMillis());
                        userRef.set(keep).addOnCompleteListener(t -> done.finished(t.isSuccessful()));
                    });
                });
            });
        });
    }
}
