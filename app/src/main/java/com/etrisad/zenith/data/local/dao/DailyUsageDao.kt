package com.etrisad.zenith.data.local.dao

import androidx.room.*
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyUsage(usage: DailyUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<DailyUsageEntity>)

    @Query("SELECT * FROM daily_usage WHERE date = :date AND packageName = :packageName LIMIT 1")
    suspend fun getUsageByDateAndPackage(date: String, packageName: String): DailyUsageEntity?

    @Query("SELECT * FROM daily_usage WHERE date = :date AND packageName = :packageName LIMIT 1")
    fun getUsageByDateAndPackageFlow(date: String, packageName: String): Flow<DailyUsageEntity?>

    @Query("SELECT usageTimeMillis FROM daily_usage WHERE date = :date AND packageName = :packageName LIMIT 1")
    suspend fun getUsageTimeByDateAndPackage(date: String, packageName: String): Long?

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName ORDER BY date DESC LIMIT :days")
    fun getLastNDaysUsageForPackage(packageName: String, days: Int): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE packageName = 'TOTAL' ORDER BY date DESC LIMIT :days")
    fun getLastNDaysGlobalUsage(days: Int): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage ORDER BY date DESC, lastUpdated DESC LIMIT :limit")
    fun getAllUsage(limit: Int = 1000): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE date >= :sinceDate ORDER BY date DESC, lastUpdated DESC")
    fun getRecentUsage(sinceDate: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun getUsagesForDate(date: String): List<DailyUsageEntity>

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun getUsagesForDateFlow(date: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT COALESCE(SUM(usageTimeMillis), 0) FROM daily_usage WHERE packageName = :packageName AND date >= :sinceDate")
    suspend fun getTotalUsageSince(packageName: String, sinceDate: String): Long

    @Query("DELETE FROM daily_usage WHERE date < :thresholdDate")
    suspend fun deleteOldUsage(thresholdDate: String)

    @Query("DELETE FROM daily_usage WHERE date = :date")
    suspend fun deleteUsageForDate(date: String)

    @Query("DELETE FROM daily_usage WHERE date = :date AND packageName = :packageName")
    suspend fun deleteUsageForPackage(date: String, packageName: String)
}
