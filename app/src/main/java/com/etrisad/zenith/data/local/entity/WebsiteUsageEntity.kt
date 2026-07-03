package com.etrisad.zenith.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "website_usage",
    indices = [Index(value = ["date", "domain"], unique = true)]
)
data class WebsiteUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val domain: String,
    val usageTimeMillis: Long,
    val lastUpdated: Long
)
