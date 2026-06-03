package com.etrisad.zenith.data.repository

import android.content.Context
import com.etrisad.zenith.data.local.dao.DailyUsageDao
import com.etrisad.zenith.data.local.dao.HourlyUsageDao
import com.etrisad.zenith.data.local.dao.ScheduleDao
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ShieldEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ShieldRepository(private val context: Context) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val allowedApps = ConcurrentHashMap<String, Long>()
    val mindfulGatewayStates = ConcurrentHashMap<String, ShieldEntity>()
    
    private val database get() = ZenithDatabase.getDatabase(context)
    private val shieldDao get() = database.shieldDao()
    private val scheduleDao get() = database.scheduleDao()
    private val dailyUsageDao get() = database.dailyUsageDao()
    private val hourlyUsageDao get() = database.hourlyUsageDao()

    private val _allShieldsCache = MutableStateFlow<List<ShieldEntity>>(emptyList())
    val allShields: Flow<List<ShieldEntity>> = _allShieldsCache.asStateFlow()
    
    private val _isShieldsLoaded = MutableStateFlow(false)
    val isShieldsLoaded: Flow<Boolean> = _isShieldsLoaded.asStateFlow()

    val allSchedules: Flow<List<ScheduleEntity>> get() = scheduleDao.getAllSchedules()

    init {
        startShieldObservation()
    }

    private fun startShieldObservation() {
        repositoryScope.launch {
            while (true) {
                try {
                    shieldDao.getAllShields().collect {
                        _allShieldsCache.value = it
                        _isShieldsLoaded.value = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShieldRepo", "Error collecting shields, retrying... ${e.message}")
                    delay(3000)
                }
            }
        }
    }

    fun getLastNDaysGlobalUsage(days: Int): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getLastNDaysGlobalUsage(days)
    }

    fun getLastNDaysUsageForPackage(packageName: String, days: Int): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getLastNDaysUsageForPackage(packageName, days)
    }

    fun getAllUsage(limit: Int = 2000): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getAllUsage(limit)
    }

    fun getRecentUsage(days: Int): Flow<List<DailyUsageEntity>> {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
        return dailyUsageDao.getRecentUsage(dateStr)
    }

    fun getHourlyUsageForDate(date: String): Flow<List<HourlyUsageEntity>> {
        return hourlyUsageDao.getHourlyUsageForDate(date)
    }

    suspend fun getHourlyUsageForDateSync(date: String): List<HourlyUsageEntity> {
        return hourlyUsageDao.getHourlyUsageForDateSync(date)
    }

    suspend fun getDailyUsagesForDateSync(date: String): List<DailyUsageEntity> {
        return dailyUsageDao.getUsagesForDate(date)
    }

    fun getShieldByPackageNameFlow(packageName: String): Flow<ShieldEntity?> {
        return shieldDao.getShieldByPackageNameFlow(packageName)
    }

    fun getUsageByDateAndPackageFlow(date: String, packageName: String): Flow<DailyUsageEntity?> {
        return dailyUsageDao.getUsageByDateAndPackageFlow(date, packageName)
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

    suspend fun deleteHourlyUsageForDate(date: String) {
        hourlyUsageDao.deleteHourlyUsageForDate(date)
    }

    suspend fun deleteHourlyUsageForPackage(date: String, packageName: String) {
        hourlyUsageDao.deleteHourlyUsageForPackage(date, packageName)
    }

    suspend fun deleteHourlyUsageAtHour(date: String, hour: Int, packageName: String) {
        hourlyUsageDao.deleteHourlyUsageAtHour(date, hour, packageName)
    }

    suspend fun deleteDailyUsageForDate(date: String) {
        dailyUsageDao.deleteUsageForDate(date)
    }

    suspend fun deleteUsageForPackage(date: String, packageName: String) {
        dailyUsageDao.deleteUsageForPackage(date, packageName)
        hourlyUsageDao.deleteHourlyUsageForPackage(date, packageName)
    }

    suspend fun insertDailyUsage(usage: DailyUsageEntity) {
        dailyUsageDao.insertDailyUsage(usage)
    }

    suspend fun insertAllDailyUsage(usages: List<DailyUsageEntity>) {
        dailyUsageDao.insertAll(usages)
    }

    suspend fun getShieldByPackageName(packageName: String): ShieldEntity? {
        val cached = _allShieldsCache.value.find { it.packageName == packageName }
        if (cached != null) return cached

        return try {
            shieldDao.getShieldByPackageName(packageName)
        } catch (e: Exception) {
            android.util.Log.e("ShieldRepo", "Error fetching shield from DB: ${e.message}")
            null
        }
    }

    suspend fun insertShield(shield: ShieldEntity) {
        val currentList = _allShieldsCache.value.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == shield.packageName }
        if (index != -1) {
            currentList[index] = shield
        } else {
            currentList.add(shield)
        }
        _allShieldsCache.value = currentList

        repositoryScope.launch {
            try {
                shieldDao.insertShield(shield)
            } catch (e: Exception) {
                android.util.Log.e("ShieldRepo", "Gagal insert ke database: ${e.message}")
            }
        }
    }

    suspend fun updateShield(shield: ShieldEntity) {
        val currentList = _allShieldsCache.value.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == shield.packageName }
        if (index != -1) {
            currentList[index] = shield
            _allShieldsCache.value = currentList
        }

        repositoryScope.launch {
            try {
                shieldDao.updateShield(shield)
            } catch (e: Exception) {
                android.util.Log.e("ShieldRepo", "Gagal update database: ${e.message}")
            }
        }
    }

    suspend fun resetAllRemainingTimes() {
        shieldDao.resetAllRemainingTimes()
    }

    suspend fun deleteShield(shield: ShieldEntity) {
        val currentList = _allShieldsCache.value.toMutableList()
        val removed = currentList.removeAll { it.packageName == shield.packageName }
        if (removed) {
            _allShieldsCache.value = currentList
        }

        repositoryScope.launch {
            try {
                shieldDao.deleteShield(shield)
            } catch (e: Exception) {
                android.util.Log.e("ShieldRepo", "Gagal menghapus shield dari database: ${e.message}")
            }
        }
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
