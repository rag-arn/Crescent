package com.crescent.app;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Changes the admin's own e-mail and password. Both need e-mail verification:
 *  - e-mail: a link is sent to the NEW address and the address only changes after it is opened;
 *  - password: a reset link is sent to the current address.
 */
public class AdminAccountActivity extends AppCompatActivity {

    private EditText newEmailInput, currentPasswordInput;
    private Button changeEmailBtn, changePasswordBtn;
    private View progress;
    private FirebaseUser user;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (!Admin.isAdmin(this, user)) { finish(); return; }

        setContentView(R.layout.activity_admin_account);
        Ui.insets(this, findViewById(R.id.rootLayout), findViewById(R.id.headerBar), null);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.currentEmailText)).setText("Current: " + user.getEmail());
        newEmailInput = findViewById(R.id.newEmailInput);
        currentPasswordInput = findViewById(R.id.currentPasswordInput);
        changeEmailBtn = findViewById(R.id.changeEmailBtn);
        changePasswordBtn = findViewById(R.id.changePasswordBtn);
        progress = findViewById(R.id.accountProgress);

        changeEmailBtn.setOnClickListener(v -> changeEmail());
        changePasswordBtn.setOnClickListener(v -> confirmPasswordLink());
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        changeEmailBtn.setEnabled(!busy);
        changePasswordBtn.setEnabled(!busy);
    }

    private void info(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        new ThemedDialog(this).title(title).message(message).positive("OK", null).show();
    }

    // ----- e-mail -----

    private void changeEmail() {
        final String newEmail = newEmailInput.getText().toString().trim();
        final String password = currentPasswordInput.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) { newEmailInput.setError("Enter a valid e-mail"); return; }
        if (newEmail.equalsIgnoreCase(user.getEmail())) { newEmailInput.setError("This is already your e-mail"); return; }
        if (password.isEmpty()) { currentPasswordInput.setError("Enter your current password"); return; }

        setBusy(true);
        // Changing the e-mail is sensitive, so the current password is checked again first
        AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(cred).addOnCompleteListener(auth -> {
            if (!auth.isSuccessful()) {
                setBusy(false);
                currentPasswordInput.setError("Incorrect password");
                return;
            }
            user.verifyBeforeUpdateEmail(newEmail).addOnCompleteListener(v -> {
                setBusy(false);
                if (v.isSuccessful()) {
                    currentPasswordInput.setText("");
                    newEmailInput.setText("");
                    info("Verify the new e-mail",
                            "We sent a verification link to " + newEmail + ".\n\nOpen it to finish the change. "
                                    + "Until then your current e-mail keeps working. After that, sign in with the new e-mail.");
                } else {
                    Exception e = v.getException();
                    String msg = e instanceof FirebaseAuthUserCollisionException ? "That e-mail is already used by another account."
                            : e instanceof FirebaseAuthInvalidCredentialsException ? "That e-mail address is not valid."
                            : "Could not send the verification link. Try again.";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // ----- password -----

    private void confirmPasswordLink() {
        new ThemedDialog(this)
                .title("Change password")
                .message("Send a password link to " + user.getEmail() + "?")
                .negative("Cancel", null)
                .positive("Send", d -> {
                    d.dismiss();
                    setBusy(true);
                    FirebaseAuth.getInstance().sendPasswordResetEmail(user.getEmail()).addOnCompleteListener(t -> {
                        setBusy(false);
                        if (t.isSuccessful()) {
                            info("Check your e-mail", "We sent a link to " + user.getEmail()
                                    + ". Open it (check spam too) and choose a new password.");
                        } else {
                            Toast.makeText(this, "Could not send the e-mail. Try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }
}
