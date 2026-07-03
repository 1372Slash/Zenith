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
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.data.website.WebsiteStateHolder
import com.etrisad.zenith.ui.components.overlay.SessionUsageOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ZenithService : AccessibilityService() {

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

    private var lastKickTime = 0L
    private var lastKickedPackage: String? = null
    private var lastDndFilter: Int? = null

    private var monitoringJob: kotlinx.coroutines.Job? = null
    private var bypassCheckRunnable: Runnable? = null
    private val websiteUrlTracker = WebsiteUrlTracker(this)
    private var urlPollJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var isOverlayCheckInProgress = false

    private var lastCheckedWebsiteDomain: String? = null

    private var cachedTotalGlobalUsage: Long = 0L
    private var lastGlobalUsageCacheTime: Long = 0L

    companion object {
        var isServiceRunning = false
            set(value) { field = value; AppStateHolder.isAccessibilityServiceRunning.value = value }
        @Volatile
        var lastEventTime = 0L
        private var instance: ZenithService? = null
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
                val gpStart = prefs.gracePeriodStartTime.split(":")
                val gpEnd = prefs.gracePeriodEndTime.split(":")
                SharedMonitoringState.cachedGracePeriodStartMinutes = (gpStart.getOrNull(0)?.toIntOrNull() ?: 12) * 60 + (gpStart.getOrNull(1)?.toIntOrNull() ?: 0)
                SharedMonitoringState.cachedGracePeriodEndMinutes = (gpEnd.getOrNull(0)?.toIntOrNull() ?: 13) * 60 + (gpEnd.getOrNull(1)?.toIntOrNull() ?: 0)
                updateBedtimeStatus(prefs)
                updateGracePeriodStatus(prefs)

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

    private fun isAnyFinancialAppInstalled(): Boolean {
        return SharedMonitoringState.FINANCIAL_APPS.any { pkg ->
            try {
                packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        lastEventTime = System.currentTimeMillis()

        val info = android.accessibilityservice.AccessibilityServiceInfo().apply {
            flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            eventTypes = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        setServiceInfo(info)

        if (isAnyFinancialAppInstalled()) {
            showFinancialAppPreventionNotification()
            disableSelf()
            return
        }

        isServiceRunning = true

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
            quitWebsite = { quitWebsite() },
            getForegroundAppName = { lastForegroundApp },
            recheckShield = { pkg -> serviceScope.launch { checkIfAppIsShielded(pkg) } },
            getTotalUsageToday = { pkg -> getTotalUsageToday(pkg) },
            getTotalGlobalUsageToday = { getTotalGlobalUsageToday() },
        )

        createBedtimeNotificationChannel()

        refreshLauncherCache()

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

                val gpStart = preferences.gracePeriodStartTime.split(":")
                val gpEnd = preferences.gracePeriodEndTime.split(":")
                SharedMonitoringState.cachedGracePeriodStartMinutes = (gpStart.getOrNull(0)?.toIntOrNull() ?: 12) * 60 + (gpStart.getOrNull(1)?.toIntOrNull() ?: 0)
                SharedMonitoringState.cachedGracePeriodEndMinutes = (gpEnd.getOrNull(0)?.toIntOrNull() ?: 13) * 60 + (gpEnd.getOrNull(1)?.toIntOrNull() ?: 0)

                updateBedtimeStatus(preferences)
                updateGracePeriodStatus(preferences)
            }
        }

        serviceScope.launch {
            packageChangeFlow.collect { packageName ->
                try {
                    handlePackageChange(packageName)
                } catch (e: Exception) {
                    Log.e("ZenithAS", "Error in handlePackageChange: ${e.message}")
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

        startEventDrivenMonitoring()
    }

    private fun startEventDrivenMonitoring() {
        monitoringJob = serviceScope.launch {
            while (true) {
                delay(2_500L)
                if (!AppStateHolder.isScreenOn.value) continue
                try {
                    val realPkg = queryCurrentForegroundApp()
                    if (realPkg != null && realPkg != lastForegroundApp && !InterceptOverlayManager.isSystemUiPackage(realPkg) && !isKeyboardApp(realPkg)) {
                        Log.d("ZenithAS", "Monitoring job detected package shift: $lastForegroundApp -> $realPkg")
                        lastForegroundApp = realPkg
                        AppStateHolder.foregroundApp.value = realPkg
                        packageChangeFlow.tryEmit(realPkg)
                    }

                    val currentPkg = lastForegroundApp
                    if (currentPkg != null) {
                        val isBypass = shouldBypassBlocking(currentPkg)
                        if (isBypass) {
                            if (SharedMonitoringState.launcherPackages.contains(currentPkg) || 
                                SharedMonitoringState.defaultLauncherPackage == currentPkg) {
                                sessionUsageOverlayManager.pauseAllSessions()
                            }
                            sessionUsageOverlayManager.updateForegroundApp(currentPkg)
                        } else if (!InterceptOverlayManager.isShowing) {
                            sessionUsageOverlayManager.ensureSessionHUDActive(currentPkg)
                            try {
                                checkAndHandleSessionExpiry(currentPkg, currentShieldCache)
                            } catch (_: Exception) {}
                        }
                    }
                    SharedMonitoringState.performPeriodicCleanup()
                } catch (_: Exception) {}
            }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var lastA11yEventProcessedTime = 0L
    private val lastA11yPackageTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private var lastContentChangeUrlCheck = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        lastEventTime = System.currentTimeMillis()
        if (!AppStateHolder.isScreenOn.value) return

        val now = System.currentTimeMillis()
        val isWindowState = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isWindowState && now - lastA11yEventProcessedTime < 40) return
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

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleWindowStateChanged(packageName, event)
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (packageName == lastForegroundApp) {
                handleViewTextChanged(packageName, event)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (packageName == lastForegroundApp && com.etrisad.zenith.data.website.WebsiteRepository.isKnownBrowser(packageName)) {
                var domain = websiteUrlTracker.checkAccessibilityEvent(event)
                if (domain == null && now - lastContentChangeUrlCheck > 1000) {
                    lastContentChangeUrlCheck = now
                    domain = websiteUrlTracker.extractFromActiveWindow(packageName)
                }

                if (domain != null && domain != WebsiteStateHolder.currentWebsiteDomain.value) {
                    Log.d("Zenith_URL", "URL from contentChanged: $domain")
                    setCurrentWebsiteDomain(domain)
                    serviceScope.launch(Dispatchers.Main) {
                        packageChangeFlow.tryEmit(packageName)
                    }
                }
            }
        }
    }

    private fun handleWindowStateChanged(packageName: String, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        val lastPkgTime = lastA11yPackageTime[packageName] ?: 0L
        if (now - lastPkgTime < 150) return
        lastA11yPackageTime[packageName] = now

        Log.d("Zenith_A11Y", "handleWindowStateChanged: pkg=$packageName domain=${WebsiteStateHolder.currentWebsiteDomain.value}")

        if (packageName !in SharedMonitoringState.CRITICAL_SYSTEM_PACKAGES && !isKeyboardApp(packageName)) {
            lastForegroundApp = packageName
        } else {
            Log.d("Zenith_A11Y", "Skipped lastForegroundApp update for transient package: $packageName (lfga=$lastForegroundApp)")
        }
        AppStateHolder.foregroundApp.value = packageName

        if (com.etrisad.zenith.data.website.WebsiteRepository.isKnownBrowser(packageName)) {
            WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                overlayActionHandler.cancelWebsiteSessionDismiss("zenith-web:$domain")
            }
            val domain = websiteUrlTracker.extractFromWindowStateChange(packageName, event)
            if (domain != null) {
                Log.d("Zenith_URL", "URL from windowStateChanged: $domain")
                setCurrentWebsiteDomain(domain)
                serviceScope.launch(Dispatchers.Main) {
                    packageChangeFlow.tryEmit(packageName)
                }
            }
            startUrlPolling(packageName)
        } else {
            val isLauncher = SharedMonitoringState.launcherPackages.contains(packageName) ||
                SharedMonitoringState.defaultLauncherPackage == packageName ||
                packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("home", ignoreCase = true)
            if (isLauncher) {
                val domain = WebsiteStateHolder.currentWebsiteDomain.value
                Log.d("Zenith_A11Y", "Launcher detected, domain=$domain calling pauseWebsiteSession")
                if (domain != null) {
                    overlayActionHandler.pauseWebsiteSession("zenith-web:$domain")
                } else {
                    Log.d("Zenith_A11Y", "Domain is null, skipping pauseWebsiteSession")
                }
                sessionUsageOverlayManager.pauseAllSessions()
                sessionUsageOverlayManager.updateForegroundApp(packageName, force = true)
            } else {
                val isSystemUI = InterceptOverlayManager.isSystemUiPackage(packageName)
                if (!isSystemUI) {
                    WebsiteStateHolder.currentWebsiteDomain.value?.let { domain ->
                        overlayActionHandler.scheduleWebsiteSessionDismiss("zenith-web:$domain")
                    }
                }
                sessionUsageOverlayManager.updateForegroundApp(packageName)
            }
            stopUrlPolling()
        }

        serviceScope.launch(Dispatchers.Main) {
            overlayManager.checkAndHide(packageName)
            packageChangeFlow.tryEmit(packageName)
        }
    }

    private fun handleViewTextChanged(packageName: String, event: AccessibilityEvent) {
        val domain = websiteUrlTracker.checkViewTextChanged(packageName, event)
        if (domain != null) {
            setCurrentWebsiteDomain(domain)
            serviceScope.launch(Dispatchers.Main) {
                packageChangeFlow.tryEmit(packageName)
            }
        }
    }

    private fun extractUrlFromAccessibilityNode(browserPackage: String? = null): String? =
        websiteUrlTracker.extractUrlFromAccessibilityNode(browserPackage)

    private fun extractUrlDomain(text: String): String? =
        websiteUrlTracker.extractUrlDomain(text)

    private fun checkWebsiteUrl(browserPackage: String) {
        urlPollJob?.let { if (it.isActive) return }
        urlPollJob = serviceScope.launch(Dispatchers.Main) {
            val domain = websiteUrlTracker.extractUrlFromAccessibilityNode(browserPackage)
            if (domain != null && domain != WebsiteStateHolder.currentWebsiteDomain.value) {
                setCurrentWebsiteDomain(domain)
                packageChangeFlow.tryEmit(browserPackage)
            }
        }
    }

    private fun startUrlPolling(browserPackage: String) {
        urlPollJob?.cancel()
        urlPollJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val firstDomain = websiteUrlTracker.extractFromActiveWindow(browserPackage)
                if (firstDomain != null) {
                    withContext(Dispatchers.Main) {
                        setCurrentWebsiteDomain(firstDomain)
                        packageChangeFlow.tryEmit(browserPackage)
                    }
                }
                repeat(30) {
                    delay(100)
                    val d = websiteUrlTracker.extractFromActiveWindow(browserPackage)
                    if (d != null && d != WebsiteStateHolder.currentWebsiteDomain.value) {
                        withContext(Dispatchers.Main) {
                            setCurrentWebsiteDomain(d)
                            packageChangeFlow.tryEmit(browserPackage)
                        }
                    }
                }
                while (true) {
                    delay(500)
                    val d = websiteUrlTracker.extractFromActiveWindow(browserPackage)
                    if (d != null && d != WebsiteStateHolder.currentWebsiteDomain.value) {
                        withContext(Dispatchers.Main) {
                            setCurrentWebsiteDomain(d)
                            packageChangeFlow.tryEmit(browserPackage)
                        }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Exception) {
                Log.e("Zenith_URL", "Error in URL polling: ${e.message}")
            }
        }
    }

    private fun stopUrlPolling() {
        urlPollJob?.cancel()
        urlPollJob = null
    }

    private fun setCurrentWebsiteDomain(domain: String?) {
        val oldDomain = WebsiteStateHolder.currentWebsiteDomain.value
        if (oldDomain != null && oldDomain != domain) {
            val currentPkg = lastForegroundApp
            if (domain == null && currentPkg != null && com.etrisad.zenith.data.website.WebsiteRepository.isKnownBrowser(currentPkg)) {
                Log.d("Zenith_URL", "Ignoring null domain update while browser $currentPkg is active")
                return
            }
            overlayActionHandler.endWebsiteSession("zenith-web:$oldDomain", resumeBrowser = true)
        }
        WebsiteStateHolder.currentWebsiteDomain.value = domain
        if (domain != null) {
            val websitePkg = "zenith-web:$domain"
            overlayActionHandler.cancelWebsiteSessionDismiss(websitePkg)
            checkAndRedirectBlockedUrl(domain)
            if (SharedMonitoringState.allShieldsCache[websitePkg] == null &&
                (SharedMonitoringState.currentPreferences?.websiteAutoTrackingEnabled == true) &&
                !WebsiteStateHolder.websiteSessionStarts.containsKey(websitePkg)) {
                WebsiteStateHolder.recordWebsiteSessionStart(websitePkg)
            }
        }
    }

    private fun checkAndRedirectBlockedUrl(domain: String) {
        val websitePkg = "zenith-web:$domain"
        val shield = SharedMonitoringState.allShieldsCache[websitePkg]
        if (shield != null && shield.isAutoQuitEnabled) {
            val allowedEnd = allowedApps[websitePkg]
            val isAllowed = allowedEnd != null && System.currentTimeMillis() < allowedEnd
            if (!isAllowed) {
                Log.d("Zenith_Block", "Immediate block: redirecting to about:blank ($domain)")
                WebsiteRepository.redirectBrowserToBlankPage(this)
            }
        }
    }

    private fun showFinancialAppPreventionNotification() {
        val channelId = "zenith_banking_channel"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId, "Banking App Compatibility", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when Zenith disables accessibility for banking app compatibility"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openSettingsIntent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Accessibility Paused")
            .setContentText("A banking app is installed. Zenith paused accessibility to avoid detection. Monitoring continues via usage stats.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1, openSettingsIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        try {
            notificationManager.notify(3002, notification)
        } catch (_: Exception) {}
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
        val actualPkg = withContext(Dispatchers.Main) {
            rootInActiveWindow?.packageName?.toString()
        }
        if (actualPkg != null && actualPkg != currentApp && actualPkg != packageName && 
            !InterceptOverlayManager.isSystemUiPackage(actualPkg) && !isKeyboardApp(actualPkg)) {
            return
        }
        if (com.etrisad.zenith.data.website.WebsiteRepository.isKnownBrowser(currentApp) &&
            WebsiteStateHolder.currentWebsiteDomain.value == null) {
            delay(150)
        }

        val currentDomain = WebsiteStateHolder.currentWebsiteDomain.value
        if (currentApp == lastForegroundApp && InterceptOverlayManager.isShowing) {
            if (WebsiteRepository.isKnownBrowser(currentApp) && currentDomain != lastCheckedWebsiteDomain) {
                Log.d("Zenith_HPC", "Website domain changed, allowing re-check")
            } else {
                Log.d("Zenith_HPC", "Early return: $currentApp same app + overlay showing")
                return
            }
        }
        lastCheckedWebsiteDomain = currentDomain

        if (shouldBypassBlocking(currentApp)) {
            if (InterceptOverlayManager.isShowing && (InterceptOverlayManager.isSystemUiPackage(currentApp) || isKeyboardApp(currentApp))) {
                Log.d("Zenith_HPC", "Bypass branch SKIPPED for transient system UI: $currentApp")
                return
            }

            Log.d("Zenith_HPC", "Bypass branch: $currentApp (lfga was $lastForegroundApp)")
            val isLauncher = SharedMonitoringState.launcherPackages.contains(currentApp) ||
                    SharedMonitoringState.defaultLauncherPackage == currentApp ||
                    currentApp.contains("launcher", ignoreCase = true) ||
                    currentApp.contains("home", ignoreCase = true)

            if (isLauncher || currentApp == packageName) {
                if (System.currentTimeMillis() - InterceptOverlayManager.lastKickTime >= 500) {
                    InterceptOverlayManager.lastKickTime = 0L
                    InterceptOverlayManager.lastKickedPackage = null
                }
                if (isLauncher) {
                    sessionUsageOverlayManager.pauseAllSessions()
                }
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
            if (WebsiteRepository.isKnownBrowser(currentApp)) {
                restoreWebsiteHUD()
            }
            return
        }

        Log.d("Zenith_HPC", "Non-bypass: $currentApp (lfga was $lastForegroundApp, updating)")
        lastForegroundApp = currentApp

        val shield = SharedMonitoringState.allShieldsCache[currentApp]
        currentShieldCache = shield

        sessionUsageOverlayManager.ensureSessionHUDActive(currentApp)
        checkAndHandleSessionExpiry(currentApp, shield)
        checkBlockingInstant(currentApp, shield)
    }

    private fun restoreWebsiteHUD() {
        val domain = WebsiteStateHolder.currentWebsiteDomain.value ?: return
        val websitePkg = "zenith-web:$domain"
        val endTime: Long
        synchronized(allowedApps) {
            endTime = allowedApps[websitePkg] ?: return
        }
        val remainingMs = endTime - System.currentTimeMillis()
        if (remainingMs <= 0) return

        val shield = SharedMonitoringState.allShieldsCache[websitePkg] ?: return
        val prefs = SharedMonitoringState.currentPreferences ?: return
        if (!prefs.sessionUsageOverlayEnabled) return

        val isGoal = shield.type == FocusType.GOAL
        val remainingMinutes = (remainingMs / 60000).toInt().coerceAtLeast(1)

        serviceScope.launch(Dispatchers.Main) {
            sessionUsageOverlayManager.showHUD(
                websitePkg,
                if (isGoal) shield.timeLimitMinutes else remainingMinutes,
                prefs.sessionUsageOverlaySize,
                prefs.sessionUsageOverlayOpacity,
                isGoal = isGoal,
                initialSeconds = if (isGoal) (getTotalUsageToday(websitePkg) / 1000).toInt() else 0,
                onSessionEnd = {
                    allowedApps.remove(websitePkg)
                    overlayActionHandler.restoreBrowserFromWebsite()
                }
            )
            if (isGoal) {
                sessionUsageOverlayManager.updateHUDUsage(websitePkg, getTotalUsageToday(websitePkg))
            }
        }
    }

    private suspend fun checkAndHandleSessionExpiry(currentPkg: String, shield: ShieldEntity?) {
        val currentTime = System.currentTimeMillis()
        if (InterceptOverlayManager.isShowing || isOverlayCheckInProgress || shouldBypassBlocking(currentPkg)) return
        val isExpired: Boolean
        synchronized(allowedApps) {
            val allowedUntil = allowedApps[currentPkg]
            isExpired = allowedUntil != null && allowedUntil != 0L && currentTime > allowedUntil
        }
        if (isExpired) {
            val s = shield ?: SharedMonitoringState.allShieldsCache[currentPkg] ?: shieldRepository.getShieldByPackageName(currentPkg)
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
            val s = shield ?: SharedMonitoringState.allShieldsCache[currentPkg]
            if (s != null && s.type != FocusType.GOAL && !isPaused(s)) {
                val limitMs = s.timeLimitMinutes * 60 * 1000L
                if (limitMs > 0) {
                    val totalUsage = getTotalUsageToday(currentPkg)
                    if (totalUsage >= limitMs) {
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
    }

    private suspend fun checkBlockingInstant(currentApp: String, shield: ShieldEntity?) {
        if (shouldBypassBlocking(currentApp)) return

        val currentTime = System.currentTimeMillis()
        val isAppPaused = shield != null && isPaused(shield)
        val wd = WebsiteStateHolder.currentWebsiteDomain.value
        if (wd != null && WebsiteRepository.isKnownBrowser(currentApp) && !isOverlayCheckInProgress) {
            val websitePkg = "zenith-web:$wd"
            val websiteGrant = allowedApps[websitePkg]
            val hasActiveGrant = websiteGrant != null && System.currentTimeMillis() < websiteGrant
            if (!hasActiveGrant) {
                checkIfAppIsShielded(currentApp)
            }
        }

        if (!isAppPaused) {
            val allowedUntil = allowedApps[currentApp] ?: 0L
            val isBedtimeBlocking = SharedMonitoringState.isBedtimeActive || (SharedMonitoringState.isWindDownActive && (SharedMonitoringState.currentPreferences?.bedtimeWindDownEnabled == true))
            val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in SharedMonitoringState.bedtimeWhitelistedPackages) || currentTime > allowedUntil

            if (shouldCheckSchedules && !isOverlayCheckInProgress) {
                val websiteDomain = WebsiteStateHolder.currentWebsiteDomain.value
                val isBrowserWithDomain = WebsiteRepository.isKnownBrowser(currentApp) && websiteDomain != null
                if (isBrowserWithDomain || !InterceptOverlayManager.isShowing) {
                    var isScheduled = checkSchedules(currentApp)
                    if (!isScheduled && isBrowserWithDomain && websiteDomain != null) {
                        isScheduled = checkSchedules("zenith-web:$websiteDomain")
                    }
                    val prefs = SharedMonitoringState.currentPreferences ?: return
                    if (!isScheduled && (shield != null || isBrowserWithDomain || (prefs.mindfulGatewayEnabled && !shouldBypassBlocking(currentApp))) && currentTime > allowedUntil) {
                        checkIfAppIsShielded(currentApp)
                    }
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
            if (targetPackageName in SharedMonitoringState.whitelistedPackages) return

            val isBrowser = WebsiteRepository.isKnownBrowser(targetPackageName)
            var websiteDomain: String? = WebsiteStateHolder.currentWebsiteDomain.value
            if (isBrowser && websiteDomain == null) {
                for (i in 1..3) {
                    websiteDomain = websiteUrlTracker.extractFromActiveWindow(targetPackageName)
                    if (websiteDomain != null) {
                        setCurrentWebsiteDomain(websiteDomain)
                        break
                    }
                    delay(150L * i)
                }
            }

            if (targetPackageName != lastForegroundApp) {
                val actualPkg = withContext(Dispatchers.Main) {
                    rootInActiveWindow?.packageName?.toString()
                }
                if (targetPackageName != actualPkg) return
            }

            val isWebsite = isBrowser && websiteDomain != null
            var actualTargetPackage = if (isWebsite && websiteDomain != null) {
                "zenith-web:$websiteDomain"
            } else {
                targetPackageName
            }

            if (actualTargetPackage == InterceptOverlayManager.lastKickedPackage && System.currentTimeMillis() - InterceptOverlayManager.lastKickTime < 500) {
                return
            }

            var shield = currentShieldCache?.takeIf { it.packageName == actualTargetPackage } ?: SharedMonitoringState.allShieldsCache[actualTargetPackage]

            if (shield != null && isWebsite && actualTargetPackage.startsWith("zenith-web:")) {
                val websiteAllowedUntil = allowedApps[actualTargetPackage] ?: 0L
                if (System.currentTimeMillis() < websiteAllowedUntil) {
                    sessionUsageOverlayManager.ensureSessionHUDActive(actualTargetPackage)
                    return
                }
            }
            if (shield == null && isWebsite) {
                val browserAllowedUntil = allowedApps[targetPackageName] ?: 0L
                if (System.currentTimeMillis() < browserAllowedUntil) return
                actualTargetPackage = targetPackageName
                shield = SharedMonitoringState.allShieldsCache[targetPackageName]
            }
            if (isWebsite && actualTargetPackage == targetPackageName) {
                val browserAllowedUntil = allowedApps[targetPackageName] ?: 0L
                if (System.currentTimeMillis() < browserAllowedUntil) {
                    return
                }
            }
            val activeAllowedUntil = allowedApps[actualTargetPackage] ?: 0L
            if (System.currentTimeMillis() < activeAllowedUntil) return

            val prefs = SharedMonitoringState.currentPreferences ?: return
            val isMindfulGateway = shield == null && prefs.mindfulGatewayEnabled && !shouldBypassBlocking(actualTargetPackage)
            val appName = shield?.appName ?: overlayActionHandler.getAppName(actualTargetPackage)
            val effectiveShield = if (isMindfulGateway) overlayActionHandler.getMindfulShield(actualTargetPackage, appName) else shield

            if (effectiveShield != null && (!InterceptOverlayManager.isShowing || InterceptOverlayManager.currentPackage != actualTargetPackage)) {
                if (isWebsite && effectiveShield.isAutoQuitEnabled) {
                    Log.d("Zenith_Block", "Strict block: redirecting browser to about:blank (shield autoQuit enabled for $actualTargetPackage)")
                    WebsiteRepository.redirectBrowserToBlankPage(this)
                    goToHomeScreen()
                    return
                }
                if (effectiveShield.type == FocusType.GOAL && !isWebsite) {
                    if (!SharedMonitoringState.notifiedGoals.contains(actualTargetPackage)) {
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

                if (effectiveShield.type == FocusType.GOAL && !isWebsite) {
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
                            com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
                            lastUsageCacheTime = 0L
                            SharedMonitoringState.lastDailyUsageFetchTime = 0L
                            getTotalUsageToday(targetPackageName)
                        }
                    },
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
            val launchers = pm.queryIntentActivities(launcherIntent, 0)
            SharedMonitoringState.launcherPackages = launchers.map { it.activityInfo.packageName }.toSet()

            SharedMonitoringState.defaultLauncherPackage = pm.resolveActivity(launcherIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName

            SharedMonitoringState.lastLauncherAppsRefreshTime = System.currentTimeMillis()
        } catch (_: Exception) {}
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

        val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = SharedMonitoringState.cachedDayStartHour, dayStartMinute = SharedMonitoringState.cachedDayStartMinute)
        val accurateUsageMap = detailedUsage.appUsageMap

        var totalToday = 0L
        accurateUsageMap.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in SharedMonitoringState.launcherAppsCache) {
                if (time > 0) {
                    totalToday += time
                }
            }
        }

        val websiteTotal = getWebsiteGlobalUsageToday()
        totalToday += websiteTotal

        cachedTotalGlobalUsage = totalToday.coerceAtMost(currentTime - SharedMonitoringState.getStartOfDay())
        lastGlobalUsageCacheTime = currentTime
        return cachedTotalGlobalUsage
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
        val saved = SharedMonitoringState.lastKnownPackageUsage[packageName]
        if (saved != null && saved > 0L) return saved
        val shield = SharedMonitoringState.allShieldsCache[packageName]
        if (shield != null && shield.limitPeriod == LimitPeriod.WEEKLY) {
            val todayUsage = getSystemUsageToday(packageName)
            val weeklyTotal = kotlinx.coroutines.runBlocking {
                try {
                    shieldRepository.getWeeklyUsageLive(packageName, todayUsage)
                } catch (_: Exception) { 0L }
            }
            if (weeklyTotal > 0) return weeklyTotal
            return todayUsage
        }

        val currentTime = System.currentTimeMillis()
        val cfg = SharedMonitoringState.performanceConfig
        val cacheDuration = cfg.usageStatsCacheMs.coerceIn(30000L, 3600000L)

        if (currentTime - lastUsageCacheTime > cacheDuration && currentTime - SharedMonitoringState.lastDailyUsageFetchTime > cacheDuration) {
            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = SharedMonitoringState.cachedDayStartHour, dayStartMinute = SharedMonitoringState.cachedDayStartMinute)
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

    private fun getSystemUsageToday(packageName: String): Long {
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
        val currentPkg = lastForegroundApp
        if (currentPkg != null && WebsiteRepository.isKnownBrowser(currentPkg)) {
            val domain = WebsiteStateHolder.currentWebsiteDomain.value
            if (domain != null) {
                Log.d("Zenith_Block", "Redirecting browser to about:blank (blocked domain: $domain)")
                WebsiteRepository.redirectBrowserToBlankPage(this)
            }
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun quitWebsite() {
        val browserPkg = WebsiteStateHolder.lastBrowserPackage
        if (browserPkg != null && WebsiteRepository.isKnownBrowser(browserPkg)) {
            Log.d("Zenith_Block", "Quitting website - opening new tab in $browserPkg")
            if (BrowserNewTabAction.performNewTabClick(this, browserPkg)) {
                Log.d("Zenith_Block", "New tab action performed for $browserPkg")
                return
            }
        }
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

    private fun updateGracePeriodStatus(prefs: UserPreferences) {
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
                    ZenithDatabase.getDatabase(this@ZenithService).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
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
