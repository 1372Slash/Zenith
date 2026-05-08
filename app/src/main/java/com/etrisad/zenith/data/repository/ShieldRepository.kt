package com.etrisad.zenith.data.repository

import com.etrisad.zenith.data.local.dao.DailyUsageDao
import com.etrisad.zenith.data.local.dao.HourlyUsageDao
import com.etrisad.zenith.data.local.dao.ScheduleDao
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
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
    private val dailyUsageDao: DailyUsageDao,
    private val hourlyUsageDao: HourlyUsageDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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

    fun getLastNDaysGlobalUsage(days: Int): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getLastNDaysGlobalUsage(days)
    }

    fun getLastNDaysUsageForPackage(packageName: String, days: Int): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getLastNDaysUsageForPackage(packageName, days)
    }

    fun getAllUsage(): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getAllUsage()
    }

    fun getHourlyUsageForDate(date: String): Flow<List<HourlyUsageEntity>> {
        return hourlyUsageDao.getHourlyUsageForDate(date)
    }

    fun getDatesWithHourlyUsage(): Flow<List<String>> {
        return hourlyUsageDao.getDatesWithHourlyUsage()
    }

    suspend fun insertHourlyUsage(usages: List<HourlyUsageEntity>) {
        hourlyUsageDao.insertAll(usages)
    }

    suspend fun deleteOldHourlyUsage(thresholdDate: String) {
        hourlyUsageDao.deleteOldUsage(thresholdDate)
    }

    suspend fun insertDailyUsage(usage: DailyUsageEntity) {
        dailyUsageDao.insertDailyUsage(usage)
    }

    suspend fun getShieldByPackageName(packageName: String): ShieldEntity? {
        val cached = _allShieldsCache.value.find { it.packageName == packageName }
        if (cached != null) return cached

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
        val currentList = _allShieldsCache.value.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == shield.packageName }
        if (index != -1) {
            currentList[index] = shield
            _allShieldsCache.value = currentList
        }
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
