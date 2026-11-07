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

        // 1. Fetch Other User: Did THEY resign?
        if (expertUid != null) {
            FirebaseFirestore.getInstance().collection("users").document(expertUid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Boolean isStillExpert = doc.getBoolean("isExpert");
                            // Determine if they were ever an expert by checking for bio/rating fields
                            boolean wasExpert = doc.contains("bio") || doc.contains("rating");

                            // Only show warning if they WERE an expert, but are no longer active
                            if (wasExpert && (isStillExpert == null || !isStillExpert)) {
                                if (expertStatusWarning != null) {
                                    expertStatusWarning.setVisibility(View.VISIBLE);
                                    expertStatusWarning.setText("No longer an active expert");
                                }
                                disableChatInput();
                            }
                        }
                    });
        }

        // 2. Fetch Current User: Did WE resign?
        if (currentUid != null && expertUid != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUid).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) return;

                        Boolean amIActive = doc.getBoolean("isExpert");
                        boolean meWasExpert = doc.contains("bio") || doc.contains("rating");

                        if (Boolean.TRUE.equals(amIActive)) {
                            isCurrentUserExpert = true;
                        } else if (meWasExpert) {
                            // If the current user resigned, tell them they are inactive and block them sending messages
                            if (expertStatusWarning != null) {
                                expertStatusWarning.setVisibility(View.VISIBLE);
                                expertStatusWarning.setText("You are no longer an active expert");
                            }
                            disableChatInput();
                        }

                        // Rating prompt logic
                        Object rated = doc.get("ratedExperts");
                        if (rated instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) rated).get(expertUid))) {
                            getSharedPreferences("crescent_ratings", MODE_PRIVATE).edit().putBoolean(ratedKey(), true).apply();
                        }
                    });
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
        MainActivity.activeRoomId = roomId;

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

        FirebaseDatabase.getInstance().getReference("user_chats").child(currentUid).child(roomId).child("hasUnread").setValue(false);

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

                FirebaseDatabase.getInstance().getReference("user_chats").child(currentUid).child(roomId).child("hasUnread").setValue(false);

                String sender = snapshot.child("senderId").getValue(String.class);
                Boolean isSeen = snapshot.child("seen").getValue(Boolean.class);

                if (sender != null && !sender.equals(currentUid) && !Boolean.TRUE.equals(isSeen)) {
                    snapshot.getRef().child("seen").setValue(true);
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

    private void disableChatInput() {
        View inputArea = findViewById(R.id.inputArea);
        if (inputArea != null) {
            inputArea.setVisibility(View.GONE); // Completely hides the input box
        }
    }

    private String ratedKey() {
        return "rated_" + currentUid + "_" + expertUid;
    }

    private boolean hasSentMessage() {
        for (DataSnapshot m : messages) {
            String sender = m.child("senderId").getValue(String.class);
            if (currentUid != null && currentUid.equals(sender)) return true;
        }
        return false;
    }

    private void handleExit(String expertName) {
        boolean hasRated = currentUid != null && expertUid != null
                && getSharedPreferences("crescent_ratings", MODE_PRIVATE).getBoolean(ratedKey(), false);

        if (!isCurrentUserExpert && !hasRated && expertUid != null && hasSentMessage()) {
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
        if (messageInput == null) return;
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

                    Object rated = mySnap.get("ratedExperts");
                    if (rated instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) rated).get(eUid))) {
                        return false;
                    }

                    Double current = expertSnap.getDouble("rating");
                    Long countObj = expertSnap.getLong("ratingCount");
                    double currentRating = current != null ? current : 5.0;
                    long count = countObj != null ? countObj : 0;
                    double updatedRating = ((currentRating * count) + newRating) / (count + 1);

                    transaction.update(expertRef, "rating", updatedRating, "ratingCount", count + 1);

                    Map<String, Object> inner = new HashMap<>();
                    inner.put(eUid, true);
                    Map<String, Object> outer = new HashMap<>();
                    outer.put("ratedExperts", inner);
                    transaction.set(myRef, outer, com.google.firebase.firestore.SetOptions.merge());
                    return true;
                }).addOnSuccessListener(applied -> {
                    if (Boolean.TRUE.equals(applied)) {
                        Toast.makeText(app, "Thank you for your feedback!", Toast.LENGTH_SHORT).show();
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