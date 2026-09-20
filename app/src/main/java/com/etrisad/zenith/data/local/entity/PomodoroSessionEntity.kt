package com.etrisad.zenith.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Keep
@Entity(
    tableName = "pomodoro_sessions",
    indices = [
        Index(value = ["date"])
    ]
)
data class PomodoroSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val completedAt: Long = System.currentTimeMillis(),
    val focusMillis: Long,
    val sessionNumber: Int = 1
)
