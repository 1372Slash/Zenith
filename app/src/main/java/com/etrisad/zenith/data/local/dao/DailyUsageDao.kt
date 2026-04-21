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

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName ORDER BY date DESC LIMIT :days")
    fun getLastNDaysUsageForPackage(packageName: String, days: Int): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE packageName = 'TOTAL' ORDER BY date DESC LIMIT :days")
    fun getLastNDaysGlobalUsage(days: Int): Flow<List<DailyUsageEntity>>

    @Query("DELETE FROM daily_usage WHERE date < :thresholdDate")
    suspend fun deleteOldUsage(thresholdDate: String)
}
