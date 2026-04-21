package com.etrisad.zenith.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

enum class ScheduleMode {
    BLOCK, ALLOW
}

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val packageNames: List<String>, // Needs a TypeConverter for List<String>
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val mode: ScheduleMode,
    val isActive: Boolean = true,
    val emergencyUseCount: Int = 0,
    val maxEmergencyUses: Int = 3
)
