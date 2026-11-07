package com.crescent.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.crescent.app.databinding.ActivityContactPickerBinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets the person choose a contact (and which of its phone numbers) to send a Zakat estimate to.
 * Contacts are read from the system Contacts content provider with a ContentResolver query, and only
 * after the READ_CONTACTS runtime permission is granted. Nothing is uploaded or stored: the chosen name and
 * number are returned to the caller in the result Intent and the list is discarded when the screen closes.
 */
public class ContactPickerActivity extends AppCompatActivity {
    static final String EXTRA_NAME = "contact_name";
    static final String EXTRA_NUMBER = "contact_number";

    private static final class Entry {
        final String name, number, typeLabel;
        Entry(String name, String number, String typeLabel) { this.name = name; this.number = number; this.typeLabel = typeLabel; }
    }

    private ActivityContactPickerBinding b;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private ContactAdapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean loading, loaded, askedOnce;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                askedOnce = true;
                if (granted) loadContacts(); else showPermissionNeeded();
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityContactPickerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.insets(this, b.rootLayout, b.headerBar, null);

        b.backButton.setOnClickListener(v -> finish());
        adapter = new ContactAdapter();
        b.contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        b.contactsRecyclerView.setAdapter(adapter);
        b.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        // Also covers coming back from the app-settings screen after the person changed the permission.
        if (hasPermission()) { if (!loaded && !loading) loadContacts(); }
        else showPermissionNeeded();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    // ---- permission explanation ----

    private void showPermissionNeeded() {
        b.loadingProgress.setVisibility(View.GONE);
        b.contactsRecyclerView.setVisibility(View.GONE);
        b.searchInput.setVisibility(View.GONE);
        b.emptyText.setVisibility(View.GONE);
        b.permissionPanel.setVisibility(View.VISIBLE);

        // After a denial with "don't ask again" the system prompt no longer appears; send the person to settings instead.
        final boolean blocked = askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS);
        b.permissionMessage.setText("Crescent reads your contacts only so you can choose who to send this estimate to. "
                + "Contacts stay on your phone and are never uploaded or saved."
                + (blocked ? "\n\nAccess is currently turned off. You can allow it in the app settings." : ""));
        b.permissionButton.setText(blocked ? "Open app settings" : "Allow access");
        b.permissionButton.setOnClickListener(v -> {
            if (blocked) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
            } else {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });
    }

    // ---- loading (ContentResolver query on a background thread) ----

    private void loadContacts() {
        if (loading) return;
        loading = true;
        b.permissionPanel.setVisibility(View.GONE);
        b.emptyText.setVisibility(View.GONE);
        b.loadingProgress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            final List<Entry> result = queryContacts();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                if (result == null) { showPermissionNeeded(); return; }     // permission was revoked while loading
                loaded = true;
                all.clear();
                all.addAll(result);
                b.loadingProgress.setVisibility(View.GONE);
                b.searchInput.setVisibility(View.VISIBLE);
                applyFilter();
            });
        });
    }

    /** Returns every phone number in the Contacts provider (one row per distinct number), or null if access was refused. */
    private List<Entry> queryContacts() {
        final List<Entry> out = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final String[] projection = { Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL };
        try (Cursor c = getContentResolver().query(Phone.CONTENT_URI, projection, null, null, Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
            if (c == null) return out;
            final int iId = c.getColumnIndexOrThrow(Phone.CONTACT_ID);
            final int iName = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME);
            final int iNum = c.getColumnIndexOrThrow(Phone.NUMBER);
            final int iType = c.getColumnIndexOrThrow(Phone.TYPE);
            final int iLabel = c.getColumnIndexOrThrow(Phone.LABEL);
            while (c.moveToNext()) {
                String number = c.getString(iNum);
                if (number == null || number.trim().isEmpty()) continue;
                number = number.trim();
                // The same number is often stored once per account (Google, SIM, ...): show it once per contact.
                if (!seen.add(c.getLong(iId) + "|" + number.replaceAll("[^0-9+]", ""))) continue;
                String name = c.getString(iName);
                if (name == null || name.trim().isEmpty()) name = number;
                out.add(new Entry(name.trim(), number, String.valueOf(Phone.getTypeLabel(getResources(), c.getInt(iType), c.getString(iLabel)))));
            }
        } catch (SecurityException e) {
            return null;
        } catch (RuntimeException e) {
            runOnUiThread(() -> Toast.makeText(this, "Could not read contacts.", Toast.LENGTH_SHORT).show());
        }
        return out;
    }

    // ---- search ----

    private void applyFilter() {
        final String q = b.searchInput.getText().toString().trim().toLowerCase(Locale.getDefault());
        final String qNum = q.replace(" ", "").replace("-", "");
        shown.clear();
        for (Entry e : all) {
            if (q.isEmpty() || e.name.toLowerCase(Locale.getDefault()).contains(q)
                    || (!qNum.isEmpty() && e.number.replace(" ", "").replace("-", "").contains(qNum))) {
                shown.add(e);
            }
        }
        adapter.notifyDataSetChanged();
        final boolean empty = shown.isEmpty();
        b.contactsRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.emptyText.setText(all.isEmpty() ? "No contacts with a phone number found." : "No matching contacts.");
        b.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void pick(Entry e) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_NAME, e.name).putExtra(EXTRA_NUMBER, e.number));
        finish();
    }

    // ---- RecyclerView ----

    private class ContactAdapter extends RecyclerView.Adapter<ContactHolder> {
        @NonNull @Override public ContactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ContactHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull ContactHolder h, int position) {
            final Entry e = shown.get(position);
            h.name.setText(e.name);
            h.number.setText(e.typeLabel.isEmpty() ? e.number : e.number + "  ·  " + e.typeLabel);
            h.initial.setText(e.name.isEmpty() ? "?" : new String(Character.toChars(e.name.codePointAt(0))).toUpperCase(Locale.getDefault()));
            h.itemView.setOnClickListener(v -> pick(e));
        }

        @Override public int getItemCount() { return shown.size(); }
    }

    private static class ContactHolder extends RecyclerView.ViewHolder {
        final TextView name, number, initial;
        ContactHolder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.contactName);
            number = v.findViewById(R.id.contactNumber);
            initial = v.findViewById(R.id.contactInitial);
        }
    }
}