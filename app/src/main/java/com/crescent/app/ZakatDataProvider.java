package com.crescent.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ZakatDataProvider extends ContentProvider {

    // The unique authority matching the AndroidManifest.xml
    private static final String AUTHORITY = "com.crescent.app.provider";

    // The specific path other apps will query
    private static final String PATH_ZAKAT = "zakat";

    // The URI representation: content://com.crescent.app.provider/zakat
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_ZAKAT);

    private static final int ZAKAT_CODE = 100;
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, PATH_ZAKAT, ZAKAT_CODE);
    }

    @Override
    public boolean onCreate() {
        return true; // Provider is successfully loaded
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {

        if (uriMatcher.match(uri) == ZAKAT_CODE) {
            Context context = getContext();
            if (context == null) return null;

            try {
                // Accessing Room's underlying SQLite database to get the Zakat history directly
                Cursor cursor = CrescentDatabase.get(context.getApplicationContext())
                        .getOpenHelper()
                        .getReadableDatabase()
                        .query("SELECT * FROM Snapshot ORDER BY year DESC");

                if (cursor != null) {
                    // Let the cursor know to update if the underlying database changes
                    cursor.setNotificationUri(context.getContentResolver(), uri);
                }
                return cursor;

            } catch (Exception e) {
                // Fallback in case Room created the table in all lowercase
                try {
                    return CrescentDatabase.get(context.getApplicationContext())
                            .getOpenHelper()
                            .getReadableDatabase()
                            .query("SELECT * FROM snapshot ORDER BY year DESC");
                } catch (Exception e2) {
                    MatrixCursor errorCursor = new MatrixCursor(new String[]{"error"});
                    errorCursor.addRow(new Object[]{"Could not read Zakat table: " + e.getMessage()});
                    return errorCursor;
                }
            }
        }

        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        if (uriMatcher.match(uri) == ZAKAT_CODE) {
            return "vnd.android.cursor.dir/vnd.com.crescent.app.zakat";
        }
        return null;
    }

    // --- READ-ONLY PROVIDER: The following operations are blocked ---

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("This Content Provider is read-only.");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("This Content Provider is read-only.");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("This Content Provider is read-only.");
    }
}