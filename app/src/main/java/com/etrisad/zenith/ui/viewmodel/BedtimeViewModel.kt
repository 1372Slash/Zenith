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
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

@Immutable
data class BedtimeUiState(
    val hourlyUsage: List<HourlyUsageInfo> = emptyList(),
    val bedtimeUsagePercentage: Float = 0f,
    val bedtimeUsageTotalMillis: Long = 0L,
    val bedtimeDurationTotalMillis: Long = 0L,
    val bedtimeHistory: List<DailyUsage> = emptyList(),
    val bedtimeDateRange: String = ""
)

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

    private val _uiState = MutableStateFlow(BedtimeUiState())
    val uiState: StateFlow<BedtimeUiState> = _uiState.asStateFlow()

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
            val date1Data = withContext(Dispatchers.IO) {
                shieldRepository.getHourlyUsageForDateSync(startDateStr)
            }
            val date2Data = if (startDateStr != nextDateStr) {
                withContext(Dispatchers.IO) {
                    shieldRepository.getHourlyUsageForDateSync(nextDateStr)
                }
            } else date1Data

            val hourlyDataByDate = mapOf(startDateStr to date1Data, nextDateStr to date2Data)

            val usageInfoList = bedtimeHours.map { hour ->
                val targetDate = if (hour >= startH) startDateStr else nextDateStr
                val hourlyData = hourlyDataByDate[targetDate] ?: emptyList()

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
                                    val label = if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(pkg)) {
                                        val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(pkg)
                                        com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
                                    } else pkg
                                    AppUsageInfo(pkg, label, entity.usageTimeMillis)
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

            val totalUsage = usageInfoList.sumOf { it.usageTimeMillis }
            val totalDurationMillis = sessionEndCal.timeInMillis - sessionStartCal.timeInMillis
            val usagePercentage = if (totalDurationMillis > 0) {
                (totalUsage.toFloat() / totalDurationMillis).coerceIn(0f, 1f)
            } else 0f

            val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
            
            val startDay = dayFormat.format(sessionStartCal.time)
            val endDay = dayFormat.format(sessionEndCal.time)
            val startMonth = monthFormat.format(sessionStartCal.time)
            val endMonth = monthFormat.format(sessionEndCal.time)
            
            val dateRange = if (startMonth == endMonth) {
                if (startDay == endDay) "$startDay $startMonth"
                else "$startDay-$endDay $startMonth"
            } else {
                "$startDay $startMonth - $endDay $endMonth"
            }

            _uiState.update {
                it.copy(
                    hourlyUsage = usageInfoList,
                    bedtimeUsageTotalMillis = totalUsage,
                    bedtimeDurationTotalMillis = totalDurationMillis,
                    bedtimeDateRange = dateRange,
                    bedtimeUsagePercentage = usagePercentage
                )
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
            val uniqueDates = mutableSetOf<String>()
            val dayEntries = mutableListOf<Triple<Long, String, String>>()

            for (i in 0 until 21) {
                val sessionStartCal = (calendar.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                }
                val startDateStr = dateFormat.format(sessionStartCal.time)
                val nextDateCal = (sessionStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                val nextDateStr = dateFormat.format(nextDateCal.time)
                uniqueDates.add(startDateStr)
                uniqueDates.add(nextDateStr)
                dayEntries.add(Triple(sessionStartCal.timeInMillis, startDateStr, nextDateStr))
            }
            val allHourlyData = withContext(Dispatchers.IO) {
                shieldRepository.getHourlyUsageForDatesSync(uniqueDates.toList())
                    .groupBy { it.date }
            }

            val historyList = dayEntries.map { (timeInMillis, startDateStr, nextDateStr) ->
                var sessionTotal = 0L
                for (hour in bedtimeHours) {
                    val targetDate = if (hour >= startH) startDateStr else nextDateStr
                    val hourlyData = allHourlyData[targetDate] ?: emptyList()
                    sessionTotal += hourlyData.find { it.hour == hour && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
                }
                DailyUsage(
                    date = timeInMillis,
                    totalTime = sessionTotal,
                    hasDatabaseRecord = true,
                    isLive = timeInMillis == dayEntries[0].first
                )
            }

            _uiState.update { it.copy(bedtimeHistory = historyList.reversed()) }
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
