package com.etrisad.zenith.service

import android.app.AlarmManager
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
import com.etrisad.zenith.R
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.preferences.ForegroundNotificationStatusMode
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.data.website.WebsiteStateHolder
import com.etrisad.zenith.service.earlykick.EarlyKickHandler
import com.etrisad.zenith.service.earlykick.EarlyKickManager
import com.etrisad.zenith.ui.components.overlay.SessionUsageOverlayManager
import com.etrisad.zenith.ui.components.overlay.UsageGlimpseOverlayManager
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
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class AppUsageMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private lateinit var overlayActionHandler: OverlayActionHandler
    private val earlyKickManager get() = SharedMonitoringState.earlyKickManager
    private val earlyKickHandler = EarlyKickHandler(SharedMonitoringState.earlyKickManager)
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
    private val periodUsageCache = ConcurrentHashMap<String, Long>()
    private var lastLauncherRefreshTime = 0L
    private val systemZone by lazy { ZoneId.systemDefault() }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var lastCheckedDayDate: LocalDate? = null
    private var lastCheckedDayStart: Long = 0L

    private var lastDndFilter: Int? = null
    private var lastCheckedDayTimestamp = 0L
    @Volatile
    private var isScreenOn = true

    private var previouslyActiveScheduleIds = setOf<Long>()

    @Volatile
    private var baseGlobalUsageAtSessionStart = 0L
    @Volatile
    private var cachedTotalGlobalUsage = 0L

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    @Volatile
    private var lastHUDUpdateTime = 0L
    private val HUD_UPDATE_INTERVAL = 1000L

    private var usageGlimpseManager: UsageGlimpseOverlayManager? = null
    private var usageGlimpseJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var lastGlimpseShowTime = 0L
    private val GLIMPSE_INTERVAL_MS = 5 * 60 * 1000L
    private val GLIMPSE_DISPLAY_MS = 5000L

    @Volatile
    private var eyeCareCumulativeSeconds = 0
    @Volatile
    private var eyeCareOnBreak = false
    private var eyeCareJob: kotlinx.coroutines.Job? = null

    private var lastUsageCacheTime = 0L

    private var foregroundNotificationStarted = false
    private var lastNotificationRefreshTime = 0L
    private var lastNotificationText: String? = null

    private var cachedBypassPackage: String? = null
    private var cachedBypassResult = false
    private var cachedBypassTime = 0L

    private var monitoringLoopActive = false
    private var lastLoopTick = 0L

    private var monitoringJob: kotlinx.coroutines.Job? = null
    private var foregroundAppJob: kotlinx.coroutines.Job? = null

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("Zenith_SCREEN", "SCREEN ON received")
                    isScreenOn = true
                    AppStateHolder.isScreenOn.value = true
                    currentSessionPackage = null
                    cachedForegroundApp = null
                    cachedForegroundAppTime = 0L
                    cancelScreenOffGoalAlarm()
                    Log.w("ZenithAUMS", "SCREEN ON: starting foreground")
                    try {
                        startForeground(NOTIFICATION_ID, createNotification())
                    } catch (e: Exception) {
                        Log.e("ZenithAUMS", "startForeground failed: ${e.message}")
                    }
                    overlayManager.hideOverlay()
                    val currentApp = getForegroundApp()
                    if (currentApp != null) {
                        lastForegroundApp = currentApp
                        AppStateHolder.foregroundApp.value = currentApp
                        serviceScope.launch {
                            delay(300)
                            if (isScreenOn && !shouldBypassBlocking(currentApp)) {
                                checkBlockingInstant(currentApp, currentShieldCache)
                                if (ZenithService.isServiceRunning) {
                                    ZenithService.lastEventTime = 0L
                                }
                            }
                        }
                    }
                    if (!monitoringLoopActive) {
                        Log.w("ZenithAUMS", "SCREEN ON: calling startMonitoring()")
                        startMonitoring()
                    } else {
                        Log.w("ZenithAUMS", "SCREEN ON: monitoringLoopActive=$monitoringLoopActive, SKIP startMonitoring")
                    }
                    Log.d("Zenith_SCREEN", "SCREEN ON: restoring HUD views")
                    sessionUsageOverlayManager.restoreAllHUDViews()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("Zenith_SCREEN", "SCREEN OFF received")
                    isScreenOn = false
                    AppStateHolder.isScreenOn.value = false
                    currentShieldCache = null
                    currentSessionPackage = null
                    monitoringJob?.cancel()
                    foregroundAppJob?.cancel()
                    monitoringJob = null
                    foregroundAppJob = null
                    monitoringLoopActive = false
                    overlayActionHandler.cancelPendingTimers()
                    Log.d("Zenith_SCREEN", "Calling hideAllHUDViews() due to SCREEN OFF")
            sessionUsageOverlayManager.destroyAllHUDs()
                    stopEyeCareTimer()
                    usageGlimpseManager?.hide()
                    usageGlimpseJob?.cancel()
                    scheduleScreenOffGoalAlarm()
                    Log.w("ZenithAUMS", "SCREEN OFF: monitoring cancelled")
                }
                    android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        AppStateHolder.isPowerSaveMode.value = powerManager.isPowerSaveMode
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SEND_TEST_NOTIFICATION" -> sendTestNotification()
            "com.etrisad.zenith.action.MIDNIGHT_RESET_SERVICE" -> onMidnightReset()
            "com.etrisad.zenith.action.HEARTBEAT" -> {
                scheduleHeartbeatAlarm()
                refreshData()
            }
            "com.etrisad.zenith.action.REFRESH_DATA" -> {
                refreshData()
            }
            "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK" -> {
                if (!isScreenOn) {
                    Log.d("Zenith_SCREEN", "SCREEN_OFF_GOAL_CHECK: running checkGoalReminders()")
                    serviceScope.launch { checkGoalReminders() }
                    scheduleScreenOffGoalAlarm()
                } else {
                    Log.d("Zenith_SCREEN", "SCREEN_OFF_GOAL_CHECK: ignored, screen is ON")
                }
            }
            "com.etrisad.zenith.action.TEST_GOAL_CALLER" -> {
                try {
                    val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
                    val alarmIntent = Intent(this, ZenithHeartbeatReceiver::class.java).apply {
                        action = "com.etrisad.zenith.action.TEST_GOAL_CALLER_FIRE"
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        this, 1003, alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 10 * 60 * 1000L,
                        pendingIntent
                    )
                    Toast.makeText(this, "Test goal caller will fire in 10 minutes", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("Zenith", "Failed to schedule test goal caller", e)
                }
            }
            "com.etrisad.zenith.action.TEST_GOAL_CALLER_FIRE" -> {
                Log.d("Zenith_SCREEN", "TEST_GOAL_CALLER_FIRE: firing test goal caller")
                serviceScope.launch { sendTestGoalCallerNotification() }
            }
        }
        return START_STICKY
    }

    private fun refreshData() {
        if (!isScreenOn) {
            Log.d("Zenith_SCREEN", "refreshData() SKIPPED: screen is OFF")
            return
        }
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val prefs = kotlinx.coroutines.withTimeoutOrNull(3000) {
                        preferencesRepository.userPreferencesFlow.first()
                    }
                    if (prefs != null) {
                        SharedMonitoringState.currentPreferences = prefs
                        SharedMonitoringState.performanceLevel = prefs.performanceLevel
                        val initCfg = prefs.buildPerformanceConfig()
                        SharedMonitoringState.performanceConfig = initCfg
                        com.etrisad.zenith.util.ScreenUsageHelper.updateCacheDuration(initCfg.usageStatsCacheMs)
                        SharedMonitoringState.whitelistedPackages = prefs.whitelistedPackages
                        SharedMonitoringState.bedtimeWhitelistedPackages = prefs.bedtimeWhitelistedPackages
                        val gpStart = prefs.gracePeriodStartTime.split(":")
                        val gpEnd = prefs.gracePeriodEndTime.split(":")
                        SharedMonitoringState.cachedGracePeriodStartMinutes = (gpStart.getOrNull(0)?.toIntOrNull() ?: 12) * 60 + (gpStart.getOrNull(1)?.toIntOrNull() ?: 0)
                        SharedMonitoringState.cachedGracePeriodEndMinutes = (gpEnd.getOrNull(0)?.toIntOrNull() ?: 13) * 60 + (gpEnd.getOrNull(1)?.toIntOrNull() ?: 0)
                        SharedMonitoringState.cachedDayStartHour = prefs.dayStartHour
                        SharedMonitoringState.cachedDayStartMinute = prefs.dayStartMinute
                        updateBedtimeStatus(prefs)
                        updateGracePeriodStatus(prefs)
                    }

                    val shields = kotlinx.coroutines.withTimeoutOrNull(5000) {
                        shieldRepository.isShieldsLoaded.first { it }
                        shieldRepository.allShields.first()
                    }
                    if (shields != null) {
                        SharedMonitoringState.allShieldsCache = shields.associateBy { it.packageName }
                        refreshPeriodUsageCache()
                    }

                    val schedules = kotlinx.coroutines.withTimeoutOrNull(3000) {
                        shieldRepository.allSchedules.first()
                    }
                    if (schedules != null) {
                        SharedMonitoringState.activeSchedules = schedules.filter { it.isActive }
                        SharedMonitoringState.parsedSchedulesCache = SharedMonitoringState.activeSchedules.map { s ->
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

                SharedMonitoringState.updateRestrictedPackages()

                SharedMonitoringState.dailyUsageCache.clear()
                lastUsageCacheTime = 0L
                lastUsageFetchTime = 0L

                if (!monitoringLoopActive && isScreenOn) {
                    startMonitoring()
                }
            } catch (e: Exception) {
                Log.e("ZenithAUMS", "Error in refreshData: ${e.message}")
                if (!monitoringLoopActive && isScreenOn) startMonitoring()
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

    private fun scheduleScreenOffGoalAlarm() {
        val hasCallerGoals = SharedMonitoringState.goalShieldsCache.any { it.isGoalCallerEnabled }
        if (!hasCallerGoals) return
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, ZenithHeartbeatReceiver::class.java).apply {
            action = "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val interval = 15 * 60 * 1000L
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + interval,
            pendingIntent
        )
    }

    private fun cancelScreenOffGoalAlarm() {
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, ZenithHeartbeatReceiver::class.java).apply {
            action = "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun onMidnightReset() {
        serviceScope.launch {
            com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
            updateStreaks()
            preferencesRepository.refreshGlobalStreak(shieldRepository)
            preferencesRepository.refreshAppStreaks(shieldRepository)
            preferencesRepository.refreshWebStreaks(shieldRepository)
            shieldRepository.resetDailyRemainingTimes()
            checkWeeklyReset()
            SharedMonitoringState.notifiedGoals.clear()
            earlyKickManager.reset()
            SharedMonitoringState.dailyUsageCache.clear()
            lastAllowedRemainingTime.clear()
            SharedMonitoringState.lastKnownPackageUsage.clear()
            periodUsageCache.clear()
            refreshPeriodUsageCache()
            SharedMonitoringState.systemAppCache.clear()
            SharedMonitoringState.launcherPackages = emptySet()
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

            lastCheckedDayDate = java.time.LocalDate.now()
            lastCheckedDayStart = com.etrisad.zenith.util.DateTimeUtils.getDayStartTime(currentTime)
            lastCheckedDayTimestamp = currentTime
        }
    }

    private suspend fun checkWeeklyReset() {
        val prefs = SharedMonitoringState.currentPreferences
        val lastReset = prefs?.lastWeeklyResetDate ?: 0L
        val now = Calendar.getInstance()
        val isMonday = now.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (isMonday && todayStart != lastReset) {
            shieldRepository.resetWeeklyRemainingTimes()
            periodUsageCache.clear()
            preferencesRepository.setLastWeeklyResetDate(todayStart)
            refreshPeriodUsageCache()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = app.userPreferencesRepository
        overlayManager = InterceptOverlayManager(this, preferencesRepository)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this, preferencesRepository)
        overlayActionHandler = OverlayActionHandler(
            shieldRepository = shieldRepository,
            overlayManager = overlayManager,
            sessionUsageOverlayManager = sessionUsageOverlayManager,
            packageManager = packageManager,
            inputMethodManager = getSystemService(android.view.inputmethod.InputMethodManager::class.java),
            contextPkg = packageName,
            scope = serviceScope,
            goToHomeScreen = { goToHomeScreen() },
            quitWebsite = { goToHomeScreen() },
            getForegroundAppName = { getForegroundApp() },
            recheckShield = { pkg -> serviceScope.launch { checkIfAppIsShielded(pkg) } },
            getTotalUsageToday = { pkg -> getTotalUsageToday(pkg) },
            getTotalGlobalUsageToday = { getTotalGlobalUsageToday() },
        )

        usageGlimpseManager = UsageGlimpseOverlayManager(this)

        launchCollectors()
        createMonitorNotificationChannel()

        startForeground(NOTIFICATION_ID, createNotification())
        foregroundNotificationStarted = true
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
        AppStateHolder.isPowerSaveMode.value = powerManager.isPowerSaveMode

        lastCheckedDayDate = java.time.LocalDate.now()
        lastCheckedDayStart = com.etrisad.zenith.util.DateTimeUtils.getDayStartTime(System.currentTimeMillis(), 0, 0)
        SharedMonitoringState.cachedDayStartHour = 0
        SharedMonitoringState.cachedDayStartMinute = 0

        com.etrisad.zenith.util.ScreenUsageHelper.clearCache()

        serviceScope.launch {
            val initPrefs = preferencesRepository.userPreferencesFlow.first()
            SharedMonitoringState.cachedDayStartHour = initPrefs.dayStartHour
            SharedMonitoringState.cachedDayStartMinute = initPrefs.dayStartMinute
            lastCheckedDayStart = com.etrisad.zenith.util.DateTimeUtils.getDayStartTime(System.currentTimeMillis(), initPrefs.dayStartHour, initPrefs.dayStartMinute)
            com.etrisad.zenith.util.AlarmTasksSchedulingHelper.scheduleMidnightResetTask(this@AppUsageMonitorService, initPrefs.dayStartHour, initPrefs.dayStartMinute)
            startMonitoring()
            scheduleHeartbeatAlarm()
        }
    }

    private var lastGoalReminderCheckTime = 0L

    private suspend fun checkGoalReminders() {
        Log.d("Zenith_SCREEN", "checkGoalReminders() called (isScreenOn=$isScreenOn)")
        val goals = SharedMonitoringState.goalShieldsCache
        if (goals.isEmpty()) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGoalReminderCheckTime < 60000) return
        lastGoalReminderCheckTime = currentTime

        try {
            val triggeredGoals = mutableListOf<ShieldEntity>()

            goals.forEach { goal ->
                if (goal.packageName == lastForegroundApp) return@forEach
                if (isPaused(goal)) return@forEach

                val usageToday = SharedMonitoringState.dailyUsageCache[goal.packageName] ?: getTotalUsageToday(goal.packageName)
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
                }

                val overlayGoals = triggeredGoals.filter { it.isGoalCallerEnabled }
                if (overlayGoals.isNotEmpty()) {
                    sendGoalCallerNotification(overlayGoals)
                }

                triggeredGoals.forEach { goal ->
                    shieldRepository.updateShield(goal.copy(lastGoalReminderTimestamp = currentTime))
                }
            }
        } catch (e: Exception) {
            Log.e("ZenithAUMS", "Error in goal reminder check: ${e.message}")
        }
    }

    private fun sendGoalSuggestionNotification(goal: ShieldEntity) {
        try {
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
                val maxSize = 256
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val (targetW, targetH) = if (width > maxSize || height > maxSize) {
                    val scale = maxSize.toFloat() / maxOf(width, height)
                    (width * scale).toInt() to (height * scale).toInt()
                } else width to height
                isCustomBitmap = true
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Throwable) {
            isCustomBitmap = false
            null
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Time for ${goal.appName}?")
            .setContentText("Your goal setting suggests it's time to open ${goal.appName} and make some progress!")
            .setSmallIcon(R.drawable.ic_flag)
            .setLargeIcon(iconBitmap)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(goal.packageName.hashCode() + 1000, notification)

        if (isCustomBitmap) iconBitmap?.recycle()
        } catch (e: Exception) {
            Log.e("ZenithAUMS", "sendGoalSuggestionNotification failed: ${e.message}", e)
        }
    }

    private fun sendGoalCallerNotification(goals: List<ShieldEntity>) {
        try {
            val channelId = "zenith_goal_caller_channel"
            val manager = getSystemService(NotificationManager::class.java)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(
                        channelId, "Goal Caller", NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Full-screen goal caller alerts"
                    }
                    manager.createNotificationChannel(channel)
                }
            }

            val firstGoal = goals.first()
            val intent = Intent(this, AppGoalOverlayActivity::class.java).apply {
                putStringArrayListExtra(
                    AppGoalOverlayActivity.EXTRA_PACKAGE_NAMES,
                    ArrayList(goals.map { it.packageName })
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Time for ${firstGoal.appName}?")
                .setContentText("Your goal setting suggests it's time to open ${firstGoal.appName} and make some progress!")
                .setSmallIcon(R.drawable.ic_flag)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setTimeoutAfter(5000L)
            }

            manager.notify(2000, builder.build())

            if (!isScreenOn) {
                val directIntent = Intent(this, AppGoalOverlayActivity::class.java).apply {
                    putStringArrayListExtra(
                        AppGoalOverlayActivity.EXTRA_PACKAGE_NAMES,
                        ArrayList(goals.map { it.packageName })
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                val wakePendingIntent = PendingIntent.getActivity(
                    this, 1002, directIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 100,
                    wakePendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e("ZenithAUMS", "sendGoalCallerNotification failed: ${e.message}", e)
        }
    }

    private suspend fun sendTestGoalCallerNotification() {
        try {
            val channelId = "zenith_goal_caller_channel"
            val manager = getSystemService(NotificationManager::class.java)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(
                        channelId, "Goal Caller", NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Full-screen goal caller alerts"
                    }
                    manager.createNotificationChannel(channel)
                }
            }

            val testPackageName = packageName
        val testAppName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(testPackageName, 0)).toString()
        } catch (_: Exception) { "Zenith" }

        val intent = Intent(this, AppGoalOverlayActivity::class.java).apply {
            putStringArrayListExtra(
                AppGoalOverlayActivity.EXTRA_PACKAGE_NAMES,
                arrayListOf(testPackageName)
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 2000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test: Time for $testAppName?")
            .setContentText("[TEST] Your goal setting suggests it's time to open $testAppName and make some progress!")
            .setSmallIcon(R.drawable.ic_flag)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setTimeoutAfter(5000L)
        }

        manager.notify(2001, builder.build())

        if (!isScreenOn) {
            val directIntent = Intent(this, AppGoalOverlayActivity::class.java).apply {
                putStringArrayListExtra(
                    AppGoalOverlayActivity.EXTRA_PACKAGE_NAMES,
                    arrayListOf(testPackageName)
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            val wakePendingIntent = PendingIntent.getActivity(
                this, 1004, directIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 100,
                wakePendingIntent
            )
        }
        } catch (e: Exception) {
            Log.e("ZenithAUMS", "sendTestGoalCallerNotification failed: ${e.message}", e)
        }
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



    private var startMonitoringCount = 0

    private fun launchCollectors() {
        serviceScope.launch {
            shieldRepository.isShieldsLoaded.first { it }
            shieldRepository.allShields.collect { shields ->
                SharedMonitoringState.allShieldsCache = shields.associateBy { it.packageName }
                lastForegroundApp?.let { currentPkg ->
                    currentShieldCache = SharedMonitoringState.allShieldsCache[currentPkg]
                }
                SharedMonitoringState.goalShieldsCache = shields.filter {
                    it.type == FocusType.GOAL && it.goalReminderPeriodMinutes > 0
                }
                if (!isScreenOn) {
                    cancelScreenOffGoalAlarm()
                    scheduleScreenOffGoalAlarm()
                }
                SharedMonitoringState.updateRestrictedPackages()
                refreshPeriodUsageCache()
                refreshForegroundNotification(force = true)
            }
        }

        serviceScope.launch {
            shieldRepository.isShieldsLoaded.first { it }
            shieldRepository.allSchedules.collect { schedules ->
                SharedMonitoringState.activeSchedules = schedules.filter { it.isActive }
                SharedMonitoringState.parsedSchedulesCache = SharedMonitoringState.activeSchedules.map { s ->
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
                SharedMonitoringState.updateRestrictedPackages()
                refreshForegroundNotification(force = true)
            }
        }

        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                SharedMonitoringState.currentPreferences = preferences
                SharedMonitoringState.performanceLevel = preferences.performanceLevel
                val cfg = preferences.buildPerformanceConfig()
                SharedMonitoringState.performanceConfig = cfg
                com.etrisad.zenith.util.ScreenUsageHelper.updateCacheDuration(cfg.usageStatsCacheMs)
                SharedMonitoringState.whitelistedPackages = preferences.whitelistedPackages
                SharedMonitoringState.bedtimeWhitelistedPackages = preferences.bedtimeWhitelistedPackages

                val startParts = preferences.bedtimeStartTime.split(":")
                val endParts = preferences.bedtimeEndTime.split(":")
                SharedMonitoringState.cachedBedtimeStartMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
                SharedMonitoringState.cachedBedtimeEndMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

                val gpStart = preferences.gracePeriodStartTime.split(":")
                val gpEnd = preferences.gracePeriodEndTime.split(":")
                SharedMonitoringState.cachedGracePeriodStartMinutes = (gpStart.getOrNull(0)?.toIntOrNull() ?: 12) * 60 + (gpStart.getOrNull(1)?.toIntOrNull() ?: 0)
                SharedMonitoringState.cachedGracePeriodEndMinutes = (gpEnd.getOrNull(0)?.toIntOrNull() ?: 13) * 60 + (gpEnd.getOrNull(1)?.toIntOrNull() ?: 0)
                SharedMonitoringState.cachedDayStartHour = preferences.dayStartHour
                SharedMonitoringState.cachedDayStartMinute = preferences.dayStartMinute

                updateBedtimeStatus(preferences)
                updateGracePeriodStatus(preferences)
                if (preferences.eyeCareEnabled) startEyeCareTimer() else stopEyeCareTimer()
                if (preferences.usageGlimpseEnabled) startUsageGlimpseTimer() else usageGlimpseJob?.cancel()
                refreshForegroundNotification(force = true)
            }
        }
    }

    private fun startMonitoring() {
        if (!isScreenOn) {
            Log.d("Zenith_SCREEN", "startMonitoring() SKIPPED: screen is OFF")
            return
        }
        startMonitoringCount++
        if (monitoringLoopActive && System.currentTimeMillis() - lastLoopTick < 60000) return
        monitoringLoopActive = true
        
        serviceScope.launch {
            try {
                checkDayChangeOnStartup()
            } catch (t: Throwable) {
                Log.e("ZenithAUMS", "Error in day change check: ${t.message}")
            }
        }

        foregroundAppJob?.cancel()
        foregroundAppJob = serviceScope.launch {
            AppStateHolder.foregroundApp
                .filterNotNull()
                .distinctUntilChanged()
                .collect { currentApp ->
                    if (!isScreenOn) return@collect
                    try {
                        lastLoopTick = System.currentTimeMillis()
                        handleForegroundChange(currentApp)
                    } catch (t: Throwable) {
                        logError(t)
                    }
                }
        }

        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch(Dispatchers.Default) {
            var maintenanceTick = 0L
            val startTime = System.currentTimeMillis()

            checkGoalReminders()

            while (true) {
                try {
                    lastLoopTick = System.currentTimeMillis()

                    if (isScreenOn) {
                        val a11yRunning = ZenithService.isServiceRunning

                        if (!a11yRunning) {
                            val currentApp = getForegroundApp()
                            if (currentApp != null && currentApp != AppStateHolder.foregroundApp.value) {
                                AppStateHolder.foregroundApp.value = currentApp
                            }
                        }

                        if (!InterceptOverlayManager.isShowing) {
                            if (a11yRunning) {
                                val detectedApp = getForegroundApp()
                                if (detectedApp != null && detectedApp != lastForegroundApp && detectedApp != packageName) {
                                    lastForegroundApp = detectedApp
                                }
                                lastForegroundApp?.let { app -> monitoringTick(app) }
                            } else {
                                getForegroundApp()?.let { app -> monitoringTick(app) }
                            }
                        }
                    }

                    val elapsedMinutes = (lastLoopTick - startTime) / 60_000L
                    if (elapsedMinutes > maintenanceTick) {
                        maintenanceTick = elapsedMinutes
                        if (isScreenOn) {
                            if (SharedMonitoringState.launcherPackages.isEmpty() || lastLoopTick - lastLauncherRefreshTime > 3_600_000L) {
                                refreshLauncherCache()
                            }
                            if (maintenanceTick % 3L == 0L) checkDayChangePeriodic()
                            if (maintenanceTick % 5L == 0L) SharedMonitoringState.performPeriodicCleanup()
                        }
                        if (maintenanceTick % 3L == 0L) checkGoalReminders()
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    logError(t)
                }

                delay(computeMonitoringDelay())
            }
        }
    }

    private fun startUsageGlimpseTimer() {
        usageGlimpseJob?.cancel()
        usageGlimpseJob = serviceScope.launch {
            while (true) {
                delay(GLIMPSE_INTERVAL_MS)
                if (!isScreenOn) continue
                val prefs = SharedMonitoringState.currentPreferences ?: continue
                if (!prefs.usageGlimpseEnabled) continue

                val now = System.currentTimeMillis()
                if (now - lastGlimpseShowTime < GLIMPSE_INTERVAL_MS) continue
                lastGlimpseShowTime = now

                val totalUsage = getTotalGlobalUsageToday()
                val isDark = when (prefs.themeConfig) {
                    com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                    com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                    else -> {
                        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                }

                usageGlimpseManager?.show(
                    usageTodayMillis = totalUsage,
                    isDark = isDark,
                    fontOption = prefs.fontOption,
                    dynamicColor = prefs.dynamicColor,
                    expressiveColors = prefs.expressiveColors,
                    gsFlexSettings = prefs.gsFlexSettings
                )
                delay(GLIMPSE_DISPLAY_MS)
                usageGlimpseManager?.hide()
            }
        }
    }

    private fun startEyeCareTimer() {
        eyeCareJob?.cancel()
        eyeCareCumulativeSeconds = 0
        eyeCareOnBreak = false

        eyeCareJob = serviceScope.launch {
            while (true) {
                delay(1000)
                if (!isScreenOn) continue
                val prefs = SharedMonitoringState.currentPreferences ?: continue
                if (!prefs.eyeCareEnabled) {
                    eyeCareCumulativeSeconds = 0
                    continue
                }
                if (eyeCareOnBreak) continue

                eyeCareCumulativeSeconds++
                val workSeconds = prefs.eyeCareWorkMinutes * 60
                if (eyeCareCumulativeSeconds >= workSeconds) {
                    eyeCareOnBreak = true
                    eyeCareCumulativeSeconds = 0

                    withContext(Dispatchers.Main) {
                        overlayManager.showEyeCareRestOverlay(
                            durationSeconds = prefs.eyeCareRestSeconds,
                            onRestComplete = {
                                eyeCareOnBreak = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun stopEyeCareTimer() {
        eyeCareJob?.cancel()
        eyeCareJob = null
        eyeCareCumulativeSeconds = 0
        eyeCareOnBreak = false
    }

    private suspend fun logError(t: Throwable) {
        Log.e("ZenithAUMS", "Error: ${t.message}")
        delay(if (t is OutOfMemoryError) 5000 else 500)
    }

    private suspend fun handleForegroundChange(currentApp: String) {
        val currentTime = System.currentTimeMillis()

        if (SharedMonitoringState.launcherPackages.isEmpty() || currentTime - lastLauncherRefreshTime > 3600000) {
            refreshLauncherCache()
        }

        val isLauncher = SharedMonitoringState.launcherPackages.contains(currentApp) ||
            currentApp == packageName ||
            currentApp.contains("launcher", ignoreCase = true) ||
            currentApp.contains("home", ignoreCase = true)
        if (isLauncher) {
            WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                overlayActionHandler.pauseWebsiteSession("zenith-web:$domain")
            }
            if (System.currentTimeMillis() - InterceptOverlayManager.lastKickTime >= 500) {
                InterceptOverlayManager.lastKickTime = 0L
                InterceptOverlayManager.lastKickedPackage = null
            }
            overlayManager.hideOverlay()
            sessionUsageOverlayManager.pauseAllSessions()
            sessionUsageOverlayManager.updateForegroundApp(currentApp)
            lastForegroundApp?.let { prevPkg ->
                if (prevPkg != packageName && !SharedMonitoringState.launcherPackages.contains(prevPkg)) {
                    SharedMonitoringState.allShieldsCache[prevPkg]?.let { shield ->
                        if (shield.isDelayAppEnabled) {
                            serviceScope.launch {
                                shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L))
                            }
                        }
                    }
                }
            }
            lastForegroundApp?.let { oldPkg ->
                if (oldPkg.isNotEmpty() && oldPkg != currentApp && cachedTotalUsage > 0L) {
                    SharedMonitoringState.lastKnownPackageUsage[oldPkg] = cachedTotalUsage
                }
            }
            currentShieldCache = null
            currentSessionPackage = currentApp
            cachedTotalUsage = 0L
            lastForegroundApp = currentApp
            return
        }

        if (shouldBypassBlocking(currentApp)) {
            if (!WebsiteRepository.isKnownBrowser(currentApp)) {
                WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                    val websitePkg = "zenith-web:$domain"
                    val isLauncher = SharedMonitoringState.launcherPackages.contains(currentApp) ||
                        currentApp.contains("launcher", ignoreCase = true) ||
                        currentApp.contains("home", ignoreCase = true)
                    if (isLauncher) {
                        overlayActionHandler.pauseWebsiteSession(websitePkg)
                    } else {
                        overlayActionHandler.scheduleWebsiteSessionDismiss(websitePkg)
                    }
                }
            }
            if (!ZenithService.isServiceRunning) {
                overlayManager.checkAndHide(currentApp)
            }
            sessionUsageOverlayManager.updateForegroundApp(currentApp)
            return
        }

        if (!ZenithService.isServiceRunning) {
            overlayManager.checkAndHide(currentApp)
        }
        if (InterceptOverlayManager.isShowing) {
            lastForegroundApp = currentApp
            return
        }

        if (WebsiteRepository.isKnownBrowser(currentApp)) {
            WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                overlayActionHandler.cancelWebsiteSessionDismiss("zenith-web:$domain")
            }
        } else {
            WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                val websitePkg = "zenith-web:$domain"
                val isLauncher = SharedMonitoringState.launcherPackages.contains(currentApp) ||
                    currentApp.contains("launcher", ignoreCase = true) ||
                    currentApp.contains("home", ignoreCase = true)
                if (isLauncher) {
                    overlayActionHandler.pauseWebsiteSession(websitePkg)
                } else {
                    overlayActionHandler.scheduleWebsiteSessionDismiss(websitePkg)
                }
            }
        }

        sessionUsageOverlayManager.updateForegroundApp(currentApp)

        val isNewSession = currentApp != lastForegroundApp ||
                (currentShieldCache != null && currentShieldCache?.packageName != currentApp) ||
                currentSessionPackage == null

        try {
            lastForegroundApp?.let { prevPkg ->
                if (prevPkg != currentApp) {
                    SharedMonitoringState.allShieldsCache[prevPkg]?.let { prevShield ->
                        updateShieldInDatabase(prevShield, force = true)
                    }
                }
            }
        } catch (_: Exception) {}

        if (isNewSession) {
            lastForegroundApp?.let { oldPkg ->
                if (oldPkg.isNotEmpty() && oldPkg != currentApp && cachedTotalUsage > 0L) {
                    SharedMonitoringState.lastKnownPackageUsage[oldPkg] = cachedTotalUsage
                }
            }
            currentShieldCache = SharedMonitoringState.allShieldsCache[currentApp]
            lastUsageFetchTime = 0L
            lastHUDUpdateTime = 0L
            sessionStartTime = currentTime
            currentSessionPackage = currentApp

            val shield = currentShieldCache
            if (shield != null) {
                val startOfDay = SharedMonitoringState.getStartOfDay()
                val timeSinceMidnight = (currentTime - startOfDay).coerceAtLeast(0L)

                val detailedUsage = withContext(Dispatchers.IO) {
                    com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = SharedMonitoringState.cachedDayStartHour, dayStartMinute = SharedMonitoringState.cachedDayStartMinute)
                }
                val systemUsage = detailedUsage.appUsageMap[currentApp] ?: 0L
                val systemGlobal = getFilteredGlobalUsage(detailedUsage.appUsageMap)

                if (shield.limitPeriod == LimitPeriod.WEEKLY) {
                    val weeklyTotal = withContext(Dispatchers.IO) {
                        shieldRepository.getWeeklyUsageLive(currentApp, systemUsage)
                    }
                    baseUsageAtSessionStart = weeklyTotal.coerceAtLeast(0L)
                    periodUsageCache[currentApp] = baseUsageAtSessionStart
                } else {
                    baseUsageAtSessionStart = systemUsage.coerceAtMost(timeSinceMidnight)
                }
                cachedTotalUsage = baseUsageAtSessionStart
                baseGlobalUsageAtSessionStart = systemGlobal.coerceAtMost(timeSinceMidnight)
                cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart
            } else {
                baseUsageAtSessionStart = 0L
                cachedTotalUsage = 0L
            }

            if (!shouldBypassBlocking(currentApp) && !ZenithService.isServiceRunning) {
                checkBlockingInstant(currentApp, currentShieldCache)
            }
        }

        lastForegroundApp = currentApp
    }

    private suspend fun monitoringTick(currentApp: String) {
        val currentTime = System.currentTimeMillis()
        val isLauncher = currentApp == packageName ||
            SharedMonitoringState.launcherPackages.contains(currentApp) ||
            currentApp.contains("launcher", ignoreCase = true) ||
            currentApp.contains("home", ignoreCase = true)

        if (isLauncher) {
            WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                overlayActionHandler.pauseWebsiteSession("zenith-web:$domain")
            }
            sessionUsageOverlayManager.pauseAllSessions()
            sessionUsageOverlayManager.updateForegroundApp(currentApp)
            currentShieldCache = null
            currentSessionPackage = currentApp
            cachedTotalUsage = 0L
            return
        }

        if (shouldBypassBlocking(currentApp)) {
            if (!WebsiteRepository.isKnownBrowser(currentApp)) {
                WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                    val websitePkg = "zenith-web:$domain"
                    overlayActionHandler.scheduleWebsiteSessionDismiss(websitePkg)
                }
            }
            return
        }

        if (WebsiteRepository.isKnownBrowser(currentApp)) {
            WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                overlayActionHandler.cancelWebsiteSessionDismiss("zenith-web:$domain")
            }
        } else {
            WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                overlayActionHandler.scheduleWebsiteSessionDismiss("zenith-web:$domain")
            }
        }

        if (ZenithService.isServiceRunning && !InterceptOverlayManager.isShowing) {
            val shield = currentShieldCache
            val isAppPaused = shield != null && isPaused(shield)
            val allowedUntilVal = allowedApps[currentApp]
            if (shield != null && shield.type != FocusType.GOAL && !isAppPaused) {
                val limitMs = shield.timeLimitMinutes * 60 * 1000L
                val remainingMs = (limitMs - cachedTotalUsage).coerceAtLeast(0L)
                val isAllowedExpired = allowedUntilVal != null && allowedUntilVal > 0L && currentTime > allowedUntilVal
                if (isAllowedExpired || (remainingMs <= 0L && (allowedUntilVal == null || currentTime > allowedUntilVal))) {
                    if (shield.isAutoQuitEnabled && !earlyKickManager.wasKicked(currentApp)) {
                        earlyKickManager.markKicked(currentApp)
                        lastKickTime = System.currentTimeMillis()
                        lastKickedPackage = currentApp
                        goToHomeScreen()
                        allowedApps.remove(currentApp)
                    } else if (!InterceptOverlayManager.isShowing) {
                        checkIfAppIsShielded(currentApp)
                    }
                    lastForegroundApp = currentApp
                }
            }
            val wsDomain = WebsiteStateHolder.currentWebsiteDomain.value
            if (wsDomain != null && WebsiteRepository.isKnownBrowser(currentApp)) {
                val wsPkg = "zenith-web:$wsDomain"
                val wsShield = SharedMonitoringState.allShieldsCache[wsPkg]
                val wsGrant = allowedApps[wsPkg]
                if (wsShield != null && wsShield.type != FocusType.GOAL && !isPaused(wsShield)) {
                    val wsLimit = wsShield.timeLimitMinutes * 60 * 1000L
                    val wsUsage = getWebsiteUsageToday(wsDomain)
                    val wsRemaining = (wsLimit - wsUsage).coerceAtLeast(0L)
                    val wsAllowedExpired = wsGrant != null && wsGrant > 0L && currentTime > wsGrant
                    if (wsAllowedExpired || (wsRemaining <= 0L && (wsGrant == null || currentTime > wsGrant))) {
                        if (wsShield.isAutoQuitEnabled && !earlyKickManager.wasKicked(wsPkg)) {
                            earlyKickManager.markKicked(wsPkg)
                            lastKickTime = System.currentTimeMillis()
                            lastKickedPackage = wsPkg
                            goToHomeScreen()
                            allowedApps.remove(wsPkg)
                        }
                    }
                }
            }

            return
        }

        updateUsageTime(currentApp)

        val prefs = SharedMonitoringState.currentPreferences
        val isOverlayShowing = InterceptOverlayManager.isShowing
        val kickDecision = earlyKickHandler.evaluate(
            currentApp = currentApp,
            currentTime = currentTime,
            cachedTotalUsage = cachedTotalUsage,
            shield = currentShieldCache,
            allowedApps = allowedApps,
            lastAllowedRemainingTime = lastAllowedRemainingTime,
            prefs = prefs,
            isOverlayShowing = isOverlayShowing,
            isAppPaused = this::isPaused,
            getWebsiteUsageToday = this::getWebsiteUsageToday,
            websiteDomainProvider = { WebsiteStateHolder.currentWebsiteDomain.value },
            isKnownBrowser = WebsiteRepository::isKnownBrowser,
            allShieldsCache = SharedMonitoringState.allShieldsCache
        )
        if (kickDecision.shouldKick && kickDecision.targetPackage != null) {
            val target = kickDecision.targetPackage
            allowedApps.remove(target)
            withContext(Dispatchers.Main) {
                sessionUsageOverlayManager.hideHUD(target)
                Toast.makeText(this@AppUsageMonitorService, "Early Kick: 5 minutes remaining", Toast.LENGTH_LONG).show()
            }
            goToHomeScreen()
            lastForegroundApp = currentApp
            return
        }

        val shieldForPauseCheck = currentShieldCache
        val isAppPaused = shieldForPauseCheck != null && isPaused(shieldForPauseCheck)

        val allowedUntilVal = allowedApps[currentApp]
        if (!isAppPaused && allowedUntilVal != null && allowedUntilVal > currentTime && !ZenithService.isServiceRunning) {
            val prefs = SharedMonitoringState.currentPreferences
            if (prefs?.sessionUsageOverlayEnabled == true) {
                val remainingMinutes = ((allowedUntilVal - currentTime) / 60000L).toInt().coerceAtLeast(1)
                val sh = currentShieldCache
                val isGoal = sh?.type == FocusType.GOAL

                val limitMillis = (sh?.timeLimitMinutes ?: 0) * 60 * 1000L
                if (isGoal) {
                    com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                    SharedMonitoringState.lastDailyUsageFetchTime = 0L
                }
                val currentUsage = if (isGoal) getTotalUsageToday(currentApp) else cachedTotalUsage

                if (!(isGoal && (currentUsage >= limitMillis || SharedMonitoringState.notifiedGoals.contains(currentApp)) && limitMillis > 0)) {
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
                                    val s = SharedMonitoringState.allShieldsCache[currentApp] ?: shieldRepository.getShieldByPackageName(currentApp)
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

        val isBedtimeBlocking = SharedMonitoringState.isBedtimeActive || (SharedMonitoringState.isWindDownActive && SharedMonitoringState.currentPreferences?.bedtimeWindDownEnabled == true)
        val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in SharedMonitoringState.bedtimeWhitelistedPackages) || (allowedUntilVal == null || currentTime > allowedUntilVal)

        if (!isAppPaused && shouldCheckSchedules && !InterceptOverlayManager.isShowing && !ZenithService.isServiceRunning) {
            if (checkSchedules(currentApp)) {
                lastForegroundApp = currentApp
                return
            }

            if (allowedUntilVal == null || currentTime > allowedUntilVal) {
                val sh = currentShieldCache
                val prefs = SharedMonitoringState.currentPreferences
                if (sh != null || (prefs?.mindfulGatewayEnabled == true && !shouldBypassBlocking(currentApp))) {
                    if (sh != null && sh.isAutoQuitEnabled && allowedUntilVal != null && allowedUntilVal > 0) {
                        if (!earlyKickManager.wasKicked(currentApp)) {
                            earlyKickManager.markKicked(currentApp)
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
                            allowedApps.remove(currentApp)
                            checkIfAppIsShielded(currentApp)
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
        val cfg = SharedMonitoringState.performanceConfig
        val currentTime = System.currentTimeMillis()
        
        if (!isScreenOn) return cfg.screenOffDelay

        if (AppStateHolder.isPowerSaveMode.value) return cfg.monPowerSave
        if (InterceptOverlayManager.isShowing) return cfg.monOverlayShowing

        val currentApp = lastForegroundApp
        val allowedUntil = currentApp?.let { allowedApps[it] } ?: 0L
        if (allowedUntil > currentTime) {
            val remaining = allowedUntil - currentTime
            return when {
                remaining < 10000 -> 1000L
                remaining < 60000 -> 3000L
                else -> 10000L
            }
        }

        val shield = currentShieldCache
        if (shield != null) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
            if (remaining < 60000 || remaining < 600000) {
                return when {
                    remaining < 60000 -> if (shield.type == FocusType.GOAL) cfg.monGoalNear else cfg.monShieldNear
                    else -> if (shield.type == FocusType.GOAL) cfg.monGoalMid else cfg.monShieldMid
                }
            }
        }

        if (ZenithService.isServiceRunning && !InterceptOverlayManager.isShowing) {
            return cfg.a11yActiveDelay
        }

        if (shield != null) {
            return if (shield.type == FocusType.GOAL) cfg.monGoalFar else cfg.monShieldFar
        }
        if (SharedMonitoringState.parsedSchedulesCache.isEmpty() && !SharedMonitoringState.isBedtimeActive) {
            return 15000L
        }
        return cfg.monDefault
    }

    private suspend fun checkDayChangePeriodic() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCheckedDayTimestamp <= 120000) return

        val currentDayStart = com.etrisad.zenith.util.DateTimeUtils.getDayStartTime(
            currentTime,
            SharedMonitoringState.cachedDayStartHour,
            SharedMonitoringState.cachedDayStartMinute
        )

        if (lastCheckedDayStart > 0 && currentDayStart != lastCheckedDayStart) {
            withContext(Dispatchers.IO) {
                com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                shieldRepository.resetDailyRemainingTimes()
                checkWeeklyReset()
            }
            SharedMonitoringState.notifiedGoals.clear()
            earlyKickManager.reset()
            SharedMonitoringState.dailyUsageCache.clear()
            lastAllowedRemainingTime.clear()
            SharedMonitoringState.lastKnownPackageUsage.clear()
            periodUsageCache.clear()
            refreshPeriodUsageCache()
            SharedMonitoringState.systemAppCache.clear()
            SharedMonitoringState.launcherPackages = emptySet()
            lastUsageCacheTime = 0L
            lastUsageFetchTime = 0L
            cachedTotalUsage = 0L
            cachedTotalGlobalUsage = 0L
            currentShieldCache = null

            sessionStartTime = currentTime
            baseUsageAtSessionStart = 0L
            baseGlobalUsageAtSessionStart = 0L
            lastHUDUpdateTime = 0L
        }

        lastCheckedDayStart = currentDayStart
        lastCheckedDayTimestamp = currentTime

        SharedMonitoringState.currentPreferences?.let { updateBedtimeStatus(it) }
        checkSchedulesTransition(java.time.LocalTime.now().hour * 60 + java.time.LocalTime.now().minute)
    }

    private suspend fun checkBlockingInstant(currentApp: String, shield: ShieldEntity?) {
        if (ZenithService.isServiceRunning) return
        
        val currentTime = System.currentTimeMillis()
        val isAppPaused = shield != null && isPaused(shield)
        val allowedUntil = allowedApps[currentApp]
        val isSessionActive = allowedUntil?.let { it > currentTime } ?: false

        if (!isSessionActive && !InterceptOverlayManager.isShowing) {
            var isScheduled = checkSchedules(currentApp)
            val websiteDomain = WebsiteStateHolder.currentWebsiteDomain.value
            val isBrowserWithDomain = WebsiteRepository.isKnownBrowser(currentApp) && websiteDomain != null
            if (!isScheduled && isBrowserWithDomain && websiteDomain != null) {
                isScheduled = checkSchedules("zenith-web:$websiteDomain")
            }
            if (isScheduled) return

            if (!isAppPaused && (allowedUntil == null || currentTime > allowedUntil)) {
                val prefs = SharedMonitoringState.currentPreferences
                if (shield != null || isBrowserWithDomain || (prefs?.mindfulGatewayEnabled == true && !shouldBypassBlocking(currentApp))) {
                    checkIfAppIsShielded(currentApp)
                }
            }
        }
        val wd = WebsiteStateHolder.currentWebsiteDomain.value
        if (wd != null && WebsiteRepository.isKnownBrowser(currentApp) && !InterceptOverlayManager.isShowing) {
            val websitePkg = "zenith-web:$wd"
            val websiteGrant = allowedApps[websitePkg]
            val hasActiveGrant = websiteGrant != null && System.currentTimeMillis() < websiteGrant
            if (!hasActiveGrant) {
                checkIfAppIsShielded(currentApp)
            }
        }
    }

    private fun refreshPeriodUsageCache() {
        serviceScope.launch {
            try {
                val weeklyShields = SharedMonitoringState.allShieldsCache.values.filter { it.limitPeriod == LimitPeriod.WEEKLY }
                for (shield in weeklyShields) {
                    val usage = shieldRepository.getWeeklyUsageLive(shield.packageName, 0L)
                    periodUsageCache[shield.packageName] = usage.coerceAtLeast(0L)
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateUsageTime(packageName: String) {
        if (packageName != currentSessionPackage) return

        val currentTime = System.currentTimeMillis()
        val sessionElapsed = (currentTime - sessionStartTime).coerceAtLeast(0L)

        cachedTotalUsage = baseUsageAtSessionStart + sessionElapsed
        val shield = currentShieldCache
        if (shield != null && shield.limitPeriod == LimitPeriod.WEEKLY) {
            periodUsageCache[packageName] = cachedTotalUsage
        }
        cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart + sessionElapsed

        val currentShield = shield ?: return
        val cfg = SharedMonitoringState.performanceConfig
        val shieldType = currentShield.type

        val limitMillis = currentShield.timeLimitMinutes * 60 * 1000L
        val remaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
        val isNearLimit = remaining < 60000
        
        val baseInterval = cfg.usageStatsCacheMs.coerceIn(30000L, 3600000L)
        
        val needsDetailedFetch = if (shieldType == FocusType.GOAL) {
            val uiUpdateInterval = when {
                isNearLimit -> 15000L
                remaining < 300000 -> 30000L.coerceAtMost(baseInterval)
                remaining < 900000 -> 45000L.coerceAtMost(baseInterval)
                else -> baseInterval
            }
            currentTime - lastHUDUpdateTime > uiUpdateInterval || lastHUDUpdateTime == 0L
        } else {
            val fetchInterval = if (isNearLimit) 30000L else baseInterval
            currentTime - lastUsageFetchTime > fetchInterval
        }

        if (needsDetailedFetch) {
            val isGoal = shieldType == FocusType.GOAL
            if (isGoal) lastHUDUpdateTime = currentTime
            else lastUsageFetchTime = currentTime

            serviceScope.launch {
                try {
                    if (packageName != currentSessionPackage) return@launch
                    val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = SharedMonitoringState.cachedDayStartHour, dayStartMinute = SharedMonitoringState.cachedDayStartMinute)

                    val systemUsage = detailedUsage.appUsageMap[packageName] ?: 0L
                    val systemGlobal = getFilteredGlobalUsage(detailedUsage.appUsageMap)
                    val startOfDay = SharedMonitoringState.getStartOfDay()
                    val now = System.currentTimeMillis()
                    val timeSinceMidnight = (now - startOfDay).coerceAtLeast(0L)

                    val s = currentShieldCache
                    if (s != null && s.limitPeriod == LimitPeriod.WEEKLY) {
                        val weeklyBase = periodUsageCache[packageName] ?: (baseUsageAtSessionStart + sessionElapsed)
                        baseUsageAtSessionStart = weeklyBase
                        cachedTotalUsage = weeklyBase
                        periodUsageCache[packageName] = weeklyBase
                    } else {
                        val systemBase = systemUsage.coerceAtMost(timeSinceMidnight)
                        if (systemBase > baseUsageAtSessionStart) {
                            baseUsageAtSessionStart = systemBase
                        }
                        baseUsageAtSessionStart = cachedTotalUsage.coerceAtLeast(baseUsageAtSessionStart)
                        cachedTotalUsage = baseUsageAtSessionStart
                    }
                    val globalBase = systemGlobal.coerceAtMost(timeSinceMidnight)
                    if (globalBase > baseGlobalUsageAtSessionStart) {
                        baseGlobalUsageAtSessionStart = globalBase
                    }
                    baseGlobalUsageAtSessionStart = cachedTotalGlobalUsage.coerceAtLeast(baseGlobalUsageAtSessionStart)
                    sessionStartTime = now
                    cachedTotalGlobalUsage = baseGlobalUsageAtSessionStart

                    if (isGoal) {
                        withContext(Dispatchers.Main) {
                            sessionUsageOverlayManager.updateHUDUsage(packageName, systemUsage)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        updateShieldInDatabase(currentShield)
        refreshForegroundNotification()
    }

    private fun refreshForegroundNotification(force: Boolean = false) {
        if (!foregroundNotificationStarted) return

        val currentTime = System.currentTimeMillis()
        if (!force && currentTime - lastNotificationRefreshTime < NOTIFICATION_UPDATE_INTERVAL) return

        val newText = createNotificationStatusText()
        if (force && newText == lastNotificationText) return
        lastNotificationText = newText

        lastNotificationRefreshTime = currentTime
        notificationManager.notify(NOTIFICATION_ID, createNotification(newText))
    }

    private var lastDbUpdateTime = 0L

    private fun updateShieldInDatabase(shield: ShieldEntity, force: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val prefs = SharedMonitoringState.currentPreferences ?: return
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

                val shouldIncrement = if (shield.limitPeriod == LimitPeriod.WEEKLY) {
                    lastUpdateDate.with(java.time.DayOfWeek.MONDAY) != today.with(java.time.DayOfWeek.MONDAY)
                } else {
                    lastUpdateDate != today
                }

                if (shouldIncrement) {
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

        val cfg = SharedMonitoringState.performanceConfig
        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000
        val shouldUpdateDB = force || timeSinceLastUsed > cfg.shieldDbWriteMs || (isNearLimit && timeSinceLastUsed > cfg.shieldDbWriteNearMs) || updatedShield != shield

        if (shouldUpdateDB) {
            val finalShield = updatedShield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )

            if (shield.packageName == currentSessionPackage) {
                currentShieldCache = finalShield
            }

            if (currentTime - lastDbUpdateTime > cfg.shieldDbWriteMs || force) {
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
                if (cachedTotalUsage >= limitMillis && !SharedMonitoringState.notifiedGoals.contains(shield.packageName)) {
                    sendGoalReachedNotification(shield.appName, shield.packageName)
                    SharedMonitoringState.notifiedGoals.add(shield.packageName)
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
            .setSmallIcon(R.drawable.ic_flag)
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
            .setSmallIcon(R.drawable.ic_fire_department_outlined)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("zenith_bedtime")
            .build()

        manager.notify(WIND_DOWN_NOTIFICATION_ID, notification)
    }

    fun sendTestNotification() {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test Notification ✅")
            .setContentText("Notifications are working correctly!")
            .setSmallIcon(R.drawable.ic_check)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup("zenith_goals")
            .build()

        manager.notify(999, notification)
    }


    private fun getMindfulShield(packageName: String, appName: String): ShieldEntity =
        overlayActionHandler.getMindfulShield(packageName, appName)

    private var lastKickedPackage: String? = null
    private var lastKickTime = 0L

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        if (targetPackageName in SharedMonitoringState.whitelistedPackages) return

        val websiteDomain = WebsiteStateHolder.currentWebsiteDomain.value
        val isWebsite = WebsiteRepository.isKnownBrowser(targetPackageName) && websiteDomain != null
        if (!isWebsite) {
            val allowedUntil = allowedApps[targetPackageName] ?: 0L
            if (System.currentTimeMillis() < allowedUntil) return
        }

        var actualTargetPackage = if (isWebsite && websiteDomain != null) {
            "zenith-web:$websiteDomain"
        } else {
            targetPackageName
        }

        if (InterceptOverlayManager.isShowing && InterceptOverlayManager.currentPackage == actualTargetPackage) return

        if (actualTargetPackage == InterceptOverlayManager.lastKickedPackage && System.currentTimeMillis() - InterceptOverlayManager.lastKickTime < 500) return

        val currentForeground = getForegroundApp() ?: lastForegroundApp
        if (!isWebsite && actualTargetPackage != currentForeground && actualTargetPackage != lastForegroundApp) return
        if (isWebsite && currentForeground != null && !WebsiteRepository.isKnownBrowser(currentForeground)) return

        var shield = if (currentShieldCache?.packageName == actualTargetPackage) {
            currentShieldCache
        } else {
            SharedMonitoringState.allShieldsCache[actualTargetPackage]
        }

        if (shield != null && isWebsite && actualTargetPackage.startsWith("zenith-web:")) {
            val websiteAllowedUntil = allowedApps[actualTargetPackage] ?: 0L
            if (System.currentTimeMillis() < websiteAllowedUntil) {
                sessionUsageOverlayManager.updateForegroundApp(actualTargetPackage)
                return
            }
        }
        if (shield == null && isWebsite) {
            val browserAllowedUntil = allowedApps[targetPackageName] ?: 0L
            if (System.currentTimeMillis() < browserAllowedUntil) {
                return
            }
            actualTargetPackage = targetPackageName
            shield = SharedMonitoringState.allShieldsCache[targetPackageName]
        }
        val activeAllowedUntil = allowedApps[actualTargetPackage] ?: 0L
        if (System.currentTimeMillis() < activeAllowedUntil) return

        val prefs = SharedMonitoringState.currentPreferences ?: return
        val isMindfulGateway = shield == null && prefs.mindfulGatewayEnabled && !shouldBypassBlocking(actualTargetPackage)
        val appName = shield?.appName ?: overlayActionHandler.getAppName(actualTargetPackage)
        val effectiveShield = if (isMindfulGateway) overlayActionHandler.getMindfulShield(actualTargetPackage, appName) else shield

        if (effectiveShield != null && !InterceptOverlayManager.isShowing) {
            if (effectiveShield.type == FocusType.GOAL && !isWebsite) {
                if (!SharedMonitoringState.notifiedGoals.contains(targetPackageName)) {
                    updateUsageTime(targetPackageName)
                    com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                    lastUsageCacheTime = 0L
                    SharedMonitoringState.lastDailyUsageFetchTime = 0L
                }
            }
            var totalUsageToday = if (isWebsite && actualTargetPackage.startsWith("zenith-web:")) {
                val domain = WebsiteRepository.extractDomainFromPackageName(actualTargetPackage)
                getWebsiteUsageToday(domain)
            } else {
                getTotalUsageToday(targetPackageName)
            }
            val totalGlobalUsageToday = getTotalGlobalUsageToday()
            val delayDurationSeconds = if (isMindfulGateway) 0 else prefs.delayAppDurationSeconds

            if (actualTargetPackage.startsWith("zenith-web:")) {
                WebsiteStateHolder.lastBrowserPackage = targetPackageName
                sessionUsageOverlayManager.updateForegroundApp(actualTargetPackage)
                overlayActionHandler.pauseBrowserSession(targetPackageName)
            }

            if (effectiveShield.type == FocusType.GOAL) {
                val limitMillis = effectiveShield.timeLimitMinutes * 60 * 1000L
                if (SharedMonitoringState.notifiedGoals.contains(actualTargetPackage)) {
                    totalUsageToday = limitMillis
                } else {
                    val hudSeconds = sessionUsageOverlayManager.getHUDElapsedSeconds(actualTargetPackage)
                    if (hudSeconds != null) {
                        val hudMillis = hudSeconds * 1000L
                        if (hudMillis > totalUsageToday) {
                            totalUsageToday = hudMillis
                        }
                    }
                    if (totalUsageToday >= limitMillis) {
                        SharedMonitoringState.notifiedGoals.add(actualTargetPackage)
                    }
                }
            }

            currentShieldCache = if (isMindfulGateway) null else effectiveShield

            overlayActionHandler.showShieldOverlay(
                targetPackageName = actualTargetPackage,
                shield = effectiveShield,
                isMindfulGateway = isMindfulGateway,
                delayDurationSeconds = delayDurationSeconds,
                totalUsageToday = totalUsageToday,
                totalGlobalUsageToday = totalGlobalUsageToday,
                updateShieldCache = { updated -> currentShieldCache = updated },
                getTotalUsageTodayFn = {
                    if (effectiveShield.type == FocusType.GOAL && SharedMonitoringState.notifiedGoals.contains(actualTargetPackage)) {
                        Long.MAX_VALUE
                    } else if (isWebsite && actualTargetPackage.startsWith("zenith-web:")) {
                        val domain = WebsiteRepository.extractDomainFromPackageName(actualTargetPackage)
                        getWebsiteUsageToday(domain)
                    } else {
                        updateUsageTime(targetPackageName)
                        com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                        lastUsageCacheTime = 0L
                        SharedMonitoringState.lastDailyUsageFetchTime = 0L
                        getTotalUsageToday(targetPackageName)
                    }
                },
            )
        }
    }

    private suspend fun checkDayChangeOnStartup() {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val lastCheckStr = prefs.lastStreakCheckDate
        val today = LocalDate.now()
        val todayStr = dateFormatter.format(today)

        if (lastCheckStr.isNotEmpty() && lastCheckStr != todayStr) {
            shieldRepository.resetDailyRemainingTimes()
            checkWeeklyReset()
            SharedMonitoringState.notifiedGoals.clear()
            SharedMonitoringState.dailyUsageCache.clear()
            SharedMonitoringState.systemAppCache.clear()
            com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
            lastAllowedRemainingTime.clear()
            periodUsageCache.clear()
        }

        if (lastCheckStr != todayStr) {
            preferencesRepository.setLastStreakCheckDate(todayStr)
        }

        lastCheckedDayDate = today
    }

    private suspend fun updateStreaks() {
        preferencesRepository.refreshGlobalStreak(shieldRepository)
        preferencesRepository.refreshAppStreaks(shieldRepository)
        preferencesRepository.refreshWebStreaks(shieldRepository)

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
        val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = SharedMonitoringState.cachedDayStartHour, dayStartMinute = SharedMonitoringState.cachedDayStartMinute)
        val appTotal = getFilteredGlobalUsage(detailedUsage.appUsageMap)
        val websiteTotal = getWebsiteGlobalUsageToday()
        return appTotal + websiteTotal
    }

    private fun getWebsiteGlobalUsageToday(): Long {
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return kotlinx.coroutines.runBlocking {
            try {
                val websiteUsages = shieldRepository.getWebsiteUsageForDate(todayDate).first()
                websiteUsages.sumOf { it.usageTimeMillis }
            } catch (_: Exception) { 0L }
        }
    }

    private fun getFilteredGlobalUsage(appUsageMap: Map<String, Long>): Long {
        val currentTime = System.currentTimeMillis()
        val todayStart = SharedMonitoringState.getStartOfDay()
        val timeSinceMidnight = (currentTime - todayStart).coerceAtLeast(0L)

        if (currentTime - SharedMonitoringState.lastLauncherAppsRefreshTime > 7200000 || SharedMonitoringState.launcherAppsCache.isEmpty()) {
            refreshLauncherCache()
        }

        val excludePackages = setOfNotNull(packageName, SharedMonitoringState.defaultLauncherPackage)

        var totalSum = 0L
        appUsageMap.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in SharedMonitoringState.launcherAppsCache) {
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
            SharedMonitoringState.launcherAppsCache = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val launchers = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            SharedMonitoringState.launcherPackages = launchers.map { it.activityInfo.packageName }.toSet()

            SharedMonitoringState.defaultLauncherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName

            val currentTime = System.currentTimeMillis()
            SharedMonitoringState.lastLauncherAppsRefreshTime = currentTime
            lastLauncherRefreshTime = currentTime
        } catch (_: Exception) {}
    }

    private fun getWebsiteUsageToday(domain: String): Long {
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return kotlinx.coroutines.runBlocking {
            try {
                val usage = shieldRepository.getWebsiteUsage(todayDate, domain)
                usage?.usageTimeMillis ?: 0L
            } catch (_: Exception) { 0L }
        }
    }

    private fun getTotalUsageToday(packageName: String): Long {
        if (WebsiteRepository.isWebsitePackageName(packageName)) {
            val domain = WebsiteRepository.extractDomainFromPackageName(packageName)
            return getWebsiteUsageToday(domain)
        }
        if (packageName == currentSessionPackage && cachedTotalUsage > 0) {
            return cachedTotalUsage
        }
        val saved = SharedMonitoringState.lastKnownPackageUsage[packageName]
        if (saved != null && saved > 0L) return saved
        val shield = SharedMonitoringState.allShieldsCache[packageName]
        if (shield != null && shield.limitPeriod != LimitPeriod.DAILY) {
            val cached = periodUsageCache[packageName]
            if (cached != null && cached > 0) return cached
            val loaded = kotlinx.coroutines.runBlocking {
                try {
                    shieldRepository.getWeeklyUsageLive(packageName, getSystemUsageToday(packageName))
                } catch (_: Exception) { 0L }
            }
            if (loaded > 0) {
                periodUsageCache[packageName] = loaded
                return loaded
            }
        }
        return getSystemUsageToday(packageName)
    }

    private fun getSystemUsageToday(packageName: String): Long {
        getUsageStatsList()
        return SharedMonitoringState.dailyUsageCache[packageName] ?: 0L
    }

    private fun getCachedUsageToday(packageName: String): Long {
        val cached = SharedMonitoringState.dailyUsageCache[packageName]
        if (cached != null) return cached
        return getSystemUsageToday(packageName)
    }

    private fun getUsageStatsList() {
        val currentTime = System.currentTimeMillis()
        val cfg = SharedMonitoringState.performanceConfig
        val cacheDuration = cfg.usageStatsCacheMs.coerceIn(60000L, 3600000L)
        
        if ((currentTime - lastUsageCacheTime < cacheDuration || currentTime - SharedMonitoringState.lastDailyUsageFetchTime < cacheDuration) && SharedMonitoringState.dailyUsageCache.isNotEmpty()) {
            return
        }

        val startTime = SharedMonitoringState.getStartOfDay()
        val timeSinceMidnight = currentTime - startTime

        try {
            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = SharedMonitoringState.cachedDayStartHour, dayStartMinute = SharedMonitoringState.cachedDayStartMinute)
            SharedMonitoringState.dailyUsageCache.clear()
            detailedUsage.appUsageMap.forEach { (pkg, time) ->
                val cappedTime = if (time > timeSinceMidnight) timeSinceMidnight else time
                if (cappedTime > 0) SharedMonitoringState.dailyUsageCache[pkg] = cappedTime
            }
        } catch (_: Exception) { }

        lastUsageCacheTime = currentTime
        SharedMonitoringState.lastDailyUsageFetchTime = currentTime
    }



    private fun goToHomeScreen() {
        lastForegroundApp = null
        cachedForegroundApp = null
        cachedForegroundAppTime = 0L
        lastEventQueryTime = 0L
        if (SharedMonitoringState.lastKnownPackageUsage.size > 20) SharedMonitoringState.lastKnownPackageUsage.clear()
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

        val startMinutes = SharedMonitoringState.cachedBedtimeStartMinutes
        val endMinutes = SharedMonitoringState.cachedBedtimeEndMinutes

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

        val wasWindDownActive = SharedMonitoringState.isWindDownActive
        if (windDownActive && !wasWindDownActive) {
            SharedMonitoringState.windDownUsedPackages.clear()
            if (prefs.bedtimeNotificationEnabled) {
                sendWindDownNotification()
            }
        }

        SharedMonitoringState.isBedtimeActive = active
        SharedMonitoringState.isWindDownActive = windDownActive
        SharedMonitoringState.isBedtimeBlockingActive = active || (windDownActive && prefs.bedtimeWindDownEnabled)

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

    private fun updateGracePeriodStatus(prefs: UserPreferences) {
        val now = Instant.now().atZone(systemZone)
        val currentDay = now.dayOfWeek.let { if (it == java.time.DayOfWeek.SUNDAY) 1 else it.value + 1 }
        val currentMinutes = now.hour * 60 + now.minute

        val yesterdayDay = now.minusDays(1).dayOfWeek.let { if (it == java.time.DayOfWeek.SUNDAY) 1 else it.value + 1 }

        val startMinutes = SharedMonitoringState.cachedGracePeriodStartMinutes
        val endMinutes = SharedMonitoringState.cachedGracePeriodEndMinutes

        var active = false

        if (prefs.gracePeriodEnabled) {
            if (startMinutes <= endMinutes) {
                if (currentDay in prefs.gracePeriodDays) {
                    active = currentMinutes in startMinutes..endMinutes
                }
            } else {
                if (currentDay in prefs.gracePeriodDays && currentMinutes >= startMinutes) {
                    active = true
                } else if (yesterdayDay in prefs.gracePeriodDays && currentMinutes <= endMinutes) {
                    active = true
                }
            }
        }

        SharedMonitoringState.isGracePeriodActive = active
    }

    private fun checkSchedulesTransition(currentTotalMinutes: Int) {
        val currentlyActiveIds = mutableSetOf<Long>()
        for (ps in SharedMonitoringState.parsedSchedulesCache) {
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
        return overlayActionHandler.checkSchedules(
            packageName = packageName,
            updateShieldCache = { updated -> currentShieldCache = updated },
            recheckSchedules = { pkg -> checkSchedules(pkg) }
        )
    }

    private fun showBedtimeOverlay(packageName: String) {
        overlayActionHandler.showBedtimeOverlay(packageName)
    }

    private fun showWindDownOverlay(packageName: String) {
        val sessionUsed = SharedMonitoringState.windDownUsedPackages[packageName] ?: false
        overlayActionHandler.showWindDownOverlay(
            packageName = packageName,
            sessionUsed = sessionUsed,
            recheckSchedules = { pkg -> checkSchedules(pkg) },
            onAllowUseExtra = { minutes ->
                val shield = SharedMonitoringState.allShieldsCache[packageName]
                if (shield != null) {
                    val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                    lastAllowedRemainingTime[packageName] = (limitMillis - getTotalUsageToday(packageName)).coerceAtLeast(0L)
                }
            }
        )
    }


    private fun isPaused(shield: ShieldEntity): Boolean = overlayActionHandler.isPaused(shield)

    private fun shouldBypassBlocking(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (packageName == cachedBypassPackage && now - cachedBypassTime < 2000) {
            return cachedBypassResult
        }

        val result = overlayActionHandler.shouldBypassBlocking(packageName)
        cachedBypassPackage = packageName; cachedBypassResult = result; cachedBypassTime = now
        return result
    }

    private fun isKeyboardApp(packageName: String): Boolean = overlayActionHandler.isKeyboardApp(packageName)

    private fun showScheduleOverlay(packageName: String, schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity) {
        val totalGlobalUsageToday = getTotalGlobalUsageToday()
        overlayActionHandler.showScheduleOverlay(
            packageName = packageName,
            schedule = schedule,
            totalGlobalUsageToday = totalGlobalUsageToday,
            updateShieldCache = {},
            onAllowUseExtra = { minutes ->
                val shield = SharedMonitoringState.allShieldsCache[packageName]
                if (shield != null) {
                    val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                    lastAllowedRemainingTime[packageName] = (limitMillis - getTotalUsageToday(packageName)).coerceAtLeast(0L)
                }
            }
        )
    }

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val shieldForCached = cachedForegroundApp?.let { SharedMonitoringState.allShieldsCache[it] }
        val cacheMs = if (shieldForCached != null) 2000L else 5000L
        if (time - cachedForegroundAppTime < cacheMs && cachedForegroundApp != null) {
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
            SharedMonitoringState.systemAppCache.clear()
            SharedMonitoringState.launcherPackages = emptySet()
            lastUsageCacheTime = 0L
            lastLauncherRefreshTime = 0L
            serviceScope.launch {
                try {
                    ZenithDatabase.getDatabase(this@AppUsageMonitorService).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
                } catch (_: Exception) {}
            }
        }
    }

    private fun createMonitorNotificationChannel() {
        val channelId = "zenith_monitor_channel"
        val channel = NotificationChannel(
            channelId, "Zenith Monitor Service", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(statusText: String? = null): Notification {
        val channelId = "zenith_monitor_channel"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Zenith is active")
            .setContentText(statusText ?: createNotificationStatusText())
            .setSmallIcon(R.drawable.ic_zenith)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationStatusText(): String {
        val prefs = SharedMonitoringState.currentPreferences ?: UserPreferences()
        val mode = prefs.foregroundNotificationStatusMode
        return NotificationStatusFormatter.format(
            mode = mode,
            dailyUsageMillis = if (mode == ForegroundNotificationStatusMode.DAILY_USAGE) getTotalGlobalUsageToday() else 0L,
            activeFocusSummary = if (mode == ForegroundNotificationStatusMode.ACTIVE_FOCUS) {
                getActiveFocusSummary()
            } else {
                ActiveFocusSummary(goals = 0, shields = 0, schedules = 0)
            }
        )
    }

    private fun getActiveFocusSummary(): ActiveFocusSummary {
        val activeShields = SharedMonitoringState.allShieldsCache.values.filter { !isPaused(it) }
        return ActiveFocusSummary(
            goals = activeShields.count { it.type == FocusType.GOAL },
            shields = activeShields.count { it.type == FocusType.SHIELD },
            schedules = SharedMonitoringState.activeSchedules.size
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitoringLoopActive = false
        try {
            overlayManager.hideOverlay()
            overlayManager.destroy()
            sessionUsageOverlayManager.destroy()
        } catch (_: Exception) {}
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        stopEyeCareTimer()
        usageGlimpseJob?.cancel()
        usageGlimpseManager?.hide()
        serviceJob.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val NOTIFICATION_UPDATE_INTERVAL = 60000L
        private const val BEDTIME_CHANNEL_ID = "zenith_bedtime_channel"
        private const val WIND_DOWN_NOTIFICATION_ID = 2001
    }
}
