package com.crescent.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CrescentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putAsset(Asset asset);
    @Query("SELECT * FROM assets ORDER BY updatedAt DESC") List<Asset> getAssets();
    @Insert void insertSnapshot(Snapshot snapshot);
    @Query("SELECT * FROM snapshots ORDER BY createdAt DESC") List<Snapshot> getSnapshots();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putPrice(PriceRecord price);
    @Query("SELECT * FROM prices") List<PriceRecord> getPrices();
    @Insert void insertPayment(PaymentLog log);
    @Query("SELECT * FROM payment_logs ORDER BY createdAt DESC") List<PaymentLog> getPayments();
}
