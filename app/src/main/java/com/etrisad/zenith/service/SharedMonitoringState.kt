package com.etrisad.zenith.service

import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.PerformanceConfig
import com.etrisad.zenith.data.preferences.PerformanceLevel
import com.etrisad.zenith.data.preferences.UserPreferences
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class ParsedSchedule(
    val id: Long,
    val startMinutes: Int,
    val endMinutes: Int,
    val mode: ScheduleMode,
    val packageNames: Set<String>
)

object SharedMonitoringState {
    @Volatile var allShieldsCache = emptyMap<String, ShieldEntity>()
    @Volatile var goalShieldsCache = listOf<ShieldEntity>()
    @Volatile var parsedSchedulesCache = listOf<ParsedSchedule>()
    @Volatile var currentPreferences: UserPreferences? = null
    @Volatile var whitelistedPackages = emptySet<String>()
    @Volatile var bedtimeWhitelistedPackages = emptySet<String>()
    @Volatile var restrictedPackages = emptySet<String>()
    @Volatile var hasGlobalAllowSchedule = false
    @Volatile var launcherAppsCache = emptySet<String>()
    @Volatile var launcherPackages = emptySet<String>()
    @Volatile var defaultLauncherPackage: String? = null
    @Volatile var lastLauncherAppsRefreshTime = 0L
    @Volatile var cachedBedtimeStartMinutes = -1
    @Volatile var cachedBedtimeEndMinutes = -1
    @Volatile var cachedGracePeriodStartMinutes = -1
    @Volatile var cachedGracePeriodEndMinutes = -1
    @Volatile var isGracePeriodActive = false
    @Volatile var isBedtimeActive = false
    @Volatile var isWindDownActive = false
    @Volatile var isBedtimeBlockingActive = false
    val windDownUsedPackages = ConcurrentHashMap<String, Boolean>()
    val systemAppCache = ConcurrentHashMap<String, Boolean>()
    val dailyUsageCache = ConcurrentHashMap<String, Long>()
    val notifiedGoals = ConcurrentHashMap.newKeySet<String>()
    @Volatile var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    @Volatile var performanceLevel: PerformanceLevel = PerformanceLevel.BALANCED
    @Volatile var performanceConfig: PerformanceConfig = PerformanceConfig()

    private var cachedStartOfDayTime = 0L
    private var cachedStartOfDayValue = 0L
    @Volatile var lastDailyUsageFetchTime = 0L

    fun getStartOfDay(): Long {
        val now = System.currentTimeMillis()
        if (now - cachedStartOfDayTime < 60000 && cachedStartOfDayValue > 0) {
            return cachedStartOfDayValue
        }
        val result = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        cachedStartOfDayTime = now
        cachedStartOfDayValue = result
        return result
    }

    fun clearDailyCaches() {
        dailyUsageCache.clear()
        notifiedGoals.clear()
    }

    fun performPeriodicCleanup() {
        val now = System.currentTimeMillis()
        val startOfDay = getStartOfDay()
        windDownUsedPackages.entries.removeIf { it.value && now - startOfDay > 86400000L }
        if (dailyUsageCache.size > 100) dailyUsageCache.entries.removeIf { it.value <= 0L }
        if (systemAppCache.size > 200) systemAppCache.clear()
    }

    val CRITICAL_SYSTEM_PACKAGES = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.android.server.telecom", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.google.android.permissioncontroller"
    )

    val BLOCKABLE_SYSTEM_APPS = setOf(
        "com.google.android.youtube", "com.android.chrome",
        "com.google.android.apps.youtube.music", "com.android.vending"
    )

    val FINANCIAL_APPS = setOf(
        "pl.mbank", "pl.pkobp.iko", "pl.ing.bmo", "pl.pekao.bm",
        "pl.bzwbk.bm", "pl.millennium.czyngasie", "pl.aliorbank.aib",
        "pl.bnpparibas.bgzbnp", "pl.creditagricole.lmpl", "pl.bosbank.bbm",
        "pl.plusbank.mobile", "pl.nestbank.mobile", "pl.toyota.mobile",
        "com.google.android.apps.walletnfcrel"
    )

    fun isFinancialApp(packageName: String): Boolean =
        packageName in FINANCIAL_APPS

    fun updateRestrictedPackages() {
        val shieldPkgs = allShieldsCache.keys
        val schedulePkgs = activeSchedules.asSequence().filter { it.mode == ScheduleMode.BLOCK }
            .flatMap { it.packageNames }.toSet()
        hasGlobalAllowSchedule = activeSchedules.any { it.mode == ScheduleMode.ALLOW }
        restrictedPackages = shieldPkgs + schedulePkgs + BLOCKABLE_SYSTEM_APPS
    }
}
