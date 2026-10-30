package com.crescent.app;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"unchecked", "deprecation"})
public final class CrescentDao_Impl implements CrescentDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Asset> __insertionAdapterOfAsset;

  private final EntityInsertionAdapter<Snapshot> __insertionAdapterOfSnapshot;

  private final EntityInsertionAdapter<PriceRecord> __insertionAdapterOfPriceRecord;

  private final EntityInsertionAdapter<PaymentLog> __insertionAdapterOfPaymentLog;

  public CrescentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAsset = new EntityInsertionAdapter<Asset>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `assets` (`id`,`category`,`amount`,`updatedAt`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Asset entity) {
        statement.bindLong(1, entity.id);
        if (entity.category == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.category);
        }
        statement.bindDouble(3, entity.amount);
        statement.bindLong(4, entity.updatedAt);
      }
    };
    this.__insertionAdapterOfSnapshot = new EntityInsertionAdapter<Snapshot>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `snapshots` (`id`,`createdAt`,`year`,`assets`,`liabilities`,`netWealth`,`nisab`,`zakat`,`breakdown`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Snapshot entity) {
        statement.bindLong(1, entity.id);
        statement.bindLong(2, entity.createdAt);
        statement.bindLong(3, entity.year);
        statement.bindDouble(4, entity.assets);
        statement.bindDouble(5, entity.liabilities);
        statement.bindDouble(6, entity.netWealth);
        statement.bindDouble(7, entity.nisab);
        statement.bindDouble(8, entity.zakat);
        if (entity.breakdown == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.breakdown);
        }
      }
    };
    this.__insertionAdapterOfPriceRecord = new EntityInsertionAdapter<PriceRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `prices` (`metal`,`pricePerGram`,`updatedAt`,`manualOverride`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final PriceRecord entity) {
        if (entity.metal == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.metal);
        }
        statement.bindDouble(2, entity.pricePerGram);
        statement.bindLong(3, entity.updatedAt);
        final int _tmp = entity.manualOverride ? 1 : 0;
        statement.bindLong(4, _tmp);
      }
    };
    this.__insertionAdapterOfPaymentLog = new EntityInsertionAdapter<PaymentLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `payment_logs` (`id`,`createdAt`,`amount`,`note`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final PaymentLog entity) {
        statement.bindLong(1, entity.id);
        statement.bindLong(2, entity.createdAt);
        statement.bindDouble(3, entity.amount);
        if (entity.note == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.note);
        }
      }
    };
  }

  @Override
  public void putAsset(final Asset asset) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfAsset.insert(asset);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insertSnapshot(final Snapshot snapshot) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfSnapshot.insert(snapshot);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void putPrice(final PriceRecord price) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfPriceRecord.insert(price);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insertPayment(final PaymentLog log) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfPaymentLog.insert(log);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public List<Asset> getAssets() {
    final String _sql = "SELECT * FROM assets ORDER BY updatedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
      final List<Asset> _result = new ArrayList<Asset>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Asset _item;
        _item = new Asset();
        _item.id = _cursor.getInt(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        _item.amount = _cursor.getDouble(_cursorIndexOfAmount);
        _item.updatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<Snapshot> getSnapshots() {
    final String _sql = "SELECT * FROM snapshots ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
      final int _cursorIndexOfAssets = CursorUtil.getColumnIndexOrThrow(_cursor, "assets");
      final int _cursorIndexOfLiabilities = CursorUtil.getColumnIndexOrThrow(_cursor, "liabilities");
      final int _cursorIndexOfNetWealth = CursorUtil.getColumnIndexOrThrow(_cursor, "netWealth");
      final int _cursorIndexOfNisab = CursorUtil.getColumnIndexOrThrow(_cursor, "nisab");
      final int _cursorIndexOfZakat = CursorUtil.getColumnIndexOrThrow(_cursor, "zakat");
      final int _cursorIndexOfBreakdown = CursorUtil.getColumnIndexOrThrow(_cursor, "breakdown");
      final List<Snapshot> _result = new ArrayList<Snapshot>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Snapshot _item;
        _item = new Snapshot();
        _item.id = _cursor.getInt(_cursorIndexOfId);
        _item.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _item.year = _cursor.getInt(_cursorIndexOfYear);
        _item.assets = _cursor.getDouble(_cursorIndexOfAssets);
        _item.liabilities = _cursor.getDouble(_cursorIndexOfLiabilities);
        _item.netWealth = _cursor.getDouble(_cursorIndexOfNetWealth);
        _item.nisab = _cursor.getDouble(_cursorIndexOfNisab);
        _item.zakat = _cursor.getDouble(_cursorIndexOfZakat);
        if (_cursor.isNull(_cursorIndexOfBreakdown)) {
          _item.breakdown = null;
        } else {
          _item.breakdown = _cursor.getString(_cursorIndexOfBreakdown);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<PriceRecord> getPrices() {
    final String _sql = "SELECT * FROM prices";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfMetal = CursorUtil.getColumnIndexOrThrow(_cursor, "metal");
      final int _cursorIndexOfPricePerGram = CursorUtil.getColumnIndexOrThrow(_cursor, "pricePerGram");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
      final int _cursorIndexOfManualOverride = CursorUtil.getColumnIndexOrThrow(_cursor, "manualOverride");
      final List<PriceRecord> _result = new ArrayList<PriceRecord>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final PriceRecord _item;
        final String _tmpMetal;
        if (_cursor.isNull(_cursorIndexOfMetal)) {
          _tmpMetal = null;
        } else {
          _tmpMetal = _cursor.getString(_cursorIndexOfMetal);
        }
        final double _tmpPricePerGram;
        _tmpPricePerGram = _cursor.getDouble(_cursorIndexOfPricePerGram);
        final long _tmpUpdatedAt;
        _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
        final boolean _tmpManualOverride;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfManualOverride);
        _tmpManualOverride = _tmp != 0;
        _item = new PriceRecord(_tmpMetal,_tmpPricePerGram,_tmpUpdatedAt,_tmpManualOverride);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<PaymentLog> getPayments() {
    final String _sql = "SELECT * FROM payment_logs ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
      final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
      final List<PaymentLog> _result = new ArrayList<PaymentLog>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final PaymentLog _item;
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final double _tmpAmount;
        _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
        final String _tmpNote;
        if (_cursor.isNull(_cursorIndexOfNote)) {
          _tmpNote = null;
        } else {
          _tmpNote = _cursor.getString(_cursorIndexOfNote);
        }
        _item = new PaymentLog(_tmpCreatedAt,_tmpAmount,_tmpNote);
        _item.id = _cursor.getInt(_cursorIndexOfId);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
