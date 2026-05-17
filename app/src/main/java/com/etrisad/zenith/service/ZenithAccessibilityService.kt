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
    private val dailyUsageCache = mutableMapOf<String, Long>()
    @Volatile
    private var usageStatsCache: List<UsageStats>? = null
    private var lastUsageCacheTime = 0L
    private var lastCheckedDay = -1
    private var lastCheckedDayTimestamp = 0L

    private var lastMonitoringError: String? = null
    private var lastMonitoringErrorTime = 0L

    private var lastKickTime = 0L
    private var lastKickedPackage: String? = null

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
        
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this, preferencesRepository)
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
                    val currentTime = System.currentTimeMillis()
                    if (e.message != lastMonitoringError || currentTime - lastMonitoringErrorTime > 30000) {
                        Log.e("ZenithAS", "Error in handlePackageChange: ${e.message}", e)
                        lastMonitoringError = e.message
                        lastMonitoringErrorTime = currentTime
                    }
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

        if (shouldBypassBlocking(packageName)) {
            serviceScope.launch(Dispatchers.Main) {
                overlayManager.checkAndHide(packageName)
                sessionUsageOverlayManager.updateForegroundApp(packageName)
            }
            return
        }

        val className = event.className?.toString() ?: ""
        if (className.contains("Toast") || className.contains("Notification") || className.contains("Tooltip")) {
            return
        }

        serviceScope.launch(Dispatchers.Main) {
            overlayManager.checkAndHide(packageName)
            sessionUsageOverlayManager.updateForegroundApp(packageName)

            packageChangeFlow.tryEmit(packageName)
        }
    }

    private suspend fun handlePackageChange(currentApp: String) {
        
        var localSessionStartTime = sessionStartTime
        var localBaseUsage = baseUsageAtSessionStart
        var localShield = allShieldsCache.find { it.packageName == currentApp }
        
        if (currentApp != lastForegroundApp || currentShieldCache == null) {
            currentShieldCache = localShield
            if (currentApp != lastForegroundApp) {
                lastUsageFetchTime = 0L
                localSessionStartTime = System.currentTimeMillis()

                localBaseUsage = getTotalUsageToday(currentApp)
                
                sessionStartTime = localSessionStartTime
                baseUsageAtSessionStart = localBaseUsage
                cachedTotalUsage = localBaseUsage
                lastForegroundApp = currentApp
            }
        }

        checkBlockingInstant(currentApp, localShield)

        while (lastForegroundApp == currentApp) {
            lastEventTime = System.currentTimeMillis()
            val currentTime = System.currentTimeMillis()

            if (InterceptOverlayManager.isShowing) {
                delay(if (currentPreferences?.bedtimeEnabled == true) 5000L else 3000L)
                continue
            }
            
            if (localShield == null) {
                localShield = allShieldsCache.find { it.packageName == currentApp }
                currentShieldCache = localShield
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

                    localBaseUsage = 0L
                    localSessionStartTime = currentTime
                    
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

            updateUsageTime(currentApp, localSessionStartTime, localBaseUsage, localShield)

            localShield = currentShieldCache?.takeIf { it.packageName == currentApp } ?: localShield

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
                val isBedtimeBlocking = isBedtimeActive || (isWindDownActive && (currentPreferences?.bedtimeWindDownEnabled == true))
                val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in bedtimeWhitelistedPackages) || System.currentTimeMillis() > allowedUntil

                if (shouldCheckSchedules && !InterceptOverlayManager.isShowing) {
                    val isScheduled = checkSchedules(currentApp)
                    val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
                    if (!isScheduled && (shield != null || (prefs.mindfulGatewayEnabled && !shouldBypassBlocking(currentApp))) && System.currentTimeMillis() > allowedUntil) {
                        if (shield?.isAutoQuitEnabled == true && allowedUntil > 0) {
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
                        remaining < 60000 -> 2000L
                        remaining < 300000 -> 5000L
                        else -> 10000L
                    }
                } else {
                    when {
                        remaining > 3600000 -> 15000L
                        remaining > 600000 -> 10000L
                        remaining > 300000 -> 5000L
                        remaining > 60000 -> 3000L
                        else -> 1500L
                    }
                }
            } else 3000L

            delay(delayTime)
        }
    }

    private suspend fun checkBlockingInstant(currentApp: String, shield: ShieldEntity?) {
        if (shouldBypassBlocking(currentApp)) return
        
        val currentTime = System.currentTimeMillis()
        val isAppPaused = shield != null && isPaused(shield)
        
        if (!isAppPaused) {
            val allowedUntil = allowedApps[currentApp] ?: 0L
            val isBedtimeBlocking = isBedtimeActive || (isWindDownActive && (currentPreferences?.bedtimeWindDownEnabled == true))
            val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in bedtimeWhitelistedPackages) || currentTime > allowedUntil

            if (shouldCheckSchedules && !InterceptOverlayManager.isShowing) {
                val isScheduled = checkSchedules(currentApp)
                val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
                if (!isScheduled && (shield != null || (prefs.mindfulGatewayEnabled && !shouldBypassBlocking(currentApp))) && currentTime > allowedUntil) {
                    checkIfAppIsShielded(currentApp)
                }
            }
        }
    }

    private fun updateUsageTime(packageName: String, startTime: Long, baseUsage: Long, shield: ShieldEntity?) {
        if (shield == null) return
        val currentTime = System.currentTimeMillis()

        val sessionElapsed = currentTime - startTime
        val currentTotalUsage = baseUsage + sessionElapsed
        cachedTotalUsage = currentTotalUsage

        if (shield.type == FocusType.GOAL) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val remaining = (limitMillis - currentTotalUsage).coerceAtLeast(0L)

            val uiUpdateInterval = when {
                remaining < 60000 -> 5000L
                remaining < 300000 -> 10000L
                remaining < 900000 -> 20000L
                else -> 30000L
            }

            if (currentTime - lastUsageFetchTime >= uiUpdateInterval) {
                val usageToReport = currentTotalUsage
                serviceScope.launch(Dispatchers.Main) {
                    sessionUsageOverlayManager.updateHUDUsage(packageName, usageToReport)
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
        val shouldUpdateDB = timeSinceLastUsed > 15000 || (isNearLimit && timeSinceLastUsed > 5000)

        if (shouldUpdateDB) {
            val updatedShield = shield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            serviceScope.launch {
                shieldRepository.updateShield(updatedShield)
            }
            if (currentShieldCache?.packageName == packageName) {
                currentShieldCache = updatedShield
            }

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

    private val mindfulGatewayStates = mutableMapOf<String, ShieldEntity>()

    private fun getMindfulShield(packageName: String, appName: String): ShieldEntity {
        val existing = mindfulGatewayStates[packageName]
        val currentTime = System.currentTimeMillis()
        
        if (existing == null) {
            val newShield = ShieldEntity(
                packageName = packageName,
                appName = appName,
                timeLimitMinutes = -1,
                maxUsesPerPeriod = 5,
                refreshPeriodMinutes = 60,
                maxEmergencyUses = 3,
                emergencyUseCount = 3,
                lastPeriodResetTimestamp = currentTime,
                lastEmergencyRechargeTimestamp = currentTime,
                lastUsedTimestamp = currentTime
            )
            mindfulGatewayStates[packageName] = newShield
            return newShield
        }

        var updated = existing
        val rechargeDurationMillis = (currentPreferences?.emergencyRechargeDurationMinutes ?: 60) * 60 * 1000L
        if (updated.emergencyUseCount < updated.maxEmergencyUses && rechargeDurationMillis > 0) {
            val timeSinceLastRecharge = currentTime - updated.lastEmergencyRechargeTimestamp
            if (timeSinceLastRecharge >= rechargeDurationMillis) {
                val chargesToAdd = (timeSinceLastRecharge / rechargeDurationMillis).toInt()
                updated = updated.copy(
                    emergencyUseCount = (updated.emergencyUseCount + chargesToAdd).coerceAtMost(updated.maxEmergencyUses),
                    lastEmergencyRechargeTimestamp = updated.lastEmergencyRechargeTimestamp + (chargesToAdd * rechargeDurationMillis)
                )
            }
        }
        
        if (currentTime - updated.lastPeriodResetTimestamp > updated.refreshPeriodMinutes * 60 * 1000L) {
            updated = updated.copy(
                currentPeriodUses = 0,
                lastPeriodResetTimestamp = currentTime
            )
        }
        
        mindfulGatewayStates[packageName] = updated
        return updated
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        if (targetPackageName != lastForegroundApp) return
        
        if (targetPackageName == lastKickedPackage && System.currentTimeMillis() - lastKickTime < 3000) {
            return
        }

        val shield = currentShieldCache ?: allShieldsCache.find { it.packageName == targetPackageName }
        val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
        val isMindfulGateway = shield == null && prefs.mindfulGatewayEnabled && !shouldBypassBlocking(targetPackageName)

        val appName = shield?.appName ?: try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackageName, 0)).toString()
        } catch (_: Exception) { targetPackageName }

        val effectiveShield = if (isMindfulGateway) getMindfulShield(targetPackageName, appName) else shield

        if (effectiveShield != null && !InterceptOverlayManager.isShowing) {
            val totalUsageToday = getTotalUsageToday(targetPackageName)
            val totalGlobalUsageToday = getTotalGlobalUsageToday()
            val delayDurationSeconds = if (isMindfulGateway) 0 else prefs.delayAppDurationSeconds

            val currentTime = System.currentTimeMillis()
            val lastAction = effectiveShield.lastDelayStartTimestamp

            val lastSessionEnd = effectiveShield.lastSessionEndTimestamp
            val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd < 5 * 60 * 1000L)

            val shieldWithTimestamp = if (effectiveShield.isDelayAppEnabled) {
                if (isGracePeriodActive) {
                    val updated = effectiveShield.copy(lastDelayStartTimestamp = currentTime - (delayDurationSeconds * 1000L) - 1000)
                    if (!isMindfulGateway) serviceScope.launch { shieldRepository.updateShield(updated) }
                    else mindfulGatewayStates[targetPackageName] = updated
                    updated
                } else if (lastAction == 0L) {
                    val updated = effectiveShield.copy(lastDelayStartTimestamp = currentTime)
                    if (!isMindfulGateway) serviceScope.launch { shieldRepository.updateShield(updated) }
                    else mindfulGatewayStates[targetPackageName] = updated
                    updated
                } else {
                    effectiveShield
                }
            } else {
                effectiveShield
            }

            serviceScope.launch(Dispatchers.Main) {
                overlayManager.showOverlay(
                    packageName = targetPackageName,
                    appName = appName,
                    shield = shieldWithTimestamp,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    delayDurationSeconds = delayDurationSeconds,
                    onAllowUse = { minutes, isEmergency ->
                        val currentTimeOnUnlock = System.currentTimeMillis()
                        allowedApps[targetPackageName] = currentTimeOnUnlock + (minutes * 60 * 1000L)

                        serviceScope.launch {
                            if (isMindfulGateway) {
                                val currentMindful = mindfulGatewayStates[targetPackageName] ?: shieldWithTimestamp
                                val updatedMindful = if (isEmergency) {
                                    currentMindful.copy(
                                        emergencyUseCount = (currentMindful.emergencyUseCount - 1).coerceAtLeast(0),
                                        lastSessionEndTimestamp = currentTimeOnUnlock
                                    )
                                } else {
                                    val periodExpired = currentTimeOnUnlock - currentMindful.lastPeriodResetTimestamp > currentMindful.refreshPeriodMinutes * 60 * 1000L
                                    currentMindful.copy(
                                        currentPeriodUses = if (periodExpired) 1 else currentMindful.currentPeriodUses + 1,
                                        lastPeriodResetTimestamp = if (periodExpired) currentTimeOnUnlock else currentMindful.lastPeriodResetTimestamp,
                                        lastSessionEndTimestamp = currentTimeOnUnlock
                                    )
                                }
                                mindfulGatewayStates[targetPackageName] = updatedMindful
                            } else {
                                val currentShield = shieldRepository.getShieldByPackageName(targetPackageName)
                                val updatedShield = if (currentShield != null) {
                                    if (isEmergency) {
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
                                } else null

                                if (updatedShield != null) {
                                    shieldRepository.updateShield(updatedShield)
                                    currentShieldCache = updatedShield
                                }
                            }

                            val currentPrefs = preferencesRepository.userPreferencesFlow.first()
                            if (currentPrefs.sessionUsageOverlayEnabled) {
                                val isGoal = shieldWithTimestamp.type == FocusType.GOAL
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        targetPackageName,
                                        minutes,
                                        currentPrefs.sessionUsageOverlaySize,
                                        currentPrefs.sessionUsageOverlayOpacity,
                                        isGoal = isGoal,
                                        initialSeconds = if (isGoal) (getTotalUsageToday(targetPackageName) / 1000).toInt() else 0,
                                        onSessionEnd = {
                                            allowedApps[targetPackageName] = 0L
                                            serviceScope.launch {
                                                val s = currentShieldCache ?: mindfulGatewayStates[targetPackageName] ?: shieldRepository.getShieldByPackageName(targetPackageName)
                                                if (s?.isAutoQuitEnabled == true) {
                                                    goToHomeScreen()
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
                    onCloseApp = { 
                        lastKickTime = System.currentTimeMillis()
                        lastKickedPackage = targetPackageName
                        goToHomeScreen() 
                    },
                    onGoalDismiss = {
                        allowedApps[targetPackageName] = System.currentTimeMillis() + (60 * 60 * 1000L)
                    }
                )
            }
        }
    }

    private var cachedTotalGlobalUsage: Long = 0L
    private var lastGlobalUsageCacheTime: Long = 0L
    private var launcherAppsCache = emptySet<String>()
    private var defaultLauncherPackage: String? = null
    private var lastLauncherAppsRefreshTime = 0L

    private fun refreshLauncherCache() {
        try {
            val pm = packageManager
            launcherAppsCache = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()
            
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            defaultLauncherPackage = pm.resolveActivity(launcherIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            
            lastLauncherAppsRefreshTime = System.currentTimeMillis()
        } catch (_: Exception) {}
    }

    private fun getTotalGlobalUsageToday(): Long {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGlobalUsageCacheTime < 5000) {
            return cachedTotalGlobalUsage
        }

        if (currentTime - lastLauncherAppsRefreshTime > 3600000 || launcherAppsCache.isEmpty()) {
            refreshLauncherCache()
        }

        val excludePackages = setOfNotNull(packageName, defaultLauncherPackage)

        val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
        val accurateUsageMap = detailedUsage.appUsageMap

        var totalToday = 0L
        accurateUsageMap.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in launcherAppsCache) {
                if (time > 0) {
                    totalToday += time
                }
            }
        }

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        cachedTotalGlobalUsage = totalToday.coerceAtMost(currentTime - todayStart)
        lastGlobalUsageCacheTime = currentTime
        return cachedTotalGlobalUsage
    }

    private fun getTotalUsageToday(packageName: String): Long {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastUsageCacheTime > 10000) {
            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
            val tempMap = detailedUsage.appUsageMap
            
            dailyUsageCache.clear()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val timeSinceMidnight = currentTime - todayStart

            tempMap.forEach { (pkg, time) ->
                val cappedTime = if (time > timeSinceMidnight + 5000) timeSinceMidnight else time
                if (cappedTime > 0) dailyUsageCache[pkg] = cappedTime
            }
            lastUsageCacheTime = currentTime
        }
        
        return dailyUsageCache[packageName] ?: 0L
    }

    private fun goToHomeScreen() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun updateBedtimeStatus(prefs: UserPreferences) {
        val currentTime = System.currentTimeMillis()
        val currentDay: Int
        val currentMinutes: Int
        
        val yesterdayCalendar = Calendar.getInstance().apply {
            timeInMillis = currentTime
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDay = yesterdayCalendar.get(Calendar.DAY_OF_WEEK)

        synchronized(reusableCalendar) {
            reusableCalendar.timeInMillis = currentTime
            currentDay = reusableCalendar.get(Calendar.DAY_OF_WEEK)
            currentMinutes = reusableCalendar.get(Calendar.HOUR_OF_DAY) * 60 + reusableCalendar.get(Calendar.MINUTE)
        }

        val startMinutes = cachedBedtimeStartMinutes
        val endMinutes = cachedBedtimeEndMinutes

        var active = false
        var windDownActive = false

        if (prefs.bedtimeEnabled) {
            if (startMinutes <= endMinutes) {
                if (currentDay in prefs.bedtimeDays) {
                    active = currentMinutes in startMinutes..endMinutes
                }
            } else {
                if (currentDay in prefs.bedtimeDays && currentMinutes >= startMinutes) {
                    active = true
                } else if (yesterdayDay in prefs.bedtimeDays && currentMinutes <= endMinutes) {
                    active = true
                }
            }

            if (!active) {
                val windDownStartMinutes = (startMinutes - 30 + 1440) % 1440
                if (windDownStartMinutes < startMinutes) {
                    if (currentDay in prefs.bedtimeDays) {
                        windDownActive = currentMinutes in windDownStartMinutes until startMinutes
                    }
                } else {
                    if (currentDay in prefs.bedtimeDays && currentMinutes >= windDownStartMinutes) {
                        windDownActive = true
                    } else if (yesterdayDay in prefs.bedtimeDays && currentMinutes < startMinutes) {
                        windDownActive = true
                    }
                }
            }
        }

        val wasWindDownActive = isWindDownActive
        isBedtimeActive = active
        isWindDownActive = windDownActive

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
                onCloseApp = { 
                    lastKickTime = System.currentTimeMillis()
                    lastKickedPackage = packageName
                    goToHomeScreen() 
                }
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
                onCloseApp = { 
                    lastKickTime = System.currentTimeMillis()
                    lastKickedPackage = packageName
                    goToHomeScreen() 
                }
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
                onCloseApp = { 
                    lastKickTime = System.currentTimeMillis()
                    lastKickedPackage = packageName
                    goToHomeScreen() 
                }
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
