package com.etrisad.zenith.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.app.usage.UsageStats
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import java.util.Calendar
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ZenithAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val packageChangeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private val earlyKickManager = EarlyKickManager()
    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val reusableCalendar = Calendar.getInstance()

    private var lastForegroundApp: String? = null
    @Volatile
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private var sessionStartTime = 0L
    private var baseUsageAtSessionStart = 0L
    private val notifiedGoals = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val allowedApps = ConcurrentHashMap<String, Long>()
    @Volatile
    private var activeSchedules = listOf<ScheduleEntity>()
    @Volatile
    private var whitelistedPackages = emptySet<String>()
    @Volatile
    private var currentPreferences: UserPreferences? = null
    @Volatile
    private var allShieldsCache = listOf<ShieldEntity>()
    @Volatile
    private var restrictedPackages = emptySet<String>()
    @Volatile
    private var hasGlobalAllowSchedule = false
    @Volatile
    private var usageStatsCache: List<UsageStats>? = null
    private var lastUsageCacheTime = 0L
    private var lastCheckedDay = -1
    private var lastCheckedDayTimestamp = 0L

    private var isBedtimeActive = false
    private var isWindDownActive = false
    private var cachedBedtimeStartMinutes = -1
    private var cachedBedtimeEndMinutes = -1
    private val windDownUsedPackages = ConcurrentHashMap<String, Boolean>()
    private var bedtimeWhitelistedPackages = emptySet<String>()
    private val systemAppCache = ConcurrentHashMap<String, Boolean>()

    private class ParsedSchedule(
        val id: Long,
        val startMinutes: Int,
        val endMinutes: Int,
        val mode: ScheduleMode,
        val packageNames: Set<String>
    )
    @Volatile
    private var parsedSchedulesCache = listOf<ParsedSchedule>()

    companion object {
        var isServiceRunning = false
            private set
        @Volatile
        var lastEventTime = 0L
        private var instance: ZenithAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        lastEventTime = System.currentTimeMillis()
        Log.d("ZenithAS", "Accessibility Service connected")
        
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        serviceScope.launch(Dispatchers.Main) {
            overlayManager.hideOverlay()
        }

        serviceScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShieldsCache = shields
                lastForegroundApp?.let { currentPkg ->
                    currentShieldCache = shields.find { it.packageName == currentPkg }
                }
                updateRestrictedPackages()
            }
        }

        serviceScope.launch {
            shieldRepository.allSchedules.collect { schedules: List<ScheduleEntity> ->
                activeSchedules = schedules.filter { it.isActive }
                parsedSchedulesCache = activeSchedules.map { s ->
                    val startParts = s.startTime.split(":")
                    val endParts = s.endTime.split(":")
                    ParsedSchedule(
                        id = s.id,
                        startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0),
                        endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0),
                        mode = s.mode,
                        packageNames = s.packageNames.toSet()
                    )
                }
                updateRestrictedPackages()
            }
        }

        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                currentPreferences = preferences
                whitelistedPackages = preferences.whitelistedPackages
                bedtimeWhitelistedPackages = preferences.bedtimeWhitelistedPackages

                val startParts = preferences.bedtimeStartTime.split(":")
                val endParts = preferences.bedtimeEndTime.split(":")
                cachedBedtimeStartMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
                cachedBedtimeEndMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)
                
                updateBedtimeStatus(preferences)
            }
        }

        serviceScope.launch {
            packageChangeFlow.collectLatest { packageName ->
                try {
                    handlePackageChange(packageName)
                } catch (e: Exception) {
                    Log.e("ZenithAS", "Error in handlePackageChange", e)
                }
            }
        }

        mainHandler.postDelayed({
            rootInActiveWindow?.packageName?.toString()?.let { pkg ->
                if (pkg != packageName) {
                    packageChangeFlow.tryEmit(pkg)
                }
            }
        }, 1000)
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        lastEventTime = System.currentTimeMillis()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        
        Log.d("ZenithAS", "onAccessibilityEvent: Window state changed to $packageName")

        serviceScope.launch(Dispatchers.Main) {
            overlayManager.checkAndHide(packageName)
            sessionUsageOverlayManager.updateForegroundApp(packageName)
            packageChangeFlow.tryEmit(packageName)
        }
    }

    private suspend fun handlePackageChange(currentApp: String) {
        Log.d("ZenithAS", "handlePackageChange: $currentApp")
        if (currentApp != lastForegroundApp || currentShieldCache == null) {
            currentShieldCache = allShieldsCache.find { it.packageName == currentApp }
            if (currentApp != lastForegroundApp) {
                lastUsageFetchTime = 0L
                sessionStartTime = System.currentTimeMillis()
                baseUsageAtSessionStart = getTotalUsageToday(currentApp)
                cachedTotalUsage = baseUsageAtSessionStart
                lastForegroundApp = currentApp
            }
        }

        while (lastForegroundApp == currentApp) {
            lastEventTime = System.currentTimeMillis()
            val currentTime = System.currentTimeMillis()
            
            if (currentShieldCache == null) {
                currentShieldCache = allShieldsCache.find { it.packageName == currentApp }
            }

            if (currentTime - lastCheckedDayTimestamp > 60000) {
                currentPreferences?.let { updateBedtimeStatus(it) }
                val currentDay = synchronized(reusableCalendar) {
                    reusableCalendar.timeInMillis = currentTime
                    reusableCalendar.get(Calendar.DAY_OF_YEAR)
                }
                if (lastCheckedDay != -1 && currentDay != lastCheckedDay) {
                    usageStatsCache = null
                    lastUsageCacheTime = 0L
                    lastUsageFetchTime = 0L
                    cachedTotalUsage = 0L
                    currentShieldCache = null
                    notifiedGoals.clear()
                    earlyKickManager.reset()

                    baseUsageAtSessionStart = 0L
                    sessionStartTime = currentTime
                    
                    if (currentPreferences?.sessionUsageOverlayEnabled == true) {
                        serviceScope.launch(Dispatchers.Main) {
                            sessionUsageOverlayManager.updateHUDUsage(currentApp, 0L)
                        }
                    }
                }
                lastCheckedDay = currentDay
                lastCheckedDayTimestamp = currentTime
            }

            if (shouldBypassBlocking(currentApp)) {
                delay(2000)
                continue
            }

            updateUsageTime(currentApp)

            val shield = currentShieldCache
            val isAppPaused = shield != null && isPaused(shield)

            if (shield != null && shield.type != FocusType.GOAL && !isAppPaused) {
                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                val actualRemaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
                val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
                
                if (!InterceptOverlayManager.isShowing && (allowedApps[currentApp] ?: 0L) <= currentTime && 
                    earlyKickManager.shouldKick(currentApp, actualRemaining, prefs.earlyKickEnabled)) {
                    goToHomeScreen()
                    delay(1000)
                    continue
                }
            }

            if (shield != null && shield.type == FocusType.GOAL && !isAppPaused) {
                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                if (cachedTotalUsage < limitMillis) {
                    val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
                    if (prefs.sessionUsageOverlayEnabled) {
                        serviceScope.launch(Dispatchers.Main) {
                            sessionUsageOverlayManager.showHUD(
                                currentApp,
                                shield.timeLimitMinutes,
                                prefs.sessionUsageOverlaySize,
                                prefs.sessionUsageOverlayOpacity,
                                isGoal = true,
                                initialSeconds = (cachedTotalUsage / 1000).toInt()
                            )
                        }
                    }
                }
            }

            if (!isAppPaused) {
                val allowedUntil = allowedApps[currentApp] ?: 0L
                if (System.currentTimeMillis() > allowedUntil && !InterceptOverlayManager.isShowing) {
                    if (!checkSchedules(currentApp) && shield != null) {
                        if (shield.isAutoQuitEnabled && allowedUntil > 0) {
                            goToHomeScreen()
                            allowedApps.remove(currentApp)
                        } else {
                            checkIfAppIsShielded(currentApp)
                        }
                    }
                }
            }

            val delayTime = if (shield != null) {
                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)

                if (shield.type == FocusType.GOAL) {
                    when {
                        remaining < 60000 -> 600L
                        remaining < 300000 -> 1000L
                        else -> 1500L
                    }
                } else {
                    when {
                        remaining > 3600000 -> 8000L
                        remaining > 600000 -> 5000L
                        remaining > 300000 -> 3000L
                        remaining > 60000 -> 1500L
                        else -> 1000L
                    }
                }
            } else 1500L

            delay(delayTime)
        }
    }

    private fun updateUsageTime(packageName: String) {
        val shield = currentShieldCache ?: return
        val currentTime = System.currentTimeMillis()

        val sessionElapsed = currentTime - sessionStartTime
        val currentTotalUsage = baseUsageAtSessionStart + sessionElapsed
        cachedTotalUsage = currentTotalUsage

        if (shield.type == FocusType.GOAL) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val remaining = (limitMillis - currentTotalUsage).coerceAtLeast(0L)

            val uiUpdateInterval = when {
                remaining < 60000 -> 1000L
                remaining < 300000 -> 2500L
                remaining < 900000 -> 8000L
                else -> 15000L
            }

            if (currentTime - lastUsageFetchTime >= uiUpdateInterval) {
                serviceScope.launch(Dispatchers.Main) {
                    sessionUsageOverlayManager.updateHUDUsage(packageName, currentTotalUsage)
                }
                lastUsageFetchTime = currentTime
            }
        } else {
            if (currentTime - lastUsageFetchTime > 5000) {
                cachedTotalUsage = getTotalUsageToday(packageName)
                lastUsageFetchTime = currentTime
            }
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)

        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000 
        val shouldUpdateDB = timeSinceLastUsed > 10000 || (isNearLimit && timeSinceLastUsed > 2000)

        if (shouldUpdateDB) {
            val updatedShield = shield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            serviceScope.launch {
                shieldRepository.updateShield(updatedShield)
            }
            currentShieldCache = updatedShield

            if (shield.type == FocusType.GOAL && !isPaused(shield)) {
                if (currentTotalUsage >= limitMillis && !notifiedGoals.contains(packageName)) {
                    sendGoalReachedNotification(shield.appName, packageName)
                    notifiedGoals.add(packageName)
                }
            }
        }
    }

    private fun sendGoalReachedNotification(appName: String, packageName: String) {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId, "Goal Reminders", NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Goal Achieved!")
            .setContentText("You've reached your target usage for $appName. Keep it up!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(packageName.hashCode(), notification)
        } catch (_: Exception) {}
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        if (targetPackageName != lastForegroundApp) return

        val shield = currentShieldCache ?: allShieldsCache.find { it.packageName == targetPackageName }
        if (shield != null && !InterceptOverlayManager.isShowing) {
            val totalUsageToday = getTotalUsageToday(targetPackageName)
            val totalGlobalUsageToday = getTotalGlobalUsageToday()
            val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
            val delayDurationSeconds = prefs.delayAppDurationSeconds

            val currentTime = System.currentTimeMillis()
            val lastAction = shield.lastDelayStartTimestamp

            val lastSessionEnd = shield.lastSessionEndTimestamp
            val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd < 5 * 60 * 1000L)

            val shieldWithTimestamp = if (shield.isDelayAppEnabled) {
                if (isGracePeriodActive) {
                    val updated = shield.copy(lastDelayStartTimestamp = currentTime - (delayDurationSeconds * 1000L) - 1000)
                    serviceScope.launch { shieldRepository.updateShield(updated) }
                    updated
                } else if (lastAction == 0L) {
                    val updated = shield.copy(lastDelayStartTimestamp = currentTime)
                    serviceScope.launch { shieldRepository.updateShield(updated) }
                    updated
                } else {
                    shield
                }
            } else {
                shield
            }

            serviceScope.launch(Dispatchers.Main) {
                overlayManager.showOverlay(
                    packageName = targetPackageName,
                    appName = shield.appName,
                    shield = shieldWithTimestamp,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    delayDurationSeconds = delayDurationSeconds,
                    onAllowUse = { minutes, isEmergency ->
                        val currentTimeOnUnlock = System.currentTimeMillis()
                        allowedApps[targetPackageName] = currentTimeOnUnlock + (minutes * 60 * 1000L)
                        
                        serviceScope.launch {
                            val currentShield = shieldRepository.getShieldByPackageName(targetPackageName) ?: return@launch
                            val updatedShield = if (isEmergency) {
                                currentShield.copy(
                                    emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                    lastDelayStartTimestamp = 0L,
                                    lastSessionEndTimestamp = currentTimeOnUnlock
                                )
                            } else {
                                currentShield.copy(
                                    currentPeriodUses = currentShield.currentPeriodUses + 1,
                                    lastDelayStartTimestamp = 0L,
                                    lastSessionEndTimestamp = currentTimeOnUnlock
                                )
                            }
                            shieldRepository.updateShield(updatedShield)
                            currentShieldCache = updatedShield

                            val prefs = preferencesRepository.userPreferencesFlow.first()
                            if (prefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        targetPackageName,
                                        minutes,
                                        prefs.sessionUsageOverlaySize,
                                        prefs.sessionUsageOverlayOpacity,
                                        initialSeconds = 0,
                                        onSessionEnd = {
                                            allowedApps[targetPackageName] = 0L
                                            serviceScope.launch {
                                                val shield = shieldRepository.getShieldByPackageName(targetPackageName)
                                                if (shield != null) {
                                                    val updated = shield.copy(lastSessionEndTimestamp = System.currentTimeMillis())
                                                    shieldRepository.updateShield(updated)
                                                    currentShieldCache = updated
                                                    
                                                    if (updated.isAutoQuitEnabled) {
                                                        goToHomeScreen()
                                                    } else {
                                                        checkIfAppIsShielded(targetPackageName)
                                                    }
                                                } else {
                                                    checkIfAppIsShielded(targetPackageName)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    onCloseApp = { goToHomeScreen() },
                    onGoalDismiss = {
                        allowedApps[targetPackageName] = System.currentTimeMillis() + (60 * 60 * 1000L)
                    }
                )
            }
        }
    }

    private var cachedTotalGlobalUsage: Long = 0L
    private var lastGlobalUsageCacheTime: Long = 0L

    private fun getTotalGlobalUsageToday(): Long {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGlobalUsageCacheTime < 3000) {
            return cachedTotalGlobalUsage
        }

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()

        val excludePackages = setOfNotNull(packageName, launcherPackage)

        synchronized(reusableCalendar) {
            reusableCalendar.timeInMillis = currentTime
            reusableCalendar.set(Calendar.HOUR_OF_DAY, 0)
            reusableCalendar.set(Calendar.MINUTE, 0)
            reusableCalendar.set(Calendar.SECOND, 0)
            reusableCalendar.set(Calendar.MILLISECOND, 0)
            val startTime = reusableCalendar.timeInMillis

            val stats = try {
                usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
            } catch (_: Exception) {
                null
            }

            var totalToday = 0L
            stats?.forEach { stat ->
                val pkg = stat.packageName
                if (pkg !in excludePackages && pkg in launcherApps) {
                    val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                    if (time > 0) {
                        totalToday += time
                    }
                }
            }

            cachedTotalGlobalUsage = totalToday
            lastGlobalUsageCacheTime = currentTime
            return totalToday
        }
    }

    private fun getTotalUsageToday(packageName: String): Long {
        val currentTime = System.currentTimeMillis()

        var stats = usageStatsCache
        if (stats == null || currentTime - lastUsageCacheTime > 3000) {
            synchronized(reusableCalendar) {
                reusableCalendar.timeInMillis = currentTime
                reusableCalendar.set(Calendar.HOUR_OF_DAY, 0)
                reusableCalendar.set(Calendar.MINUTE, 0)
                reusableCalendar.set(Calendar.SECOND, 0)
                reusableCalendar.set(Calendar.MILLISECOND, 0)
                val startTime = reusableCalendar.timeInMillis

                stats = try {
                    usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
                } catch (_: Exception) { null }
                usageStatsCache = stats
                lastUsageCacheTime = currentTime
            }
        }
        
        return stats?.find { it.packageName == packageName }?.totalTimeVisible ?: 0L
    }

    private fun goToHomeScreen() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun updateBedtimeStatus(prefs: UserPreferences) {
        if (!prefs.bedtimeEnabled) {
            isBedtimeActive = false
            isWindDownActive = false
            return
        }

        val currentDay: Int
        val currentMinutes: Int
        synchronized(reusableCalendar) {
            reusableCalendar.timeInMillis = System.currentTimeMillis()
            currentDay = reusableCalendar.get(Calendar.DAY_OF_WEEK)
            currentMinutes = reusableCalendar.get(Calendar.HOUR_OF_DAY) * 60 + reusableCalendar.get(Calendar.MINUTE)
        }
        
        if (currentDay !in prefs.bedtimeDays) {
            isBedtimeActive = false
            isWindDownActive = false
            return
        }

        val startMinutes = cachedBedtimeStartMinutes
        val endMinutes = cachedBedtimeEndMinutes

        isBedtimeActive = if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }

        val windDownStartMinutes = (startMinutes - 30 + 1440) % 1440
        
        val wasWindDownActive = isWindDownActive
        isWindDownActive = if (windDownStartMinutes <= startMinutes) {
            currentMinutes in windDownStartMinutes until startMinutes
        } else {
            currentMinutes >= windDownStartMinutes || currentMinutes < startMinutes
        }

        if (isWindDownActive && !wasWindDownActive) {
            windDownUsedPackages.clear()
        }
    }

    private fun checkSchedules(packageName: String): Boolean {
        if (shouldBypassBlocking(packageName)) return false
        
        val prefs = currentPreferences ?: return false

        if (isBedtimeActive) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showBedtimeOverlay(packageName)
                return true
            }
        }

        if (isWindDownActive && prefs.bedtimeWindDownEnabled) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showWindDownOverlay(packageName)
                return true
            }
        }

        val now = System.currentTimeMillis()
        val currentTotalMinutes = synchronized(reusableCalendar) {
            reusableCalendar.timeInMillis = now
            reusableCalendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + reusableCalendar.get(java.util.Calendar.MINUTE)
        }
        
        for (ps in parsedSchedulesCache) {
            val isInInterval = if (ps.startMinutes <= ps.endMinutes) {
                currentTotalMinutes in ps.startMinutes..ps.endMinutes
            } else {
                currentTotalMinutes >= ps.startMinutes || currentTotalMinutes <= ps.endMinutes
            }

            if (isInInterval) {
                when (ps.mode) {
                    com.etrisad.zenith.data.local.entity.ScheduleMode.BLOCK -> {
                        if (packageName in ps.packageNames) {
                            showScheduleOverlayFromParsed(packageName, ps)
                            return true
                        }
                    }
                    com.etrisad.zenith.data.local.entity.ScheduleMode.ALLOW -> {
                        if (packageName !in ps.packageNames) {
                            showScheduleOverlayFromParsed(packageName, ps)
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun showBedtimeOverlay(packageName: String) {
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }
            
            overlayManager.showBedtimeOverlay(
                packageName = packageName,
                appName = appName,
                onCloseApp = { goToHomeScreen() }
            )
        }
    }

    private fun showWindDownOverlay(packageName: String) {
        val sessionUsed = windDownUsedPackages[packageName] ?: false
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }
            
            overlayManager.showWindDownOverlay(
                packageName = packageName,
                appName = appName,
                sessionUsed = sessionUsed,
                onAllowUse = { minutes ->
                    allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                    windDownUsedPackages[packageName] = true
                },
                onCloseApp = { goToHomeScreen() }
            )
        }
    }


    private fun showScheduleOverlayFromParsed(packageName: String, ps: ParsedSchedule) {
        val originalSchedule = activeSchedules.find { it.id == ps.id } ?: return
        showScheduleOverlay(packageName, originalSchedule)
    }

    private fun isPaused(shield: ShieldEntity): Boolean {
        if (!shield.isPaused) return false
        if (shield.pauseEndTimestamp == 0L) return true
        return System.currentTimeMillis() < shield.pauseEndTimestamp
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName) return true

        val prefs = currentPreferences
        val isBedtimeOrWindDown = isBedtimeActive || (isWindDownActive && prefs?.bedtimeWindDownEnabled == true)
        
        if (isBedtimeOrWindDown) {
            if (packageName in bedtimeWhitelistedPackages) return true
        } else {
            if (packageName in whitelistedPackages) return true
        }

        if (packageName in CRITICAL_SYSTEM_PACKAGES) return true
        if (packageName.contains("launcher", ignoreCase = true) || packageName.contains("home", ignoreCase = true)) return true

        val isSystem = systemAppCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } catch (_: Exception) { false }
        }

        if (isSystem) {
            if (packageName.contains("car.mode", ignoreCase = true)) return true
            return !(packageName in restrictedPackages || hasGlobalAllowSchedule)
        }

        return false
    }

    private fun updateRestrictedPackages() {
        val shieldPkgs = allShieldsCache.map { it.packageName }.toSet()
        val schedulePkgs = activeSchedules.filter { it.mode == ScheduleMode.BLOCK }
            .flatMap { it.packageNames }.toSet()
        hasGlobalAllowSchedule = activeSchedules.any { it.mode == ScheduleMode.ALLOW }
        restrictedPackages = shieldPkgs + schedulePkgs + BLOCKABLE_SYSTEM_APPS
    }

    private val CRITICAL_SYSTEM_PACKAGES = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.android.server.telecom", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.google.android.permissioncontroller"
    )
    private val BLOCKABLE_SYSTEM_APPS = setOf(
        "com.google.android.youtube", "com.android.chrome",
        "com.google.android.apps.youtube.music", "com.android.vending"
    )

    private fun showScheduleOverlay(packageName: String, schedule: ScheduleEntity) {
        val totalGlobalUsageToday = getTotalGlobalUsageToday()
        serviceScope.launch(Dispatchers.Main) {
            overlayManager.showScheduleOverlay(
                packageName = packageName,
                appName = packageName, 
                schedule = schedule,
                totalGlobalUsageToday = totalGlobalUsageToday,
                onAllowUse = { minutes, isEmergency ->
                    if (isEmergency) {
                        allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        serviceScope.launch {
                            val currentSchedule = shieldRepository.getScheduleById(schedule.id) ?: return@launch
                            shieldRepository.updateSchedule(currentSchedule.copy(emergencyUseCount = currentSchedule.emergencyUseCount - 1))

                            val prefs = preferencesRepository.userPreferencesFlow.first()
                            if (prefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        packageName,
                                        minutes,
                                        prefs.sessionUsageOverlaySize,
                                        prefs.sessionUsageOverlayOpacity,
                                        initialSeconds = 0,
                                        onSessionEnd = {
                                            allowedApps[packageName] = 0L
                                            serviceScope.launch {
                                                val shield = shieldRepository.getShieldByPackageName(packageName)
                                                if (shield != null) {
                                                    val updated = shield.copy(lastSessionEndTimestamp = System.currentTimeMillis())
                                                    shieldRepository.updateShield(updated)
                                                    currentShieldCache = updated

                                                    if (updated.isAutoQuitEnabled) {
                                                        goToHomeScreen()
                                                    } else {
                                                        checkIfAppIsShielded(packageName)
                                                    }
                                                } else {
                                                    checkIfAppIsShielded(packageName)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                onCloseApp = { goToHomeScreen() }
            )
        }
    }

    override fun onInterrupt() {}

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
            usageStatsCache = null
            lastUsageCacheTime = 0L
            systemAppCache.clear()
            try {
                ZenithDatabase.getDatabase(this).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
            } catch (_: Exception) {}
            System.gc()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceJob.cancel()
    }
}
