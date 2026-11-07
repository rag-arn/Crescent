package com.crescent.app;

import android.content.Intent;
import android.graphics.Outline;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private TextView authTitle, toggleAuthModeText, togglePasswordText, toggleConfirmPasswordText;
    private EditText nameInput, emailInput, passwordInput, confirmPasswordInput;
    private View confirmPasswordWrapper;
    private Button authActionBtn, googleSignInBtn, guestBtn;
    private ProgressBar loginProgress;
    private ImageView logoImage;

    private boolean isLoginMode = true; // True = Sign In, False = Sign Up
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    try {
                        GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData()).getResult(ApiException.class);
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        showError("Google sign in failed.");
                    }
                } else {
                    showError("Sign in canceled.");
                }
            }
    );

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.rootLayout), null);

        mAuth = FirebaseAuth.getInstance();
        authTitle = findViewById(R.id.authTitle);
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        confirmPasswordWrapper = findViewById(R.id.confirmPasswordWrapper);
        togglePasswordText = findViewById(R.id.togglePasswordText);
        toggleConfirmPasswordText = findViewById(R.id.toggleConfirmPasswordText);

        authActionBtn = findViewById(R.id.authActionBtn);
        toggleAuthModeText = findViewById(R.id.toggleAuthModeText);
        googleSignInBtn = findViewById(R.id.googleSignInBtn);
        guestBtn = findViewById(R.id.guestBtn);
        loginProgress = findViewById(R.id.loginProgress);
        logoImage = findViewById(R.id.logoImage);
        makeLogoRound();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Toggle between Sign In and Sign Up modes
        toggleAuthModeText.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateAuthUI();
        });

        // Toggle Password Visibility
        togglePasswordText.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            toggleVisibility(passwordInput, togglePasswordText, isPasswordVisible);
        });

        // Toggle Confirm Password Visibility
        toggleConfirmPasswordText.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            toggleVisibility(confirmPasswordInput, toggleConfirmPasswordText, isConfirmPasswordVisible);
        });

        // The main button handles BOTH login and registration depending on the mode
        authActionBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirm = confirmPasswordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isLoginMode) {
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (password.length() < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!password.equals(confirm)) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            showLoading(true);

            if (isLoginMode) {
                // Execute Sign In
                mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) goToMain();
                    else showError("Login failed: " + task.getException().getMessage());
                });
            } else {
                // Execute Sign Up
                mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Update Name in Auth Profile (Syncs immediately to PDF)
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name).build();

                            user.updateProfile(profileUpdates).addOnCompleteListener(pTask -> {
                                // Save Name to Firestore for Expert matching
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("uid", user.getUid());
                                userData.put("name", name);
                                userData.put("email", email);
                                userData.put("isExpert", false);

                                FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                                        .set(userData)
                                        .addOnCompleteListener(dbTask -> goToMain());
                            });
                        }
                    } else {
                        showError("Sign up failed: " + task.getException().getMessage());
                    }
                });
            }
        });

        googleSignInBtn.setOnClickListener(v -> {
            showLoading(true);
            signInLauncher.launch(mGoogleSignInClient.getSignInIntent());
        });

        guestBtn.setOnClickListener(v -> {
            showLoading(true);
            mAuth.signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) goToMain();
                else showError("Guest login failed.");
            });
        });
    }

    private void toggleVisibility(EditText input, TextView toggleBtn, boolean isVisible) {
        if (isVisible) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleBtn.setText("HIDE");
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleBtn.setText("SHOW");
        }
        input.setSelection(input.getText().length());
    }

    private void makeLogoRound() {
        logoImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        logoImage.setClipToOutline(true);
    }

    private void updateAuthUI() {
        if (isLoginMode) {
            authTitle.setText("Sign In");
            authActionBtn.setText("Sign In");
            toggleAuthModeText.setText("Don't have an account? Sign Up");
            nameInput.setVisibility(View.GONE);
            confirmPasswordWrapper.setVisibility(View.GONE);
        } else {
            authTitle.setText("Create Account");
            authActionBtn.setText("Sign Up");
            toggleAuthModeText.setText("Already have an account? Sign In");
            nameInput.setVisibility(View.VISIBLE);
            confirmPasswordWrapper.setVisibility(View.VISIBLE);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) goToMain();
                    else showError("Authentication Failed.");
                });
    }

    private void goToMain() {
        Intent main = new Intent(LoginActivity.this, MainActivity.class);
        if (getIntent().getData() != null) main.setData(getIntent().getData());
        startActivity(main);
        finish();
    }

    private void showLoading(boolean loading) {
        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        authActionBtn.setEnabled(!loading);
        toggleAuthModeText.setEnabled(!loading);
        googleSignInBtn.setEnabled(!loading);
        guestBtn.setEnabled(!loading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        showLoading(false);
    }
}