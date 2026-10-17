package com.crescent.app;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities={Asset.class, Snapshot.class, PriceRecord.class, PaymentLog.class}, version=1, exportSchema=false)
public abstract class CrescentDatabase extends RoomDatabase {
    public abstract CrescentDao dao();
    private static volatile CrescentDatabase INSTANCE;
    public static CrescentDatabase get(Context context) {
        if (INSTANCE == null) synchronized (CrescentDatabase.class) {
            if (INSTANCE == null) INSTANCE = Room.databaseBuilder(context.getApplicationContext(), CrescentDatabase.class, "crescent.db").build();
        }
        return INSTANCE;
    }
}
