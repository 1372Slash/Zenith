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

    @Query("SELECT DISTINCT date FROM hourly_usage")
    fun getDatesWithHourlyUsage(): Flow<List<String>>

    @Query("DELETE FROM hourly_usage WHERE date < :thresholdDate")
    suspend fun deleteOldUsage(thresholdDate: String)
}
