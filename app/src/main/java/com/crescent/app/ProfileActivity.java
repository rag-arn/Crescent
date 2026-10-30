package com.crescent.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {
    private ImageView profileImage;
    private TextView uploadProgressText;
    private FirebaseUser user;
    private FirebaseFirestore db;
    private static final int CAMERA_PERMISSION_CODE = 105;

    // Launcher for Gallery
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) processAndUploadUri(uri);
            });

    // Launcher for Camera
    private final ActivityResultLauncher<Void> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) processAndUploadBitmap(bitmap);
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        user = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        profileImage = findViewById(R.id.profileImage);
        uploadProgressText = findViewById(R.id.uploadProgressText);
        TextView nameView = findViewById(R.id.profileName);
        TextView emailView = findViewById(R.id.profileEmail);
        View ratingLayout = findViewById(R.id.expertRatingLayout);
        RatingBar ratingBar = findViewById(R.id.expertRatingBar);
        TextView ratingCount = findViewById(R.id.expertRatingCount);
        View resignBtn = findViewById(R.id.resignBtn);

        profileImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        profileImage.setClipToOutline(true);

        findViewById(R.id.profileImageContainer).setOnClickListener(v -> {
            if (user != null && !user.isAnonymous()) {
                showImageOptions();
            } else {
                Toast.makeText(this, "Guests cannot upload photos.", Toast.LENGTH_SHORT).show();
            }
        });

        if (user != null) {
            String email = user.getEmail() != null ? user.getEmail() : "Anonymous Guest";
            String name = user.getDisplayName() != null ? user.getDisplayName() : (email.contains("@") ? email.split("@")[0] : "User");
            nameView.setText(name);
            emailView.setText(email);

            db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String base64Img = doc.getString("profileImageBase64");
                    if (base64Img != null && !base64Img.isEmpty()) loadBase64Image(base64Img);

                    if (Boolean.TRUE.equals(doc.getBoolean("isExpert"))) {
                        ratingLayout.setVisibility(View.VISIBLE);
                        Double r = doc.getDouble("rating");
                        Long c = doc.getLong("ratingCount");
                        ratingBar.setRating(r != null ? r.floatValue() : 0f);
                        ratingCount.setText(String.format(Locale.US, "(%d ratings)", c != null ? c : 0));
                    }
                }
            });

            resignBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Resign as Expert")
                    .setMessage("Are you sure you want to resign? You will be removed from the directory.")
                    .setPositiveButton("Resign", (d, w) -> {
                        db.collection("users").document(user.getUid()).update("isExpert", false).addOnSuccessListener(a -> {
                            ratingLayout.setVisibility(View.GONE);
                            Toast.makeText(this, "Resigned from expert directory.", Toast.LENGTH_LONG).show();
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        }
    }

    private void showImageOptions() {
        CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        new AlertDialog.Builder(this)
                .setTitle("Update Profile Picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // SYLLABUS: Check Runtime Permission (Camera)
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                        } else {
                            cameraLauncher.launch(null);
                        }
                    } else if (which == 1) {
                        imagePicker.launch("image/*");
                    }
                }).show();
    }

    // SYLLABUS: onRequestPermissionsResult, Handling Granted/Denied, Toast messages
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera Permission Granted!", Toast.LENGTH_SHORT).show();
                cameraLauncher.launch(null);
            } else {
                Toast.makeText(this, "Camera Permission Denied. Cannot take photo.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void processAndUploadUri(Uri imageUri) {
        uploadProgressText.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(imageUri);
                Bitmap original = BitmapFactory.decodeStream(is);
                processAndUploadBitmap(original);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    uploadProgressText.setVisibility(View.GONE);
                    Toast.makeText(ProfileActivity.this, "Error processing image", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void processAndUploadBitmap(Bitmap original) {
        if (original == null) return;
        runOnUiThread(() -> uploadProgressText.setVisibility(View.VISIBLE));

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int max = 250;
                int w = original.getWidth();
                int h = original.getHeight();
                float ratio = (float) w / h;
                if (w > max || h > max) {
                    if (ratio > 1) { w = max; h = (int)(max / ratio); }
                    else { h = max; w = (int)(max * ratio); }
                }
                Bitmap resized = Bitmap.createScaledBitmap(original, w, h, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                runOnUiThread(() -> {
                    db.collection("users").document(user.getUid()).update("profileImageBase64", base64Image)
                            .addOnSuccessListener(a -> {
                                loadBase64Image(base64Image);
                                uploadProgressText.setVisibility(View.GONE);
                                Toast.makeText(ProfileActivity.this, "Profile picture updated", Toast.LENGTH_SHORT).show();
                            });
                });
            } catch (Exception e) {
                runOnUiThread(() -> uploadProgressText.setVisibility(View.GONE));
            }
        });
    }

    private void loadBase64Image(String base64) {
        try {
            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            profileImage.setPadding(0, 0, 0, 0);
            profileImage.setImageTintList(null);
            profileImage.setImageBitmap(decodedByte);
        } catch (Exception ignored) {}
    }
}