package com.etrisad.zenith.data.repository

import com.etrisad.zenith.data.local.dao.DailyUsageDao
import com.etrisad.zenith.data.local.dao.ScheduleDao
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ShieldEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShieldRepository(
    private val shieldDao: ShieldDao,
    private val scheduleDao: ScheduleDao,
    private val dailyUsageDao: DailyUsageDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Cache memory untuk performa maksimal
    private val _allShieldsCache = MutableStateFlow<List<ShieldEntity>>(emptyList())
    val allShields: Flow<List<ShieldEntity>> = _allShieldsCache.asStateFlow()
    
    val allSchedules: Flow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()

    init {
        repositoryScope.launch {
            shieldDao.getAllShields().collect {
                _allShieldsCache.value = it
            }
        }
    }

    // Daily Usage Methods
    fun getLastNDaysGlobalUsage(days: Int): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getLastNDaysGlobalUsage(days)
    }

    fun getLastNDaysUsageForPackage(packageName: String, days: Int): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getLastNDaysUsageForPackage(packageName, days)
    }

    suspend fun insertDailyUsage(usage: DailyUsageEntity) {
        dailyUsageDao.insertDailyUsage(usage)
    }

    suspend fun getShieldByPackageName(packageName: String): ShieldEntity? {
        // PERBAIKAN: Percaya pada cache memori. Jika tidak ada di cache, berarti aplikasi tidak di-shield.
        // Ini mencegah hit database (disk I/O) yang terjadi setiap 1-2 detik pada monitoring.
        val cached = _allShieldsCache.value.find { it.packageName == packageName }
        if (cached != null) return cached
        
        // Hanya query DB jika cache masih kosong (saat startup)
        if (_allShieldsCache.value.isEmpty()) {
            return shieldDao.getShieldByPackageName(packageName)
        }
        return null
    }

    suspend fun insertShield(shield: ShieldEntity) {
        shieldDao.insertShield(shield)
    }

    suspend fun updateShield(shield: ShieldEntity) {
        shieldDao.updateShield(shield)
    }

    suspend fun resetAllRemainingTimes() {
        shieldDao.resetAllRemainingTimes()
    }

    suspend fun deleteShield(shield: ShieldEntity) {
        shieldDao.deleteShield(shield)
    }

    fun isAppShielded(packageName: String): Flow<Boolean> {
        return shieldDao.isAppShielded(packageName)
    }

    // Schedule methods
    suspend fun insertSchedule(schedule: ScheduleEntity) {
        scheduleDao.insertSchedule(schedule)
    }

    suspend fun updateSchedule(schedule: ScheduleEntity) {
        scheduleDao.updateSchedule(schedule)
    }

    suspend fun deleteSchedule(schedule: ScheduleEntity) {
        scheduleDao.deleteSchedule(schedule)
    }

    suspend fun getActiveSchedules(): List<ScheduleEntity> {
        return scheduleDao.getActiveSchedules()
    }

    suspend fun getScheduleById(id: Long): ScheduleEntity? {
        return scheduleDao.getScheduleById(id)
    }
}
