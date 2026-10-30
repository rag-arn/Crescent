package com.crescent.app;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "snapshots")
public class Snapshot {
    @PrimaryKey(autoGenerate = true) public int id;
    public long createdAt;
    public int year;
    public double assets, liabilities, netWealth, nisab, zakat;
    public String breakdown;

    public Snapshot() {} // Required for Firestore Sync

    public Snapshot(long createdAt, int year, double assets, double liabilities, double netWealth, double nisab, double zakat, String breakdown) {
        this.createdAt=createdAt; this.year=year; this.assets=assets; this.liabilities=liabilities; this.netWealth=netWealth; this.nisab=nisab; this.zakat=zakat; this.breakdown=breakdown;
    }
}