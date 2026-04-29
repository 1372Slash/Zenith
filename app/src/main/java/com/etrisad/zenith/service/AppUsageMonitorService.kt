package com.etrisad.zenith.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AppUsageMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val powerManager by lazy { getSystemService(POWER_SERVICE) as android.os.PowerManager }
    private val reusableEvent = UsageEvents.Event()
    private val reusableCalendar = java.util.Calendar.getInstance()
    private var lastForegroundApp: String? = null
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private var sessionStartTime = 0L
    private var baseUsageAtSessionStart = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = emptySet<String>()
    private var launcherPackages = emptySet<String>()
    private val systemAppCache = mutableMapOf<String, Boolean>()
    private var lastLauncherRefreshTime = 0L
    private var currentPreferences: UserPreferences? = null
    private var allShieldsCache = listOf<ShieldEntity>()
    private var goalShieldsCache = listOf<ShieldEntity>()
    
    private var isBedtimeActive = false
    private var isWindDownActive = false
    private val windDownUsedPackages = mutableMapOf<String, Boolean>()
    private var bedtimeWhitelistedPackages = emptySet<String>()
    private var lastCheckedDayTimestamp = 0L
    private var isScreenOn = true
    private var isPowerSaveMode = false

    private var lastHUDUpdateTime = 0L
    private val HUD_UPDATE_INTERVAL = 1000L
    private val dailyUsageCache = mutableMapOf<String, Long>()
    private var cachedStartOfDay = 0L
    private var lastStartOfDayDate = -1

    private var usageStatsCache: List<android.app.usage.UsageStats>? = null
    private var lastUsageCacheTime = 0L

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    currentShieldCache = null
                    usageStatsCache = null
                }
                android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    isPowerSaveMode = powerManager.isPowerSaveMode
                }
            }
        }
    }
    
    private class ParsedSchedule(
        val id: Long,
        val startMinutes: Int,
        val endMinutes: Int,
        val mode: ScheduleMode,
        val packageNames: Set<String>
    )
    private var parsedSchedulesCache = listOf<ParsedSchedule>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SEND_TEST_NOTIFICATION") {
            sendTestNotification()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        serviceScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShieldsCache = shields
                goalShieldsCache = shields.filter { 
                    it.type == FocusType.GOAL && it.goalReminderPeriodMinutes > 0 
                }
            }
        }

        serviceScope.launch {
            shieldRepository.allSchedules.collect { schedules ->
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
            }
        }

        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                currentPreferences = preferences
                whitelistedPackages = preferences.whitelistedPackages
                bedtimeWhitelistedPackages = preferences.bedtimeWhitelistedPackages
                updateBedtimeStatus(preferences)
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        createGoalNotificationChannel()
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        registerReceiver(screenStateReceiver, filter)
        isScreenOn = powerManager.isInteractive
        isPowerSaveMode = powerManager.isPowerSaveMode

        serviceScope.launch {
            currentPreferences = preferencesRepository.userPreferencesFlow.first()
            startMonitoring()
        }
        startGoalReminderCheck()
    }

    private fun startGoalReminderCheck() {
        serviceScope.launch {
            while (true) {
                val currentTime = System.currentTimeMillis()
                val goals = goalShieldsCache

                if (goals.isNotEmpty()) {
                    goals.forEach { goal ->
                        if (goal.packageName == lastForegroundApp) return@forEach

                        if (isPaused(goal)) return@forEach

                        val usageToday = dailyUsageCache[goal.packageName] ?: getTotalUsageToday(goal.packageName)
                        val limitMillis = goal.timeLimitMinutes * 60 * 1000L
                        if (usageToday >= limitMillis) return@forEach

                        val periodMillis = goal.goalReminderPeriodMinutes * 60 * 1000L
                        if (periodMillis > 0 && currentTime - goal.lastGoalReminderTimestamp >= periodMillis) {
                            sendGoalSuggestionNotification(goal)
                            shieldRepository.updateShield(goal.copy(lastGoalReminderTimestamp = currentTime))
                        }
                    }
                }

                delay(60000)
            }
        }
    }

    private fun sendGoalSuggestionNotification(goal: ShieldEntity) {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(NotificationManager::class.java)
        
        val intent = packageManager.getLaunchIntentForPackage(goal.packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            goal.packageName.hashCode(), 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconBitmap = try {
            val drawable = packageManager.getApplicationIcon(goal.packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Time for ${goal.appName}?")
            .setContentText("Your goal setting suggests it's time to open ${goal.appName} and make some progress!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(iconBitmap)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(goal.packageName.hashCode() + 1000, notification)
    }

    private fun createGoalNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "zenith_goal_channel"
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId, "Goal Reminders", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies you when you reach your app usage goals"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private var lastCheckedDay = -1
    private val notifiedGoals = mutableSetOf<String>()

    private fun startMonitoring() {
        serviceScope.launch {
            checkDayChangeOnStartup()

            while (true) {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastCheckedDayTimestamp > 60000) {
                    currentPreferences?.let { updateBedtimeStatus(it) }

                    reusableCalendar.timeInMillis = currentTime
                    val currentDay = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
                    if (lastCheckedDay != -1 && currentDay != lastCheckedDay) {
                        updateStreaks()
                        shieldRepository.resetAllRemainingTimes()
                        notifiedGoals.clear()
                        dailyUsageCache.clear()
                        usageStatsCache = null
                        lastUsageCacheTime = 0L
                        lastUsageFetchTime = 0L
                        cachedTotalUsage = 0L
                        currentShieldCache = null
                    }
                    lastCheckedDay = currentDay
                    lastCheckedDayTimestamp = currentTime
                }

                if (!isScreenOn) {
                    delay(5000)
                    continue
                }

                val isAccessibilityActive = ZenithAccessibilityService.isServiceRunning

                if (isAccessibilityActive) {
                    currentShieldCache = null
                    usageStatsCache = null
                    lastUsageFetchTime = 0L
                    delay(3000) 
                    continue
                }

                val currentApp = getForegroundApp()

                if (currentApp != null) {
                    overlayManager.checkAndHide(currentApp)
                }

                if (launcherPackages.isEmpty() || currentTime - lastLauncherRefreshTime > 60000) {
                    try {
                        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        val launchers = packageManager.queryIntentActivities(homeIntent, android.content.pm.PackageManager.MATCH_ALL)
                        launcherPackages = launchers.map { it.activityInfo.packageName }.toSet()
                        lastLauncherRefreshTime = currentTime
                    } catch (_: Exception) {}
                }

                if (currentTime - lastCheckedDayTimestamp > 60000) {
                    reusableCalendar.timeInMillis = currentTime
                    val currentDay = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
                    if (lastCheckedDay != -1 && currentDay != lastCheckedDay) {
                        updateStreaks()
                        shieldRepository.resetAllRemainingTimes()
                        notifiedGoals.clear()
                        dailyUsageCache.clear()
                        usageStatsCache = null
                        lastUsageCacheTime = 0L
                        lastUsageFetchTime = 0L
                        cachedTotalUsage = 0L
                        currentShieldCache = null
                    }
                    lastCheckedDay = currentDay
                    lastCheckedDayTimestamp = currentTime
                }

                if (currentApp != null && currentApp != packageName) {
                    sessionUsageOverlayManager.updateForegroundApp(currentApp)
                    
                    if (currentApp != lastForegroundApp || currentShieldCache == null) {
                        currentShieldCache = allShieldsCache.find { it.packageName == currentApp }
                        lastUsageFetchTime = 0L 
                        lastHUDUpdateTime = 0L
                        sessionStartTime = currentTime
                        
                        val systemUsage = getTotalUsageToday(currentApp)
                        val startOfDay = getStartOfDay()
                        val shield = currentShieldCache
                        val dbUsage = if (shield != null && shield.lastUsedTimestamp >= startOfDay) {
                            val limitMillis = (shield.timeLimitMinutes * 60 * 1000L)
                            (limitMillis - shield.remainingTimeMillis).coerceAtLeast(0L)
                        } else 0L
                        
                        baseUsageAtSessionStart = maxOf(systemUsage, dbUsage)
                        cachedTotalUsage = baseUsageAtSessionStart
                    }

                    if (shouldBypassBlocking(currentApp)) {
                        lastForegroundApp = currentApp
                        delay(if (isPowerSaveMode) 2000L else 1200L)
                        continue
                    }

                    val allowedUntil = allowedApps[currentApp] ?: 0L
                    updateUsageTime(currentApp)

                    val shieldForPauseCheck = currentShieldCache
                    val isAppPaused = shieldForPauseCheck != null && isPaused(shieldForPauseCheck)

                    if (!isAppPaused && allowedUntil > currentTime) {
                        val prefs = currentPreferences ?: return@launch
                        if (prefs.sessionUsageOverlayEnabled) {
                            val remainingMinutes = ((allowedUntil - currentTime) / 60000L).toInt().coerceAtLeast(1)
                            val shield = currentShieldCache
                            val isGoal = shield?.type == FocusType.GOAL

                            val limitMillis = (shield?.timeLimitMinutes ?: 0) * 60 * 1000L
                            if (isGoal && cachedTotalUsage >= limitMillis && limitMillis > 0) {
                            } else {
                                val duration = if (isGoal) shield?.timeLimitMinutes ?: 0 else remainingMinutes
                                val currentUsageSeconds = (cachedTotalUsage / 1000).toInt()
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        currentApp,
                                        duration,
                                        prefs.sessionUsageOverlaySize,
                                        prefs.sessionUsageOverlayOpacity,
                                        isGoal = isGoal,
                                        initialSeconds = if (isGoal) currentUsageSeconds else 0,
                                        onSessionEnd = {
                                            allowedApps[currentApp] = 0L
                                            serviceScope.launch {
                                                val s = allShieldsCache.find { it.packageName == currentApp }
                                                if (s != null) {
                                                    val updated = s.copy(lastSessionEndTimestamp = System.currentTimeMillis())
                                                    shieldRepository.updateShield(updated)
                                                    currentShieldCache = updated

                                                    if (updated.isAutoQuitEnabled) {
                                                        goToHomeScreen()
                                                    } else {
                                                        checkIfAppIsShielded(currentApp)
                                                    }
                                                } else {
                                                    checkIfAppIsShielded(currentApp)
                                                }
                                            }
                                        }
                                    )
                                    if (isGoal && currentTime - lastHUDUpdateTime > HUD_UPDATE_INTERVAL) {
                                        lastHUDUpdateTime = currentTime
                                        sessionUsageOverlayManager.updateHUDUsage(currentApp, cachedTotalUsage)
                                    }
                                }
                            }
                        }
                    }

                    if (!isAppPaused && currentTime > allowedUntil && !InterceptOverlayManager.isShowing) {
                        if (checkSchedules(currentApp)) {
                            lastForegroundApp = currentApp
                            delay(1000)
                            continue
                        }

                        val shield = currentShieldCache
                        if (shield != null) {
                            if (shield.isAutoQuitEnabled && allowedUntil > 0) {
                                goToHomeScreen()
                                allowedApps.remove(currentApp)
                                if (shield.isDelayAppEnabled) {
                                    serviceScope.launch {
                                        shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L))
                                    }
                                    currentShieldCache = currentShieldCache?.copy(lastDelayStartTimestamp = 0L)
                                }
                            } else if (currentApp != lastForegroundApp || allowedUntil > 0) {
                                checkIfAppIsShielded(currentApp)
                                if (allowedUntil > 0) allowedApps[currentApp] = 0L
                            }
                        }
                    }
                } else if (currentApp == null || currentApp == packageName || launcherPackages.contains(currentApp)) {
                    sessionUsageOverlayManager.updateForegroundApp(currentApp ?: "")
                    currentShieldCache = null
                }
                
                lastForegroundApp = currentApp

                // Background Sync: Update last known daily usage without user opening the app
                val currentHour = reusableCalendar.get(Calendar.HOUR_OF_DAY)
                val currentMinute = reusableCalendar.get(Calendar.MINUTE)
                if (currentHour == 23 && currentMinute >= 45) {
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(currentTime))
                    val globalUsage = getTotalGlobalUsageToday()
                    serviceScope.launch {
                        preferencesRepository.setLastKnownDailyUsage(globalUsage, todayStr)
                    }
                }

                val delayTime = when {
                    isPowerSaveMode -> 2000L
                    currentShieldCache != null -> {
                        val shield = currentShieldCache!!
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
                                remaining > 3600000 -> 5000L
                                remaining > 600000 -> 3000L
                                remaining > 60000 -> 1500L
                                else -> 600L
                            }
                        }
                    }
                    else -> 1200L
                }
                delay(delayTime)
            }
        }
    }

    private suspend fun updateUsageTime(packageName: String) {
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
                else -> 5000L
            }

            if (currentTime - lastHUDUpdateTime > uiUpdateInterval) {
                lastHUDUpdateTime = currentTime
                serviceScope.launch(Dispatchers.Main) {
                    sessionUsageOverlayManager.updateHUDUsage(packageName, currentTotalUsage)
                }
            }
        }

        if (currentTime - lastUsageFetchTime > 30000) {
            val systemUsage = getTotalUsageToday(packageName)
            val startOfDay = getStartOfDay()
            
            val dbUsage = if (shield.lastUsedTimestamp >= startOfDay) {
                val limitMillis = (shield.timeLimitMinutes * 60 * 1000L)
                (limitMillis - shield.remainingTimeMillis).coerceAtLeast(0L)
            } else 0L
            
            baseUsageAtSessionStart = maxOf(systemUsage, dbUsage) - (currentTime - sessionStartTime)
            lastUsageFetchTime = currentTime
        }

        val prefs = currentPreferences ?: return
        val rechargeDurationMillis = prefs.emergencyRechargeDurationMinutes * 60 * 1000L

        var updatedShield = shield
        if (shield.emergencyUseCount < shield.maxEmergencyUses && rechargeDurationMillis > 0) {
            val timeSinceLastRecharge = currentTime - shield.lastEmergencyRechargeTimestamp
            if (timeSinceLastRecharge >= rechargeDurationMillis) {
                val chargesToAdd = (timeSinceLastRecharge / rechargeDurationMillis).toInt()
                val newCount = (shield.emergencyUseCount + chargesToAdd).coerceAtMost(shield.maxEmergencyUses)
                updatedShield = updatedShield.copy(
                    emergencyUseCount = newCount,
                    lastEmergencyRechargeTimestamp = shield.lastEmergencyRechargeTimestamp + (chargesToAdd * rechargeDurationMillis)
                )
            }
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)

        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000 
        val shouldUpdateDB = timeSinceLastUsed > 10000 || (isNearLimit && timeSinceLastUsed > 2000) || updatedShield != shield

        if (shouldUpdateDB) {
            val finalShield = updatedShield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            shieldRepository.updateShield(finalShield)
            currentShieldCache = finalShield

            if (shield.type == FocusType.GOAL && !isPaused(shield)) {
                if (cachedTotalUsage >= limitMillis && !notifiedGoals.contains(packageName)) {
                    sendGoalReachedNotification(shield.appName, packageName)
                    notifiedGoals.add(packageName)
                }
            }
        }
    }

    private fun sendGoalReachedNotification(appName: String, packageName: String) {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Goal Achieved!")
            .setContentText("You've reached your target usage for $appName. Keep it up!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(packageName.hashCode(), notification)
    }

    fun sendTestNotification() {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test Notification ✅")
            .setContentText("Notifications are working correctly!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(999, notification)
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        val currentForeground = getForegroundApp()
        if (targetPackageName != currentForeground) return

        val shield = currentShieldCache ?: allShieldsCache.find { it.packageName == targetPackageName }
        val prefs = currentPreferences ?: return
        if (shield != null && !InterceptOverlayManager.isShowing) {
            val totalUsageToday = getTotalUsageToday(targetPackageName)
            val totalGlobalUsageToday = getTotalGlobalUsageToday()
            val delayDurationSeconds = prefs.delayAppDurationSeconds
            
            val currentTime = System.currentTimeMillis()
            val lastAction = shield.lastDelayStartTimestamp

            val lastSessionEnd = shield.lastSessionEndTimestamp
            val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd > 30 * 60 * 1000L)

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

            currentShieldCache = shieldWithTimestamp

            serviceScope.launch(Dispatchers.Main) {
                overlayManager.showOverlay(
                    packageName = targetPackageName,
                    appName = shield.appName,
                    shield = shieldWithTimestamp,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    delayDurationSeconds = delayDurationSeconds,
                    onAllowUse = { minutes, isEmergency ->
                                val currentTime = System.currentTimeMillis()
                                serviceScope.launch {
                                    val currentShield = shieldRepository.getShieldByPackageName(targetPackageName) ?: return@launch
                                    val updatedShield = if (isEmergency) {
                                        val isFirstChargeUsed = currentShield.emergencyUseCount == currentShield.maxEmergencyUses
                                        currentShield.copy(
                                            emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                            lastEmergencyRechargeTimestamp = if (isFirstChargeUsed) System.currentTimeMillis() else currentShield.lastEmergencyRechargeTimestamp,
                                            lastDelayStartTimestamp = 0L,
                                            lastSessionEndTimestamp = currentTime
                                        )
                                    } else {
                                        val periodExpired = System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                        currentShield.copy(
                                            currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                            lastPeriodResetTimestamp = if (periodExpired) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp,
                                            lastDelayStartTimestamp = 0L,
                                            lastSessionEndTimestamp = currentTime
                                        )
                                    }
                                    shieldRepository.updateShield(updatedShield)
                                    currentShieldCache = updatedShield
                                    allowedApps[targetPackageName] = currentTime + (minutes * 60 * 1000L)
                            
                            val currentPrefs = currentPreferences ?: return@launch
                                    if (currentPrefs.sessionUsageOverlayEnabled) {
                                        val isGoal = updatedShield.type == FocusType.GOAL
                                        val limitMillis = updatedShield.timeLimitMinutes * 60 * 1000L
                                        val currentUsage = getTotalUsageToday(targetPackageName)

                                        if (isGoal && currentUsage >= limitMillis && limitMillis > 0) {
                                        } else {
                                            val duration = if (isGoal) updatedShield.timeLimitMinutes else minutes
                                            val currentUsageSeconds = (currentUsage / 1000).toInt()

                                            serviceScope.launch(Dispatchers.Main) {
                                                sessionUsageOverlayManager.showHUD(
                                                    targetPackageName,
                                                    duration,
                                                    currentPrefs.sessionUsageOverlaySize,
                                                    currentPrefs.sessionUsageOverlayOpacity,
                                                    isGoal = isGoal,
                                                    initialSeconds = if (isGoal) currentUsageSeconds else 0,
                                                    onSessionEnd = {
                                                allowedApps[targetPackageName] = 0L
                                                serviceScope.launch {
                                                    val shield = currentShieldCache ?: shieldRepository.getShieldByPackageName(targetPackageName)
                                                    if (shield?.isAutoQuitEnabled == true) {
                                                        goToHomeScreen()
                                                    } else {
                                                        checkIfAppIsShielded(targetPackageName)
                                                    }
                                                }
                                            }
                                        )
                                        if (isGoal) {
                                            sessionUsageOverlayManager.updateHUDUsage(targetPackageName, currentUsage)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onCloseApp = {
                        goToHomeScreen()
                    },
                    onGoalDismiss = {
                        allowedApps[targetPackageName] = System.currentTimeMillis() + (60 * 60 * 1000L) 
                    }
                )
            }
        }
    }

    private suspend fun checkDayChangeOnStartup() {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val lastCheckStr = prefs.lastStreakCheckDate
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayStr = dateFormat.format(java.util.Date())

        if (lastCheckStr.isNotEmpty() && lastCheckStr != todayStr) {
            updateStreaks()
            shieldRepository.resetAllRemainingTimes()
            notifiedGoals.clear()
            dailyUsageCache.clear()
        }
        
        if (lastCheckStr != todayStr) {
            preferencesRepository.setLastStreakCheckDate(todayStr)
        }
        
        reusableCalendar.timeInMillis = System.currentTimeMillis()
        lastCheckedDay = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private suspend fun updateStreaks() {
        val shields = shieldRepository.allShields.first()
        if (shields.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val todayCal = java.util.Calendar.getInstance().apply { timeInMillis = currentTime }

        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = currentTime
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis

        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        val endTime = cal.timeInMillis

        val usageMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

        shields.forEach { shield ->
            val lastUpdateCal = java.util.Calendar.getInstance().apply { 
                timeInMillis = shield.lastStreakUpdateTimestamp 
            }
            
            val isAlreadyUpdatedToday = lastUpdateCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                    lastUpdateCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
            
            if (isAlreadyUpdatedToday) return@forEach

            val stats = usageMap[shield.packageName]
            val totalUsageYesterday = stats?.let { 
                it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) 
            } ?: 0L

            val limitMillis = shield.timeLimitMinutes * 60 * 1000L

            var shouldIncrement = false
            if (shield.type == FocusType.GOAL) {
                if (totalUsageYesterday >= limitMillis && limitMillis > 0) {
                    shouldIncrement = true
                }
            } else {
                if (totalUsageYesterday <= limitMillis) {
                    shouldIncrement = true
                }
            }

            if (shouldIncrement) {
                val newStreak = shield.currentStreak + 1
                val newBest = maxOf(shield.bestStreak, newStreak)
                shieldRepository.updateShield(shield.copy(
                    currentStreak = newStreak,
                    bestStreak = newBest,
                    lastStreakUpdateTimestamp = currentTime
                ))
            } else {
                shieldRepository.updateShield(shield.copy(
                    currentStreak = 0,
                    lastStreakUpdateTimestamp = currentTime
                ))
            }
        }

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        preferencesRepository.setLastStreakCheckDate(dateFormat.format(java.util.Date(currentTime)))
    }

    private fun getTotalGlobalUsageToday(): Long {
        val currentTime = System.currentTimeMillis()
        val startTime = getStartOfDay()

        val stats = try {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
        } catch (_: Exception) {
            null
        }

        val pm = packageManager
        if (System.currentTimeMillis() - lastLauncherRefreshTime > 3600000 || launcherPackages.isEmpty()) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            launcherPackages = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }.toSet()
            lastLauncherRefreshTime = System.currentTimeMillis()
        }

        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()

        val excludePackages = launcherPackages + packageName

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
        return totalToday
    }

    private fun getTotalUsageToday(packageName: String): Long {
        getUsageStatsList()
        return dailyUsageCache[packageName] ?: 0L
    }

    private fun getUsageStatsList(): List<android.app.usage.UsageStats>? {
        val currentTime = System.currentTimeMillis()
        if (usageStatsCache != null && currentTime - lastUsageCacheTime < 3000) {
            return usageStatsCache
        }

        val startTime = getStartOfDay()

        try {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
            
            if (stats != null) {
                val tempUsageMap = mutableMapOf<String, Long>()
                
                stats.forEach { stat ->
                    val pkg = stat.packageName
                    val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)

                    if (time > (tempUsageMap[pkg] ?: 0L)) {
                        tempUsageMap[pkg] = time
                    }
                }

                tempUsageMap.forEach { (pkg, usage) ->
                    dailyUsageCache[pkg] = usage
                }
                
                usageStatsCache = stats
            }
        } catch (e: Exception) {
            usageStatsCache = null
        }

        lastUsageCacheTime = currentTime
        return usageStatsCache
    }

    private fun getStartOfDay(): Long {
        val currentTime = System.currentTimeMillis()
        reusableCalendar.timeInMillis = currentTime
        val today = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
        if (today != lastStartOfDayDate) {
            reusableCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            reusableCalendar.set(java.util.Calendar.MINUTE, 0)
            reusableCalendar.set(java.util.Calendar.SECOND, 0)
            reusableCalendar.set(java.util.Calendar.MILLISECOND, 0)
            cachedStartOfDay = reusableCalendar.timeInMillis
            lastStartOfDayDate = today
        }
        return cachedStartOfDay
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun updateBedtimeStatus(prefs: UserPreferences) {
        if (!prefs.bedtimeEnabled) {
            isBedtimeActive = false
            isWindDownActive = false
            updateDndAndWindDown(false, false)
            return
        }

        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        
        if (currentDay !in prefs.bedtimeDays) {
            isBedtimeActive = false
            isWindDownActive = false
            updateDndAndWindDown(false, false)
            return
        }

        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startParts = prefs.bedtimeStartTime.split(":")
        val endParts = prefs.bedtimeEndTime.split(":")
        val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

        val active = if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }

        // Wind Down starts 30 minutes before bedtime
        val windDownStartMinutes = (startMinutes - 30 + 1440) % 1440
        
        val wasWindDownActive = isWindDownActive
        val windDownActive = if (windDownStartMinutes <= startMinutes) {
            currentMinutes in windDownStartMinutes until startMinutes
        } else {
            currentMinutes >= windDownStartMinutes || currentMinutes < startMinutes
        }

        if (windDownActive && !wasWindDownActive) {
            windDownUsedPackages.clear()
        }

        isBedtimeActive = active
        isWindDownActive = windDownActive

        updateDndAndWindDown(
            active && prefs.bedtimeDndEnabled,
            (active || windDownActive) && prefs.bedtimeWindDownEnabled
        )
    }

    private fun updateDndAndWindDown(dnd: Boolean, windDown: Boolean) {
        // DND Implementation
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            try {
                val currentFilter = nm.currentInterruptionFilter
                if (dnd && currentFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } else if (!dnd && currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Wind Down implementation logic
        if (windDown) {
            // When wind down is enabled and bedtime is active, 
            // the checkSchedules function will handle showing the overlay
            // for apps that are not in the bedtime whitelist.
        }
    }

    private fun checkSchedules(packageName: String): Boolean {
        if (shouldBypassBlocking(packageName)) return false
        
        val prefs = currentPreferences
        
        // 1. Bedtime First (Strict)
        if (isBedtimeActive) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showBedtimeOverlay(packageName)
                return true
            }
        }

        // 2. Wind Down (30 mins before Bedtime)
        if (isWindDownActive && prefs?.bedtimeWindDownEnabled == true) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showWindDownOverlay(packageName)
                return true
            }
        }

        val now = System.currentTimeMillis()
        reusableCalendar.timeInMillis = now
        val currentTotalMinutes = reusableCalendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + 
                                 reusableCalendar.get(java.util.Calendar.MINUTE)
        
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
                    val currentTime = System.currentTimeMillis()
                    allowedApps[packageName] = currentTime + (minutes * 60 * 1000L)
                    windDownUsedPackages[packageName] = true
                    
                    val currentPrefs = currentPreferences ?: return@showWindDownOverlay
                    if (currentPrefs.sessionUsageOverlayEnabled) {
                        serviceScope.launch(Dispatchers.Main) {
                            sessionUsageOverlayManager.showHUD(
                                packageName,
                                minutes,
                                currentPrefs.sessionUsageOverlaySize,
                                currentPrefs.sessionUsageOverlayOpacity,
                                onSessionEnd = {
                                    allowedApps[packageName] = 0L
                                    serviceScope.launch {
                                        checkSchedules(packageName)
                                    }
                                }
                            )
                        }
                    }
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
        
        // During bedtime or wind down, we only bypass if it's in the bedtime whitelist
        val prefs = currentPreferences
        val isBedtimeOrWindDown = isBedtimeActive || (isWindDownActive && prefs?.bedtimeWindDownEnabled == true)
        
        if (isBedtimeOrWindDown) {
            if (packageName in bedtimeWhitelistedPackages) return true
        } else {
            if (packageName in whitelistedPackages) return true
        }

        if (packageName in CRITICAL_SYSTEM_PACKAGES) return true

        if (launcherPackages.contains(packageName)) return true

        val isSystem = systemAppCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } catch (_: Exception) { true }
        }

        if (isSystem) {
            if (packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("home", ignoreCase = true) ||
                packageName.contains("car.mode", ignoreCase = true)) {
                return true
            }
            return packageName !in BLOCKABLE_SYSTEM_APPS
        }

        return false
    }

    private fun showScheduleOverlay(packageName: String, schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity) {
        val totalGlobalUsageToday = getTotalGlobalUsageToday()
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }
            
            overlayManager.showScheduleOverlay(
                packageName = packageName,
                appName = appName,
                schedule = schedule,
                totalGlobalUsageToday = totalGlobalUsageToday,
                onAllowUse = { minutes, isEmergency ->
                    serviceScope.launch {
                        if (isEmergency) {
                            val currentSchedule = shieldRepository.getScheduleById(schedule.id) ?: return@launch
                            val updatedSchedule = currentSchedule.copy(
                                emergencyUseCount = (currentSchedule.emergencyUseCount - 1).coerceAtLeast(0)
                            )
                            shieldRepository.updateSchedule(updatedSchedule)
                            allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                            
                            val currentPrefs = currentPreferences ?: return@launch
                            if (currentPrefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        packageName,
                                        minutes,
                                        currentPrefs.sessionUsageOverlaySize,
                                        currentPrefs.sessionUsageOverlayOpacity,
                                        onSessionEnd = {
                                            allowedApps[packageName] = 0L
                                            serviceScope.launch {
                                                checkIfAppIsShielded(packageName)
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

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val usageEvents = try {
            usageStatsManager.queryEvents(time - 3000, time)
        } catch (_: Exception) { null } ?: return lastForegroundApp
        
        var lastPackage: String? = null
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(reusableEvent)
            if (reusableEvent.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = reusableEvent.packageName
            }
        }
        
        return lastPackage ?: lastForegroundApp
    }

    override fun onLowMemory() {
        super.onLowMemory()
        onTrimMemory(TRIM_MEMORY_COMPLETE)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
            allowedApps.clear()
            systemAppCache.clear()
            launcherPackages = emptySet()
            usageStatsCache = null
            lastUsageCacheTime = 0L
            lastLauncherRefreshTime = 0L
            try {
                ZenithDatabase.getDatabase(this).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
            } catch (_: Exception) {}
            System.gc()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "zenith_monitor_channel"
        val channel = NotificationChannel(
            channelId, "Zenith Monitor Service", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Zenith is active")
            .setContentText("Protecting your focus...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        serviceJob.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private val CRITICAL_SYSTEM_PACKAGES = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.server.telecom", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller"
        )
        private val BLOCKABLE_SYSTEM_APPS = setOf(
            "com.google.android.youtube", "com.android.chrome",
            "com.google.android.apps.youtube.music", "com.android.vending"
        )
    }
}
