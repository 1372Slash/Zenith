package com.etrisad.zenith.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed Pomodoro focus session. Breaks are not recorded: the stats
 * below (counts, focus time) describe focused work only.
 */
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
    /** Day-start yyyy-MM-dd key, honoring the user's day-start boundary. */
    val date: String,
    val completedAt: Long = System.currentTimeMillis(),
    val focusMillis: Long,
    val sessionNumber: Int = 1
)
