package com.crescent.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private TextView authTitle, toggleAuthModeText;
    private EditText emailInput, passwordInput;
    private Button authActionBtn, googleSignInBtn, guestBtn;
    private ProgressBar loginProgress;

    private boolean isLoginMode = true; // True = Sign In, False = Sign Up

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
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        authActionBtn = findViewById(R.id.authActionBtn);
        toggleAuthModeText = findViewById(R.id.toggleAuthModeText);
        googleSignInBtn = findViewById(R.id.googleSignInBtn);
        guestBtn = findViewById(R.id.guestBtn);
        loginProgress = findViewById(R.id.loginProgress);

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

        // The main button handles BOTH login and registration depending on the mode
        authActionBtn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isLoginMode && password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
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
                    if (task.isSuccessful()) goToMain();
                    else showError("Sign up failed: " + task.getException().getMessage());
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

    private void updateAuthUI() {
        if (isLoginMode) {
            authTitle.setText("Sign In");
            authActionBtn.setText("Sign In");
            toggleAuthModeText.setText("Don't have an account? Sign Up");
        } else {
            authTitle.setText("Create Account");
            authActionBtn.setText("Sign Up");
            toggleAuthModeText.setText("Already have an account? Sign In");
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
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
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