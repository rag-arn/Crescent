package com.crescent.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatListActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null) { finish(); return; }

        LinearLayout container = findViewById(R.id.inboxContainer);
        View progress = findViewById(R.id.loadingProgress);
        TextView emptyText = findViewById(R.id.emptyText);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Removed the complex orderBy to prevent Firestore Index crashes
        db.collection("chats")
                .whereArrayContains("users", currentUid)
                .addSnapshotListener((query, error) -> {
                    progress.setVisibility(View.GONE);
                    if (error != null) {
                        Toast.makeText(this, "Failed to load chats: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    container.removeAllViews();
                    if (query == null || query.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        return;
                    }
                    emptyText.setVisibility(View.GONE);

                    // Sort the documents locally to bypass Firebase Index requirements
                    List<DocumentSnapshot> docs = new ArrayList<>(query.getDocuments());
                    Collections.sort(docs, (d1, d2) -> {
                        Long t1 = d1.getLong("timestamp");
                        Long t2 = d2.getLong("timestamp");
                        if (t1 == null) t1 = 0L;
                        if (t2 == null) t2 = 0L;
                        return t2.compareTo(t1); // Descending order
                    });

                    LayoutInflater inflater = LayoutInflater.from(this);
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());

                    for (DocumentSnapshot doc : docs) {
                        List<String> users = (List<String>) doc.get("users");
                        if (users == null || users.size() != 2) continue;

                        String otherUid = users.get(0).equals(currentUid) ? users.get(1) : users.get(0);
                        String lastMessage = doc.getString("lastMessage");
                        Long time = doc.getLong("timestamp");
                        boolean hasUnread = Boolean.TRUE.equals(doc.getBoolean("hasUnread_" + currentUid));

                        View item = inflater.inflate(R.layout.item_chat_list, container, false);
                        TextView nameView = item.findViewById(R.id.inboxName);
                        TextView msgView = item.findViewById(R.id.inboxMessage);
                        TextView timeView = item.findViewById(R.id.inboxTime);
                        ImageView imgView = item.findViewById(R.id.inboxImage);
                        View dot = item.findViewById(R.id.inboxUnreadDot);

                        msgView.setText(lastMessage != null ? lastMessage : "");
                        timeView.setText(time != null ? sdf.format(new Date(time)) : "");

                        if (hasUnread) {
                            dot.setVisibility(View.VISIBLE);
                            nameView.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
                        }

                        imgView.setOutlineProvider(new ViewOutlineProvider() {
                            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
                        });
                        imgView.setClipToOutline(true);

                        db.collection("users").document(otherUid).get().addOnSuccessListener(userDoc -> {
                            if (userDoc.exists()) {
                                String name = userDoc.getString("name");
                                nameView.setText(name != null ? name : "User");

                                String base64Img = userDoc.getString("profileImageBase64");
                                if (base64Img != null && !base64Img.isEmpty()) {
                                    try {
                                        byte[] decoded = Base64.decode(base64Img, Base64.DEFAULT);
                                        Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                                        imgView.setPadding(0,0,0,0);
                                        imgView.setImageTintList(null);
                                        imgView.setImageBitmap(bmp);
                                    } catch (Exception ignored) {}
                                }

                                item.setOnClickListener(v -> {
                                    Intent intent = new Intent(this, ChatActivity.class);
                                    intent.putExtra("expertUid", otherUid);
                                    intent.putExtra("expertName", name);
                                    intent.putExtra("expertImage", base64Img);
                                    startActivity(intent);
                                });
                            }
                        });
                        container.addView(item);
                    }
                });
    }
}