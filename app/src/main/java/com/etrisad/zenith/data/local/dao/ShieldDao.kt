package com.etrisad.zenith.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.etrisad.zenith.data.local.entity.ShieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldDao {
    @Query("SELECT * FROM shields")
    fun getAllShields(): Flow<List<ShieldEntity>>

    @Query("SELECT * FROM shields WHERE packageName = :packageName")
    suspend fun getShieldByPackageName(packageName: String): ShieldEntity?

    @Query("SELECT * FROM shields WHERE packageName = :packageName")
    fun getShieldByPackageNameFlow(packageName: String): Flow<ShieldEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShield(shield: ShieldEntity)

    @Update
    suspend fun updateShield(shield: ShieldEntity)

    @Query("UPDATE shields SET remainingTimeMillis = timeLimitMinutes * 60 * 1000")
    suspend fun resetAllRemainingTimes()

    @Query("UPDATE shields SET remainingTimeMillis = timeLimitMinutes * 60 * 1000 WHERE limitPeriod = 'DAILY'")
    suspend fun resetDailyRemainingTimes()

    @Query("UPDATE shields SET remainingTimeMillis = timeLimitMinutes * 60 * 1000 WHERE limitPeriod = 'WEEKLY'")
    suspend fun resetWeeklyRemainingTimes()

    @Delete
    suspend fun deleteShield(shield: ShieldEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM shields WHERE packageName = :packageName LIMIT 1)")
    fun isAppShielded(packageName: String): Flow<Boolean>
}