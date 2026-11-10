package com.crescent.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText messageInput;
    private TextView typingIndicator;
    private TextView expertStatusWarning;
    private ChatAdapter adapter;
    private List<DataSnapshot> messages = new ArrayList<>();

    private HashSet<String> pendingMessageKeys = new HashSet<>();

    private String currentUid, expertUid, roomId;
    private DatabaseReference roomRef;

    private boolean isCurrentUserExpert = false;
    // Who may chat with whom: a resigned expert may only chat with ACTIVE experts.
    private boolean otherLoaded = false, otherActive = false, otherWasExpert = false;
    private boolean meLoaded = false, meActive = false, meWasExpert = false;
    private boolean chatBlocked = false;
    private boolean otherDisabled = false;
    // Rating: one rating per person per "rating period" of an expert (a new period starts when he resigns / re-registers)
    private long otherEpoch = 0;            // the expert's current rating period
    private Long myRatedEpoch = null;       // the period in which the current user last rated this expert (null = never)
    private boolean myRatingKnown = false;  // true once the current user's record was read successfully
    private com.google.firebase.firestore.ListenerRegistration expertStatusReg;

    // True only while this chat is actually on screen (resumed). Messages are marked "seen" only then.
    private boolean isResumed = false;

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private ChildEventListener messagesListener;
    private ValueEventListener typingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        currentUid = FirebaseAuth.getInstance().getUid();
        expertUid = getIntent().getStringExtra("expertUid");
        final String expertName = getIntent().getStringExtra("expertName");

        TextView nameTitle = findViewById(R.id.expertName);
        expertStatusWarning = findViewById(R.id.expertStatusWarning);
        if (nameTitle != null) nameTitle.setText(expertName != null ? expertName : "Expert");

        // 1. Other person: is he/she an active expert, a former expert, or a general user? (live)
        if (expertUid != null) {
            expertStatusReg = FirebaseFirestore.getInstance().collection("users").document(expertUid)
                    .addSnapshotListener((doc, error) -> {
                        if (doc == null || !doc.exists()) return;
                        otherActive = Boolean.TRUE.equals(doc.getBoolean("isExpert"));
                        // Was ever an expert if the expert profile fields exist
                        otherWasExpert = doc.contains("rating");
                        otherDisabled = Boolean.TRUE.equals(doc.getBoolean("disabled"));
                        Long ep = doc.getLong("ratingEpoch");
                        otherEpoch = ep != null ? ep : 0L;
                        otherLoaded = true;
                        applyChatPolicy();
                    });
        }

        // 2. Current user: active expert, former expert (resigned), or general user?
        if (currentUid != null && expertUid != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            meActive = Boolean.TRUE.equals(doc.getBoolean("isExpert"));
                            meWasExpert = doc.contains("rating");
                            isCurrentUserExpert = meActive;

                            Object rated = doc.get("ratedExperts");
                            if (rated instanceof Map) myRatedEpoch = epochOf(((Map<?, ?>) rated).get(expertUid));
                        }
                        myRatingKnown = true;
                        meLoaded = true;
                        applyChatPolicy();
                    })
                    .addOnFailureListener(e -> { meLoaded = true; applyChatPolicy(); });
        }

        View backBtn = findViewById(R.id.backButton);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> handleExit(expertName));
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExit(expertName);
            }
        });

        if (currentUid == null || expertUid == null) {
            Toast.makeText(this, "Error loading chat", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomId = currentUid.compareTo(expertUid) < 0 ? currentUid + "_" + expertUid : expertUid + "_" + currentUid;

        roomRef = FirebaseDatabase.getInstance().getReference("chats").child(roomId);

        recyclerView = findViewById(R.id.chatRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        typingIndicator = findViewById(R.id.typingIndicator);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(lm);
            adapter = new ChatAdapter();
            recyclerView.setAdapter(adapter);
        }

        typingListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    typingIndicator.setVisibility(View.VISIBLE);
                    typingIndicator.setText((expertName != null ? expertName : "Expert") + " is typing...");
                } else {
                    typingIndicator.setVisibility(View.GONE);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomRef.child("typing").child(expertUid).addValueEventListener(typingListener);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                roomRef.child("typing").child(currentUid).setValue(s.length() > 0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        messagesListener = new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot snapshot, String prevChildKey) {
                messages.add(snapshot);
                adapter.notifyDataSetChanged();
                if (recyclerView != null) recyclerView.scrollToPosition(messages.size() - 1);

                // Only count it as read if this person's chat is open and on screen right now.
                if (isResumed) {
                    markIncomingAsSeen(snapshot);
                    clearUnreadFlag();
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String prevChildKey) {
                for (int i = 0; i < messages.size(); i++) {
                    if (messages.get(i).getKey() != null && messages.get(i).getKey().equals(snapshot.getKey())) {
                        messages.set(i, snapshot);
                        adapter.notifyItemChanged(i);
                        break;
                    }
                }
            }

            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String prevChildKey) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomRef.child("messages").addChildEventListener(messagesListener);

        View sendBtn = findViewById(R.id.sendButton);
        if (sendBtn != null) sendBtn.setOnClickListener(v -> sendMessage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        if (roomId == null) return;
        MainActivity.activeRoomId = roomId;               // tells the background service: do not notify for this chat
        androidx.core.app.NotificationManagerCompat.from(this)
                .cancel(ChatNotificationService.notificationId(roomId));
        clearUnreadFlag();
        for (DataSnapshot m : messages) markIncomingAsSeen(m);   // messages that arrived while the chat was not visible
    }

    @Override
    protected void onPause() {
        isResumed = false;
        if (roomId != null && roomId.equals(MainActivity.activeRoomId)) MainActivity.activeRoomId = null;
        super.onPause();
    }

    private void clearUnreadFlag() {
        if (currentUid == null || roomId == null) return;
        FirebaseDatabase.getInstance().getReference("user_chats").child(currentUid).child(roomId).child("hasUnread").setValue(false);
    }

    private void markIncomingAsSeen(DataSnapshot snapshot) {
        String sender = snapshot.child("senderId").getValue(String.class);
        Boolean isSeen = snapshot.child("seen").getValue(Boolean.class);
        if (sender != null && !sender.equals(currentUid) && !Boolean.TRUE.equals(isSeen)) {
            snapshot.getRef().child("seen").setValue(true);
        }
    }

    /**
     * Rules, decided from both people's current status:
     *  - A resigned (former) expert cannot chat with general users.
     *  - A resigned expert CAN chat with active experts (and they can reply).
     *  - A general user cannot chat with a resigned expert.
     */
    private void applyChatPolicy() {
        if (!otherLoaded || !meLoaded) return;

        boolean otherResigned = otherWasExpert && !otherActive;
        boolean meResigned = meWasExpert && !meActive;

        String reason = null;
        if (otherDisabled) {
            reason = "This account has been disabled";
        } else if (otherResigned && !meActive) {
            reason = "No longer an active expert";
        } else if (meResigned && !otherActive) {
            reason = "You are no longer an active expert. You can only message active experts.";
        }

        chatBlocked = reason != null;
        if (expertStatusWarning != null) {
            expertStatusWarning.setVisibility(chatBlocked ? View.VISIBLE : View.GONE);
            if (chatBlocked) expertStatusWarning.setText(reason);
        }
        View inputArea = findViewById(R.id.inputArea);
        if (inputArea != null) inputArea.setVisibility(chatBlocked ? View.GONE : View.VISIBLE);
        if (chatBlocked && isResumed) clearUnreadFlag();   // the person is looking at it, so no unread dot
    }

    private String ratedKey() {
        return "rated_" + currentUid + "_" + expertUid + "_" + otherEpoch;
    }

    /** Old data stored "true" (= rating period 0); new data stores the period number. */
    private static Long epochOf(Object v) {
        if (Boolean.TRUE.equals(v)) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        return null;
    }

    /**
     * Anyone (a general user or another expert) is asked to rate an ACTIVE expert when leaving the chat,
     * once per rating period, after having sent at least one message.
     */
    private boolean canShowRating() {
        if (currentUid == null || expertUid == null || currentUid.equals(expertUid)) return false;
        if (!otherLoaded || !myRatingKnown || !otherActive) return false;           // only active experts are rated
        if (myRatedEpoch != null && myRatedEpoch == otherEpoch) return false;       // already rated in this period
        if (getSharedPreferences("crescent_ratings", MODE_PRIVATE).getBoolean(ratedKey(), false)) return false;
        return hasSentMessage();
    }

    private boolean hasSentMessage() {
        for (DataSnapshot m : messages) {
            String sender = m.child("senderId").getValue(String.class);
            if (currentUid != null && currentUid.equals(sender)) return true;
        }
        return false;
    }

    private void handleExit(String expertName) {
        if (canShowRating()) {
            showRatingDialogAndExit(expertName);
        } else {
            finish();
        }
    }

    private void showRatingDialogAndExit(String expertName) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View customView = getLayoutInflater().inflate(R.layout.dialog_rating, null);
        builder.setView(customView);

        android.app.AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView title = customView.findViewById(R.id.dialogTitle);
        if (title != null) title.setText("Rate " + (expertName != null ? expertName : "Expert"));

        android.widget.RatingBar ratingBar = customView.findViewById(R.id.dialogRatingBar);
        View btnSubmit = customView.findViewById(R.id.dialogBtnSubmit);
        View btnCancel = customView.findViewById(R.id.dialogBtnCancel);

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                float rating = ratingBar != null ? ratingBar.getRating() : 0f;
                if (rating < 1f) {
                    Toast.makeText(this, "Please select 1 to 5 stars", Toast.LENGTH_SHORT).show();
                    return;
                }
                btnSubmit.setEnabled(false);
                getSharedPreferences("crescent_ratings", MODE_PRIVATE).edit().putBoolean(ratedKey(), true).apply();
                submitRatingToFirestore(rating);
                dialog.dismiss();
                finish();
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dialog.dismiss();
                finish();
            });
        }

        dialog.setOnCancelListener(d -> finish());

        try {
            dialog.show();
        } catch (Exception e) {
            finish();
        }
    }

    private void sendMessage() {
        if (messageInput == null || chatBlocked) return;
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        messageInput.setText("");

        DatabaseReference msgRef = roomRef.child("messages").push();
        String msgKey = msgRef.getKey();

        if (msgKey != null) {
            pendingMessageKeys.add(msgKey);
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId", currentUid);
        msg.put("text", text);
        msg.put("timestamp", ServerValue.TIMESTAMP);
        msg.put("seen", false);

        msgRef.setValue(msg, (error, ref) -> {
            if (error == null && msgKey != null) {
                pendingMessageKeys.remove(msgKey);
                if (adapter != null && !messages.isEmpty()) {
                    adapter.notifyItemChanged(messages.size() - 1);
                }
            }
        });

        DatabaseReference myInbox = FirebaseDatabase.getInstance().getReference("user_chats").child(currentUid).child(roomId);
        Map<String, Object> myUpdates = new HashMap<>();
        myUpdates.put("otherUid", expertUid);
        myUpdates.put("lastMessage", text);
        myUpdates.put("lastTimestamp", ServerValue.TIMESTAMP);
        myUpdates.put("hasUnread", false);
        myInbox.updateChildren(myUpdates);

        DatabaseReference theirInbox = FirebaseDatabase.getInstance().getReference("user_chats").child(expertUid).child(roomId);
        Map<String, Object> theirUpdates = new HashMap<>();
        theirUpdates.put("otherUid", currentUid);
        theirUpdates.put("lastMessage", text);
        theirUpdates.put("lastTimestamp", ServerValue.TIMESTAMP);
        theirUpdates.put("hasUnread", true);
        theirInbox.updateChildren(theirUpdates);
    }

    private void submitRatingToFirestore(float newRating) {
        final android.content.Context app = getApplicationContext();
        final String prefKey = ratedKey();
        final String myUid = currentUid;
        final String eUid = expertUid;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference expertRef = db.collection("users").document(eUid);
        com.google.firebase.firestore.DocumentReference myRef = db.collection("users").document(myUid);

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot expertSnap = transaction.get(expertRef);
                    com.google.firebase.firestore.DocumentSnapshot mySnap = transaction.get(myRef);

                    if (!Boolean.TRUE.equals(expertSnap.getBoolean("isExpert"))) return false;   // resigned meanwhile

                    Long epochObj = expertSnap.getLong("ratingEpoch");
                    long epoch = epochObj != null ? epochObj : 0L;

                    Object rated = mySnap.get("ratedExperts");
                    if (rated instanceof Map) {
                        Long mine = epochOf(((Map<?, ?>) rated).get(eUid));
                        if (mine != null && mine == epoch) return false;   // already rated in this rating period
                    }

                    Double current = expertSnap.getDouble("rating");
                    Long countObj = expertSnap.getLong("ratingCount");
                    double currentRating = current != null ? current : 5.0;
                    long count = countObj != null ? countObj : 0;
                    double updatedRating = ((currentRating * count) + newRating) / (count + 1);

                    transaction.update(expertRef, "rating", updatedRating, "ratingCount", count + 1);

                    Map<String, Object> inner = new HashMap<>();
                    inner.put(eUid, epoch);
                    Map<String, Object> outer = new HashMap<>();
                    outer.put("ratedExperts", inner);

                    // Full record (visible to the admin only): how many stars and when
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("stars", (double) newRating);
                    detail.put("epoch", epoch);
                    detail.put("timestamp", System.currentTimeMillis());
                    Map<String, Object> detailByExpert = new HashMap<>();
                    detailByExpert.put(eUid, detail);
                    outer.put("ratingsGiven", detailByExpert);
                    transaction.set(myRef, outer, com.google.firebase.firestore.SetOptions.merge());
                    return true;
                }).addOnSuccessListener(applied -> {
                    if (Boolean.TRUE.equals(applied)) {
                        Toast.makeText(app, "Thank you for your feedback!", Toast.LENGTH_SHORT).show();

                        // Tell the expert about the rating. Only the stars are included, never who gave them.
                        // It is stored for the expert, so it is delivered when they are next signed in.
                        int whole = Math.round(newRating);
                        String value = Math.abs(newRating - whole) < 0.01f ? String.valueOf(whole) : String.format(Locale.US, "%.1f", newRating);
                        StringBuilder stars = new StringBuilder();
                        for (int i = 1; i <= 5; i++) stars.append(i <= Math.round(newRating) ? "\u2605" : "\u2606");
                        Map<String, Object> notif = new HashMap<>();
                        notif.put("title", "New rating received");
                        notif.put("message", "A user rated you " + value + " out of 5  " + stars);
                        notif.put("type", "rating");
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("read", false);
                        expertRef.collection("notifications").add(notif);
                    }
                })
                .addOnFailureListener(e -> {
                    app.getSharedPreferences("crescent_ratings", MODE_PRIVATE).edit().remove(prefKey).apply();
                    Toast.makeText(app, "Failed to submit rating. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainActivity.activeRoomId = null;

        if (expertStatusReg != null) expertStatusReg.remove();
        if (roomRef != null) {
            if (currentUid != null) {
                roomRef.child("typing").child(currentUid).setValue(false);
            }
            if (messagesListener != null) {
                roomRef.child("messages").removeEventListener(messagesListener);
            }
            if (typingListener != null && expertUid != null) {
                roomRef.child("typing").child(expertUid).removeEventListener(typingListener);
            }
        }
    }

    public class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {

        @Override public int getItemViewType(int position) {
            String sender = messages.get(position).child("senderId").getValue(String.class);
            return currentUid.equals(sender) ? TYPE_SENT : TYPE_RECEIVED;
        }

        @NonNull @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = (viewType == TYPE_SENT) ? R.layout.item_message_sent : R.layout.item_message_received;
            View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new ChatViewHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            DataSnapshot msg = messages.get(position);
            String text = msg.child("text").getValue(String.class);
            Long time = msg.child("timestamp").getValue(Long.class);
            String msgKey = msg.getKey();

            if (holder.msgText != null) holder.msgText.setText(text);

            if (holder.timeText != null) {
                if (time != null) {
                    holder.timeText.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(new Date(time)));
                } else {
                    holder.timeText.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(new Date()));
                }
            }

            if (holder.statusText != null) {
                if (getItemViewType(position) == TYPE_SENT && position == messages.size() - 1) {
                    holder.statusText.setVisibility(View.VISIBLE);

                    Boolean isSeen = msg.child("seen").getValue(Boolean.class);

                    if (Boolean.TRUE.equals(isSeen)) {
                        holder.statusText.setText("Seen");
                    } else if (pendingMessageKeys.contains(msgKey)) {
                        holder.statusText.setText("Sending...");
                    } else {
                        holder.statusText.setText("Sent");
                    }
                } else {
                    holder.statusText.setVisibility(View.GONE);
                }
            }
        }
        @Override public int getItemCount() { return messages.size(); }
    }

    private static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView msgText, timeText, statusText;
        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            msgText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.messageTime);
            statusText = itemView.findViewById(R.id.messageStatus);
        }
    }
}