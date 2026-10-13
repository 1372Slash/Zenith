package com.etrisad.zenith.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "daily_usage",
    indices = [
        Index(value = ["date", "packageName"], unique = true),
        Index(value = ["date"]),
        Index(value = ["packageName", "date"])
    ]
)
data class DailyUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val packageName: String,
    val usageTimeMillis: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
