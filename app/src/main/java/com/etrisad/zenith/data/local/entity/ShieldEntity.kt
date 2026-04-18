package com.etrisad.zenith.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shields")
data class ShieldEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val timeLimitMinutes: Int,
    val emergencyUseCount: Int = 0,
    val isRemindersEnabled: Boolean = true,
    val isStrictModeEnabled: Boolean = false,
    val isAutoQuitEnabled: Boolean = false,
    val remainingTimeMillis: Long = 0L,
    val lastUsedTimestamp: Long = 0L,
    val maxUsesPerPeriod: Int = 5,
    val refreshPeriodMinutes: Int = 60,
    val currentPeriodUses: Int = 0,
    val lastPeriodResetTimestamp: Long = 0L
)
