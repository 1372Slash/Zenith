package com.etrisad.zenith.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Keep
enum class ScheduleMode {
    BLOCK, ALLOW
}

@Keep
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val packageNames: List<String>,
    val startTime: String,
    val endTime: String,
    val mode: ScheduleMode,
    val isActive: Boolean = true,
    val interceptNotifications: Boolean = false,
    val emergencyUseCount: Int = 0,
    val maxEmergencyUses: Int = 3,
    val linkedGoalPackageName: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "1,2,3,4,5,6,7")
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
)
