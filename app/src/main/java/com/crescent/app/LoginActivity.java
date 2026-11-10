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
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import java.util.regex.Pattern;

public class LoginActivity extends AppCompatActivity {
    // name@domain.tld : no spaces, a real domain with a dot, and a letters-only ending of 2+ characters
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9]([A-Za-z0-9\\-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9\\-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$");

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private TextView authTitle, toggleAuthModeText, togglePasswordText, toggleConfirmPasswordText, forgotPasswordText;
    private EditText nameInput, emailInput, passwordInput, confirmPasswordInput;
    private View confirmPasswordWrapper;
    private TextView passwordErrorText;   // red "Incorrect password" under the password field
    private Button authActionBtn, googleSignInBtn, guestBtn;
    private ProgressBar loginProgress;
    private ImageView logoImage;

    private boolean isLoginMode = true; // True = Sign In, False = Sign Up
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK && result.getData() == null) {
                    showError("Google sign-in canceled.");
                    return;
                }
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account == null || account.getIdToken() == null) {
                        showError("Google sign-in failed. Please try again.");
                    } else {
                        firebaseAuthWithGoogle(account.getIdToken());
                    }
                } catch (ApiException e) {
                    int code = e.getStatusCode();
                    if (code == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) showError("Google sign-in canceled.");
                    else if (code == GoogleSignInStatusCodes.NETWORK_ERROR) showError("No internet connection. Please try again.");
                    else showError("Google sign-in failed (code " + code + "). Please try again.");
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
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        passwordErrorText = findViewById(R.id.passwordErrorText);

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

        forgotPasswordText.setOnClickListener(v -> showForgotPasswordDialog());

        // The red message disappears as soon as the person edits the email or password
        android.text.TextWatcher clearPasswordError = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { passwordErrorText.setVisibility(View.GONE); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
        passwordInput.addTextChangedListener(clearPasswordError);
        emailInput.addTextChangedListener(clearPasswordError);

        if (getIntent().getBooleanExtra("disabled", false)) showDisabledDialog();   // sent here after being disabled

        // Rotation: bring back the Sign In / Sign Up mode and the show/hide password states
        // (typed text is restored by the system).
        if (savedInstanceState != null) {
            isLoginMode = savedInstanceState.getBoolean("isLoginMode", true);
            isPasswordVisible = savedInstanceState.getBoolean("isPasswordVisible", false);
            isConfirmPasswordVisible = savedInstanceState.getBoolean("isConfirmPasswordVisible", false);
            updateAuthUI();
            toggleVisibility(passwordInput, togglePasswordText, isPasswordVisible);
            toggleVisibility(confirmPasswordInput, toggleConfirmPasswordText, isConfirmPasswordVisible);
        }

        // The main button handles BOTH login and registration depending on the mode
        authActionBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim().toLowerCase();
            String password = passwordInput.getText().toString().trim();
            String confirm = confirmPasswordInput.getText().toString().trim();
            passwordErrorText.setVisibility(View.GONE);

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isValidEmail(email)) {
                emailInput.setError("Enter a valid email address (for example name@gmail.com)");
                emailInput.requestFocus();
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

            if (isLoginMode) signIn(email, password);
            else signUp(name, email, password);
        });

        googleSignInBtn.setOnClickListener(v -> {
            showLoading(true);
            // Sign out of the Google client first so the account chooser always opens:
            // the same Google account (or a different one) can be picked every time.
            mGoogleSignInClient.signOut().addOnCompleteListener(t ->
                    signInLauncher.launch(mGoogleSignInClient.getSignInIntent()));
        });

        guestBtn.setOnClickListener(v -> {
            showLoading(true);
            mAuth.signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) goToMain();
                else showError("Guest login failed.");
            });
        });
    }

    // ---------------- Email / password ----------------

    private boolean isValidEmail(String email) {
        if (email.length() > 254 || email.contains("..")) return false;
        int at = email.indexOf('@');
        if (at < 1) return false;
        String local = email.substring(0, at);
        if (local.startsWith(".") || local.endsWith(".")) return false;
        return EMAIL.matcher(email).matches();
    }

    private void signIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Exception e = task.getException();
                showError(authMessage(e));                                   // toast
                if (e instanceof FirebaseAuthInvalidCredentialsException) {  // plus red text under the password
                    passwordErrorText.setVisibility(View.VISIBLE);
                }
                return;
            }
            FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
            if (user != null && !user.isEmailVerified()) {
                showLoading(false);
                showUnverifiedDialog(user);     // only verified (real) email addresses may use the app
            } else {
                goToMain();
            }
        });
    }

    private void signUp(String name, String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                showError(authMessage(task.getException()));
                return;
            }
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) { showError("Sign up failed. Please try again."); return; }

            UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder().setDisplayName(name).build();
            user.updateProfile(profile).addOnCompleteListener(p ->
                    user.sendEmailVerification().addOnCompleteListener(v -> {
                        boolean sent = v.isSuccessful();
                        mAuth.signOut();                       // no access until the email is verified
                        showLoading(false);
                        isLoginMode = true;
                        updateAuthUI();
                        passwordInput.setText("");
                        confirmPasswordInput.setText("");
                        showVerificationSentDialog(email, sent);
                    }));
        });
    }

    private void showVerificationSentDialog(String email, boolean sent) {
        if (isFinishing() || isDestroyed()) return;
        String message = sent
                ? "We sent a verification link to " + email + ".\n\nOpen the email (check your spam folder too), "
                + "tap the link, then come back and sign in."
                : "Your account was created, but we could not send the verification email to " + email
                + ".\n\nTry signing in and choose \"Resend email\".";
        new ThemedDialog(this)
                .title(sent ? "Verify your email" : "Verification email not sent")
                .message(message)
                .positive("OK", null)
                .show();
    }

    private void showUnverifiedDialog(FirebaseUser user) {
        if (isFinishing() || isDestroyed()) return;
        new ThemedDialog(this)
                .title("Email not verified")
                .message("Please verify " + user.getEmail() + " using the link we emailed you, then sign in again.")
                .negative("OK", null)
                .positive("Resend email", d -> {      // stays open while sending
                    d.positiveButton().setEnabled(false);
                    user.sendEmailVerification().addOnCompleteListener(t -> {
                        Toast.makeText(this, t.isSuccessful()
                                ? "Verification email sent to " + user.getEmail()
                                : authMessage(t.getException()), Toast.LENGTH_LONG).show();
                        d.dismiss();
                    });
                })
                .onDismiss(() -> mAuth.signOut())     // never stay signed in while unverified
                .show();
    }

    // ---------------- Forgot password ----------------

    private void showForgotPasswordDialog() {
        if (isFinishing() || isDestroyed()) return;
        new ThemedDialog(this)
                .title("Reset password")
                .message("Enter your account email and we will send you a link to choose a new password.")
                .emailInput("Email address", emailInput.getText().toString().trim())
                .negative("Cancel", null)
                .positive("Send link", d -> {
                    String email = d.inputText().toLowerCase();
                    if (!isValidEmail(email)) {
                        d.setError("Enter a valid email address");
                        return;
                    }
                    d.setError(null);
                    d.positiveButton().setEnabled(false);
                    mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                        d.dismiss();
                        Exception e = task.getException();
                        if (e instanceof FirebaseNetworkException) {
                            Toast.makeText(this, "No internet connection. Please try again.", Toast.LENGTH_LONG).show();
                        } else if (e instanceof FirebaseTooManyRequestsException) {
                            Toast.makeText(this, "Too many attempts. Please wait a moment and try again.", Toast.LENGTH_LONG).show();
                        } else {
                            // Same message whether or not the address has an account, so emails cannot be probed.
                            new ThemedDialog(this)
                                    .title("Check your email")
                                    .message("If an account exists for " + email + ", a password reset link is on its way. "
                                            + "Check your inbox and spam folder.")
                                    .positive("OK", null)
                                    .show();
                        }
                    });
                })
                .show();
    }

    // ---------------- Google ----------------

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        goToMain();
                    } else {
                        Exception e = task.getException();
                        mGoogleSignInClient.signOut();
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            showError("This email is already registered with another sign-in method. "
                                    + "Sign in with your email and password instead.");
                        } else {
                            showError(authMessage(e));
                        }
                    }
                });
    }

    // ---------------- Helpers ----------------

    private String authMessage(Exception e) {
        if (e instanceof FirebaseNetworkException) return "No internet connection. Please try again.";
        if (e instanceof FirebaseTooManyRequestsException) return "Too many attempts. Please wait a moment and try again.";
        if (e instanceof FirebaseAuthUserCollisionException) return "An account with this email already exists. Sign in instead, or reset your password.";
        if (e instanceof FirebaseAuthWeakPasswordException) return "Password is too weak. Use at least 6 characters.";
        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            return "ERROR_USER_DISABLED".equals(code) ? "This account has been disabled." : "No account found with this email.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) return "Incorrect email or password.";
        return e != null && e.getMessage() != null ? e.getMessage() : "Something went wrong. Please try again.";
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

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("isLoginMode", isLoginMode);
        out.putBoolean("isPasswordVisible", isPasswordVisible);
        out.putBoolean("isConfirmPasswordVisible", isConfirmPasswordVisible);
    }

    private void updateAuthUI() {
        if (passwordErrorText != null) passwordErrorText.setVisibility(View.GONE);
        if (isLoginMode) {
            authTitle.setText("Sign In");
            authActionBtn.setText("Sign In");
            toggleAuthModeText.setText("Don't have an account? Sign Up");
            nameInput.setVisibility(View.GONE);
            confirmPasswordWrapper.setVisibility(View.GONE);
            forgotPasswordText.setVisibility(View.VISIBLE);
        } else {
            authTitle.setText("Create Account");
            authActionBtn.setText("Sign Up");
            toggleAuthModeText.setText("Already have an account? Sign In");
            nameInput.setVisibility(View.VISIBLE);
            confirmPasswordWrapper.setVisibility(View.VISIBLE);
            forgotPasswordText.setVisibility(View.GONE);
        }
    }

    private void goToMain() {
        final FirebaseUser u = mAuth.getCurrentUser();
        if (u == null || u.isAnonymous()) { openMain(); return; }

        showLoading(true);
        Admin.verify(this, u, isAdmin -> {
            if (isAdmin) {                                   // admin account: open the admin dashboard
                startActivity(new Intent(LoginActivity.this, AdminDashboardActivity.class));
                finish();
                return;
            }
            // A disabled account is not allowed in
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(u.getUid()).get()
                    .addOnCompleteListener(t -> {
                        if (t.isSuccessful() && t.getResult() != null
                                && Boolean.TRUE.equals(t.getResult().getBoolean("disabled"))) {
                            mAuth.signOut();
                            showLoading(false);
                            showDisabledDialog();
                            return;
                        }
                        openMain();
                    });
        });
    }

    private void openMain() {
        Intent main = new Intent(LoginActivity.this, MainActivity.class);
        if (getIntent().getData() != null) main.setData(getIntent().getData());
        startActivity(main);
        finish();
    }

    private void showDisabledDialog() {
        if (isFinishing() || isDestroyed()) return;
        new ThemedDialog(this)
                .title("Account disabled")
                .message("Your account has been disabled by the administrator. If you think this is a mistake, please contact support.")
                .positive("OK", null)
                .show();
    }

    private void showLoading(boolean loading) {
        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        authActionBtn.setEnabled(!loading);
        toggleAuthModeText.setEnabled(!loading);
        googleSignInBtn.setEnabled(!loading);
        guestBtn.setEnabled(!loading);
        forgotPasswordText.setEnabled(!loading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        showLoading(false);
    }
}