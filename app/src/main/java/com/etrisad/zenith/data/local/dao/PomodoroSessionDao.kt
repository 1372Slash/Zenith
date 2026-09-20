package com.etrisad.zenith.data.local.dao

import androidx.room.*
import com.etrisad.zenith.data.local.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroSessionDao {
    @Insert
    suspend fun insert(session: PomodoroSessionEntity)

    @Query("SELECT * FROM pomodoro_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY completedAt ASC")
    fun getBetween(startDate: String, endDate: String): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT COUNT(*) FROM pomodoro_sessions WHERE date = :date")
    fun getCountForDateFlow(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM pomodoro_sessions")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM pomodoro_sessions")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(focusMillis), 0) FROM pomodoro_sessions")
    suspend fun getTotalFocusMillis(): Long
}
