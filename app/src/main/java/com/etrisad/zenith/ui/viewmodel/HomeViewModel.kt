package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- Data classes tetap sama ---
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeVisible: Long,
    val icon: android.graphics.drawable.Drawable? = null
)

data class DailyUsage(
    val date: Long,
    val totalTime: Long
)

data class AppDetailUiState(
    val packageName: String = "",
    val appName: String = "",
    val icon: android.graphics.drawable.Drawable? = null,
    val type: com.etrisad.zenith.data.local.entity.FocusType? = null,
    val todayUsage: Long = 0L,
    val yesterdayUsage: Long = 0L,
    val percentageChange: Float = 0f,
    val usageHistory: List<DailyUsage> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val shieldEntity: ShieldEntity? = null,
    val isPaused: Boolean = false,
    val pauseEndTimestamp: Long = 0L
)

data class HomeUiState(
    val totalScreenTime: Long = 0L,
    val yesterdayScreenTime: Long = 0L,
    val percentageChange: Float = 0f,
    val dailyUsageHistory: List<DailyUsage> = emptyList(),
    val topApps: List<AppUsageInfo> = emptyList(),
    val allAppsUsage: List<AppUsageInfo> = emptyList(),
    val activeShields: List<ShieldEntity> = emptyList(),
    val activeGoals: List<ShieldEntity> = emptyList(),
    val shieldSortType: ShieldSortType = ShieldSortType.ALPHABETICAL,
    val goalSortType: ShieldSortType = ShieldSortType.ALPHABETICAL
)

class HomeViewModel(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allShields: List<ShieldEntity> = emptyList()

    private val _appDetailUiState = MutableStateFlow(AppDetailUiState())
    val appDetailUiState: StateFlow<AppDetailUiState> = _appDetailUiState.asStateFlow()

    private var appDetailJob: Job? = null

    // History from database
    private var globalHistory: List<DailyUsageEntity> = emptyList()
    private var currentAppHistory: List<DailyUsageEntity> = emptyList()

    init {
        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShields = shields
                updateShieldedLists()

                // Update detail if open to reflect changes immediately (e.g. pause/resume)
                val currentPkg = _appDetailUiState.value.packageName
                if (currentPkg.isNotEmpty()) {
                    val shield = shields.find { it.packageName == currentPkg }
                    _appDetailUiState.update { it.copy(
                        shieldEntity = shield,
                        type = shield?.type,
                        isPaused = shield?.isPaused ?: false,
                        pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L,
                        currentStreak = shield?.currentStreak ?: 0,
                        bestStreak = shield?.currentStreak ?: 0
                    ) }
                }
            }
        }

        // Observe global history
        viewModelScope.launch {
            shieldRepository.getLastNDaysGlobalUsage(21).collect { history ->
                globalHistory = history
                refreshUsageStats()
            }
        }

        refreshUsageStats()
        startRealTimeUpdates()
    }

    // --- Helper: hitung midnight dari offset hari ---
    private fun getMidnight(offsetDaysFromToday: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -offsetDaysFromToday)
        return cal.timeInMillis
    }

    /**
     * Ambil total usage untuk SATU package pada rentang waktu [startMs, endMs).
     * Menggunakan INTERVAL_BEST dan menjumlahkan manual setiap event
     * yang benar-benar jatuh di dalam window — menghindari "bocor" dari queryAndAggregateUsageStats.
     */
    private fun getUsageForPackageInRange(
        usm: UsageStatsManager,
        packageName: String,
        startMs: Long,
        endMs: Long
    ): Long {
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
        var best = 0L
        stats.forEach { stat ->
            if (stat.packageName != packageName) return@forEach
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > best) best = time
        }
        return best
    }

    /**
     * Ambil total semua launcher apps untuk rentang waktu [startMs, endMs).
     * Filter secara ketat: hanya stat yang firstTimeStamp-nya ada di dalam window.
     */
    private fun getTotalUsageInRange(
        usm: UsageStatsManager,
        launcherApps: Set<String>,
        excludePackages: Set<String>,
        startMs: Long,
        endMs: Long
    ): Long {
        // Query dengan INTERVAL_DAILY, percayakan boundary ke Android
        // Jangan filter pakai firstTimeStamp/lastTimeUsed karena field itu tidak reliable untuk windowing
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)

        val aggregated = mutableMapOf<String, Long>()
        stats.forEach { stat ->
            val pkg = stat.packageName
            if (pkg in excludePackages || pkg !in launcherApps) return@forEach

            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > 0) {
                aggregated[pkg] = maxOf(aggregated[pkg] ?: 0L, time)
            }
        }
        return aggregated.values.sumOf { it }
    }

    private fun refreshUsageStats() {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()

        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

        val now = System.currentTimeMillis()
        val todayStart = getMidnight(0)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // ── TODAY ──────────────────────────────────────────────────────────────
        val todayRawStats = usm.queryAndAggregateUsageStats(todayStart, now)
        var totalToday = 0L
        val appList = mutableListOf<AppUsageInfo>()

        todayRawStats.forEach { (pkg, stat) ->
            if (pkg in excludePackages || pkg !in launcherApps) return@forEach
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > 0) {
                totalToday += time
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    appList.add(AppUsageInfo(pkg, pm.getApplicationLabel(appInfo).toString(), time))
                } catch (_: Exception) {}
            }
        }

        // ── YESTERDAY & HISTORY (From DB) ──────────────────────────────────────
        val totalYesterday = globalHistory.find { 
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            it.date == dateFormat.format(cal.time)
        }?.usageTimeMillis ?: 0L

        // Assemble 21-day list
        val history = (0 until 21).map { i ->
            val dStart = getMidnight(i)
            val dateStr = dateFormat.format(Date(dStart))
            
            val dayTotal = if (i == 0) {
                totalToday
            } else {
                globalHistory.find { it.date == dateStr }?.usageTimeMillis ?: 0L
            }
            DailyUsage(dStart, dayTotal)
        }

        val percentageChange = when {
            totalYesterday > 0 -> ((totalToday - totalYesterday).toFloat() / totalYesterday) * 100
            totalToday > 0     -> 100f
            else               -> 0f
        }

        val topApps = appList.sortedByDescending { it.totalTimeVisible }.take(5).map { app ->
            try { app.copy(icon = pm.getApplicationIcon(app.packageName)) }
            catch (_: PackageManager.NameNotFoundException) { app }
        }

        val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }.map { app ->
            try { app.copy(icon = pm.getApplicationIcon(app.packageName)) }
            catch (_: PackageManager.NameNotFoundException) { app }
        }

        // Live calculation for shields/goals
        val liveShields = allShields.map { shield ->
            val usage = todayRawStats.getUsageTime(shield.packageName)
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            shield.copy(remainingTimeMillis = (limitMillis - usage).coerceAtLeast(0L))
        }

        _uiState.update { it.copy(
            totalScreenTime      = totalToday,
            yesterdayScreenTime  = totalYesterday,
            percentageChange     = percentageChange,
            dailyUsageHistory    = history.reversed(), // oldest → newest
            topApps              = topApps,
            allAppsUsage         = allAppsUsage,
            activeShields = sortShields(liveShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }, it.shieldSortType),
            activeGoals   = sortShields(liveShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }, it.goalSortType)
        ) }
    }

    fun loadAppDetail(packageName: String) {
        if (_appDetailUiState.value.packageName == packageName && appDetailJob?.isActive == true) return
        
        val isNewPackage = _appDetailUiState.value.packageName != packageName
        appDetailJob?.cancel()
        
        if (isNewPackage) {
            _appDetailUiState.value = AppDetailUiState(packageName = packageName)
        }

        appDetailJob = viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm  = context.packageManager
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStart = getMidnight(0)

            // Load app info immediately
            var appName = packageName
            var icon: android.graphics.drawable.Drawable? = null
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                icon    = pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {}

            _appDetailUiState.update { it.copy(appName = appName, icon = icon) }

            // ── HISTORY (From DB) ──────────────────────────────────────────────
            shieldRepository.getLastNDaysUsageForPackage(packageName, 21).collect { historyFromDb ->
                val currentNow = System.currentTimeMillis()
                val currentTodayUsage = usm.queryAndAggregateUsageStats(todayStart, currentNow)
                    .getUsageTime(packageName)

                val yesterdayUsage = historyFromDb.find { 
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    it.date == dateFormat.format(cal.time)
                }?.usageTimeMillis ?: 0L

                val history = (0 until 21).map { i ->
                    val dStart = getMidnight(i)
                    val dateStr = dateFormat.format(Date(dStart))
                    
                    val dayTotal = if (i == 0) {
                        currentTodayUsage
                    } else {
                        historyFromDb.find { it.date == dateStr }?.usageTimeMillis ?: 0L
                    }
                    DailyUsage(dStart, dayTotal)
                }

                val percentageChange = when {
                    yesterdayUsage > 0 -> ((currentTodayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
                    currentTodayUsage > 0     -> 100f
                    else               -> 0f
                }

                val shield = allShields.find { it.packageName == packageName }

                _appDetailUiState.update { it.copy(
                    packageName      = packageName,
                    appName          = appName,
                    icon             = icon,
                    type             = shield?.type,
                    todayUsage       = currentTodayUsage,
                    yesterdayUsage   = yesterdayUsage,
                    percentageChange = percentageChange,
                    usageHistory     = history.reversed(),
                    currentStreak    = shield?.currentStreak ?: 0,
                    bestStreak       = shield?.currentStreak ?: 0,
                    shieldEntity     = shield,
                    isPaused         = shield?.isPaused ?: false,
                    pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L
                ) }
            }
        }
    }

    fun pauseShield(durationHours: Int?) {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        val pauseEndTimestamp = if (durationHours != null) {
            System.currentTimeMillis() + (durationHours * 60 * 60 * 1000L)
        } else {
            0L // Indefinite
        }

        val updatedShield = shield.copy(
            isPaused = true,
            pauseEndTimestamp = pauseEndTimestamp
        )

        // Segera update UI state untuk feedback instan
        _appDetailUiState.update { it.copy(
            shieldEntity = updatedShield,
            isPaused = true,
            pauseEndTimestamp = pauseEndTimestamp
        ) }

        viewModelScope.launch {
            shieldRepository.updateShield(updatedShield)
        }
    }

    fun resumeShield() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        val updatedShield = shield.copy(
            isPaused = false,
            pauseEndTimestamp = 0L
        )

        // Segera update UI state untuk feedback instan
        _appDetailUiState.update { it.copy(
            shieldEntity = updatedShield,
            isPaused = false,
            pauseEndTimestamp = 0L
        ) }

        viewModelScope.launch {
            shieldRepository.updateShield(updatedShield)
        }
    }

    // Extension helper biar lebih clean
    private fun Map<String, android.app.usage.UsageStats>.getUsageTime(packageName: String): Long {
        return this[packageName]?.let {
            it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground)
        } ?: 0L
    }

    // --- Sisa fungsi tidak berubah ---

    fun onShieldSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(shieldSortType = sortType)
        updateShieldedLists()
    }

    fun onGoalSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(goalSortType = sortType)
        updateShieldedLists()
    }

    private fun updateShieldedLists() {
        val shields = allShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }
        val goals   = allShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }
        _uiState.value = _uiState.value.copy(
            activeShields = sortShields(shields, _uiState.value.shieldSortType),
            activeGoals   = sortShields(goals,   _uiState.value.goalSortType)
        )
    }

    private fun sortShields(shields: List<ShieldEntity>, sortType: ShieldSortType): List<ShieldEntity> {
        return when (sortType) {
            ShieldSortType.ALPHABETICAL  -> shields.sortedBy { it.appName.lowercase() }
            ShieldSortType.REMAINING_TIME -> shields.sortedBy {
                if (it.timeLimitMinutes > 0) it.remainingTimeMillis.toDouble() / (it.timeLimitMinutes * 60 * 1000L) else 0.0
            }
        }
    }

    private fun startRealTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(10000)
                refreshUsageStats()
                refreshCurrentAppDetailUsage()
            }
        }
    }

    private fun refreshCurrentAppDetailUsage() {
        val uiState = _appDetailUiState.value
        val packageName = uiState.packageName
        if (packageName.isEmpty()) return

        viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val todayStart = getMidnight(0)
            val currentNow = System.currentTimeMillis()
            val currentTodayUsage = usm.queryAndAggregateUsageStats(todayStart, currentNow)
                .getUsageTime(packageName)

            val yesterdayUsage = uiState.yesterdayUsage
            val percentageChange = when {
                yesterdayUsage > 0 -> ((currentTodayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
                currentTodayUsage > 0 -> 100f
                else -> 0f
            }

            _appDetailUiState.update { it.copy(
                todayUsage = currentTodayUsage,
                percentageChange = percentageChange
            ) }
        }
    }

    fun clearAppDetail(packageName: String) {
        if (_appDetailUiState.value.packageName == packageName) {
            appDetailJob?.cancel()
            _appDetailUiState.value = AppDetailUiState()
        }
    }

    fun deleteShieldFromDetail() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        viewModelScope.launch {
            shieldRepository.deleteShield(shield)
            _appDetailUiState.update { it.copy(type = null, shieldEntity = null) }
        }
    }

    fun formatDuration(millis: Long): String {
        val hours   = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}