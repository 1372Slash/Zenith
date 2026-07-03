package com.etrisad.zenith.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etrisad.zenith.data.local.entity.WebsiteUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteUsageDao {
    @Query("SELECT * FROM website_usage WHERE date = :date AND domain = :domain")
    suspend fun getUsageByDateAndDomain(date: String, domain: String): WebsiteUsageEntity?

    @Query("SELECT * FROM website_usage WHERE date = :date AND domain = :domain")
    fun getUsageByDateAndDomainFlow(date: String, domain: String): Flow<WebsiteUsageEntity?>

    @Query("SELECT * FROM website_usage WHERE date = :date")
    fun getUsageForDate(date: String): Flow<List<WebsiteUsageEntity>>

    @Query("SELECT SUM(usageTimeMillis) FROM website_usage WHERE domain = :domain AND date >= :sinceDate")
    suspend fun getTotalUsageSince(domain: String, sinceDate: String): Long?

    @Query("SELECT * FROM website_usage WHERE domain = :domain ORDER BY date DESC")
    fun getUsageForDomain(domain: String): Flow<List<WebsiteUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: WebsiteUsageEntity)

    @Query("DELETE FROM website_usage WHERE date < :thresholdDate")
    suspend fun deleteOldUsage(thresholdDate: String)
}
