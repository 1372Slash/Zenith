package com.etrisad.zenith.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<HourlyUsageEntity>)

    @Query("SELECT * FROM hourly_usage WHERE date = :date")
    fun getHourlyUsageForDate(date: String): Flow<List<HourlyUsageEntity>>

    @Query("SELECT * FROM hourly_usage WHERE date = :date AND packageName = :packageName")
    fun getHourlyUsageForPackage(date: String, packageName: String): Flow<List<HourlyUsageEntity>>

    @Query("SELECT * FROM hourly_usage WHERE date = :date")
    suspend fun getHourlyUsageForDateSync(date: String): List<HourlyUsageEntity>

    @Query("SELECT * FROM hourly_usage WHERE date IN (:dates)")
    suspend fun getHourlyUsageForDatesSync(dates: List<String>): List<HourlyUsageEntity>

    @Query("SELECT DISTINCT date FROM hourly_usage")
    fun getDatesWithHourlyUsage(): Flow<List<String>>

    @Query("SELECT COUNT(DISTINCT date) FROM hourly_usage WHERE hour BETWEEN :startHour AND :endHour AND usageTimeMillis > 0")
    suspend fun getActiveDatesInHourRange(startHour: Int, endHour: Int): Int

    @Query("DELETE FROM hourly_usage WHERE date < :thresholdDate")
    suspend fun deleteOldUsage(thresholdDate: String)

    @Query("DELETE FROM hourly_usage WHERE date = :date")
    suspend fun deleteHourlyUsageForDate(date: String)

    @Query("DELETE FROM hourly_usage WHERE date = :date AND packageName = :packageName")
    suspend fun deleteHourlyUsageForPackage(date: String, packageName: String)

    @Query("DELETE FROM hourly_usage WHERE date = :date AND hour = :hour AND packageName = :packageName")
    suspend fun deleteHourlyUsageAtHour(date: String, hour: Int, packageName: String)

    @Query("SELECT SUM(usageTimeMillis) FROM hourly_usage WHERE packageName = :packageName AND (date > :date OR (date = :date AND hour >= :hour))")
    suspend fun getUsageSince(packageName: String, date: String, hour: Int): Long?
}
