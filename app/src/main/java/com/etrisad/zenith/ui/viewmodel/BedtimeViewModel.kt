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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

class BedtimeViewModel(
    context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val context = context.applicationContext
    private val appInfoCache = mutableMapOf<String, Pair<String, android.graphics.drawable.Drawable?>>()


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

    init {
        refreshStreak()
        viewModelScope.launch {
            userPreferences.collectLatest {
                loadHourlyUsage()
            }
        }
    }

    private suspend fun getLauncherInfo(): Pair<Set<String>, String?> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        
        apps to launcherPackage
    }

    fun loadHourlyUsage() {
        viewModelScope.launch {
            val prefs = userPreferences.value
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val zoneId = ZoneId.systemDefault()
            
            val (launcherApps, launcherPackage) = getLauncherInfo()
            val excludePackages = setOfNotNull(context.packageName, launcherPackage)

            val startH = try { prefs.bedtimeStartTime.split(":")[0].toInt() } catch(_: Exception) { 22 }
            val startM = try { prefs.bedtimeStartTime.split(":")[1].toInt() } catch(_: Exception) { 0 }
            val endH = try { prefs.bedtimeEndTime.split(":")[0].toInt() } catch(_: Exception) { 7 }
            val endM = try { prefs.bedtimeEndTime.split(":")[1].toInt() } catch(_: Exception) { 0 }

            val bedtimeHours = mutableListOf<Int>()
            var h = startH
            while (h != endH) {
                bedtimeHours.add(h)
                h = (h + 1) % 24
            }

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            val sessionStartCal = Calendar.getInstance().apply {
                if (currentHour < startH && startH > endH) {
                    add(Calendar.DAY_OF_YEAR, -1)
                } else if (currentHour >= startH) {
                } else {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                set(Calendar.HOUR_OF_DAY, startH)
                set(Calendar.MINUTE, startM)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val sessionEndCal = Calendar.getInstance().apply {
                timeInMillis = sessionStartCal.timeInMillis
                if (endH < startH || (endH == startH && endM <= startM)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                set(Calendar.HOUR_OF_DAY, endH)
                set(Calendar.MINUTE, endM)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val queryStart = sessionStartCal.timeInMillis
            val queryEnd = minOf(sessionEndCal.timeInMillis, now)

            val hourlyMap = mutableMapOf<Int, Long>()
            val packageHourlyMap = mutableMapOf<Int, MutableMap<String, Long>>()
            if (queryEnd > queryStart) {
                withContext(Dispatchers.IO) {
                    val events = usm.queryEvents(queryStart, queryEnd)
                    val event = UsageEvents.Event()
                    var activePkg: String? = null
                    var activeStartTime = 0L

                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val pkg = event.packageName
                        val time = event.timeStamp

                        when (event.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED,
                            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                                if (activePkg != null) {
                                    addHourlySegment(hourlyMap, packageHourlyMap, activePkg!!, maxOf(activeStartTime, queryStart), minOf(time, queryEnd), zoneId, launcherApps, excludePackages)
                                }
                                activePkg = pkg
                                activeStartTime = time
                            }
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                                if (activePkg == pkg) {
                                    addHourlySegment(hourlyMap, packageHourlyMap, pkg, maxOf(activeStartTime, queryStart), minOf(time, queryEnd), zoneId, launcherApps, excludePackages)
                                    activePkg = null
                                    activeStartTime = 0L
                                }
                            }
                        }
                    }
                    if (activePkg != null) {
                        addHourlySegment(hourlyMap, packageHourlyMap, activePkg!!, maxOf(activeStartTime, queryStart), queryEnd, zoneId, launcherApps, excludePackages)
                    }
                }
            }

            val pm = context.packageManager
            val usageInfoList = bedtimeHours.map { hour ->
                val appsForHour = withContext(Dispatchers.IO) {
                    packageHourlyMap[hour]?.mapNotNull { (pkg, duration) ->
                        if (duration <= 0) return@mapNotNull null
                        
                        val cached = appInfoCache[pkg]
                        if (cached != null) {
                            AppUsageInfo(pkg, cached.first, duration, cached.second)
                        } else {
                            try {
                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                val label = pm.getApplicationLabel(appInfo).toString()
                                val icon = pm.getApplicationIcon(appInfo)
                                appInfoCache[pkg] = label to icon
                                AppUsageInfo(pkg, label, duration, icon)
                            } catch (e: Exception) {
                                AppUsageInfo(pkg, pkg, duration, null)
                            }
                        }
                    }?.sortedByDescending { it.totalTimeVisible } ?: emptyList()
                }

                HourlyUsageInfo(
                    hour = hour,
                    usageTimeMillis = hourlyMap[hour] ?: 0L,
                    apps = appsForHour,
                    isLive = hour == currentHour
                )
            }

            _hourlyUsage.value = usageInfoList
            val totalUsage = usageInfoList.sumOf { it.usageTimeMillis }
            _bedtimeUsageTotalMillis.value = totalUsage

            val totalDurationMillis = sessionEndCal.timeInMillis - sessionStartCal.timeInMillis
            _bedtimeDurationTotalMillis.value = totalDurationMillis

            if (totalDurationMillis > 0) {
                _bedtimeUsagePercentage.value = (totalUsage.toFloat() / totalDurationMillis).coerceIn(0f, 1f)
            } else {
                _bedtimeUsagePercentage.value = 0f
            }
        }
    }

    private fun addHourlySegment(
        hourlyMap: MutableMap<Int, Long>,
        packageHourlyMap: MutableMap<Int, MutableMap<String, Long>>,
        packageName: String,
        start: Long,
        end: Long,
        zoneId: ZoneId,
        launcherApps: Set<String>,
        excludePackages: Set<String>
    ) {
        if (packageName !in launcherApps || packageName in excludePackages || start >= end) return

        var currentMillis = start
        var currentZdt = Instant.ofEpochMilli(currentMillis).atZone(zoneId)

        var nextHourZdt = currentZdt.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        var nextHourMillis = nextHourZdt.toInstant().toEpochMilli()

        while (currentMillis < end) {
            val hour = currentZdt.hour
            val segmentEnd = if (nextHourMillis < end) nextHourMillis else end
            val duration = segmentEnd - currentMillis

            if (duration > 0) {
                hourlyMap[hour] = (hourlyMap[hour] ?: 0L) + duration
                
                val hourPkgMap = packageHourlyMap.getOrPut(hour) { mutableMapOf() }
                hourPkgMap[packageName] = (hourPkgMap[packageName] ?: 0L) + duration
            }

            currentMillis = segmentEnd
            if (currentMillis < end) {
                currentZdt = nextHourZdt
                nextHourZdt = currentZdt.plusHours(1)
                nextHourMillis = nextHourZdt.toInstant().toEpochMilli()
            }
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
