package com.crescent.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prices")
public class PriceRecord {
    @PrimaryKey
    @NonNull
    public String metal;
    public double pricePerGram;
    public long updatedAt;
    public boolean manualOverride;
    public PriceRecord(@NonNull String metal, double pricePerGram, long updatedAt, boolean manualOverride) {
        this.metal=metal; this.pricePerGram=pricePerGram; this.updatedAt=updatedAt; this.manualOverride=manualOverride;
    }
}
