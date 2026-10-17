package com.crescent.app;

import android.content.Context;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CrescentRepository {
    public interface Result<T> { void onResult(T value); }
    private final CrescentDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    public CrescentRepository(Context context) { dao = CrescentDatabase.get(context).dao(); }
    public void saveSnapshot(Snapshot snapshot) { executor.execute(() -> dao.insertSnapshot(snapshot)); }
    public void snapshots(Result<List<Snapshot>> result) { executor.execute(() -> result.onResult(dao.getSnapshots())); }
    public void saveAsset(String category, double amount) { executor.execute(() -> dao.putAsset(new Asset(category, amount, System.currentTimeMillis()))); }
    public void savePrice(PriceRecord price) { executor.execute(() -> dao.putPrice(price)); }
}
