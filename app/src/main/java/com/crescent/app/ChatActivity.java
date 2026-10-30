package com.crescent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
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
    private String currentUid;
    private String roomId;
    private FirebaseFirestore db;
    private LinearLayout messagesContainer;
    private ScrollView chatScroll;

    private boolean amIExpert = false;
    private boolean isOtherExpert = false;

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

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        db = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();

        expertUid = getIntent().getStringExtra("expertUid");
        String expertName = getIntent().getStringExtra("expertName");
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
        nameView.setText(expertName != null ? expertName : "Expert");

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

        listenForMessages();
        listenForExpertStatus();
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
                .addSnapshotListener(MetadataChanges.INCLUDE, (value, error) -> { // <-- THIS LINE FIXES THE CHECK MARK
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