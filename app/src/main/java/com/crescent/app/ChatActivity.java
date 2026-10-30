package com.crescent.app;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {
    private String expertUid;
    private String expertNameStr;
    private String currentUid;
    private String roomId;
    private FirebaseFirestore db;
    private LinearLayout messagesContainer;
    private ScrollView chatScroll;

    private boolean amIExpert = false;
    private boolean isOtherExpert = false;
    private boolean hasRated = false;

    @Override protected void onResume() {
        super.onResume();
        MainActivity.activeRoomId = roomId;
        if (roomId != null && currentUid != null) {
            db.collection("chats").document(roomId).update("hasUnread_" + currentUid, false);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        MainActivity.activeRoomId = null;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        db = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();

        expertUid = getIntent().getStringExtra("expertUid");
        expertNameStr = getIntent().getStringExtra("expertName");
        String expertBase64 = getIntent().getStringExtra("expertImage");

        if (currentUid == null || expertUid == null) {
            Toast.makeText(this, "Error loading chat.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomId = currentUid.compareTo(expertUid) < 0 ? currentUid + "_" + expertUid : expertUid + "_" + currentUid;

        db.collection("chats").document(roomId).update("hasUnread_" + currentUid, false);

        TextView nameView = findViewById(R.id.chatExpertName);
        ImageView imgView = findViewById(R.id.chatExpertImage);
        nameView.setText(expertNameStr != null ? expertNameStr : "Expert");

        imgView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        imgView.setClipToOutline(true);

        if (expertBase64 != null && !expertBase64.isEmpty()) {
            try {
                byte[] decoded = Base64.decode(expertBase64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                imgView.setPadding(0, 0, 0, 0);
                imgView.setImageTintList(null);
                imgView.setImageBitmap(bmp);
            } catch (Exception ignored) {}
        }

        messagesContainer = findViewById(R.id.messagesContainer);
        chatScroll = findViewById(R.id.chatScroll);
        EditText messageInput = findViewById(R.id.messageInput);
        View sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(v -> {
            String text = messageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
                messageInput.setText("");
            }
        });

        // Intercept back actions to check if rating is needed
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { attemptExit(); }
        });
        findViewById(R.id.backButton).setOnClickListener(v -> attemptExit());

        verifyRatingStatus();
        listenForMessages();
        listenForExpertStatus();
    }

    private void verifyRatingStatus() {
        SharedPreferences prefs = getSharedPreferences("expert_ratings", MODE_PRIVATE);
        if (prefs.getBoolean(currentUid + "_" + expertUid, false)) {
            hasRated = true; // Checked locally first for speed
        } else {
            // Check Firestore in case they logged in on a new device
            db.collection("users").document(currentUid).collection("rated_experts").document(expertUid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    hasRated = true;
                    prefs.edit().putBoolean(currentUid + "_" + expertUid, true).apply();
                }
            });
        }
    }

    private void attemptExit() {
        // Only show rating dialog if user is NOT an expert, the other person IS an expert, and they haven't rated them yet.
        if (!amIExpert && isOtherExpert && !hasRated) {
            showRatingDialog();
        } else {
            finish();
        }
    }

    private void showRatingDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_rate_expert);

        Window w = dialog.getWindow();
        if (w != null) {
            int margin = Math.round(24 * getResources().getDisplayMetrics().density);
            w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margin));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView titleText = dialog.findViewById(R.id.rateTitleText);
        titleText.setText("Rate " + (expertNameStr != null ? expertNameStr : "Expert"));

        RatingBar ratingBar = dialog.findViewById(R.id.ratingBar);
        Button skipBtn = dialog.findViewById(R.id.skipBtn);
        Button submitBtn = dialog.findViewById(R.id.submitBtn);

        skipBtn.setOnClickListener(v -> {
            dialog.dismiss(); // If they skip, we do NOT set hasRated = true, allowing them to rate later.
            finish();
        });

        submitBtn.setOnClickListener(v -> {
            float rating = ratingBar.getRating();
            if (rating > 0) {
                submitRatingToFirestore(rating);

                // Save locally and in Firestore so it never shows again across any device
                hasRated = true;
                getSharedPreferences("expert_ratings", MODE_PRIVATE).edit().putBoolean(currentUid + "_" + expertUid, true).apply();
                Map<String, Object> record = new HashMap<>();
                record.put("ratedAt", System.currentTimeMillis());
                db.collection("users").document(currentUid).collection("rated_experts").document(expertUid).set(record);
            }
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }

    private void submitRatingToFirestore(float newRating) {
        db.runTransaction(transaction -> {
            DocumentReference expertRef = db.collection("users").document(expertUid);
            DocumentSnapshot snap = transaction.get(expertRef);

            double currentRating = snap.contains("rating") && snap.getDouble("rating") != null ? snap.getDouble("rating") : 0.0;
            long count = snap.contains("ratingCount") && snap.getLong("ratingCount") != null ? snap.getLong("ratingCount") : 0;

            double updatedRating = ((currentRating * count) + newRating) / (count + 1);
            transaction.update(expertRef, "rating", updatedRating, "ratingCount", count + 1);
            return null;
        });
    }

    private void listenForExpertStatus() {
        db.collection("users").document(currentUid).addSnapshotListener((doc, e) -> {
            if (doc != null && doc.exists()) {
                amIExpert = Boolean.TRUE.equals(doc.getBoolean("isExpert"));
                checkChatValidity();
            }
        });
        db.collection("users").document(expertUid).addSnapshotListener((doc, e) -> {
            if (doc != null && doc.exists()) {
                isOtherExpert = Boolean.TRUE.equals(doc.getBoolean("isExpert"));
                checkChatValidity();
            }
        });
    }

    private void checkChatValidity() {
        View inputContainer = findViewById(R.id.inputContainer);
        View expertResignedBanner = findViewById(R.id.expertResignedBanner);
        if (inputContainer == null || expertResignedBanner == null) return;

        if (amIExpert || isOtherExpert) {
            inputContainer.setVisibility(View.VISIBLE);
            expertResignedBanner.setVisibility(View.GONE);
        } else {
            inputContainer.setVisibility(View.GONE);
            expertResignedBanner.setVisibility(View.VISIBLE);
        }
    }

    private void sendMessage(String text) {
        long time = System.currentTimeMillis();

        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId", currentUid);
        msg.put("receiverId", expertUid);
        msg.put("text", text);
        msg.put("timestamp", time);
        db.collection("chats").document(roomId).collection("messages").add(msg);

        Map<String, Object> roomData = new HashMap<>();
        roomData.put("users", Arrays.asList(currentUid, expertUid));
        roomData.put("lastMessage", text);
        roomData.put("timestamp", time);
        roomData.put("hasUnread_" + expertUid, true);
        roomData.put("hasUnread_" + currentUid, false);
        db.collection("chats").document(roomId).set(roomData, SetOptions.merge());
    }

    private void listenForMessages() {
        db.collection("chats").document(roomId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (value, error) -> {
                    if (error != null || value == null) return;

                    messagesContainer.removeAllViews();
                    LayoutInflater inflater = LayoutInflater.from(this);
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                    boolean hasNewReceivedMessage = false;

                    for (DocumentSnapshot doc : value.getDocuments()) {
                        View item = inflater.inflate(R.layout.item_chat, messagesContainer, false);
                        View leftBubble = item.findViewById(R.id.leftChatLayout);
                        View rightBubble = item.findViewById(R.id.rightChatLayout);
                        TextView leftText = item.findViewById(R.id.leftMessageText);
                        TextView rightText = item.findViewById(R.id.rightMessageText);
                        TextView leftTime = item.findViewById(R.id.leftTimeText);
                        TextView rightTime = item.findViewById(R.id.rightTimeText);
                        ImageView rightTick = item.findViewById(R.id.rightTick);

                        String senderId = doc.getString("senderId");
                        String text = doc.getString("text");
                        Long time = doc.getLong("timestamp");
                        String timeStr = time != null ? sdf.format(new Date(time)) : "";

                        if (currentUid.equals(senderId)) {
                            leftBubble.setVisibility(View.GONE);
                            rightText.setText(text);
                            rightTime.setText(timeStr);

                            // Checkmark logic using MetadataChanges
                            if (doc.getMetadata().hasPendingWrites()) {
                                rightTick.setVisibility(View.GONE);
                            } else {
                                rightTick.setVisibility(View.VISIBLE);
                            }
                        } else {
                            rightBubble.setVisibility(View.GONE);
                            leftText.setText(text);
                            leftTime.setText(timeStr);
                            hasNewReceivedMessage = true;
                        }
                        messagesContainer.addView(item);
                    }

                    if (hasNewReceivedMessage) {
                        db.collection("chats").document(roomId).update("hasUnread_" + currentUid, false);
                    }

                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                });
    }
}