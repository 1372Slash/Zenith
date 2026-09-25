package com.etrisad.zenith.data.repository

import com.etrisad.zenith.data.local.dao.DailyUsageDao
import com.etrisad.zenith.data.local.dao.HourlyUsageDao
import com.etrisad.zenith.data.local.dao.ScheduleDao
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.dao.WebsiteUsageDao
import com.etrisad.zenith.data.local.database.DbLogBuffer
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.WebsiteUsageEntity
import com.etrisad.zenith.data.model.IncentiveTier
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ShieldRepository(
    private var shieldDao: ShieldDao,
    private var scheduleDao: ScheduleDao,
    private var dailyUsageDao: DailyUsageDao,
    private var hourlyUsageDao: HourlyUsageDao,
    private var websiteUsageDao: WebsiteUsageDao,
    private var database: ZenithDatabase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    val allowedApps = ConcurrentHashMap<String, Long>()
    val mindfulGatewayStates = ConcurrentHashMap<String, ShieldEntity>()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _allShieldsCache = MutableStateFlow<List<ShieldEntity>>(emptyList())
    val allShields: Flow<List<ShieldEntity>> = _allShieldsCache.asStateFlow()

    private val _isShieldsLoaded = MutableStateFlow(false)
    val isShieldsLoaded: Flow<Boolean> = _isShieldsLoaded.asStateFlow()

    // Getter (not a one-time val): after rebindDatabase() this must serve the
    // new DAO handles, otherwise collectors stay blind on a closed instance.
    val allSchedules: Flow<List<ScheduleEntity>> get() = scheduleDao.getAllSchedules()

    private var collectorJob: kotlinx.coroutines.Job? = null

    init {
        startShieldCollectors()
        repositoryScope.launch {
            kotlinx.coroutines.delay(5000)
            verifyTableHealth()
        }
    }

    private fun startShieldCollectors() {
        collectorJob?.cancel()
        collectorJob = repositoryScope.launch {
            shieldDao.getAllShields().collect {
                _allShieldsCache.value = it
                _isShieldsLoaded.value = true
                android.util.Log.d("ZenithGoalShield", "DB_LOADED: ${it.size} shields from DB [${it.filter { s -> s.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }.size} shields, ${it.filter { s -> s.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }.size} goals]")
                DbLogBuffer.d("ZenithGoalShield", "DB_LOADED: ${it.size} shields from DB [${it.filter { s -> s.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }.size} shields, ${it.filter { s -> s.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }.size} goals]")
            }
        }
    }

    /**
     * Re-points every DAO handle at a freshly opened database instance.
     * Needed after ZenithDatabase.closeDatabase() (backup/restore): the old
     * handles are bound to a closed connection pool, so every Flow/suspend
     * query on them fails silently and the UI goes permanently all-zero until
     * the process restarts. Callers should then re-collect their streams.
     */
    fun rebindDatabase(db: ZenithDatabase) {
        database = db
        shieldDao = db.shieldDao()
        scheduleDao = db.scheduleDao()
        dailyUsageDao = db.dailyUsageDao()
        hourlyUsageDao = db.hourlyUsageDao()
        websiteUsageDao = db.websiteUsageDao()
        startShieldCollectors()
        android.util.Log.d("ZenithDB", "DB_REBOUND: repository DAO handles re-pointed at a fresh instance")
        DbLogBuffer.d("ZenithDB", "DB_REBOUND: repository DAO handles re-pointed at a fresh instance")
    }

    fun isDatabaseOpen(): Boolean = try { database.isOpen } catch (_: Exception) { false }

    private suspend fun verifyTableHealth() {
        try {
            val db = database.openHelper.readableDatabase
            val shieldCount = db.query("SELECT COUNT(*) FROM shields").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            val dailyCount = db.query("SELECT COUNT(*) FROM daily_usage").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            val hourlyCount = db.query("SELECT COUNT(*) FROM hourly_usage").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            val websiteCount = db.query("SELECT COUNT(*) FROM website_usage").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            val scheduleCount = db.query("SELECT COUNT(*) FROM schedules").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            android.util.Log.d("ZenithDB", "HEALTH_CHECK: shields=$shieldCount daily=$dailyCount hourly=$hourlyCount website=$websiteCount schedules=$scheduleCount")
            DbLogBuffer.d("ZenithDB", "HEALTH_CHECK: shields=$shieldCount daily=$dailyCount hourly=$hourlyCount website=$websiteCount schedules=$scheduleCount")
            if (shieldCount > 0 && dailyCount == 0) {
                android.util.Log.w("ZenithDB", "DATA_INCONSISTENCY: shields=$shieldCount but daily_usage=0 - stats data is MISSING!")
                DbLogBuffer.w("ZenithDB", "DATA_INCONSISTENCY: shields=$shieldCount but daily_usage=0 - stats data is MISSING!")
            }
        } catch (e: Exception) {
            android.util.Log.e("ZenithDB", "HEALTH_CHECK_FAILED: ${e.message}")
            DbLogBuffer.e("ZenithDB", "HEALTH_CHECK_FAILED: ${e.message}")
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
        val cappedDays = days.coerceAtMost(30)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -cappedDays)
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        return dailyUsageDao.getRecentUsage(dateStr)
    }

    fun getLongTermUsage(days: Int): Flow<List<DailyUsageEntity>> {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        return dailyUsageDao.getRecentUsage(dateStr)
    }

    fun getUsageBetween(startDate: String, endDate: String): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getUsageBetween(startDate, endDate)
    }

    suspend fun getEarliestDataDate(): String? {
        return dailyUsageDao.getEarliestDate()
    }

    fun getHourlyUsageForDate(date: String): Flow<List<HourlyUsageEntity>> {
        return hourlyUsageDao.getHourlyUsageForDate(date)
    }

    suspend fun getHourlyUsageForDateSync(date: String): List<HourlyUsageEntity> {
        return hourlyUsageDao.getHourlyUsageForDateSync(date)
    }

    suspend fun getHourlyUsageForDatesSync(dates: List<String>): List<HourlyUsageEntity> {
        return hourlyUsageDao.getHourlyUsageForDatesSync(dates)
    }

    suspend fun getDailyUsagesForDateSync(date: String): List<DailyUsageEntity> {
        return dailyUsageDao.getUsagesForDate(date)
    }

    suspend fun getDailyUsagesSinceSync(sinceDate: String): List<DailyUsageEntity> {
        return dailyUsageDao.getUsagesSince(sinceDate)
    }

    suspend fun getPomodoroTotalCount(): Int {
        return try { database.pomodoroSessionDao().getTotalCount() } catch (_: Exception) { 0 }
    }

    suspend fun getWebsiteDomainCount(): Int {
        return try { websiteUsageDao.getDistinctDomainCount() } catch (_: Exception) { 0 }
    }

    suspend fun getInterceptedNotificationCount(): Int {
        return try { database.interceptedNotificationDao().getTotalCount() } catch (_: Exception) { 0 }
    }

    suspend fun getTrackedDayCount(): Int {
        return try { dailyUsageDao.getDistinctDateCount() } catch (_: Exception) { 0 }
    }

    suspend fun getHourlyActiveDayCount(startHour: Int, endHour: Int): Int {
        return try {
            database.hourlyUsageDao().getActiveDatesInHourRange(startHour, endHour)
        } catch (_: Exception) { 0 }
    }

    suspend fun getAllTrackedDates(): List<String> {
        return try { dailyUsageDao.getDistinctDates() } catch (_: Exception) { emptyList() }
    }

    suspend fun getWebsiteTotalMillis(): Long {
        return try { websiteUsageDao.getTotalMillis() } catch (_: Exception) { 0L }
    }

    fun getPomodoroTotalCountFlow(): Flow<Int> {
        return database.pomodoroSessionDao().getTotalCountFlow()
    }

    suspend fun getPomodoroTotalFocusMillis(): Long {
        return try { database.pomodoroSessionDao().getTotalFocusMillis() } catch (_: Exception) { 0L }
    }

    fun getShieldByPackageNameFlow(packageName: String): Flow<ShieldEntity?> {
        return shieldDao.getShieldByPackageNameFlow(packageName)
    }

    fun getUsageByDateAndPackageFlow(date: String, packageName: String): Flow<DailyUsageEntity?> {
        return dailyUsageDao.getUsageByDateAndPackageFlow(date, packageName)
    }

    suspend fun getUsageByDateAndPackage(date: String, packageName: String): DailyUsageEntity? {
        return dailyUsageDao.getUsageByDateAndPackage(date, packageName)
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

    suspend fun getUsageSince(packageName: String, date: String, hour: Int): Long {
        return hourlyUsageDao.getUsageSince(packageName, date, hour) ?: 0L
    }

    suspend fun getWeeklyUsageForPackage(packageName: String): Long {
        val cal = java.util.Calendar.getInstance()
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        val weekStart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        return dailyUsageDao.getTotalUsageSince(packageName, weekStart)
    }

    suspend fun getWeeklyUsageLive(packageName: String, todayLiveUsage: Long): Long {
        val weeklyFromDb = getWeeklyUsageForPackage(packageName)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayFromDb = dailyUsageDao.getUsageTimeByDateAndPackage(todayStr, packageName) ?: 0L
        return weeklyFromDb - todayFromDb + todayLiveUsage
    }

    suspend fun deleteDailyUsageForDate(date: String) {
        dailyUsageDao.deleteUsageForDate(date)
    }

    suspend fun deleteUsageForPackage(date: String, packageName: String) {
        database.deleteUsageForPackageTransaction(date, packageName)
    }

    suspend fun insertDailyUsage(usage: DailyUsageEntity) {
        dailyUsageDao.insertDailyUsage(usage)
    }

    suspend fun insertAllDailyUsage(usages: List<DailyUsageEntity>) {
        dailyUsageDao.insertAll(usages)
    }

    suspend fun getShieldByPackageName(packageName: String): ShieldEntity? {
        _allShieldsCache.value.find { it.packageName == packageName }?.let { return it }
        // Cache may lag behind Room (debounce / fresh REPLACE). Always fall
        // back to DAO so callers never see a false-null right after insert.
        return try {
            shieldDao.getShieldByPackageName(packageName)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun insertShield(shield: ShieldEntity) {
        android.util.Log.d("ZenithGoalShield", "DB_INSERT: pkg=${shield.packageName} type=${shield.type} appName=${shield.appName} timeLimit=${shield.timeLimitMinutes}m isWebsite=${shield.isWebsite}")
        DbLogBuffer.d("ZenithGoalShield", "DB_INSERT: pkg=${shield.packageName} type=${shield.type} appName=${shield.appName} timeLimit=${shield.timeLimitMinutes}m isWebsite=${shield.isWebsite}")
        try {
            shieldDao.insertShield(shield)
            android.util.Log.d("ZenithGoalShield", "DB_INSERT_SUCCESS: pkg=${shield.packageName} type=${shield.type}")
            DbLogBuffer.d("ZenithGoalShield", "DB_INSERT_SUCCESS: pkg=${shield.packageName} type=${shield.type}")
        } catch (e: Exception) {
            android.util.Log.e("ZenithGoalShield", "DB_INSERT_FAILED: pkg=${shield.packageName} error=${e.message}")
            DbLogBuffer.e("ZenithGoalShield", "DB_INSERT_FAILED: pkg=${shield.packageName} error=${e.message}")
            throw e
        }
    }

    suspend fun updateShield(shield: ShieldEntity) {
        val currentList = _allShieldsCache.value.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == shield.packageName }
        if (index != -1) {
            currentList[index] = shield
            _allShieldsCache.value = currentList
        }

        try {
            shieldDao.updateShield(shield)
        } catch (e: Exception) {
            android.util.Log.e("ShieldRepo", "Gagal update database: ${e.message}")
        }
    }

    suspend fun resetAllRemainingTimes() {
        shieldDao.resetAllRemainingTimes()
    }

    suspend fun resetDailyRemainingTimes() {
        shieldDao.resetDailyRemainingTimes()
    }

    suspend fun resetWeeklyRemainingTimes() {
        shieldDao.resetWeeklyRemainingTimes()
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

    suspend fun getWebsiteUsage(date: String, domain: String): WebsiteUsageEntity? {
        return websiteUsageDao.getUsageByDateAndDomain(date, domain)
    }

    fun getWebsiteUsageFlow(date: String, domain: String): Flow<WebsiteUsageEntity?> {
        return websiteUsageDao.getUsageByDateAndDomainFlow(date, domain)
    }

    fun getWebsiteUsageForDate(date: String): Flow<List<WebsiteUsageEntity>> {
        return websiteUsageDao.getUsageForDate(date)
    }

    suspend fun getWebsiteUsageListForDate(date: String): List<WebsiteUsageEntity> {
        return websiteUsageDao.getUsageForDateSnapshot(date)
    }

    suspend fun getWebsiteTotalUsageSince(domain: String, sinceDate: String): Long {
        return websiteUsageDao.getTotalUsageSince(domain, sinceDate) ?: 0L
    }

    fun getWebsiteUsageForDomain(domain: String): Flow<List<WebsiteUsageEntity>> {
        return websiteUsageDao.getUsageForDomain(domain)
    }

    suspend fun insertWebsiteUsage(usage: WebsiteUsageEntity) {
        websiteUsageDao.insertUsage(usage)
    }

    suspend fun deleteOldWebsiteUsage(thresholdDate: String) {
        websiteUsageDao.deleteOldUsage(thresholdDate)
    }

    suspend fun consumeIncentiveBonusUse(): Boolean {
        val progress = getIncentiveGoalProgress().first()
        val tier = IncentiveTier.fromProgress(progress)
        if (tier.isUnlocked || tier.bonusUses == Int.MAX_VALUE) return true
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val used = prefs.incentiveBonusUsesUsed
        if (used >= tier.bonusUses) return false
        userPreferencesRepository.setIncentiveBonusUsesUsed(used + 1)
        return true
    }

    suspend fun getIncentiveBonusUsesLeft(): Int {
        val progress = getIncentiveGoalProgress().first()
        val tier = IncentiveTier.fromProgress(progress)
        if (tier.isUnlocked || tier.bonusUses == Int.MAX_VALUE) return Int.MAX_VALUE
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        return (tier.bonusUses - prefs.incentiveBonusUsesUsed).coerceAtLeast(0)
    }

    fun getSingleGoalProgress(packageName: String): Flow<Float> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return combine(
            allShields,
            dailyUsageDao.getUsagesForDateFlow(today)
        ) { shields, usages ->
            val goal = shields.find { it.packageName == packageName && it.type == FocusType.GOAL }
            if (goal == null) return@combine 1f
            val usage = usages.find { it.packageName == packageName }?.usageTimeMillis ?: 0L
            val target = goal.timeLimitMinutes * 60000L
            if (target > 0) (usage.toDouble() / target).coerceAtMost(1.0).toFloat() else 1f
        }
    }

    fun getIncentiveGoalProgress(): Flow<Float> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return combine(
            allShields,
            dailyUsageDao.getUsagesForDateFlow(today)
        ) { shields, usages ->
            val goals = shields.filter { it.type == FocusType.GOAL }
            if (goals.isEmpty()) return@combine 1f

            val usageMap = usages.associateBy { it.packageName }
            var totalProgress = 0.0
            goals.forEach { goal ->
                val usage = usageMap[goal.packageName]?.usageTimeMillis ?: 0L
                val target = goal.timeLimitMinutes * 60000L
                if (target > 0) {
                    totalProgress += (usage.toDouble() / target).coerceAtMost(1.0)
                } else {
                    totalProgress += 1.0
                }
            }
            (totalProgress / goals.size).toFloat()
        }
    }

    fun getIncentiveTier(): Flow<IncentiveTier> {
        return getIncentiveGoalProgress().map { IncentiveTier.fromProgress(it) }
    }

    suspend fun recordPomodoroSession(date: String, focusMillis: Long, sessionNumber: Int) {
        database.pomodoroSessionDao().insert(
            com.etrisad.zenith.data.local.entity.PomodoroSessionEntity(
                date = date,
                completedAt = System.currentTimeMillis(),
                focusMillis = focusMillis,
                sessionNumber = sessionNumber
            )
        )
    }

    fun getPomodoroSessionsBetween(startDate: String, endDate: String): Flow<List<com.etrisad.zenith.data.local.entity.PomodoroSessionEntity>> {
        return database.pomodoroSessionDao().getBetween(startDate, endDate)
    }
}
