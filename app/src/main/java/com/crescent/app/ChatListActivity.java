package com.crescent.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private ChatListAdapter adapter;
    private List<DataSnapshot> chatRooms = new ArrayList<>();
    private String currentUid;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        View backBtn = findViewById(R.id.backButton);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        currentUid = FirebaseAuth.getInstance().getUid();

        emptyText = findViewById(R.id.emptyText);
        recyclerView = findViewById(R.id.chatListRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatListAdapter();
        recyclerView.setAdapter(adapter);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("user_chats").child(currentUid);
        ref.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatRooms.clear();
                for (DataSnapshot doc : snapshot.getChildren()) {
                    chatRooms.add(doc);
                }

                // Toggle empty state
                if (chatRooms.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                    chatRooms.sort((a, b) -> {
                        Long timeA = a.child("lastTimestamp").getValue(Long.class);
                        Long timeB = b.child("lastTimestamp").getValue(Long.class);
                        if (timeA == null) timeA = 0L;
                        if (timeB == null) timeB = 0L;
                        return timeB.compareTo(timeA);
                    });
                    adapter.notifyDataSetChanged();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_list, parent, false);
            return new ViewHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DataSnapshot room = chatRooms.get(position);

            String lastMsg = room.child("lastMessage").getValue(String.class);
            Long time = room.child("lastTimestamp").getValue(Long.class);
            Boolean unread = room.child("hasUnread").getValue(Boolean.class);
            String otherUid = room.child("otherUid").getValue(String.class);

            if (holder.messageText != null) holder.messageText.setText(lastMsg != null ? lastMsg : "No messages yet");
            if (holder.timeText != null && time != null) {
                holder.timeText.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(new Date(time)));
            }
            if (holder.unreadDot != null) holder.unreadDot.setVisibility(Boolean.TRUE.equals(unread) ? View.VISIBLE : View.GONE);

            if (otherUid != null) {
                FirebaseFirestore.getInstance().collection("users").document(otherUid).get()
                        .addOnSuccessListener(doc -> {

                            if (!doc.exists()) {
                                FirebaseDatabase.getInstance().getReference("user_chats")
                                        .child(currentUid).child(room.getKey()).removeValue();
                                return;
                            }

                            String name = doc.getString("name");
                            String imgBase64 = doc.getString("profileImageBase64");

                            if (holder.nameText != null) holder.nameText.setText(name != null ? name : "User");

                            if (imgBase64 != null && !imgBase64.isEmpty() && holder.profileImage != null) {
                                try {
                                    byte[] dec = Base64.decode(imgBase64, Base64.DEFAULT);
                                    Bitmap bmp = BitmapFactory.decodeByteArray(dec, 0, dec.length);
                                    holder.profileImage.setPadding(0,0,0,0);
                                    holder.profileImage.setImageTintList(null);
                                    holder.profileImage.setImageBitmap(bmp);
                                } catch(Exception e){}
                            }

                            holder.itemView.setOnClickListener(v -> {
                                Intent intent = new Intent(ChatListActivity.this, ChatActivity.class);
                                intent.putExtra("expertUid", otherUid);
                                intent.putExtra("expertName", name != null ? name : "User");
                                intent.putExtra("expertImage", imgBase64);
                                startActivity(intent);
                            });
                        });
            }
        }
        @Override public int getItemCount() { return chatRooms.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, messageText, timeText;
            ImageView profileImage;
            View unreadDot;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.inboxName);
                messageText = itemView.findViewById(R.id.inboxMessage);
                timeText = itemView.findViewById(R.id.inboxTime);
                profileImage = itemView.findViewById(R.id.inboxImage);
                unreadDot = itemView.findViewById(R.id.inboxUnreadDot);
            }
        }
    }
}