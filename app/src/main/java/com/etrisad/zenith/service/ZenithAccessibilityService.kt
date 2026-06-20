package com.etrisad.zenith.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.app.usage.UsageStats
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import java.util.Calendar
import com.etrisad.zenith.R
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.ui.components.overlay.SessionUsageOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZenithAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val packageChangeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private lateinit var overlayActionHandler: OverlayActionHandler
    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val reusableCalendar = Calendar.getInstance()

    private var lastForegroundApp: String? = null
    @Volatile
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private val allowedApps get() = shieldRepository.allowedApps
    @Volatile
    private var usageStatsCache: List<UsageStats>? = null
    private var lastUsageCacheTime = 0L

    private var lastMonitoringError: String? = null
    private var lastMonitoringErrorTime = 0L

    private var lastKickTime = 0L
    private var lastKickedPackage: String? = null
    private var lastDndFilter: Int? = null

    private var monitoringJob: kotlinx.coroutines.Job? = null
    private var bypassCheckRunnable: Runnable? = null
    @Volatile
    private var isOverlayCheckInProgress = false

    private var cachedTotalGlobalUsage: Long = 0L
    private var lastGlobalUsageCacheTime: Long = 0L

    companion object {
        var isServiceRunning = false
            set(value) { field = value; AppStateHolder.isAccessibilityServiceRunning.value = value }
        @Volatile
        var lastEventTime = 0L
        private var instance: ZenithAccessibilityService? = null
        const val BEDTIME_CHANNEL_ID = "zenith_bedtime_channel"
        const val WIND_DOWN_NOTIFICATION_ID = 2001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.etrisad.zenith.action.REFRESH_DATA") {
            refreshService()
        }
        return START_STICKY
    }

    private fun refreshService() {
        if (!AppStateHolder.isScreenOn.value) {
            Log.d("Zenith_SCREEN", "A11Y refreshService() SKIPPED: screen is OFF")
            return
        }
        serviceScope.launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                SharedMonitoringState.currentPreferences = prefs
                SharedMonitoringState.performanceLevel = prefs.performanceLevel
                val initCfg = prefs.buildPerformanceConfig()
                SharedMonitoringState.performanceConfig = initCfg
                com.etrisad.zenith.util.ScreenUsageHelper.updateCacheDuration(initCfg.usageStatsCacheMs)
                SharedMonitoringState.whitelistedPackages = prefs.whitelistedPackages
                SharedMonitoringState.bedtimeWhitelistedPackages = prefs.bedtimeWhitelistedPackages

                shieldRepository.isShieldsLoaded.first { it }
                SharedMonitoringState.allShieldsCache = shieldRepository.allShields.first().associateBy { it.packageName }

                val schedules = shieldRepository.allSchedules.first()
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
                updateBedtimeStatus(prefs)

                usageStatsCache = null
                lastUsageCacheTime = 0L
                lastUsageFetchTime = 0L
                SharedMonitoringState.dailyUsageCache.clear()

                lastForegroundApp?.let { pkg ->
                    packageChangeFlow.tryEmit(pkg)
                }

                Log.d("ZenithAS", "Service refreshed successfully via REFRESH_DATA")
            } catch (e: Exception) {
                Log.e("ZenithAS", "Error refreshing service: ${e.message}")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        lastEventTime = System.currentTimeMillis()

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
            inputMethodManager = getSystemService(InputMethodManager::class.java),
            contextPkg = packageName,
            scope = serviceScope,
            goToHomeScreen = { goToHomeScreen() },
            getForegroundAppName = { lastForegroundApp },
            recheckShield = { pkg -> serviceScope.launch { checkIfAppIsShielded(pkg) } },
            getTotalUsageToday = { pkg -> getTotalUsageToday(pkg) },
            getTotalGlobalUsageToday = { getTotalGlobalUsageToday() },
        )

        createBedtimeNotificationChannel()

        serviceScope.launch(Dispatchers.Main) {
            overlayManager.hideOverlay()
        }

        serviceScope.launch {
            shieldRepository.isShieldsLoaded.first { it }
            shieldRepository.allShields.collect { shields ->
                SharedMonitoringState.allShieldsCache = shields.associateBy { it.packageName }
                lastForegroundApp?.let { currentPkg ->
                    currentShieldCache = SharedMonitoringState.allShieldsCache[currentPkg]
                }
                SharedMonitoringState.updateRestrictedPackages()
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

                if (preferences.incentiveLockDisableRequestTimestamp > 0) {
                    val now = System.currentTimeMillis()
                    if (now >= preferences.incentiveLockDisableRequestTimestamp + 3600000L) {
                        serviceScope.launch {
                            preferencesRepository.setIncentiveLockEnabled(false)
                            preferencesRepository.setIncentiveLockDisableRequestTimestamp(0L)
                        }
                    }
                }

                val startParts = preferences.bedtimeStartTime.split(":")
                val endParts = preferences.bedtimeEndTime.split(":")
                SharedMonitoringState.cachedBedtimeStartMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
                SharedMonitoringState.cachedBedtimeEndMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

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

        serviceScope.launch {
            delay(200)
            val pkg = queryCurrentForegroundApp()
            if (pkg != null && pkg != packageName && lastForegroundApp == null) {
                if (SharedMonitoringState.isFinancialApp(pkg)) {
                    Log.d("ZenithAS", "Financial app already in foreground ($pkg) — disabling accessibility")
                    showFinancialAppNotification(pkg)
                    disableSelf()
                    return@launch
                }
                lastForegroundApp = pkg
                AppStateHolder.foregroundApp.value = pkg
                packageChangeFlow.tryEmit(pkg)
                Log.d("ZenithAS", "Initial foreground app detected via UsageStats: $pkg")
            }
            if (lastForegroundApp == null) {
                val windowPkg = withContext(Dispatchers.Main) {
                    rootInActiveWindow?.packageName?.toString()
                }
                if (windowPkg != null && windowPkg != packageName && !shouldBypassBlocking(windowPkg)) {
                    if (SharedMonitoringState.isFinancialApp(windowPkg)) {
                        Log.d("ZenithAS", "Financial app already in foreground ($windowPkg) — disabling accessibility")
                        showFinancialAppNotification(windowPkg)
                        disableSelf()
                        return@launch
                    }
                    lastForegroundApp = windowPkg
                    AppStateHolder.foregroundApp.value = windowPkg
                    packageChangeFlow.tryEmit(windowPkg)
                }
            }
        }

        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (true) {
                try {
                    val cfg = SharedMonitoringState.performanceConfig
                    if (!AppStateHolder.isScreenOn.value) {
                        Log.d("Zenith_SCREEN", "A11Y monitoring loop: screen OFF, delaying ${cfg.screenOffDelay}ms")
                        delay(cfg.screenOffDelay)
                        continue
                    }

                    val currentTime = System.currentTimeMillis()
                    val currentPkg = lastForegroundApp

                    var checkInterval = cfg.a11yActiveDelay
                    if (currentPkg != null) {
                        val isMonitored = SharedMonitoringState.allShieldsCache.containsKey(currentPkg) || 
                                         SharedMonitoringState.restrictedPackages.contains(currentPkg)
                        
                        if (!isMonitored) {
                            checkInterval = cfg.a11yInactiveDelay
                        }
                        
                        val allowedUntil = allowedApps[currentPkg] ?: 0L
                        if (allowedUntil > 0) {
                            val remaining = allowedUntil - currentTime
                            val dynamicInterval = when {
                                remaining < 10000 -> 1000L
                                remaining < 60000 -> 5000L
                                else -> 15000L
                            }
                            checkInterval = dynamicInterval.coerceAtMost(checkInterval)
                        }
                    } else {
                        checkInterval = cfg.a11yInactiveDelay
                        val fallbackPkg = queryCurrentForegroundApp()
                        if (fallbackPkg != null && fallbackPkg != packageName) {
                            lastForegroundApp = fallbackPkg
                            AppStateHolder.foregroundApp.value = fallbackPkg
                            packageChangeFlow.tryEmit(fallbackPkg)
                        }
                    }

                    if (currentPkg != null) {
                        sessionUsageOverlayManager.ensureSessionHUDActive(currentPkg)
                    }

                    if (currentPkg != null && !InterceptOverlayManager.isShowing && !isOverlayCheckInProgress && !shouldBypassBlocking(currentPkg)) {
                        val isExpired: Boolean
                        synchronized(allowedApps) {
                            val allowedUntil = allowedApps[currentPkg]
                            isExpired = allowedUntil != null && allowedUntil != 0L && currentTime > allowedUntil
                        }
                        if (isExpired) {
                            val s = currentShieldCache ?: SharedMonitoringState.allShieldsCache[currentPkg] ?: shieldRepository.getShieldByPackageName(currentPkg)
                            if (s?.isAutoQuitEnabled == true) {
                                synchronized(allowedApps) { allowedApps.remove(currentPkg) }
                                lastKickTime = System.currentTimeMillis()
                                lastKickedPackage = currentPkg
                                goToHomeScreen()
                                if (s.isDelayAppEnabled) {
                                    val updated = s.copy(lastDelayStartTimestamp = 0L)
                                    shieldRepository.updateShield(updated)
                                    currentShieldCache = updated
                                }
                            } else if (!InterceptOverlayManager.isShowing) {
                                checkIfAppIsShielded(currentPkg)
                            }
                        } else {
                            val s = currentShieldCache ?: SharedMonitoringState.allShieldsCache[currentPkg]
                            if (s != null && s.type != FocusType.GOAL && !isPaused(s)) {
                                val limitMs = s.timeLimitMinutes * 60 * 1000L
                                val totalUsage = getTotalUsageToday(currentPkg)
                                if (limitMs > 0 && totalUsage >= limitMs) {
                                    val allowedUntil = allowedApps[currentPkg]
                                    if (allowedUntil == null || allowedUntil == 0L || currentTime > allowedUntil) {
                                        if (s.isAutoQuitEnabled) {
                                            synchronized(allowedApps) { allowedApps.remove(currentPkg) }
                                            lastKickTime = System.currentTimeMillis()
                                            lastKickedPackage = currentPkg
                                            goToHomeScreen()
                                        } else if (!InterceptOverlayManager.isShowing) {
                                            checkIfAppIsShielded(currentPkg)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!InterceptOverlayManager.isShowing && !isOverlayCheckInProgress && currentPkg != null && shouldBypassBlocking(currentPkg)) {
                        val actualPkg = withContext(Dispatchers.Main) {
                            rootInActiveWindow?.packageName?.toString()
                        }
                        if (actualPkg != null && actualPkg != currentPkg && !shouldBypassBlocking(actualPkg)) {
                            val actualExpired: Boolean
                            synchronized(allowedApps) {
                                val actualAllowedUntil = allowedApps[actualPkg]
                                actualExpired = actualAllowedUntil != null && actualAllowedUntil != 0L && currentTime > actualAllowedUntil
                            }
                            if (actualExpired) {
                                synchronized(allowedApps) { allowedApps.remove(actualPkg) }
                                val s = SharedMonitoringState.allShieldsCache[actualPkg]
                                if (s?.isAutoQuitEnabled == true) {
                                    lastKickTime = System.currentTimeMillis()
                                    lastKickedPackage = actualPkg
                                    goToHomeScreen()
                                } else if (!InterceptOverlayManager.isShowing) {
                                    checkIfAppIsShielded(actualPkg)
                                }
                            }
                        }
                    }
                    if (currentTime % 300_000L < checkInterval) SharedMonitoringState.performPeriodicCleanup()
                    delay(checkInterval)
                } catch (e: Exception) {
                    Log.e("ZenithAS", "Monitoring loop error: ${e.message}")
                    delay(5000)
                }
            }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var lastA11yEventProcessedTime = 0L
    private val lastA11yPackageTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        lastEventTime = System.currentTimeMillis()
        if (!AppStateHolder.isScreenOn.value || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val now = System.currentTimeMillis()
        if (now - lastA11yEventProcessedTime < 40) return
        lastA11yEventProcessedTime = now

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val className = event.className?.toString() ?: ""
        if (className.contains("Toast") || className.contains("Notification") || className.contains("Tooltip")) {
            return
        }

        if (isKeyboardApp(packageName)) return

        if (SharedMonitoringState.isFinancialApp(packageName)) {
            Log.d("ZenithAS", "Financial app detected ($packageName) — disabling accessibility service to avoid detection")
            showFinancialAppNotification(packageName)
            disableSelf()
            return
        }

        val lastPkgTime = lastA11yPackageTime[packageName] ?: 0L
        if (now - lastPkgTime < 300) return
        lastA11yPackageTime[packageName] = now

        if (packageName !in SharedMonitoringState.CRITICAL_SYSTEM_PACKAGES && !isKeyboardApp(packageName)) {
            lastForegroundApp = packageName
        } else {
            Log.d("Zenith_A11Y", "Skipped lastForegroundApp update for transient package: $packageName (lfga=$lastForegroundApp)")
        }
        AppStateHolder.foregroundApp.value = packageName

        serviceScope.launch(Dispatchers.Main) {
            overlayManager.checkAndHide(packageName)
            packageChangeFlow.tryEmit(packageName)
        }
    }

    private fun showFinancialAppNotification(packageName: String) {
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) { "financial app" }

        val channelId = "zenith_banking_channel"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId, "Banking App Compatibility", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when Zenith disables accessibility for banking app compatibility"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val openSettingsIntent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Accessibility Disabled")
            .setContentText("Zenith disabled accessibility so $appName can work. Monitoring still active via usage stats.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, openSettingsIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        try {
            notificationManager.notify(3001, notification)
        } catch (_: Exception) {}
    }

    private suspend fun handlePackageChange(currentApp: String) {
        if (currentApp == lastForegroundApp && InterceptOverlayManager.isShowing) {
            Log.d("Zenith_HPC", "Early return: $currentApp same app + overlay showing")
            return
        }

        if (shouldBypassBlocking(currentApp)) {
            Log.d("Zenith_HPC", "Bypass branch: $currentApp (lfga was $lastForegroundApp)")
            if (SharedMonitoringState.launcherPackages.contains(currentApp) || currentApp == packageName) {
                InterceptOverlayManager.lastKickTime = 0L
                InterceptOverlayManager.lastKickedPackage = null
            }

            val previousApp = lastForegroundApp
            previousApp?.let { prevPkg ->
                SharedMonitoringState.allShieldsCache[prevPkg]?.let { shield ->
                    if (shield.isDelayAppEnabled) {
                        serviceScope.launch {
                            shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L))
                        }
                    }
                }
            }
            currentShieldCache = null
            if (SharedMonitoringState.isFinancialApp(currentApp)) {
                sessionUsageOverlayManager.removeAllHUDViews()
            }
            serviceScope.launch(Dispatchers.Main) {
                overlayManager.hideOverlay()
            }
            val bypassedPkg = currentApp
            bypassCheckRunnable?.let { mainHandler.removeCallbacks(it) }
            bypassCheckRunnable = Runnable {
                if (!AppStateHolder.isScreenOn.value) {
                    Log.d("Zenith_SCREEN", "A11Y 800ms callback SKIPPED: screen is OFF")
                    return@Runnable
                }
                if (!InterceptOverlayManager.isShowing) return@Runnable
                val actualPkg = rootInActiveWindow?.packageName?.toString()
                if (actualPkg != null && actualPkg != packageName && actualPkg != bypassedPkg && actualPkg != lastForegroundApp) {
                    AppStateHolder.foregroundApp.value = actualPkg
                    packageChangeFlow.tryEmit(actualPkg)
                }
            }
            mainHandler.postDelayed(bypassCheckRunnable!!, 800)
            return
        }

        Log.d("Zenith_HPC", "Non-bypass: $currentApp (lfga was $lastForegroundApp, updating)")
        lastForegroundApp = currentApp

        val shield = SharedMonitoringState.allShieldsCache[currentApp]
        currentShieldCache = shield

        checkBlockingInstant(currentApp, shield)
    }

    private suspend fun checkBlockingInstant(currentApp: String, shield: ShieldEntity?) {
        if (shouldBypassBlocking(currentApp)) return

        val currentTime = System.currentTimeMillis()
        val isAppPaused = shield != null && isPaused(shield)

        if (!isAppPaused) {
            val allowedUntil = allowedApps[currentApp] ?: 0L
            val isBedtimeBlocking = SharedMonitoringState.isBedtimeActive || (SharedMonitoringState.isWindDownActive && (SharedMonitoringState.currentPreferences?.bedtimeWindDownEnabled == true))
            val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in SharedMonitoringState.bedtimeWhitelistedPackages) || currentTime > allowedUntil

            if (shouldCheckSchedules && !InterceptOverlayManager.isShowing && !isOverlayCheckInProgress) {
                val isScheduled = checkSchedules(currentApp)
                val prefs = SharedMonitoringState.currentPreferences ?: return
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
            if (currentTime - lastUsageFetchTime > SharedMonitoringState.performanceConfig.usageStatsCacheMs) {
                cachedTotalUsage = getTotalUsageToday(packageName)
                lastUsageFetchTime = currentTime
            }
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)

        val cfg = SharedMonitoringState.performanceConfig
        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000
        val shouldUpdateDB = timeSinceLastUsed > cfg.shieldDbWriteMs || (isNearLimit && timeSinceLastUsed > cfg.shieldDbWriteNearMs)

        if (shouldUpdateDB) {
            val updatedShield = shield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            serviceScope.launch {
                try {
                    val exists = shieldRepository.getShieldByPackageName(updatedShield.packageName)
                    if (exists != null) {
                        shieldRepository.updateShield(updatedShield)
                    } else {
                        if (lastForegroundApp == updatedShield.packageName) {
                            currentShieldCache = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ZenithAS", "Failed background DB update: ${e.message}")
                }
            }
            if (currentShieldCache?.packageName == packageName) {
                currentShieldCache = updatedShield
            }

            if (shield.type == FocusType.GOAL && !isPaused(shield)) {
                if (currentTotalUsage >= limitMillis && !SharedMonitoringState.notifiedGoals.contains(packageName)) {
                    sendGoalReachedNotification(shield.appName, packageName)
                    SharedMonitoringState.notifiedGoals.add(packageName)
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
            .setSmallIcon(R.drawable.ic_flag)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup("zenith_goals")
            .build()

        try {
            manager.notify(packageName.hashCode(), notification)
        } catch (_: Exception) {}
    }


    private fun getMindfulShield(packageName: String, appName: String): ShieldEntity =
        overlayActionHandler.getMindfulShield(packageName, appName)

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        if (isOverlayCheckInProgress) return
        isOverlayCheckInProgress = true
        try {
            if (targetPackageName != lastForegroundApp) {
                val actualPkg = withContext(Dispatchers.Main) {
                    rootInActiveWindow?.packageName?.toString()
                }
                if (targetPackageName != actualPkg) return
            }

            if (targetPackageName == InterceptOverlayManager.lastKickedPackage && System.currentTimeMillis() - InterceptOverlayManager.lastKickTime < 500) {
                return
            }

            val shield = currentShieldCache ?: SharedMonitoringState.allShieldsCache[targetPackageName]
            val prefs = SharedMonitoringState.currentPreferences ?: return
            val isMindfulGateway = shield == null && prefs.mindfulGatewayEnabled && !shouldBypassBlocking(targetPackageName)
            val appName = shield?.appName ?: overlayActionHandler.getAppName(targetPackageName)
            val effectiveShield = if (isMindfulGateway) overlayActionHandler.getMindfulShield(targetPackageName, appName) else shield

            if (effectiveShield != null && !InterceptOverlayManager.isShowing) {
                val totalUsageToday = getTotalUsageToday(targetPackageName)
                val totalGlobalUsageToday = getTotalGlobalUsageToday()
                val delayDurationSeconds = if (isMindfulGateway) 0 else prefs.delayAppDurationSeconds

                overlayActionHandler.showShieldOverlay(
                    targetPackageName = targetPackageName,
                    shield = effectiveShield,
                    isMindfulGateway = isMindfulGateway,
                    delayDurationSeconds = delayDurationSeconds,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    updateShieldCache = { updated -> currentShieldCache = updated },
                    getTotalUsageTodayFn = { getTotalUsageToday(targetPackageName) },
                )
            }
        } finally {
            isOverlayCheckInProgress = false
        }
    }

    private fun refreshLauncherCache() {
        try {
            val pm = packageManager
            SharedMonitoringState.launcherAppsCache = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            SharedMonitoringState.defaultLauncherPackage = pm.resolveActivity(launcherIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName

            SharedMonitoringState.lastLauncherAppsRefreshTime = System.currentTimeMillis()
        } catch (_: Exception) {}
    }

    private fun getTotalGlobalUsageToday(): Long {
        val currentTime = System.currentTimeMillis()
        val cfg = SharedMonitoringState.performanceConfig
        val cacheDuration = (cfg.usageStatsCacheMs / 2).coerceIn(15000L, 1800000L)
        
        if (currentTime - lastGlobalUsageCacheTime < cacheDuration) {
            return cachedTotalGlobalUsage
        }

        if (currentTime - SharedMonitoringState.lastLauncherAppsRefreshTime > 3600000 || SharedMonitoringState.launcherAppsCache.isEmpty()) {
            refreshLauncherCache()
        }

        val excludePackages = setOfNotNull(packageName, SharedMonitoringState.defaultLauncherPackage)

        val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
        val accurateUsageMap = detailedUsage.appUsageMap

        var totalToday = 0L
        accurateUsageMap.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in SharedMonitoringState.launcherAppsCache) {
                if (time > 0) {
                    totalToday += time
                }
            }
        }

        cachedTotalGlobalUsage = totalToday.coerceAtMost(currentTime - SharedMonitoringState.getStartOfDay())
        lastGlobalUsageCacheTime = currentTime
        return cachedTotalGlobalUsage
    }

    private fun getTotalUsageToday(packageName: String): Long {
        val currentTime = System.currentTimeMillis()
        val cfg = SharedMonitoringState.performanceConfig
        val cacheDuration = cfg.usageStatsCacheMs.coerceIn(30000L, 3600000L)

        if (currentTime - lastUsageCacheTime > cacheDuration && currentTime - SharedMonitoringState.lastDailyUsageFetchTime > cacheDuration) {
            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
            val tempMap = detailedUsage.appUsageMap

            SharedMonitoringState.dailyUsageCache.clear()
            val todayStart = SharedMonitoringState.getStartOfDay()
            val timeSinceMidnight = currentTime - todayStart

            tempMap.forEach { (pkg, time) ->
                val cappedTime = if (time > timeSinceMidnight) timeSinceMidnight else time
                if (cappedTime > 0) SharedMonitoringState.dailyUsageCache[pkg] = cappedTime
            }
            lastUsageCacheTime = currentTime
            SharedMonitoringState.lastDailyUsageFetchTime = currentTime
        }

        return SharedMonitoringState.dailyUsageCache[packageName] ?: 0L
    }

    private fun queryCurrentForegroundApp(): String? {
        val currentTime = System.currentTimeMillis()
        val queryStart = currentTime - 5000
        val usageEvents = try {
            usageStatsManager.queryEvents(queryStart, currentTime)
        } catch (_: Exception) { null } ?: return null

        var foundPackage: String? = null
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName
                if (pkg != null && pkg != packageName) {
                    val className = event.className ?: ""
                    if (className.contains("Toast", ignoreCase = true) ||
                        className.contains("Notification", ignoreCase = true) ||
                        className.contains("Tooltip", ignoreCase = true)) continue
                    foundPackage = pkg
                }
            }
        }
        return foundPackage
    }

    private fun goToHomeScreen() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun updateBedtimeStatus(prefs: UserPreferences) {
        val currentTime = System.currentTimeMillis()
        val currentDay: Int
        val currentMinutes: Int
        val yesterdayDay: Int

        synchronized(reusableCalendar) {
            reusableCalendar.timeInMillis = currentTime
            currentDay = reusableCalendar.get(Calendar.DAY_OF_WEEK)
            currentMinutes = reusableCalendar.get(Calendar.HOUR_OF_DAY) * 60 + reusableCalendar.get(Calendar.MINUTE)
            reusableCalendar.add(Calendar.DAY_OF_YEAR, -1)
            yesterdayDay = reusableCalendar.get(Calendar.DAY_OF_WEEK)
        }

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

    private fun createBedtimeNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(BEDTIME_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    BEDTIME_CHANNEL_ID, "Bedtime & Wind Down", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for bedtime and wind down mode"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun sendWindDownNotification() {
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

        notificationManager.notify(WIND_DOWN_NOTIFICATION_ID, notification)
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
            recheckSchedules = { pkg -> checkSchedules(pkg) }
        )
    }


    private fun showScheduleOverlayFromParsed(packageName: String, ps: ParsedSchedule) {
        val originalSchedule = SharedMonitoringState.activeSchedules.find { it.id == ps.id } ?: return
        showScheduleOverlay(packageName, originalSchedule)
    }

    private fun isPaused(shield: ShieldEntity): Boolean = overlayActionHandler.isPaused(shield)

    private fun isKeyboardApp(packageName: String): Boolean = overlayActionHandler.isKeyboardApp(packageName)

    private fun shouldBypassBlocking(packageName: String): Boolean {
        return overlayActionHandler.shouldBypassBlocking(packageName)
    }

    private fun showScheduleOverlay(packageName: String, schedule: ScheduleEntity) {
        val totalGlobalUsageToday = getTotalGlobalUsageToday()
        overlayActionHandler.showScheduleOverlay(
            packageName = packageName,
            schedule = schedule,
            totalGlobalUsageToday = totalGlobalUsageToday,
            updateShieldCache = { updated -> currentShieldCache = updated }
        )
    }

    override fun onInterrupt() {}

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
            usageStatsCache = null
            lastUsageCacheTime = 0L
            SharedMonitoringState.systemAppCache.clear()
            serviceScope.launch {
                try {
                    ZenithDatabase.getDatabase(this@ZenithAccessibilityService).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        bypassCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        bypassCheckRunnable = null
        try {
            overlayManager.hideOverlay()
            overlayManager.destroy()
            sessionUsageOverlayManager.destroy()
        } catch (_: Exception) {}
        serviceJob.cancel()
    }
}