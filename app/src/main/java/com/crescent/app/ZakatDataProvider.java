package com.crescent.app;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

/**
 * Read-only Content Provider that shares the saved Zakat history with other apps.
 *
 *   content://com.crescent.app.provider/zakat        -> every saved calculation (newest first)
 *   content://com.crescent.app.provider/zakat/{id}   -> one calculation
 *
 * Columns: id, createdAt (ms), year, assets, liabilities, netWealth, nisab, zakat, breakdown.
 * The data comes from the local Room table "snapshots", which CrescentRepository keeps in step
 * with the signed-in user's cloud history.
 */
public class ZakatDataProvider extends android.content.ContentProvider {

    public static final String AUTHORITY = "com.crescent.app.provider";
    private static final String PATH_ZAKAT = "zakat";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_ZAKAT);

    private static final String TABLE = "snapshots";            // Room table name (see Snapshot.java)
    private static final String DEFAULT_ORDER = "createdAt DESC";

    private static final int ZAKAT_LIST = 100;
    private static final int ZAKAT_ITEM = 101;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, PATH_ZAKAT, ZAKAT_LIST);
        MATCHER.addURI(AUTHORITY, PATH_ZAKAT + "/#", ZAKAT_ITEM);
    }

    @Override public boolean onCreate() { return true; }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        final Context context = getContext();
        if (context == null) return null;

        final int match = MATCHER.match(uri);
        if (match != ZAKAT_LIST && match != ZAKAT_ITEM) throw new IllegalArgumentException("Unknown URI: " + uri);

        SupportSQLiteQueryBuilder qb = SupportSQLiteQueryBuilder.builder(TABLE)
                .columns(projection)                                          // null = all columns
                .orderBy(sortOrder != null ? sortOrder : DEFAULT_ORDER);

        if (match == ZAKAT_ITEM) {
            String where = "id = " + ContentUris.parseId(uri)
                    + (selection != null && !selection.isEmpty() ? " AND (" + selection + ")" : "");
            qb.selection(where, selectionArgs);
        } else if (selection != null && !selection.isEmpty()) {
            qb.selection(selection, selectionArgs);
        }

        Cursor cursor = CrescentDatabase.get(context.getApplicationContext())
                .getOpenHelper().getReadableDatabase().query(qb.create());
        cursor.setNotificationUri(context.getContentResolver(), uri);        // clients refresh when history changes
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case ZAKAT_LIST: return "vnd.android.cursor.dir/vnd.com.crescent.app.zakat";
            case ZAKAT_ITEM: return "vnd.android.cursor.item/vnd.com.crescent.app.zakat";
            default: return null;
        }
    }

    // --- READ-ONLY PROVIDER: writes are blocked ---

    @Nullable @Override
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