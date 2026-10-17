package com.crescent.app;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unchecked", "deprecation"})
public final class CrescentDatabase_Impl extends CrescentDatabase {
  private volatile CrescentDao _crescentDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT, `amount` REAL NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, `year` INTEGER NOT NULL, `assets` REAL NOT NULL, `liabilities` REAL NOT NULL, `netWealth` REAL NOT NULL, `nisab` REAL NOT NULL, `zakat` REAL NOT NULL, `breakdown` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `prices` (`metal` TEXT NOT NULL, `pricePerGram` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, `manualOverride` INTEGER NOT NULL, PRIMARY KEY(`metal`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `payment_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, `amount` REAL NOT NULL, `note` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6d9ea16c85b23b76711fcf0a31b0e336')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `assets`");
        db.execSQL("DROP TABLE IF EXISTS `snapshots`");
        db.execSQL("DROP TABLE IF EXISTS `prices`");
        db.execSQL("DROP TABLE IF EXISTS `payment_logs`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsAssets = new HashMap<String, TableInfo.Column>(4);
        _columnsAssets.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("category", new TableInfo.Column("category", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAssets = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAssets = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAssets = new TableInfo("assets", _columnsAssets, _foreignKeysAssets, _indicesAssets);
        final TableInfo _existingAssets = TableInfo.read(db, "assets");
        if (!_infoAssets.equals(_existingAssets)) {
          return new RoomOpenHelper.ValidationResult(false, "assets(com.crescent.app.Asset).\n"
                  + " Expected:\n" + _infoAssets + "\n"
                  + " Found:\n" + _existingAssets);
        }
        final HashMap<String, TableInfo.Column> _columnsSnapshots = new HashMap<String, TableInfo.Column>(9);
        _columnsSnapshots.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("year", new TableInfo.Column("year", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("assets", new TableInfo.Column("assets", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("liabilities", new TableInfo.Column("liabilities", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("netWealth", new TableInfo.Column("netWealth", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("nisab", new TableInfo.Column("nisab", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("zakat", new TableInfo.Column("zakat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSnapshots.put("breakdown", new TableInfo.Column("breakdown", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSnapshots = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSnapshots = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSnapshots = new TableInfo("snapshots", _columnsSnapshots, _foreignKeysSnapshots, _indicesSnapshots);
        final TableInfo _existingSnapshots = TableInfo.read(db, "snapshots");
        if (!_infoSnapshots.equals(_existingSnapshots)) {
          return new RoomOpenHelper.ValidationResult(false, "snapshots(com.crescent.app.Snapshot).\n"
                  + " Expected:\n" + _infoSnapshots + "\n"
                  + " Found:\n" + _existingSnapshots);
        }
        final HashMap<String, TableInfo.Column> _columnsPrices = new HashMap<String, TableInfo.Column>(4);
        _columnsPrices.put("metal", new TableInfo.Column("metal", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrices.put("pricePerGram", new TableInfo.Column("pricePerGram", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrices.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrices.put("manualOverride", new TableInfo.Column("manualOverride", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPrices = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPrices = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPrices = new TableInfo("prices", _columnsPrices, _foreignKeysPrices, _indicesPrices);
        final TableInfo _existingPrices = TableInfo.read(db, "prices");
        if (!_infoPrices.equals(_existingPrices)) {
          return new RoomOpenHelper.ValidationResult(false, "prices(com.crescent.app.PriceRecord).\n"
                  + " Expected:\n" + _infoPrices + "\n"
                  + " Found:\n" + _existingPrices);
        }
        final HashMap<String, TableInfo.Column> _columnsPaymentLogs = new HashMap<String, TableInfo.Column>(4);
        _columnsPaymentLogs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPaymentLogs.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPaymentLogs.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPaymentLogs.put("note", new TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPaymentLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPaymentLogs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPaymentLogs = new TableInfo("payment_logs", _columnsPaymentLogs, _foreignKeysPaymentLogs, _indicesPaymentLogs);
        final TableInfo _existingPaymentLogs = TableInfo.read(db, "payment_logs");
        if (!_infoPaymentLogs.equals(_existingPaymentLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "payment_logs(com.crescent.app.PaymentLog).\n"
                  + " Expected:\n" + _infoPaymentLogs + "\n"
                  + " Found:\n" + _existingPaymentLogs);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "6d9ea16c85b23b76711fcf0a31b0e336", "f234f885ec39c8f76505b31862c2341a");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "assets","snapshots","prices","payment_logs");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `assets`");
      _db.execSQL("DELETE FROM `snapshots`");
      _db.execSQL("DELETE FROM `prices`");
      _db.execSQL("DELETE FROM `payment_logs`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(CrescentDao.class, CrescentDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public CrescentDao dao() {
    if (_crescentDao != null) {
      return _crescentDao;
    } else {
      synchronized(this) {
        if(_crescentDao == null) {
          _crescentDao = new CrescentDao_Impl(this);
        }
        return _crescentDao;
      }
    }
  }
}
