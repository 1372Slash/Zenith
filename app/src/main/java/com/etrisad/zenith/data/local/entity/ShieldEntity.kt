package com.etrisad.zenith.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
enum class FocusType {
    SHIELD, GOAL
}

@Keep
enum class LimitPeriod {
    DAILY, WEEKLY
}

@Keep
@Entity(
    tableName = "shields",
    indices = [Index(value = ["packageName"], unique = true)]
)
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
    val goalReminderPeriodMinutes: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val lastGoalReminderTimestamp: Long = 0L,
    val isDelayAppEnabled: Boolean = false,
    val lastDelayStartTimestamp: Long = 0L,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastStreakUpdateTimestamp: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val lastSessionEndTimestamp: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isPaused: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val pauseEndTimestamp: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isGoalCallerEnabled: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "1")
    val isGoalCallerSoundEnabled: Boolean = true,
    val goalCallerSoundUri: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "DAILY")
    val limitPeriod: LimitPeriod = LimitPeriod.DAILY,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val timeAdded: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isWebsite: Boolean = false,
    val url: String? = null
)
