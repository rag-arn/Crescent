package com.crescent.app;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "assets")
public class Asset {
    @PrimaryKey(autoGenerate = true) public int id;
    public String category;
    public double amount;
    public long updatedAt;

    public Asset() {} // Required for Firestore Sync

    public Asset(String category, double amount, long updatedAt) {
        this.category = category; this.amount = amount; this.updatedAt = updatedAt;
    }
}