package com.crescent.app;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "payment_logs")
public class PaymentLog {
    @PrimaryKey(autoGenerate = true) public int id;
    public long createdAt;
    public double amount;
    public String note;
    public PaymentLog(long createdAt, double amount, String note) { this.createdAt=createdAt; this.amount=amount; this.note=note; }
}
