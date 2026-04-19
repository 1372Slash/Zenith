package com.etrisad.zenith.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FocusType {
    SHIELD, GOAL
}

@Entity(tableName = "shields")
data class ShieldEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val type: FocusType = FocusType.SHIELD,
    val timeLimitMinutes: Int,
    val emergencyUseCount: Int = 0,
    val maxEmergencyUses: Int = 3,
    val isRemindersEnabled: Boolean = true,
    val isStrictModeEnabled: Boolean = false,
    val isAutoQuitEnabled: Boolean = false,
    val remainingTimeMillis: Long = 0L,
    val lastUsedTimestamp: Long = 0L,
    val maxUsesPerPeriod: Int = 5,
    val refreshPeriodMinutes: Int = 60,
    val currentPeriodUses: Int = 0,
    val lastPeriodResetTimestamp: Long = 0L,
    val lastEmergencyRechargeTimestamp: Long = 0L,
    val goalReminderPeriodMinutes: Int = 0 // New field for Goal
)
