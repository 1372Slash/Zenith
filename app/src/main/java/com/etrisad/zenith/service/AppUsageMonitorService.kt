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
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.service.ZenithNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    private val earlyKickManager = EarlyKickManager()
    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val powerManager by lazy { getSystemService(POWER_SERVICE) as android.os.PowerManager }
    private val reusableEvent = UsageEvents.Event()
    private var lastEventQueryTime = 0L
    private var lastForegroundApp: String? = null
    private var currentShieldCache: ShieldEntity? = null
    private var currentSessionPackage: String? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private var sessionStartTime = 0L
    private var baseUsageAtSessionStart = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private val lastAllowedRemainingTime = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = emptySet<String>()
    private var launcherPackages = emptySet<String>()
    private val systemAppCache = mutableMapOf<String, Boolean>()
    private var lastLauncherRefreshTime = 0L
    private var currentPreferences: UserPreferences? = null
    private var allShieldsCache = listOf<ShieldEntity>()
    private var goalShieldsCache = listOf<ShieldEntity>()
    private var restrictedPackages = emptySet<String>()
    private var hasGlobalAllowSchedule = false
    private var launcherAppsCache = emptySet<String>()
    private var defaultLauncherPackage: String? = null
    private var lastLauncherAppsRefreshTime = 0L

    private var isBedtimeActive = false
    private var isWindDownActive = false
    private var isBedtimeBlockingActive = false
    private var lastDndFilter: Int? = null
    private var cachedBedtimeStartMinutes = -1
    private var cachedBedtimeEndMinutes = -1
    private val windDownUsedPackages = mutableMapOf<String, Boolean>()
    private var bedtimeWhitelistedPackages = emptySet<String>()
    private var lastCheckedDayTimestamp = 0L
    private var isScreenOn = true
    private var isPowerSaveMode = false

    private var previouslyActiveScheduleIds = setOf<Long>()

    private var baseGlobalUsageAtSessionStart = 0L
    private var cachedTotalGlobalUsage = 0L

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var lastHUDUpdateTime = 0L
    private val HUD_UPDATE_INTERVAL = 1000L
    private val dailyUsageCache = mutableMapOf<String, Long>()
    private var cachedStartOfDay = 0L
    private var lastStartOfDayDate = -1

    private var usageStatsCache: List<android.app.usage.UsageStats>? = null
    private var lastUsageCacheTime = 0L

    private var lastMonitoringError: String? = null
    private var lastMonitoringErrorTime = 0L

    private var monitoringWakeLock: android.os.PowerManager.WakeLock? = null
    private var monitoringLoopActive = false

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    monitoringWakeLock?.let { if (it.isHeld) it.release() }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    currentShieldCache = null
                    currentSessionPackage = null
                    usageStatsCache = null
                    
                    if (monitoringWakeLock == null) {
                        monitoringWakeLock = powerManager.newWakeLock(
                            android.os.PowerManager.PARTIAL_WAKE_LOCK,
                            "Zenith:MonitorWakeLock"
                        )
                    }
                    monitoringWakeLock?.acquire(600_000L)
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
        when (intent?.action) {
            "SEND_TEST_NOTIFICATION" -> sendTestNotification()
            "com.etrisad.zenith.action.MIDNIGHT_RESET_SERVICE" -> onMidnightReset()
            "com.etrisad.zenith.action.HEARTBEAT" -> {
                scheduleHeartbeatAlarm()
                if (!monitoringLoopActive) {
                    startMonitoring()
                }
            }
            "com.etrisad.zenith.action.REFRESH_DATA" -> {
                refreshData()
            }
        }
        return START_STICKY
    }

    private fun refreshData() {
        serviceScope.launch {
            currentPreferences = preferencesRepository.userPreferencesFlow.first()
            whitelistedPackages = currentPreferences?.whitelistedPackages ?: emptySet()
            bedtimeWhitelistedPackages = currentPreferences?.bedtimeWhitelistedPackages ?: emptySet()

            allShieldsCache = shieldRepository.allShields.first()

            val schedules = shieldRepository.allSchedules.first()
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
            currentPreferences?.let { updateBedtimeStatus(it) }

            dailyUsageCache.clear()
            usageStatsCache = null
            lastUsageCacheTime = 0L
            lastUsageFetchTime = 0L

            if (!monitoringLoopActive) {
                startMonitoring()
            }
        }
    }

    private fun scheduleHeartbeatAlarm() {
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, ZenithHeartbeatReceiver::class.java).apply {
            action = "com.etrisad.zenith.action.HEARTBEAT"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + 15 * 60 * 1000L
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            try {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }

    private fun onMidnightReset() {
        serviceScope.launch {
            updateStreaks()
            preferencesRepository.refreshGlobalStreak(shieldRepository)
            shieldRepository.resetAllRemainingTimes()
            notifiedGoals.clear()
            earlyKickManager.reset()
            dailyUsageCache.clear()
            com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
            lastAllowedRemainingTime.clear()
            usageStatsCache = null
            lastUsageCacheTime = 0L
            lastUsageFetchTime = 0L
            cachedTotalUsage = 0L
            cachedTotalGlobalUsage = 0L
            currentShieldCache = null
            
            val currentTime = System.currentTimeMillis()
            sessionStartTime = currentTime
            baseUsageAtSessionStart = 0L
            baseGlobalUsageAtSessionStart = 0L
            lastHUDUpdateTime = 0L
            
            val calendar = Calendar.getInstance()
            lastCheckedDay = calendar.get(Calendar.DAY_OF_YEAR)
            lastCheckedDayTimestamp = currentTime
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this, preferencesRepository)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        serviceScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShieldsCache = shields
                lastForegroundApp?.let { currentPkg ->
                    currentShieldCache = shields.find { it.packageName == currentPkg }
                }
                goalShieldsCache = shields.filter { 
                    it.type == FocusType.GOAL && it.goalReminderPeriodMinutes > 0 
                }
                updateRestrictedPackages()
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

        startForeground(NOTIFICATION_ID, createNotification())
        createGoalNotificationChannel()
        createBedtimeNotificationChannel()
        
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
            com.etrisad.zenith.util.AlarmTasksSchedulingHelper.scheduleMidnightResetTask(this@AppUsageMonitorService)
            startMonitoring()
            scheduleHeartbeatAlarm()
        }
        startGoalReminderCheck()
    }

    private fun startGoalReminderCheck() {
        serviceScope.launch {
            while (true) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val goals = goalShieldsCache
                    val triggeredGoals = mutableListOf<ShieldEntity>()

                    if (goals.isNotEmpty()) {
                        goals.forEach { goal ->
                            if (goal.packageName == lastForegroundApp) return@forEach
                            if (isPaused(goal)) return@forEach

                            val usageToday = dailyUsageCache[goal.packageName] ?: getTotalUsageToday(goal.packageName)
                            val limitMillis = goal.timeLimitMinutes * 60 * 1000L
                            if (usageToday >= limitMillis) return@forEach

                            val periodMillis = goal.goalReminderPeriodMinutes * 60 * 1000L
                            if (periodMillis > 0 && currentTime - goal.lastGoalReminderTimestamp >= periodMillis) {
                                triggeredGoals.add(goal)
                            }
                        }
                    }

                    if (triggeredGoals.isNotEmpty()) {
                        triggeredGoals.forEach { goal ->
                            sendGoalSuggestionNotification(goal)
                            shieldRepository.updateShield(goal.copy(lastGoalReminderTimestamp = currentTime))
                        }

                        val overlayGoals = triggeredGoals.filter { it.isGoalCallerEnabled }
                        if (overlayGoals.isNotEmpty()) {
                            val calendar = Calendar.getInstance()
                            val hour = calendar.get(Calendar.HOUR_OF_DAY)
                            val isNightTime = hour >= 22 || hour < 5

                            if ((!isBedtimeActive || hour >= 6) && !isNightTime) {
                                try {
                                    val wakeLock = powerManager.newWakeLock(
                                        android.os.PowerManager.FULL_WAKE_LOCK or
                                                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                                                android.os.PowerManager.ON_AFTER_RELEASE,
                                        "Zenith:GoalWakeLock"
                                    )
                                    wakeLock.acquire(3000)
                                } catch (_: Exception) {}

                                AppGoalOverlayActivity.start(this@AppUsageMonitorService, overlayGoals.map { it.packageName })
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ZenithAUMS", "Error in goal reminder check: ${e.message}")
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
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                if (width > 2000 || height > 2000) null
                else {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
            }
        } catch (e: Throwable) {
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
        if (monitoringLoopActive) return
        monitoringLoopActive = true
        serviceScope.launch {
            checkDayChangeOnStartup()

            while (true) {
                try {
                    val currentTime = System.currentTimeMillis()

                    val calendar = java.util.Calendar.getInstance()
                    val currentDay = calendar.get(Calendar.DAY_OF_YEAR)

                    if (lastCheckedDay != -1 && currentDay != lastCheckedDay) {
                        withContext(Dispatchers.IO) {
                            updateStreaks()
                            shieldRepository.resetAllRemainingTimes()
                        }
                        preferencesRepository.refreshGlobalStreak(shieldRepository)
                        notifiedGoals.clear()
                        earlyKickManager.reset()
                        dailyUsageCache.clear()
                        com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                        lastAllowedRemainingTime.clear()
                        usageStatsCache = null
                        lastUsageCacheTime = 0L
                        lastUsageFetchTime = 0L
                        cachedTotalUsage = 0L
                        cachedTotalGlobalUsage = 0L
                        currentShieldCache = null
                        
                        sessionStartTime = currentTime
                        baseUsageAtSessionStart = 0L
                        baseGlobalUsageAtSessionStart = 0L
                        lastHUDUpdateTime = 0L
                        
                        lastCheckedDay = currentDay
                        lastCheckedDayTimestamp = currentTime

                        delay(1000)
                        continue
                    }

                    if (currentTime - lastCheckedDayTimestamp > 60000) {
                        currentPreferences?.let { updateBedtimeStatus(it) }

                        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                        
                        checkSchedulesTransition(currentMinutes)
                        lastCheckedDayTimestamp = currentTime
                    }

                    if (!isScreenOn) {
                        delay(5000)
                        continue
                    }

                    val isAccessibilityActive = try {
                        ZenithAccessibilityService.isServiceRunning
                    } catch (_: Exception) { false }

                    if (isAccessibilityActive) {
                        currentShieldCache = null
                        usageStatsCache = null
                        lastUsageFetchTime = 0L
                        delay(3000)
                        continue
                    }

                    var currentApp = getForegroundApp()
                    
                    val isSystemTransient = currentApp == "com.android.systemui" || currentApp == "android"
                    
                    if ((currentApp == null || isSystemTransient) && isScreenOn && lastForegroundApp != null &&
                        lastForegroundApp != packageName && !launcherPackages.contains(lastForegroundApp)) {
                        currentApp = lastForegroundApp
                    }

                    if (currentApp != null) {
                        overlayManager.checkAndHide(currentApp)
                    } else if (lastForegroundApp != null && !InterceptOverlayManager.isShowing) {
                        overlayManager.hideOverlay()
                    }

                    if (InterceptOverlayManager.isShowing) {
                        lastForegroundApp = currentApp
                        delay(5000)
                        continue
                    }

                    if (launcherPackages.isEmpty() || currentTime - lastLauncherRefreshTime > 600000) {
                        refreshLauncherCache()
                    }

                    if (currentApp != null && currentApp != packageName) {
                        sessionUsageOverlayManager.updateForegroundApp(currentApp)
                        
                        if (currentApp != lastForegroundApp || currentShieldCache?.packageName != currentApp) {
                            lastForegroundApp?.let { prevPkg ->
                                try {
                                    allShieldsCache.find { it.packageName == prevPkg }?.let { prevShield ->
                                        updateShieldInDatabase(prevShield, force = true)
                                    }
                                } catch (_: Exception) {}
                            }

                            currentShieldCache = allShieldsCache.find { it.packageName == currentApp }
                            if (currentApp != lastForegroundApp) {
                                com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                                lastUsageFetchTime = 0L
                                lastHUDUpdateTime = 0L
                                sessionStartTime = currentTime
                                currentSessionPackage = currentApp

                                val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
                                val systemUsage = detailedUsage.appUsageMap[currentApp] ?: 0L
                                val systemGlobal = getFilteredGlobalUsage(detailedUsage.appUsageMap)

                                val startOfDay = getStartOfDay()
                                val timeSinceMidnight = (currentTime - startOfDay).coerceAtLeast(0L)
                                
                                baseUsageAtSessionStart = systemUsage.coerceAtMost(timeSinceMidnight)
                                cachedTotalUsage = baseUsageAtSessionStart
                                baseGlobalUsageAtSessionStart = systemGlobal.coerceAtMost(timeSinceMidnight)
                                cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart

                                if (!shouldBypassBlocking(currentApp)) {
                                    checkBlockingInstant(currentApp, currentShieldCache)
                                }
                            }
                        }

                        if (shouldBypassBlocking(currentApp)) {
                            lastForegroundApp = currentApp
                            val delayTime = if (isPowerSaveMode) 3000L else 1800L
                            delay(delayTime)
                            continue
                        }

                        val allowedUntilVal = allowedApps[currentApp]
                        try {
                            updateUsageTime(currentApp)
                        } catch (_: Exception) {}

                        val shield = currentShieldCache
                        if (shield != null && shield.type != FocusType.GOAL && !isPaused(shield)) {
                            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                            val actualRemaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
                            val prefs = currentPreferences
                            
                            val isOverlayShowing = InterceptOverlayManager.isShowing
                            val isSessionActive = allowedUntilVal != null && allowedUntilVal > currentTime
                            val isShieldLimitReached = actualRemaining <= 0L
                            
                            val allowedAtRemaining = lastAllowedRemainingTime[currentApp] ?: Long.MAX_VALUE
                            val threshold = 300000L
                            val sessionStartedAboveThreshold = allowedAtRemaining > threshold
                            
                            if (!isOverlayShowing && !isShieldLimitReached &&
                                earlyKickManager.shouldKick(currentApp, actualRemaining, prefs?.earlyKickEnabled ?: false) &&
                                (!isSessionActive || sessionStartedAboveThreshold)) {
                                
                                allowedApps.remove(currentApp)
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.hideHUD(currentApp)
                                    Toast.makeText(this@AppUsageMonitorService, "Early Kick: 5 minutes remaining", Toast.LENGTH_LONG).show()
                                }
                                goToHomeScreen()
                                lastForegroundApp = currentApp
                                delay(1500)
                                continue
                            }
                        }
                        
                        val shieldForPauseCheck = currentShieldCache
                        val isAppPaused = shieldForPauseCheck != null && isPaused(shieldForPauseCheck)

                        if (!isAppPaused && allowedUntilVal != null && allowedUntilVal > currentTime) {
                            val prefs = currentPreferences ?: continue
                            if (prefs.sessionUsageOverlayEnabled) {
                                val remainingMinutes = ((allowedUntilVal - currentTime) / 60000L).toInt().coerceAtLeast(1)
                                val shield = currentShieldCache
                                val isGoal = shield?.type == FocusType.GOAL

                                val limitMillis = (shield?.timeLimitMinutes ?: 0) * 60 * 1000L
                                if (!(isGoal && cachedTotalUsage >= limitMillis && limitMillis > 0)) {
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
                                                allowedApps.remove(currentApp)
                                                serviceScope.launch {
                                                    val s = allShieldsCache.find { it.packageName == currentApp } ?: shieldRepository.getShieldByPackageName(currentApp)
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
                                    }
                                }
                            }
                        }

                        val isBedtimeBlocking = isBedtimeActive || (isWindDownActive && currentPreferences?.bedtimeWindDownEnabled == true)
                        val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in bedtimeWhitelistedPackages) || (allowedUntilVal == null || currentTime > allowedUntilVal)

                        if (!isAppPaused && shouldCheckSchedules && !InterceptOverlayManager.isShowing) {
                            if (checkSchedules(currentApp)) {
                                lastForegroundApp = currentApp
                                delay(1000)
                                continue
                            }

                            if (allowedUntilVal == null || currentTime > allowedUntilVal) {
                                val shield = currentShieldCache
                                val prefs = currentPreferences
                                if (shield != null || (prefs?.mindfulGatewayEnabled == true && !shouldBypassBlocking(currentApp))) {
                                    if (shield != null && shield.isAutoQuitEnabled && allowedUntilVal != null && allowedUntilVal > 0) {
                                        goToHomeScreen()
                                        allowedApps.remove(currentApp)
                                        if (shield.isDelayAppEnabled) {
                                            serviceScope.launch {
                                                shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L))
                                            }
                                            currentShieldCache = currentShieldCache?.copy(lastDelayStartTimestamp = 0L)
                                        }
                                    } else {
                                        if (allowedUntilVal != null && allowedUntilVal > 0) {
                                            allowedApps.remove(currentApp)
                                        }
                                        checkIfAppIsShielded(currentApp)
                                    }
                                }
                            }
                        }
                    } else if (currentApp != null && (currentApp == packageName || launcherPackages.contains(currentApp))) {
                        sessionUsageOverlayManager.updateForegroundApp(currentApp)
                        currentShieldCache = null
                        currentSessionPackage = currentApp
                        cachedTotalUsage = 0L
                    }
                    
                    lastForegroundApp = currentApp
                } catch (e: Exception) {
                    val currentTime = System.currentTimeMillis()
                    if (e.message != lastMonitoringError || currentTime - lastMonitoringErrorTime > 30000) {
                        Log.e("ZenithAUMS", "Error in monitoring loop: ${e.message}", e)
                        lastMonitoringError = e.message
                        lastMonitoringErrorTime = currentTime
                    }
                }

                val delayTime = try {
                    if (currentPreferences?.customDelayEnabled == true) {
                        val prefs = currentPreferences!!
                        when {
                            isPowerSaveMode -> prefs.delayPowerSave
                            InterceptOverlayManager.isShowing -> prefs.delayOverlayShowing
                            currentShieldCache != null -> {
                                val shield = currentShieldCache!!
                                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                                val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)

                                if (shield.type == FocusType.GOAL) {
                                    when {
                                        remaining < 60000 -> prefs.delayGoalNear
                                        remaining < 300000 -> prefs.delayGoalMid
                                        else -> prefs.delayGoalFar
                                    }
                                } else {
                                    when {
                                        remaining > 3600000 -> prefs.delayShieldVeryFar
                                        remaining > 600000 -> prefs.delayShieldFar
                                        remaining > 60000 -> prefs.delayShieldMid
                                        else -> prefs.delayShieldNear
                                    }
                                }
                            }
                            else -> prefs.delayDefault
                        }
                    } else {
                        when {
                            isPowerSaveMode -> 5000L
                            InterceptOverlayManager.isShowing -> 8000L
                            currentShieldCache != null -> {
                                val shield = currentShieldCache!!
                                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                                val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)

                                if (shield.type == FocusType.GOAL) {
                                    when {
                                        remaining < 60000 -> 600L
                                        remaining < 300000 -> 1200L
                                        else -> 1800L
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
                    }
                } catch (_: Exception) { 2000L }
                delay(delayTime)
            }
        }
    }

    private suspend fun checkBlockingInstant(currentApp: String, shield: ShieldEntity?) {
        val currentTime = System.currentTimeMillis()
        val isAppPaused = shield != null && isPaused(shield)
        
        if (!isAppPaused) {
            val allowedUntil = allowedApps[currentApp]
            val isBedtimeBlocking = isBedtimeActive || (isWindDownActive && currentPreferences?.bedtimeWindDownEnabled == true)
            val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in bedtimeWhitelistedPackages) || (allowedUntil == null || currentTime > allowedUntil)

            if (shouldCheckSchedules && !InterceptOverlayManager.isShowing) {
                if (checkSchedules(currentApp)) return

                if (allowedUntil == null || currentTime > allowedUntil) {
                    val prefs = currentPreferences
                    if (shield != null || (prefs?.mindfulGatewayEnabled == true && !shouldBypassBlocking(currentApp))) {
                        checkIfAppIsShielded(currentApp)
                    }
                }
            }
        }
    }

    private suspend fun updateUsageTime(packageName: String) {
        if (packageName != currentSessionPackage) return

        val currentTime = System.currentTimeMillis()
        val sessionElapsed = (currentTime - sessionStartTime).coerceAtLeast(0L)
        
        cachedTotalUsage = baseUsageAtSessionStart + sessionElapsed
        cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart + sessionElapsed

        val shield = currentShieldCache ?: return

        if (shield.type == FocusType.GOAL) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
            
            val uiUpdateInterval = when {
                remaining < 60000 -> 5000L
                remaining < 300000 -> 10000L
                remaining < 900000 -> 20000L
                else -> 30000L
            }

            if (currentTime - lastHUDUpdateTime > uiUpdateInterval || lastHUDUpdateTime == 0L) {
                lastHUDUpdateTime = currentTime

                val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
                val realUsage = (detailedUsage.appUsageMap[packageName] ?: 0L).coerceAtMost(currentTime - getStartOfDay())
                
                val systemGlobal = getFilteredGlobalUsage(detailedUsage.appUsageMap)
                baseGlobalUsageAtSessionStart = systemGlobal
                cachedTotalGlobalUsage = systemGlobal

                baseUsageAtSessionStart = realUsage
                sessionStartTime = currentTime
                cachedTotalUsage = realUsage
                
                serviceScope.launch(Dispatchers.Main) {
                    sessionUsageOverlayManager.updateHUDUsage(packageName, realUsage)
                }
            }
        } else if (currentTime - lastUsageFetchTime > 30000) {
            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
            val systemUsage = detailedUsage.appUsageMap[packageName] ?: 0L
            val systemGlobal = getFilteredGlobalUsage(detailedUsage.appUsageMap)
            val startOfDay = getStartOfDay()
            val timeSinceMidnight = (currentTime - startOfDay).coerceAtLeast(0L)
            
            baseUsageAtSessionStart = systemUsage.coerceAtMost(timeSinceMidnight) - sessionElapsed
            baseGlobalUsageAtSessionStart = systemGlobal.coerceAtMost(timeSinceMidnight) - sessionElapsed
            lastUsageFetchTime = currentTime
        }

        updateShieldInDatabase(shield)
    }

    private suspend fun updateShieldInDatabase(shield: ShieldEntity, force: Boolean = false) {
        val currentTime = System.currentTimeMillis()
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
        val shouldUpdateDB = force || timeSinceLastUsed > 10000 || (isNearLimit && timeSinceLastUsed > 5000) || updatedShield != shield

        if (shouldUpdateDB) {
            val finalShield = updatedShield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            shieldRepository.updateShield(finalShield)
            if (shield.packageName == currentSessionPackage) {
                currentShieldCache = finalShield
            }

            if (shield.type == FocusType.GOAL && !isPaused(shield)) {
                if (cachedTotalUsage >= limitMillis && !notifiedGoals.contains(shield.packageName)) {
                    sendGoalReachedNotification(shield.appName, shield.packageName)
                    notifiedGoals.add(shield.packageName)
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

    private fun createBedtimeNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(BEDTIME_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    BEDTIME_CHANNEL_ID, "Bedtime & Wind Down", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for bedtime and wind down mode"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun sendWindDownNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, BEDTIME_CHANNEL_ID)
            .setContentTitle("Time for Wind Down")
            .setContentText("Bedtime is in 30 minutes. Prepare and get ready for bed.")
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(WIND_DOWN_NOTIFICATION_ID, notification)
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
        if (InterceptOverlayManager.isShowing && InterceptOverlayManager.currentPackage == targetPackageName) {
            return
        }
        val currentForeground = getForegroundApp() ?: lastForegroundApp
        if (targetPackageName != currentForeground && targetPackageName != lastForegroundApp) return

        val shield = if (currentShieldCache?.packageName == targetPackageName) {
            currentShieldCache
        } else {
            allShieldsCache.find { it.packageName == targetPackageName }
        }
        
        val prefs = currentPreferences ?: return
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
            val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd > 30 * 60 * 1000L)

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

            currentShieldCache = if (isMindfulGateway) null else shieldWithTimestamp

            serviceScope.launch(Dispatchers.Main) {
                overlayManager.showOverlay(
                    packageName = targetPackageName,
                    appName = appName,
                    shield = shieldWithTimestamp,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    delayDurationSeconds = delayDurationSeconds,
                    onAllowUse = { minutes, isEmergency ->
                                val currentTimeOnAllow = System.currentTimeMillis()
                                val limitMillis = (shieldWithTimestamp.timeLimitMinutes) * 60 * 1000L
                                lastAllowedRemainingTime[targetPackageName] = if (shieldWithTimestamp.timeLimitMinutes > 0) (limitMillis - getTotalUsageToday(targetPackageName)).coerceAtLeast(0L) else Long.MAX_VALUE
                                
                                serviceScope.launch {
                                    if (isMindfulGateway) {
                                        val currentMindful = mindfulGatewayStates[targetPackageName] ?: shieldWithTimestamp
                                        val updatedMindful = if (isEmergency) {
                                            currentMindful.copy(
                                                emergencyUseCount = (currentMindful.emergencyUseCount - 1).coerceAtLeast(0),
                                                lastSessionEndTimestamp = currentTimeOnAllow
                                            )
                                        } else {
                                            val periodExpired = currentTimeOnAllow - currentMindful.lastPeriodResetTimestamp > currentMindful.refreshPeriodMinutes * 60 * 1000L
                                            currentMindful.copy(
                                                currentPeriodUses = if (periodExpired) 1 else currentMindful.currentPeriodUses + 1,
                                                lastPeriodResetTimestamp = if (periodExpired) currentTimeOnAllow else currentMindful.lastPeriodResetTimestamp,
                                                lastSessionEndTimestamp = currentTimeOnAllow
                                            )
                                        }
                                        mindfulGatewayStates[targetPackageName] = updatedMindful
                                    } else {
                                        val currentShield = shieldRepository.getShieldByPackageName(targetPackageName)
                                        val updatedShield = if (currentShield != null) {
                                            if (isEmergency) {
                                                val isFirstChargeUsed = currentShield.emergencyUseCount == currentShield.maxEmergencyUses
                                                currentShield.copy(
                                                    emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                                    lastEmergencyRechargeTimestamp = if (isFirstChargeUsed) System.currentTimeMillis() else currentShield.lastEmergencyRechargeTimestamp,
                                                    lastDelayStartTimestamp = 0L,
                                                    lastSessionEndTimestamp = currentTimeOnAllow
                                                )
                                            } else {
                                                val periodExpired = System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                                currentShield.copy(
                                                    currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                                    lastPeriodResetTimestamp = if (periodExpired) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp,
                                                    lastDelayStartTimestamp = 0L,
                                                    lastSessionEndTimestamp = currentTimeOnAllow
                                                )
                                            }
                                        } else null

                                        if (updatedShield != null) {
                                            shieldRepository.updateShield(updatedShield)
                                            currentShieldCache = updatedShield
                                        }
                                    }
                                    
                                    allowedApps[targetPackageName] = currentTimeOnAllow + (minutes * 60 * 1000L)
                            
                                    val currentPrefs = currentPreferences ?: return@launch
                                    if (currentPrefs.sessionUsageOverlayEnabled) {
                                        val isGoal = shieldWithTimestamp.type == FocusType.GOAL
                                        val limitMillisOnHUD = (shieldWithTimestamp.timeLimitMinutes) * 60 * 1000L
                                        val currentUsage = getTotalUsageToday(targetPackageName)

                                        if (isGoal && currentUsage >= limitMillisOnHUD && limitMillisOnHUD > 0) {
                                        } else {
                                            val duration = if (isGoal) shieldWithTimestamp.timeLimitMinutes else minutes
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
                                                        allowedApps.remove(targetPackageName)
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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        if (lastCheckStr.isNotEmpty() && lastCheckStr != todayStr) {
            updateStreaks()
            shieldRepository.resetAllRemainingTimes()
            notifiedGoals.clear()
            dailyUsageCache.clear()
            com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
            lastAllowedRemainingTime.clear()
        }
        
        if (lastCheckStr != todayStr) {
            preferencesRepository.setLastStreakCheckDate(todayStr)
        }
        
        lastCheckedDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    }

    private suspend fun updateStreaks() {
        val shields = shieldRepository.allShields.first()
        val prefs = preferencesRepository.userPreferencesFlow.first()
        
        if (shields.isEmpty() && prefs.screenTimeTargetMinutes <= 0) return

        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime
        val todayYear = calendar.get(Calendar.YEAR).toLong()
        val todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR).toLong()
        
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endTime = calendar.timeInMillis

        val usageMap = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (_: Exception) { null }
        
        val lastUpdateCalendar = Calendar.getInstance().apply { timeInMillis = prefs.globalLastStreakUpdateTimestamp }
        val isGlobalAlreadyUpdated = lastUpdateCalendar.get(Calendar.YEAR).toLong() == todayYear &&
                                   lastUpdateCalendar.get(Calendar.DAY_OF_YEAR).toLong() == todayDayOfYear

        if (usageMap != null && !isGlobalAlreadyUpdated && prefs.screenTimeTargetMinutes > 0) {
            var totalUsageYesterday = 0L
            val excludePackages = launcherPackages + packageName
            
            usageMap.forEach { (pkg, stat) ->
                if (pkg !in excludePackages) {
                    totalUsageYesterday += stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                }
            }

            val targetMillis = prefs.screenTimeTargetMinutes * 60 * 1000L
            if (totalUsageYesterday <= targetMillis) {
                val newStreak = prefs.globalCurrentStreak + 1
                val newBest = maxOf(prefs.globalBestStreak, newStreak)
                preferencesRepository.updateGlobalStreak(newStreak, newBest, currentTime)
            } else {
                preferencesRepository.updateGlobalStreak(0, prefs.globalBestStreak, currentTime)
            }
        }

        shields.forEach { shield ->
            val shieldUpdateCalendar = Calendar.getInstance().apply { timeInMillis = shield.lastStreakUpdateTimestamp }
            val isAlreadyUpdatedToday = shieldUpdateCalendar.get(Calendar.YEAR).toLong() == todayYear &&
                                       shieldUpdateCalendar.get(Calendar.DAY_OF_YEAR).toLong() == todayDayOfYear
            
            if (isAlreadyUpdatedToday) return@forEach

            val stats = usageMap?.get(shield.packageName)
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

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        preferencesRepository.setLastStreakCheckDate(dateFormat.format(Date(currentTime)))
    }

    private fun getTotalGlobalUsageToday(): Long {
        if (cachedTotalGlobalUsage > 0) return cachedTotalGlobalUsage
        return getSystemGlobalUsageToday()
    }

    private fun getSystemGlobalUsageToday(): Long {
        val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
        return getFilteredGlobalUsage(detailedUsage.appUsageMap)
    }

    private fun getFilteredGlobalUsage(appUsageMap: Map<String, Long>): Long {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLauncherAppsRefreshTime > 3600000 || launcherAppsCache.isEmpty()) {
            refreshLauncherCache()
        }

        val excludePackages = setOfNotNull(packageName, defaultLauncherPackage)

        var totalToday = 0L
        appUsageMap.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in launcherAppsCache) {
                if (time > 0) {
                    totalToday += time
                }
            }
        }
        val todayStart = getStartOfDay()
        return totalToday.coerceAtMost(currentTime - todayStart)
    }

    private fun refreshLauncherCache() {
        try {
            val pm = packageManager
            launcherAppsCache = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()
            
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val launchers = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            launcherPackages = launchers.map { it.activityInfo.packageName }.toSet()
            
            defaultLauncherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            
            val currentTime = System.currentTimeMillis()
            lastLauncherAppsRefreshTime = currentTime
            lastLauncherRefreshTime = currentTime
        } catch (_: Exception) {}
    }

    private fun getTotalUsageToday(packageName: String): Long {
        if (packageName == currentSessionPackage && cachedTotalUsage > 0) {
            return cachedTotalUsage
        }
        return getSystemUsageToday(packageName)
    }

    private fun getSystemUsageToday(packageName: String): Long {
        getUsageStatsList()
        return dailyUsageCache[packageName] ?: 0L
    }

    private fun getUsageStatsList(): List<android.app.usage.UsageStats>? {
        val currentTime = System.currentTimeMillis()
        if (usageStatsCache != null && currentTime - lastUsageCacheTime < 5000) {
            return usageStatsCache
        }

        val startTime = getStartOfDay()
        val timeSinceMidnight = currentTime - startTime

        try {
            val tempUsageMap = mutableMapOf<String, Long>()
            val statsList = mutableListOf<android.app.usage.UsageStats>()

            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
            detailedUsage.appUsageMap.forEach { (pkg, millis) ->
                tempUsageMap[pkg] = millis
            }

            usageStatsManager.queryAndAggregateUsageStats(startTime, currentTime)?.forEach { (_, stat) ->
                statsList.add(stat)
            }

            dailyUsageCache.clear()
            tempUsageMap.forEach { (pkg, time) ->
                val cappedTime = if (time > timeSinceMidnight) timeSinceMidnight else time
                if (cappedTime > 0) dailyUsageCache[pkg] = cappedTime
            }
            usageStatsCache = statsList
        } catch (e: Exception) {
            usageStatsCache = null
        }

        lastUsageCacheTime = currentTime
        return usageStatsCache
    }

    private fun getStartOfDay(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun updateBedtimeStatus(prefs: UserPreferences) {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTime }
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        
        val yesterdayCalendar = Calendar.getInstance().apply {
            timeInMillis = currentTime
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDay = yesterdayCalendar.get(Calendar.DAY_OF_WEEK)

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
        if (windDownActive && !wasWindDownActive) {
            windDownUsedPackages.clear()
            if (prefs.bedtimeNotificationEnabled) {
                sendWindDownNotification()
            }
        }

        isBedtimeActive = active
        isWindDownActive = windDownActive
        isBedtimeBlockingActive = active || (windDownActive && prefs.bedtimeWindDownEnabled)

        updateDndAndWindDown(
            active && prefs.bedtimeDndEnabled,
            (active || windDownActive) && prefs.bedtimeWindDownEnabled
        )
    }

    private fun updateDndAndWindDown(dnd: Boolean, windDown: Boolean) {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            try {
                val targetFilter = if (dnd) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                
                if (lastDndFilter == null) {
                    lastDndFilter = notificationManager.currentInterruptionFilter
                }

                if (lastDndFilter != targetFilter) {
                    notificationManager.setInterruptionFilter(targetFilter)
                    lastDndFilter = targetFilter
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkSchedulesTransition(currentTotalMinutes: Int) {
        val currentlyActiveIds = mutableSetOf<Long>()
        for (ps in parsedSchedulesCache) {
            val isInInterval = if (ps.startMinutes <= ps.endMinutes) {
                currentTotalMinutes in ps.startMinutes..ps.endMinutes
            } else {
                currentTotalMinutes >= ps.startMinutes || currentTotalMinutes <= ps.endMinutes
            }
            if (isInInterval) {
                currentlyActiveIds.add(ps.id)
            }
        }

        val endedSchedules = previouslyActiveScheduleIds - currentlyActiveIds
        endedSchedules.forEach { id ->
            ZenithNotificationListener.restoreNotifications(this, id)
        }
        previouslyActiveScheduleIds = currentlyActiveIds
    }

    private fun checkSchedules(packageName: String): Boolean {
        if (shouldBypassBlocking(packageName)) return false
        
        val prefs = currentPreferences

        if (isBedtimeActive) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showBedtimeOverlay(packageName)
                return true
            }
        }

        if (isWindDownActive && prefs?.bedtimeWindDownEnabled == true) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showWindDownOverlay(packageName)
                return true
            }
        }

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentTotalMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + 
                                 calendar.get(java.util.Calendar.MINUTE)
        
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
                    val shield = allShieldsCache.find { it.packageName == packageName }
                    if (shield != null) {
                        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                        lastAllowedRemainingTime[packageName] = (limitMillis - getTotalUsageToday(packageName)).coerceAtLeast(0L)
                    }
                    
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
                                    allowedApps.remove(packageName)
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

        val prefs = currentPreferences
        val isBedtimeOrWindDown = isBedtimeActive || (isWindDownActive && prefs?.bedtimeWindDownEnabled == true)
        
        if (isBedtimeOrWindDown) {
            if (packageName in bedtimeWhitelistedPackages) return true
        } else {
            if (packageName in whitelistedPackages) return true
        }

        if (packageName in CRITICAL_SYSTEM_PACKAGES) return true
        if (launcherPackages.contains(packageName) || 
            packageName.contains("launcher", ignoreCase = true) || 
            packageName.contains("home", ignoreCase = true)) return true

        if (packageName in restrictedPackages) return false

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
        val schedulePkgs = activeSchedules.filter { it.mode == com.etrisad.zenith.data.local.entity.ScheduleMode.BLOCK }
            .flatMap { it.packageNames }.toSet()
        hasGlobalAllowSchedule = activeSchedules.any { it.mode == com.etrisad.zenith.data.local.entity.ScheduleMode.ALLOW }
        restrictedPackages = shieldPkgs + schedulePkgs + BLOCKABLE_SYSTEM_APPS
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
                            val shield = allShieldsCache.find { it.packageName == packageName }
                            if (shield != null) {
                                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                                lastAllowedRemainingTime[packageName] = (limitMillis - getTotalUsageToday(packageName)).coerceAtLeast(0L)
                            }
                            
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
                                            allowedApps.remove(packageName)
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
        val queryStart = if (lastEventQueryTime == 0L) time - 10000 else lastEventQueryTime
        val usageEvents = try {
            usageStatsManager.queryEvents(queryStart, time)
        } catch (_: Exception) { null } 

        var foundPackage: String? = null
        if (usageEvents != null) {
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(reusableEvent)
                if (reusableEvent.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                    reusableEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {

                    val className = reusableEvent.className ?: ""
                    if (className.contains("Notification", ignoreCase = true) || 
                        className.contains("Toast", ignoreCase = true) ||
                        className.contains("Tooltip", ignoreCase = true)) continue
                    
                    foundPackage = reusableEvent.packageName
                }
                lastEventQueryTime = reusableEvent.timeStamp
            }
        }

        if (lastEventQueryTime < time) {
            lastEventQueryTime = time
        }

        if (foundPackage == null) {
            return lastForegroundApp
        }
        
        return foundPackage
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
            lastAllowedRemainingTime.clear()
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
        monitoringLoopActive = false
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        serviceJob.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val BEDTIME_CHANNEL_ID = "zenith_bedtime_channel"
        private const val WIND_DOWN_NOTIFICATION_ID = 2001
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
