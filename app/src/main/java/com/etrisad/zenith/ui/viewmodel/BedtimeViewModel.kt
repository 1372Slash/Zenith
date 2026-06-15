package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

class BedtimeViewModel(
    context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val shieldRepository: ShieldRepository
) : ViewModel() {

    private val context = context.applicationContext
    private val appInfoCache = mutableMapOf<String, String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())


    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    private val _hourlyUsage = MutableStateFlow<List<HourlyUsageInfo>>(emptyList())
    val hourlyUsage: StateFlow<List<HourlyUsageInfo>> = _hourlyUsage.asStateFlow()

    private val _bedtimeUsagePercentage = MutableStateFlow(0f)
    val bedtimeUsagePercentage: StateFlow<Float> = _bedtimeUsagePercentage.asStateFlow()

    private val _bedtimeUsageTotalMillis = MutableStateFlow(0L)
    val bedtimeUsageTotalMillis: StateFlow<Long> = _bedtimeUsageTotalMillis.asStateFlow()

    private val _bedtimeDurationTotalMillis = MutableStateFlow(0L)
    val bedtimeDurationTotalMillis: StateFlow<Long> = _bedtimeDurationTotalMillis.asStateFlow()

    private val _bedtimeHistory = MutableStateFlow<List<DailyUsage>>(emptyList())
    val bedtimeHistory: StateFlow<List<DailyUsage>> = _bedtimeHistory.asStateFlow()

    private val _bedtimeDateRange = MutableStateFlow("")
    val bedtimeDateRange: StateFlow<String> = _bedtimeDateRange.asStateFlow()

    init {
        refreshStreak()
        loadHourlyUsage()
        loadBedtimeHistory()
        viewModelScope.launch {
            userPreferences
                .debounce(500)
                .collectLatest {
                    loadHourlyUsage()
                    loadBedtimeHistory()
                }
        }
    }

    private suspend fun getLauncherInfo(): Pair<Set<String>, String?> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val apps = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()
            
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            
            apps to launcherPackage
        } catch (_: Exception) {
            emptySet<String>() to null
        }
    }

    fun loadHourlyUsage() {
        viewModelScope.launch {
            try {
                val syncManager = com.etrisad.zenith.service.UsageSyncManager(context, shieldRepository, userPreferencesRepository)
                syncManager.syncUsageData()
            } catch (_: Exception) {}

            val prefs = userPreferences.value
            val now = System.currentTimeMillis()
            
            val (launcherApps, launcherPackage) = getLauncherInfo()
            val excludePackages = setOfNotNull(context.packageName, launcherPackage)

            val startH = try { prefs.bedtimeStartTime.split(":")[0].toInt() } catch(_: Exception) { 22 }
            val startM = try { prefs.bedtimeStartTime.split(":")[1].toInt() } catch(_: Exception) { 0 }
            val endH = try { prefs.bedtimeEndTime.split(":")[0].toInt() } catch(_: Exception) { 7 }
            val endM = try { prefs.bedtimeEndTime.split(":")[1].toInt() } catch(_: Exception) { 0 }

            val bedtimeHours = mutableListOf<Int>()
            var h = startH
            val stopH = if (endM > 0) (endH + 1) % 24 else endH
            
            while (h != stopH) {
                bedtimeHours.add(h)
                h = (h + 1) % 24
            }

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            calendar.set(Calendar.HOUR_OF_DAY, startH)
            calendar.set(Calendar.MINUTE, startM)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            if (currentHour < startH && startH > endH) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else if (currentHour >= startH) {
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }

            val sessionStartCal = calendar.clone() as Calendar
            val sessionEndCal = calendar.clone() as Calendar
            sessionEndCal.timeInMillis = sessionStartCal.timeInMillis
            if (endH < startH || (endH == startH && endM <= startM)) {
                sessionEndCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            sessionEndCal.set(Calendar.HOUR_OF_DAY, endH)
            sessionEndCal.set(Calendar.MINUTE, endM)
            sessionEndCal.set(Calendar.SECOND, 0)
            sessionEndCal.set(Calendar.MILLISECOND, 0)

            val startDateStr = dateFormat.format(sessionStartCal.time)
            val nextDateCal = (sessionStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val nextDateStr = dateFormat.format(nextDateCal.time)

            val pm = context.packageManager
            val usageInfoList = bedtimeHours.map { hour ->
                val targetDate = if (hour >= startH) startDateStr else nextDateStr
                val hourlyData = withContext(Dispatchers.IO) {
                    shieldRepository.getHourlyUsageForDateSync(targetDate)
                }
                
                val hourTotal = hourlyData.find { it.hour == hour && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
                val appsForHour = withContext(Dispatchers.IO) {
                    hourlyData.filter { it.hour == hour && it.packageName != "TOTAL" && it.packageName !in excludePackages && it.packageName in launcherApps }
                        .mapNotNull { entity ->
                            if (entity.usageTimeMillis <= 0) return@mapNotNull null
                            
                            val pkg = entity.packageName
                            val cached = appInfoCache[pkg]
                            if (cached != null) {
                                AppUsageInfo(pkg, cached, entity.usageTimeMillis)
                            } else {
                                try {
                                    val appInfo = pm.getApplicationInfo(pkg, 0)
                                    val label = pm.getApplicationLabel(appInfo).toString()
                                    appInfoCache[pkg] = label
                                    AppUsageInfo(pkg, label, entity.usageTimeMillis)
                                } catch (e: Exception) {
                                    AppUsageInfo(pkg, pkg, entity.usageTimeMillis)
                                }
                            }
                        }.sortedByDescending { it.totalTimeVisible }
                }

                HourlyUsageInfo(
                    hour = hour,
                    usageTimeMillis = hourTotal,
                    apps = appsForHour,
                    isLive = hour == currentHour && targetDate == dateFormat.format(calendar.time)
                )
            }

            _hourlyUsage.value = usageInfoList
            val totalUsage = usageInfoList.sumOf { it.usageTimeMillis }
            _bedtimeUsageTotalMillis.value = totalUsage

            val totalDurationMillis = sessionEndCal.timeInMillis - sessionStartCal.timeInMillis
            _bedtimeDurationTotalMillis.value = totalDurationMillis

            val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
            
            val startDay = dayFormat.format(sessionStartCal.time)
            val endDay = dayFormat.format(sessionEndCal.time)
            val startMonth = monthFormat.format(sessionStartCal.time)
            val endMonth = monthFormat.format(sessionEndCal.time)
            
            _bedtimeDateRange.value = if (startMonth == endMonth) {
                if (startDay == endDay) "$startDay $startMonth"
                else "$startDay-$endDay $startMonth"
            } else {
                "$startDay $startMonth - $endDay $endMonth"
            }

            if (totalDurationMillis > 0) {
                _bedtimeUsagePercentage.value = (totalUsage.toFloat() / totalDurationMillis).coerceIn(0f, 1f)
            } else {
                _bedtimeUsagePercentage.value = 0f
            }
        }
    }

    fun loadBedtimeHistory() {
        viewModelScope.launch {
            val prefs = userPreferences.value
            val startH = try { prefs.bedtimeStartTime.split(":")[0].toInt() } catch(_: Exception) { 22 }
            val endH = try { prefs.bedtimeEndTime.split(":")[0].toInt() } catch(_: Exception) { 7 }
            val endM = try { prefs.bedtimeEndTime.split(":")[1].toInt() } catch(_: Exception) { 0 }

            val bedtimeHours = mutableListOf<Int>()
            var h = startH
            val stopH = if (endM > 0) (endH + 1) % 24 else endH
            
            while (h != stopH) {
                bedtimeHours.add(h)
                h = (h + 1) % 24
            }

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val historyList = mutableListOf<DailyUsage>()

            for (i in 0 until 21) {
                val sessionStartCal = (calendar.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                }

                val startDateStr = dateFormat.format(sessionStartCal.time)
                val nextDateCal = (sessionStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                val nextDateStr = dateFormat.format(nextDateCal.time)

                var sessionTotal = 0L
                for (hour in bedtimeHours) {
                    val targetDate = if (hour >= startH) startDateStr else nextDateStr
                    val hourlyData = withContext(Dispatchers.IO) {
                        shieldRepository.getHourlyUsageForDateSync(targetDate)
                    }
                    val hourTotal = hourlyData.find { it.hour == hour && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
                    sessionTotal += hourTotal
                }

                historyList.add(
                    DailyUsage(
                        date = sessionStartCal.timeInMillis,
                        totalTime = sessionTotal,
                        hasDatabaseRecord = true,
                        isLive = i == 0
                    )
                )
            }

            _bedtimeHistory.value = historyList.reversed()
        }
    }

    fun formatDuration(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        val seconds = (millis / 1000) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun refreshStreak() {
        viewModelScope.launch {
            userPreferencesRepository.refreshBedtimeStreak()
        }
    }

    fun setBedtimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeEnabled(enabled)
        }
    }

    fun setBedtimeStartTime(time: String) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeStartTime(time)
        }
    }

    fun setBedtimeEndTime(time: String) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeEndTime(time)
        }
    }

    fun setBedtimeDays(days: Set<Int>) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeDays(days)
        }
    }

    fun setBedtimeDndEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeDndEnabled(enabled)
        }
    }

    fun setBedtimeWindDownEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeWindDownEnabled(enabled)
        }
    }

    fun setBedtimeNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeNotificationEnabled(enabled)
        }
    }

    fun setBedtimeWhitelistedPackages(packages: Set<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeWhitelistedPackages(packages)
        }
    }
}
