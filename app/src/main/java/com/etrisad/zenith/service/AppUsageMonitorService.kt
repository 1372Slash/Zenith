package com.etrisad.zenith.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
import com.etrisad.zenith.ui.components.overlay.SessionUsageOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

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
    @Volatile
    private var lastForegroundApp: String? = null
    private var cachedForegroundApp: String? = null
    private var cachedForegroundAppTime = 0L
    @Volatile
    private var currentShieldCache: ShieldEntity? = null
    @Volatile
    private var currentSessionPackage: String? = null
    @Volatile
    private var lastUsageFetchTime = 0L
    @Volatile
    private var cachedTotalUsage = 0L
    @Volatile
    private var sessionStartTime = 0L
    @Volatile
    private var baseUsageAtSessionStart = 0L
    private val allowedApps get() = shieldRepository.allowedApps
    private val lastAllowedRemainingTime = ConcurrentHashMap<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = emptySet<String>()
    private var launcherPackages = emptySet<String>()
    private val systemAppCache = ConcurrentHashMap<String, Boolean>()
    private var lastLauncherRefreshTime = 0L
    private var currentPreferences: UserPreferences? = null
    private var allShieldsCache = emptyMap<String, ShieldEntity>()
    private var goalShieldsCache = listOf<ShieldEntity>()
    private val systemZone by lazy { ZoneId.systemDefault() }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var lastCheckedDayDate: LocalDate? = null
    private var keyboardPackages = emptySet<String>()
    private var lastKeyboardRefreshTime = 0L

    private var isBedtimeActive = false
    private var isWindDownActive = false
    private var isBedtimeBlockingActive = false
    private var lastDndFilter: Int? = null
    private var cachedBedtimeStartMinutes = -1
    private var cachedBedtimeEndMinutes = -1
    private val windDownUsedPackages = ConcurrentHashMap<String, Boolean>()
    private var bedtimeWhitelistedPackages = emptySet<String>()
    private var lastCheckedDayTimestamp = 0L
    private var isScreenOn = true
    private var isPowerSaveMode = false

    private var restrictedPackages = emptySet<String>()
    private var hasGlobalAllowSchedule = false
    private var launcherAppsCache = emptySet<String>()
    private var defaultLauncherPackage: String? = null
    private var lastLauncherAppsRefreshTime = 0L

    private var previouslyActiveScheduleIds = setOf<Long>()

    private val mindfulGatewayStates get() = shieldRepository.mindfulGatewayStates

    @Volatile
    private var baseGlobalUsageAtSessionStart = 0L
    @Volatile
    private var cachedTotalGlobalUsage = 0L

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    @Volatile
    private var lastHUDUpdateTime = 0L
    private val HUD_UPDATE_INTERVAL = 1000L
    private val dailyUsageCache = ConcurrentHashMap<String, Long>()
    private var cachedStartOfDay = 0L
    private var lastStartOfDayDate = -1

    private var lastUsageCacheTime = 0L

    private var lastMonitoringError: String? = null
    private var lastMonitoringErrorTime = 0L

    private var cachedBypassPackage: String? = null
    private var cachedBypassResult = false
    private var cachedBypassTime = 0L

    private var monitoringWakeLock: android.os.PowerManager.WakeLock? = null
    private var monitoringLoopActive = false
    private var lastLoopTick = 0L

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    AppStateHolder.isScreenOn.value = true
                    monitoringWakeLock?.let { if (it.isHeld) it.release() }
                    currentSessionPackage = null
                    lastForegroundApp = null
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    AppStateHolder.isScreenOn.value = false
                    currentShieldCache = null
                    currentSessionPackage = null

                    monitoringWakeLock?.let { if (it.isHeld) it.release() }
                }
                android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    isPowerSaveMode = powerManager.isPowerSaveMode
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    serviceScope.launch {
                        preferencesRepository.updateLastChargeTimestamp(System.currentTimeMillis())
                        com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                        refreshData()
                    }
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
            try {
                withContext(Dispatchers.IO) {
                    val prefs = kotlinx.coroutines.withTimeoutOrNull(3000) {
                        preferencesRepository.userPreferencesFlow.first()
                    }
                    if (prefs != null) {
                        currentPreferences = prefs
                        whitelistedPackages = prefs.whitelistedPackages
                        bedtimeWhitelistedPackages = prefs.bedtimeWhitelistedPackages
                        updateBedtimeStatus(prefs)
                    }

                    val shields = kotlinx.coroutines.withTimeoutOrNull(5000) {
                        shieldRepository.isShieldsLoaded.first { it }
                        shieldRepository.allShields.first()
                    }
                    if (shields != null) {
                        allShieldsCache = shields.associateBy { it.packageName }
                    }

                    val schedules = kotlinx.coroutines.withTimeoutOrNull(3000) {
                        shieldRepository.allSchedules.first()
                    }
                    if (schedules != null) {
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

                updateRestrictedPackages()

                dailyUsageCache.clear()
                lastUsageCacheTime = 0L
                lastUsageFetchTime = 0L

                if (!monitoringLoopActive) {
                    startMonitoring()
                }
            } catch (e: Exception) {
                Log.e("ZenithAUMS", "Error in refreshData: ${e.message}")
                if (!monitoringLoopActive) startMonitoring()
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
        val triggerAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        alarmManager.setWindow(android.app.AlarmManager.RTC, triggerAt, 15 * 60 * 1000L, pendingIntent)
    }

    private fun onMidnightReset() {
        serviceScope.launch {
            com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
            updateStreaks()
            preferencesRepository.refreshGlobalStreak(shieldRepository)
            preferencesRepository.refreshAllAppStreaks(shieldRepository)
            shieldRepository.resetAllRemainingTimes()
            notifiedGoals.clear()
            earlyKickManager.reset()
            dailyUsageCache.clear()
            lastAllowedRemainingTime.clear()
            systemAppCache.clear()
            launcherPackages = emptySet()
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

            lastCheckedDayDate = LocalDate.now()
            lastCheckedDayTimestamp = currentTime
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = app.userPreferencesRepository
        overlayManager = InterceptOverlayManager(this, preferencesRepository)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        serviceScope.launch {
            shieldRepository.isShieldsLoaded.first { it }
            shieldRepository.allShields.collect { shields ->
                allShieldsCache = shields.associateBy { it.packageName }
                lastForegroundApp?.let { currentPkg ->
                    currentShieldCache = allShieldsCache[currentPkg]
                }
                goalShieldsCache = shields.filter {
                    it.type == FocusType.GOAL && it.goalReminderPeriodMinutes > 0
                }
                updateRestrictedPackages()
            }
        }

        serviceScope.launch {
            shieldRepository.isShieldsLoaded.first { it }
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
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
        isScreenOn = powerManager.isInteractive
        isPowerSaveMode = powerManager.isPowerSaveMode

        lastCheckedDayDate = LocalDate.now()

        serviceScope.launch {
            currentPreferences = preferencesRepository.userPreferencesFlow.first()
            com.etrisad.zenith.util.AlarmTasksSchedulingHelper.scheduleMidnightResetTask(this@AppUsageMonitorService)
            startMonitoring()
            scheduleHeartbeatAlarm()
        }
    }

    private var lastGoalReminderCheckTime = 0L

    private suspend fun checkGoalReminders() {
        if (!isScreenOn || goalShieldsCache.isEmpty()) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGoalReminderCheckTime < 60000) return
        lastGoalReminderCheckTime = currentTime

        try {
            val goals = goalShieldsCache
            val triggeredGoals = mutableListOf<ShieldEntity>()

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

            if (triggeredGoals.isNotEmpty()) {
                triggeredGoals.forEach { goal ->
                    sendGoalSuggestionNotification(goal)
                    shieldRepository.updateShield(goal.copy(lastGoalReminderTimestamp = currentTime))
                }

                val overlayGoals = triggeredGoals.filter { it.isGoalCallerEnabled }
                if (overlayGoals.isNotEmpty()) {
                    val now = LocalTime.now()
                    val hour = now.hour
                    val isNightTime = hour >= 22 || hour < 5

                    if ((!isBedtimeActive || hour >= 6) && !isNightTime) {
                        AppGoalOverlayActivity.start(this@AppUsageMonitorService, overlayGoals.map { it.packageName })
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ZenithAUMS", "Error in goal reminder check: ${e.message}")
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

        var isCustomBitmap: Boolean
        val iconBitmap = try {
            val drawable = packageManager.getApplicationIcon(goal.packageName)
            if (drawable is BitmapDrawable) {
                isCustomBitmap = false
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                if (width > 2000 || height > 2000) { isCustomBitmap = false; null }
                else {
                    isCustomBitmap = true
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
            }
        } catch (e: Throwable) {
            isCustomBitmap = false
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

        if (isCustomBitmap) iconBitmap?.recycle()
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

    private val notifiedGoals = ConcurrentHashMap.newKeySet<String>()

    private fun startMonitoring() {
        if (monitoringLoopActive && System.currentTimeMillis() - lastLoopTick < 60000) return
        monitoringLoopActive = true
        serviceScope.launch {
            try {
                checkDayChangeOnStartup()
            } catch (t: Throwable) {
                Log.e("ZenithAUMS", "Error in day change check: ${t.message}")
            }
        }

        // Loop 1: Foreground detection (ringan, 3s, only when AS not active)
        serviceScope.launch {
            while (true) {
                try {
                    lastLoopTick = System.currentTimeMillis()
                    if (isScreenOn && !ZenithAccessibilityService.isServiceRunning) {
                        val currentApp = getForegroundApp()
                        if (currentApp != null && currentApp != AppStateHolder.foregroundApp.value) {
                            AppStateHolder.foregroundApp.value = currentApp
                        }
                    }
                } catch (_: Exception) {}
                delay(3000)
            }
        }

        // Loop 2: Main monitoring (event-driven on app change + periodic fallback)
        serviceScope.launch {
            // Process foreground changes reactively
            launch {
                AppStateHolder.foregroundApp
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { currentApp ->
                        try {
                            lastLoopTick = System.currentTimeMillis()
                            handleForegroundChange(currentApp)
                        } catch (t: Throwable) {
                            logError(t)
                        }
                    }
            }

            // Periodic monitoring tick
            while (true) {
                try {
                    lastLoopTick = System.currentTimeMillis()
                    if (isScreenOn && !InterceptOverlayManager.isShowing) {
                        val currentApp = AppStateHolder.foregroundApp.value
                        if (currentApp != null) {
                            monitoringTick(currentApp)
                        }
                    }
                } catch (t: Throwable) {
                    logError(t)
                }

                val delayTime = computeMonitoringDelay()
                delay(delayTime)
            }
        }

        // Loop 3: Background data sync (60s)
        serviceScope.launch {
            while (true) {
                try {
                    if (isScreenOn && launcherPackages.isEmpty()) {
                        refreshLauncherCache()
                    }
                } catch (_: Exception) {}
                delay(60000)
            }
        }

        // Loop 4: Goal reminders (120s)
        serviceScope.launch {
            while (true) {
                try {
                    checkGoalReminders()
                } catch (_: Exception) {}
                delay(120000)
            }
        }

        // Loop 5: Day change & jadwal transisi (120s)
        serviceScope.launch {
            while (true) {
                try {
                    checkDayChangePeriodic()
                } catch (_: Exception) {}
                delay(120000)
            }
        }
    }

    private suspend fun logError(t: Throwable) {
        val currentTime = System.currentTimeMillis()
        if (t.message != lastMonitoringError || currentTime - lastMonitoringErrorTime > 30000) {
            Log.e("ZenithAUMS", "Critical error: ${t.message}", t)
            lastMonitoringError = t.message
            lastMonitoringErrorTime = currentTime
        }
        if (t is OutOfMemoryError) delay(10000) else delay(2000)
    }

    private suspend fun handleForegroundChange(currentApp: String) {
        val currentTime = System.currentTimeMillis()

        if (launcherPackages.isEmpty() || currentTime - lastLauncherRefreshTime > 3600000) {
            refreshLauncherCache()
        }

        if (launcherPackages.contains(currentApp) || currentApp == packageName) {
            overlayManager.hideOverlay()
            sessionUsageOverlayManager.destroyAllHUDs()
            lastForegroundApp?.let { prevPkg ->
                if (prevPkg != packageName && !launcherPackages.contains(prevPkg)) {
                    allShieldsCache[prevPkg]?.let { shield ->
                        if (shield.isDelayAppEnabled) {
                            serviceScope.launch {
                                shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L))
                            }
                        }
                    }
                }
            }
            currentShieldCache = null
            currentSessionPackage = currentApp
            cachedTotalUsage = 0L
            lastForegroundApp = currentApp
            return
        }

        overlayManager.checkAndHide(currentApp)
        if (InterceptOverlayManager.isShowing) {
            lastForegroundApp = currentApp
            return
        }

        sessionUsageOverlayManager.updateForegroundApp(currentApp)

        val isNewSession = currentApp != lastForegroundApp ||
                (currentShieldCache != null && currentShieldCache?.packageName != currentApp) ||
                currentSessionPackage == null

        try {
            lastForegroundApp?.let { prevPkg ->
                if (prevPkg != currentApp) {
                    allShieldsCache[prevPkg]?.let { prevShield ->
                        updateShieldInDatabase(prevShield, force = true)
                    }
                }
            }
        } catch (_: Exception) {}

        if (isNewSession) {
            currentShieldCache = allShieldsCache[currentApp]
            lastUsageFetchTime = 0L
            lastHUDUpdateTime = 0L
            sessionStartTime = currentTime
            currentSessionPackage = currentApp

            val startOfDay = getStartOfDay()
            val timeSinceMidnight = (currentTime - startOfDay).coerceAtLeast(0L)

            val detailedUsage = withContext(Dispatchers.IO) {
                com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
            }
            val systemUsage = detailedUsage.appUsageMap[currentApp] ?: 0L
            val systemGlobal = getFilteredGlobalUsage(detailedUsage.appUsageMap)

            baseUsageAtSessionStart = systemUsage.coerceAtMost(timeSinceMidnight)
            cachedTotalUsage = baseUsageAtSessionStart
            baseGlobalUsageAtSessionStart = systemGlobal.coerceAtMost(timeSinceMidnight)
            cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart

            if (!shouldBypassBlocking(currentApp)) {
                checkBlockingInstant(currentApp, currentShieldCache)
            }
        }

        lastForegroundApp = currentApp
    }

    private suspend fun monitoringTick(currentApp: String) {
        val currentTime = System.currentTimeMillis()
        if (currentApp == packageName || launcherPackages.contains(currentApp)) {
            sessionUsageOverlayManager.updateForegroundApp(currentApp)
            currentShieldCache = null
            currentSessionPackage = currentApp
            cachedTotalUsage = 0L
            return
        }

        if (shouldBypassBlocking(currentApp)) return

        updateUsageTime(currentApp)

        val shield = currentShieldCache
        if (shield != null && shield.type != FocusType.GOAL && !isPaused(shield)) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val actualRemaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
            val prefs = currentPreferences

            val isOverlayShowing = InterceptOverlayManager.isShowing
            val isSessionActive = allowedApps[currentApp]?.let { it > currentTime } ?: false
            val isShieldLimitReached = actualRemaining <= 0L

            val allowedAtRemaining = lastAllowedRemainingTime[currentApp] ?: Long.MAX_VALUE
            val threshold = 300000L
            val sessionStartedAboveThreshold = allowedAtRemaining > threshold

            if (!isOverlayShowing && !isShieldLimitReached &&
                earlyKickManager.shouldKick(currentApp, actualRemaining, prefs?.earlyKickEnabled ?: false) &&
                (!isSessionActive || sessionStartedAboveThreshold)) {

                allowedApps.remove(currentApp)
                withContext(Dispatchers.Main) {
                    sessionUsageOverlayManager.hideHUD(currentApp)
                    Toast.makeText(this@AppUsageMonitorService, "Early Kick: 5 minutes remaining", Toast.LENGTH_LONG).show()
                }
                goToHomeScreen()
                lastForegroundApp = currentApp
                return
            }
        }

        val shieldForPauseCheck = currentShieldCache
        val isAppPaused = shieldForPauseCheck != null && isPaused(shieldForPauseCheck)

        val allowedUntilVal = allowedApps[currentApp]
        if (!isAppPaused && allowedUntilVal != null && allowedUntilVal > currentTime && !ZenithAccessibilityService.isServiceRunning) {
            val prefs = currentPreferences
            if (prefs?.sessionUsageOverlayEnabled == true) {
                val remainingMinutes = ((allowedUntilVal - currentTime) / 60000L).toInt().coerceAtLeast(1)
                val sh = currentShieldCache
                val isGoal = sh?.type == FocusType.GOAL

                val limitMillis = (sh?.timeLimitMinutes ?: 0) * 60 * 1000L
                val currentUsage = if (isGoal) getTotalUsageToday(currentApp) else cachedTotalUsage

                if (!(isGoal && (currentUsage >= limitMillis || notifiedGoals.contains(currentApp)) && limitMillis > 0)) {
                    val duration = if (isGoal) sh?.timeLimitMinutes ?: 0 else remainingMinutes
                    val currentUsageSeconds = (currentUsage / 1000).toInt()
                    withContext(Dispatchers.Main) {
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
                                    val s = allShieldsCache[currentApp] ?: shieldRepository.getShieldByPackageName(currentApp)
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

        if (!isAppPaused && shouldCheckSchedules && !InterceptOverlayManager.isShowing && !ZenithAccessibilityService.isServiceRunning) {
            if (checkSchedules(currentApp)) {
                lastForegroundApp = currentApp
                return
            }

            if (allowedUntilVal == null || currentTime > allowedUntilVal) {
                val sh = currentShieldCache
                val prefs = currentPreferences
                if (sh != null || (prefs?.mindfulGatewayEnabled == true && !shouldBypassBlocking(currentApp))) {
                    if (sh != null && sh.isAutoQuitEnabled && allowedUntilVal != null && allowedUntilVal > 0) {
                        lastKickTime = System.currentTimeMillis()
                        lastKickedPackage = currentApp
                        goToHomeScreen()
                        allowedApps.remove(currentApp)
                        if (sh.isDelayAppEnabled) {
                            serviceScope.launch {
                                shieldRepository.updateShield(sh.copy(lastDelayStartTimestamp = 0L))
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
    }

    private fun computeMonitoringDelay(): Long {
        return try {
            if (!isScreenOn) {
                120000L
            } else if (isPowerSaveMode) {
                120000L
            } else if (InterceptOverlayManager.isShowing) {
                30000L
            } else if (currentPreferences?.customDelayEnabled == true) {
                val prefs = currentPreferences!!
                when {
                    currentShieldCache != null -> {
                        val shield = currentShieldCache!!
                        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                        val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
                        if (shield.type == FocusType.GOAL) {
                            when {
                                remaining < 60000 -> prefs.delayGoalNear.coerceIn(5000L, 10000L)
                                remaining < 300000 -> prefs.delayGoalMid.coerceIn(7000L, 15000L)
                                else -> prefs.delayGoalFar.coerceIn(10000L, 25000L)
                            }
                        } else {
                            when {
                                remaining > 3600000 -> prefs.delayShieldVeryFar.coerceIn(15000L, 30000L)
                                remaining > 600000 -> prefs.delayShieldFar.coerceIn(10000L, 25000L)
                                remaining > 60000 -> prefs.delayShieldMid.coerceIn(7000L, 15000L)
                                else -> prefs.delayShieldNear.coerceIn(5000L, 10000L)
                            }
                        }
                    }
                    else -> prefs.delayDefault.coerceIn(10000L, 30000L)
                }
            } else {
                when {
                    currentShieldCache != null -> {
                        val shield = currentShieldCache!!
                        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                        val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
                        if (shield.type == FocusType.GOAL) {
                            when {
                                remaining < 60000 -> 6000L
                                remaining < 300000 -> 10000L
                                else -> 20000L
                            }
                        } else {
                            when {
                                remaining > 3600000 -> 25000L
                                remaining > 600000 -> 20000L
                                remaining > 60000 -> 12000L
                                else -> 6000L
                            }
                        }
                    }
                    else -> 15000L
                }
            }
        } catch (_: Exception) { 7000L }
    }

    private suspend fun checkDayChangePeriodic() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCheckedDayTimestamp <= 120000) return
        if (lastCheckedDayDate == null) return

        val today = LocalDate.now()
        if (today != lastCheckedDayDate) {
            withContext(Dispatchers.IO) {
                com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                shieldRepository.resetAllRemainingTimes()
            }
            notifiedGoals.clear()
            earlyKickManager.reset()
            dailyUsageCache.clear()
            lastAllowedRemainingTime.clear()
            systemAppCache.clear()
            launcherPackages = emptySet()
            lastUsageCacheTime = 0L
            lastUsageFetchTime = 0L
            cachedTotalUsage = 0L
            cachedTotalGlobalUsage = 0L
            currentShieldCache = null

            sessionStartTime = currentTime
            baseUsageAtSessionStart = 0L
            baseGlobalUsageAtSessionStart = 0L
            lastHUDUpdateTime = 0L

            lastCheckedDayDate = today
            lastCheckedDayTimestamp = currentTime
            return
        }

        currentPreferences?.let { updateBedtimeStatus(it) }
        checkSchedulesTransition(LocalTime.now().hour * 60 + LocalTime.now().minute)
        lastCheckedDayTimestamp = currentTime
    }

    private suspend fun checkBlockingInstant(currentApp: String, shield: ShieldEntity?) {
        if (ZenithAccessibilityService.isServiceRunning) return
        
        val currentTime = System.currentTimeMillis()
        val isAppPaused = shield != null && isPaused(shield)
        val allowedUntil = allowedApps[currentApp]
        val isSessionActive = allowedUntil?.let { it > currentTime } ?: false

        if (!isSessionActive && !InterceptOverlayManager.isShowing) {
            if (checkSchedules(currentApp)) return

            if (!isAppPaused && (allowedUntil == null || currentTime > allowedUntil)) {
                val prefs = currentPreferences
                if (shield != null || (prefs?.mindfulGatewayEnabled == true && !shouldBypassBlocking(currentApp))) {
                    checkIfAppIsShielded(currentApp)
                }
            }
        }
    }

    private fun updateUsageTime(packageName: String) {
        if (packageName != currentSessionPackage) return

        val currentTime = System.currentTimeMillis()
        val sessionElapsed = (currentTime - sessionStartTime).coerceAtLeast(0L)

        cachedTotalUsage = baseUsageAtSessionStart + sessionElapsed
        cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart + sessionElapsed

        val shield = currentShieldCache ?: return
        val shieldType = shield.type

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
        val isNearLimit = remaining < 60000
        val needsDetailedFetch = if (shieldType == FocusType.GOAL) {
            val uiUpdateInterval = when {
                isNearLimit -> 15000L
                remaining < 300000 -> 30000L
                remaining < 900000 -> 45000L
                else -> 60000L
            }
            currentTime - lastHUDUpdateTime > uiUpdateInterval || lastHUDUpdateTime == 0L
        } else {
            val fetchInterval = if (isNearLimit) 30000L else 60000L
            currentTime - lastUsageFetchTime > fetchInterval
        }

        if (needsDetailedFetch) {
            val isGoal = shieldType == FocusType.GOAL
            if (isGoal) lastHUDUpdateTime = currentTime
            else lastUsageFetchTime = currentTime

            serviceScope.launch {
                try {
                    val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)

                    val systemUsage = detailedUsage.appUsageMap[packageName] ?: 0L
                    val systemGlobal = getFilteredGlobalUsage(detailedUsage.appUsageMap)
                    val startOfDay = getStartOfDay()
                    val now = System.currentTimeMillis()
                    val timeSinceMidnight = (now - startOfDay).coerceAtLeast(0L)

                    baseUsageAtSessionStart = systemUsage.coerceAtMost(timeSinceMidnight)
                    baseGlobalUsageAtSessionStart = systemGlobal.coerceAtMost(timeSinceMidnight)
                    sessionStartTime = now
                    cachedTotalUsage = baseUsageAtSessionStart
                    cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart

                    if (isGoal) {
                        withContext(Dispatchers.Main) {
                            sessionUsageOverlayManager.updateHUDUsage(packageName, systemUsage)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        updateShieldInDatabase(shield)
    }

    private var lastDbUpdateTime = 0L

    private fun updateShieldInDatabase(shield: ShieldEntity, force: Boolean = false) {
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

        if (shield.type == FocusType.GOAL && !isPaused(shield) && shield.timeLimitMinutes > 0) {
            if (cachedTotalUsage >= limitMillis) {
                val lastUpdateDate = Instant.ofEpochMilli(shield.lastStreakUpdateTimestamp)
                    .atZone(systemZone).toLocalDate()
                val today = LocalDate.now()

                if (lastUpdateDate != today) {
                    val newStreak = shield.currentStreak + 1
                    val newBest = maxOf(shield.bestStreak, newStreak)
                    updatedShield = updatedShield.copy(
                        currentStreak = newStreak,
                        bestStreak = newBest,
                        lastStreakUpdateTimestamp = currentTime
                    )
                }
            }
        }

        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000
        val shouldUpdateDB = force || timeSinceLastUsed > 120000 || (isNearLimit && timeSinceLastUsed > 60000) || updatedShield != shield

        if (shouldUpdateDB) {
            val finalShield = updatedShield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )

            if (shield.packageName == currentSessionPackage) {
                currentShieldCache = finalShield
            }

            if (currentTime - lastDbUpdateTime > 120000 || force) {
                lastDbUpdateTime = currentTime
                serviceScope.launch {
                    try {
                        val exists = shieldRepository.getShieldByPackageName(finalShield.packageName)
                        if (exists != null) {
                            shieldRepository.updateShield(finalShield)
                        } else {
                            if (currentSessionPackage == finalShield.packageName) {
                                currentShieldCache = null
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("ZenithAUMS", "Failed background DB update for ${shield.packageName}: ${t.message}")
                    }
                }
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

    private var lastKickedPackage: String? = null
    private var lastKickTime = 0L

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        val allowedUntil = allowedApps[targetPackageName] ?: 0L
        if (System.currentTimeMillis() < allowedUntil) return

        if (InterceptOverlayManager.isShowing && InterceptOverlayManager.currentPackage == targetPackageName) {
            return
        }

        if (targetPackageName == InterceptOverlayManager.lastKickedPackage && System.currentTimeMillis() - InterceptOverlayManager.lastKickTime < 4000) {
            return
        }

        val currentForeground = getForegroundApp() ?: lastForegroundApp
        if (targetPackageName != currentForeground && targetPackageName != lastForegroundApp) return

        val shield = if (currentShieldCache?.packageName == targetPackageName) {
            currentShieldCache
        } else {
            allShieldsCache[targetPackageName]
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
            val gracePeriodMillis = if (effectiveShield.isDelayAppEnabled) 15 * 1000L else 30 * 60 * 1000L
            val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd < gracePeriodMillis)

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
                        allowedApps[targetPackageName] = currentTimeOnAllow + (minutes * 60 * 1000L)

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

                            val currentPrefs = currentPreferences ?: return@launch
                            if (currentPrefs.sessionUsageOverlayEnabled) {
                                val isGoal = shieldWithTimestamp.type == FocusType.GOAL
                                val limitMillisOnHUD = (shieldWithTimestamp.timeLimitMinutes) * 60 * 1000L
                                val currentUsage = getTotalUsageToday(targetPackageName)

                                if (!(isGoal && (currentUsage >= limitMillisOnHUD || notifiedGoals.contains(targetPackageName)) && limitMillisOnHUD > 0)) {
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
                                                        if (getForegroundApp() == targetPackageName) {
                                                            goToHomeScreen()
                                                        }
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
                        val now = System.currentTimeMillis()
                        InterceptOverlayManager.lastKickTime = now
                        InterceptOverlayManager.lastKickedPackage = targetPackageName
                        lastKickTime = now
                        lastKickedPackage = targetPackageName
                        serviceScope.launch {
                            val s = currentShieldCache ?: allShieldsCache[targetPackageName] ?: shieldRepository.getShieldByPackageName(targetPackageName)
                            if (s != null && s.isDelayAppEnabled) {
                                val updated = s.copy(lastDelayStartTimestamp = 0L)
                                shieldRepository.updateShield(updated)
                                currentShieldCache = updated
                            }
                        }
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
        val today = LocalDate.now()
        val todayStr = dateFormatter.format(today)

        if (lastCheckStr.isNotEmpty() && lastCheckStr != todayStr) {
            shieldRepository.resetAllRemainingTimes()
            notifiedGoals.clear()
            dailyUsageCache.clear()
            systemAppCache.clear()
            com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
            lastAllowedRemainingTime.clear()
        }

        if (lastCheckStr != todayStr) {
            preferencesRepository.setLastStreakCheckDate(todayStr)
        }

        lastCheckedDayDate = today
    }

    private suspend fun updateStreaks() {
        preferencesRepository.refreshGlobalStreak(shieldRepository)
        preferencesRepository.refreshAllAppStreaks(shieldRepository)

        val prefs = preferencesRepository.userPreferencesFlow.first()
        if (prefs.bedtimeEnabled) {
            preferencesRepository.refreshBedtimeStreak()
        }

        preferencesRepository.setLastStreakCheckDate(dateFormatter.format(LocalDate.now()))
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
        val todayStart = getStartOfDay()
        val timeSinceMidnight = (currentTime - todayStart).coerceAtLeast(0L)

        if (currentTime - lastLauncherAppsRefreshTime > 7200000 || launcherAppsCache.isEmpty()) {
            refreshLauncherCache()
        }

        val excludePackages = setOfNotNull(packageName, defaultLauncherPackage)

        var totalSum = 0L
        appUsageMap.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in launcherAppsCache) {
                if (time > 0) {
                    totalSum += time
                }
            }
        }

        return totalSum.coerceAtMost(timeSinceMidnight)
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

    private fun getCachedUsageToday(packageName: String): Long {
        val cached = dailyUsageCache[packageName]
        if (cached != null) return cached
        return getSystemUsageToday(packageName)
    }

    private fun getUsageStatsList() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUsageCacheTime < 60000 && dailyUsageCache.isNotEmpty()) {
            return
        }

        val startTime = getStartOfDay()
        val timeSinceMidnight = currentTime - startTime

        try {
            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
            dailyUsageCache.clear()
            detailedUsage.appUsageMap.forEach { (pkg, time) ->
                val cappedTime = if (time > timeSinceMidnight) timeSinceMidnight else time
                if (cappedTime > 0) dailyUsageCache[pkg] = cappedTime
            }
        } catch (_: Exception) { }

        lastUsageCacheTime = currentTime
    }

    private var cachedStartOfDayTime = 0L
    private var cachedStartOfDayValue = 0L

    private fun getStartOfDay(): Long {
        val now = System.currentTimeMillis()
        if (now - cachedStartOfDayTime < 60000 && cachedStartOfDayValue > 0) {
            return cachedStartOfDayValue
        }
        val result = LocalDate.now().atStartOfDay(systemZone).toInstant().toEpochMilli()
        cachedStartOfDayTime = now
        cachedStartOfDayValue = result
        return result
    }

    private fun goToHomeScreen() {
        lastForegroundApp = null
        cachedForegroundApp = null
        cachedForegroundAppTime = 0L
        lastEventQueryTime = 0L
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun updateBedtimeStatus(prefs: UserPreferences) {
        val now = Instant.now().atZone(systemZone)
        val currentDay = now.dayOfWeek.let { if (it == java.time.DayOfWeek.SUNDAY) 1 else it.value + 1 }
        val currentMinutes = now.hour * 60 + now.minute

        val yesterdayDay = now.minusDays(1).dayOfWeek.let { if (it == java.time.DayOfWeek.SUNDAY) 1 else it.value + 1 }

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

        val allowedUntil = allowedApps[packageName] ?: 0L
        if (System.currentTimeMillis() < allowedUntil) return false

        val prefs = currentPreferences

        if (isBedtimeActive) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showBedtimeOverlay(packageName)
                return true
            }
            return false
        }

        if (isWindDownActive && prefs?.bedtimeWindDownEnabled == true) {
            if (packageName !in bedtimeWhitelistedPackages) {
                showWindDownOverlay(packageName)
                return true
            }
            return false
        }

        val nowTime = LocalTime.now()
        val currentTotalMinutes = nowTime.hour * 60 + nowTime.minute

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
                    val now = System.currentTimeMillis()
                    InterceptOverlayManager.lastKickTime = now
                    InterceptOverlayManager.lastKickedPackage = packageName
                    lastKickTime = now
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
                    val currentTime = System.currentTimeMillis()
                    val shield = allShieldsCache[packageName]
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
                onCloseApp = {
                    val now = System.currentTimeMillis()
                    InterceptOverlayManager.lastKickTime = now
                    InterceptOverlayManager.lastKickedPackage = packageName
                    lastKickTime = now
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
        val now = System.currentTimeMillis()
        if (packageName == cachedBypassPackage && now - cachedBypassTime < 2000) {
            return cachedBypassResult
        }

        if (packageName == this.packageName) {
            cachedBypassPackage = packageName; cachedBypassResult = true; cachedBypassTime = now
            return true
        }

        if (packageName == InterceptOverlayManager.lastKickedPackage && now - InterceptOverlayManager.lastKickTime < 5000) {
            cachedBypassPackage = packageName; cachedBypassResult = true; cachedBypassTime = now
            return true
        }

        val prefs = currentPreferences
        val isBedtimeOrWindDown = isBedtimeActive || (isWindDownActive && prefs?.bedtimeWindDownEnabled == true)

        val result = when {
            packageName in whitelistedPackages && packageName !in restrictedPackages -> {
                if (!isBedtimeOrWindDown || prefs?.mindfulGatewayEnabled != true) true else null
            }
            isBedtimeOrWindDown && packageName in bedtimeWhitelistedPackages && packageName !in restrictedPackages -> {
                if (prefs?.mindfulGatewayEnabled != true) true else null
            }
            isKeyboardApp(packageName) -> true
            packageName in CRITICAL_SYSTEM_PACKAGES -> true
            launcherPackages.contains(packageName) ||
                packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("home", ignoreCase = true) -> true
            packageName in restrictedPackages -> false
            else -> null
        }

        if (result != null) {
            cachedBypassPackage = packageName; cachedBypassResult = result; cachedBypassTime = now
            return result
        }

        val isSystem = systemAppCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } catch (_: Exception) { false }
        }

        val finalResult = if (isSystem) {
            if (packageName.contains("car.mode", ignoreCase = true)) true
            else if (isBedtimeOrWindDown) false
            else !(packageName in restrictedPackages || hasGlobalAllowSchedule || (prefs?.mindfulGatewayEnabled == true))
        } else false

        cachedBypassPackage = packageName; cachedBypassResult = finalResult; cachedBypassTime = now
        return finalResult
    }

    private fun isKeyboardApp(packageName: String): Boolean {
        val currentTime = System.currentTimeMillis()
        if (keyboardPackages.isEmpty() || currentTime - lastKeyboardRefreshTime > 600000) {
            try {
                val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                keyboardPackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
                lastKeyboardRefreshTime = currentTime
            } catch (_: Exception) {}
        }
        return packageName in keyboardPackages
    }

    private fun updateRestrictedPackages() {
        val shieldPkgs = allShieldsCache.keys
        val schedulePkgs = activeSchedules.asSequence().filter { it.mode == com.etrisad.zenith.data.local.entity.ScheduleMode.BLOCK }
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
                    if (isEmergency) {
                        val currentTime = System.currentTimeMillis()
                        allowedApps[packageName] = currentTime + (minutes * 60 * 1000L)

                        serviceScope.launch {
                            val shield = allShieldsCache[packageName]
                            if (shield != null) {
                                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                                lastAllowedRemainingTime[packageName] = (limitMillis - getTotalUsageToday(packageName)).coerceAtLeast(0L)
                            }

                            val currentSchedule = shieldRepository.getScheduleById(schedule.id) ?: return@launch
                            val updatedSchedule = currentSchedule.copy(
                                emergencyUseCount = (currentSchedule.emergencyUseCount - 1).coerceAtLeast(0)
                            )
                            shieldRepository.updateSchedule(updatedSchedule)

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
                onCloseApp = {
                    val now = System.currentTimeMillis()
                    InterceptOverlayManager.lastKickTime = now
                    InterceptOverlayManager.lastKickedPackage = packageName
                    lastKickTime = now
                    lastKickedPackage = packageName
                    goToHomeScreen()
                }
            )
        }
    }

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        if (time - cachedForegroundAppTime < 4000 && cachedForegroundApp != null) {
            return cachedForegroundApp
        }

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

                    val pkg = reusableEvent.packageName
                    if (pkg != null) {
                        val className = reusableEvent.className ?: ""
                        if (className.contains("Notification", ignoreCase = true) ||
                            className.contains("Toast", ignoreCase = true) ||
                            className.contains("Tooltip", ignoreCase = true)) continue

                        foundPackage = pkg
                    }
                }
                lastEventQueryTime = reusableEvent.timeStamp
            }
        }

        if (lastEventQueryTime < time) {
            lastEventQueryTime = time
        }

        val result = foundPackage ?: lastForegroundApp
        cachedForegroundApp = result
        cachedForegroundAppTime = time
        return result
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
            systemAppCache.clear()
            launcherPackages = emptySet()
            lastUsageCacheTime = 0L
            lastLauncherRefreshTime = 0L
            try {
                ZenithDatabase.getDatabase(this).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
            } catch (_: Exception) {}
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
            overlayManager.hideOverlay()
            sessionUsageOverlayManager.destroyAllHUDs()
        } catch (_: Exception) {}
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