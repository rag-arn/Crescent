package com.crescent.app;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tells everyone who chatted with an expert that the expert has resigned.
 *
 * It is stored as a normal notification (users/<partner>/notifications), so it appears on the
 * Notifications screen, not in the chat. ChatNotificationService shows it in the notification tray,
 * even when the app is closed or the person signs in later.
 * The expert's own chat list (user_chats/<expertUid>) is only used to find who to tell.
 */
final class ExpertResignNotifier {
    private ExpertResignNotifier() {}

    static void notifyChatPartners(String expertUid) {
        if (expertUid == null) return;
        final FirebaseFirestore fs = FirebaseFirestore.getInstance();
        final DatabaseReference inboxes = FirebaseDatabase.getInstance().getReference("user_chats");

        fs.collection("users").document(expertUid).get().addOnCompleteListener(me -> {
            String n = (me.isSuccessful() && me.getResult() != null) ? me.getResult().getString("name") : null;
            final String expertName = (n == null || n.trim().isEmpty()) ? "An expert" : n.trim();

            inboxes.child(expertUid).get().addOnSuccessListener(snapshot -> {
                Set<String> done = new HashSet<>();
                for (DataSnapshot room : snapshot.getChildren()) {
                    final String partnerUid = room.child("otherUid").getValue(String.class);
                    if (partnerUid == null || !done.add(partnerUid)) continue;

                    // Experts can still chat with a resigned expert; general users cannot. Word the notice accordingly.
                    fs.collection("users").document(partnerUid).get().addOnCompleteListener(task -> {
                        boolean partnerIsExpert = task.isSuccessful() && task.getResult() != null
                                && Boolean.TRUE.equals(task.getResult().getBoolean("isExpert"));
                        String message = expertName + " has resigned as a Zakat expert. "
                                + (partnerIsExpert
                                ? "You can still message each other as experts."
                                : "You can no longer send messages in that chat.");

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("title", "Expert resigned");
                        notif.put("message", message);
                        notif.put("type", "expert_resigned");
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("read", false);
                        fs.collection("users").document(partnerUid).collection("notifications").add(notif);
                    });
                }
            });
        });
    }
}