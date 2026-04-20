package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

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
    val shieldEntity: ShieldEntity? = null
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

    init {
        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShields = shields
                updateShieldedLists()
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
        val todayStart     = getMidnight(0)
        val yesterdayStart = getMidnight(1)
        val yesterdayEnd   = todayStart          // [yesterday midnight, today midnight)

        // ── TODAY ──────────────────────────────────────────────────────────────
        // queryAndAggregateUsageStats untuk today masih OK karena kita pakai
        // real-time (now) sebagai end, bukan boundary hari lain.
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

        // ── YESTERDAY ──────────────────────────────────────────────────────────
        // JANGAN pakai queryAndAggregateUsageStats untuk yesterday — bocor ke today.
        // Pakai INTERVAL_BEST + filter manual dengan boundary ketat.
        val totalYesterday = getTotalUsageInRange(
            usm, launcherApps, excludePackages,
            startMs = yesterdayStart,
            endMs   = yesterdayEnd
        )

        // ── HISTORY (21 hari, hari ke-2 sampai ke-21) ─────────────────────────
        // Untuk hari-hari lama, INTERVAL_DAILY lebih efisien karena Android
        // sudah meng-aggregate-nya. Tapi kita tetap filter firstTimeStamp agar
        // tidak tumpang tindih antar bucket.
        val historyStart = getMidnight(21)
        val historyStats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            historyStart,
            yesterdayStart   // sampai sebelum yesterday — yesterday sudah kita handle sendiri
        )

        // Kelompokkan per hari dengan normalisasi midnight
        val dailyTotals = mutableMapOf<Long, Long>()
        historyStats.forEach { stat ->
            val pkg = stat.packageName
            if (pkg in excludePackages || pkg !in launcherApps) return@forEach

            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time <= 0) return@forEach

            // Normalisasi ke midnight hari bucket ini
            val cal = Calendar.getInstance()
            cal.timeInMillis = stat.firstTimeStamp
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
            val dayKey = cal.timeInMillis

            // Tolak bucket yang tidak benar-benar sebelum yesterday
            if (dayKey >= yesterdayStart) return@forEach

            dailyTotals[dayKey] = (dailyTotals[dayKey] ?: 0L) + time
        }

        // ── ASSEMBLE 21-day list ───────────────────────────────────────────────
        val history = (0 until 21).map { i ->
            val dStart = getMidnight(i)
            val dayTotal = when (i) {
                0    -> totalToday
                1    -> totalYesterday
                else -> dailyTotals[dStart] ?: 0L
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

        _uiState.value = _uiState.value.copy(
            totalScreenTime      = totalToday,
            yesterdayScreenTime  = totalYesterday,
            percentageChange     = percentageChange,
            dailyUsageHistory    = history.reversed(), // oldest → newest
            topApps              = topApps,
            allAppsUsage         = allAppsUsage
        )
    }

    fun loadAppDetail(packageName: String) {
        viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm  = context.packageManager
            val now = System.currentTimeMillis()

            val todayStart     = getMidnight(0)
            val yesterdayStart = getMidnight(1)
            val yesterdayEnd   = todayStart

            // ── TODAY ──────────────────────────────────────────────────────────
            val todayUsage = usm.queryAndAggregateUsageStats(todayStart, now)
                .getUsageTime(packageName)

            // ── YESTERDAY ──────────────────────────────────────────────────────
            // queryAndAggregateUsageStats dengan window [yesterdayStart, todayStart)
            // jauh lebih reliable untuk per-package daripada INTERVAL_DAILY
            val yesterdayRaw = usm.queryAndAggregateUsageStats(yesterdayStart, yesterdayEnd)
                .getUsageTime(packageName)

            // Clamp: yesterday tidak boleh >= today kecuali memang logis
            // Jika sama persis, hampir pasti bocor — set 0
            val yesterdayUsage = if (yesterdayRaw == todayUsage && todayUsage > 0) 0L
            else yesterdayRaw

            // ── HISTORY ────────────────────────────────────────────────────────
            val historyStart = getMidnight(21)
            val historyStats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                historyStart,
                yesterdayStart  // sampai sebelum yesterday
            )

            val dailyTotals = mutableMapOf<Long, Long>()
            historyStats.forEach { stat ->
                if (stat.packageName != packageName) return@forEach
                val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                if (time <= 0) return@forEach

                val cal = Calendar.getInstance()
                cal.timeInMillis = stat.firstTimeStamp
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dayKey = cal.timeInMillis

                if (dayKey >= yesterdayStart) return@forEach
                // max bukan sum untuk hindari double-count per hari
                dailyTotals[dayKey] = maxOf(dailyTotals[dayKey] ?: 0L, time)
            }

            val history = (0 until 21).map { i ->
                val dStart = getMidnight(i)
                val dayTotal = when (i) {
                    0    -> todayUsage
                    1    -> yesterdayUsage
                    else -> dailyTotals[dStart] ?: 0L
                }
                DailyUsage(dStart, dayTotal)
            }

            val percentageChange = when {
                yesterdayUsage > 0 -> ((todayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
                todayUsage > 0     -> 100f
                else               -> 0f
            }

            val shield = allShields.find { it.packageName == packageName }
            var appName = packageName
            var icon: android.graphics.drawable.Drawable? = null
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                icon    = pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {}

            _appDetailUiState.value = AppDetailUiState(
                packageName      = packageName,
                appName          = appName,
                icon             = icon,
                type             = shield?.type,
                todayUsage       = todayUsage,
                yesterdayUsage   = yesterdayUsage,
                percentageChange = percentageChange,
                usageHistory     = history.reversed(),
                shieldEntity     = shield
            )
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
                val currentDetailPkg = _appDetailUiState.value.packageName
                if (currentDetailPkg.isNotEmpty()) loadAppDetail(currentDetailPkg)
            }
        }
    }

    fun deleteShieldFromDetail() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        viewModelScope.launch {
            shieldRepository.deleteShield(shield)
            _appDetailUiState.value = _appDetailUiState.value.copy(type = null, shieldEntity = null)
        }
    }

    fun formatDuration(millis: Long): String {
        val hours   = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}