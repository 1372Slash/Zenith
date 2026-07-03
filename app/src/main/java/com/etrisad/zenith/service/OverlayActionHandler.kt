package com.etrisad.zenith.service

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.WebsiteUsageEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.ui.components.overlay.SessionUsageOverlayManager
import com.etrisad.zenith.service.AppStateHolder
import com.etrisad.zenith.util.ScreenUsageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class OverlayActionHandler(
    private val shieldRepository: ShieldRepository,
    private val overlayManager: InterceptOverlayManager,
    private val sessionUsageOverlayManager: SessionUsageOverlayManager,
    private val packageManager: PackageManager,
    private val inputMethodManager: InputMethodManager,
    private val contextPkg: String,
    private val scope: CoroutineScope,
    private val goToHomeScreen: () -> Unit,
    private val getForegroundAppName: () -> String?,
    private val recheckShield: (String) -> Unit,
    private val getTotalUsageToday: (String) -> Long,
    private val getTotalGlobalUsageToday: () -> Long,
) {
    private val allowedApps get() = shieldRepository.allowedApps
    private val mindfulGatewayStates get() = shieldRepository.mindfulGatewayStates
    private val reusableCalendar = Calendar.getInstance()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val allowedSessionHandlers = ConcurrentHashMap<String, Runnable>()
    private val pausedBrowserSessions = ConcurrentHashMap<String, Long>()
    private val websiteDismissHandlers = ConcurrentHashMap<String, Runnable>()
    private val pausedWebsiteSessions = ConcurrentHashMap<String, Long>()

    private companion object {
        private const val WEBSITE_DISMISS_GRACE_MS = 60_000L
    }

    fun cancelPendingTimers() {
        Log.d("Zenith_SCREEN", "OverlayActionHandler: cancelling ${allowedSessionHandlers.size} pending timers")
        allowedSessionHandlers.values.forEach { mainHandler.removeCallbacks(it) }
        allowedSessionHandlers.clear()
        websiteDismissHandlers.values.forEach { mainHandler.removeCallbacks(it) }
        websiteDismissHandlers.clear()
        pausedWebsiteSessions.clear()
    }

    private var keyboardPackages = emptySet<String>()
    private var lastKeyboardRefreshTime = 0L

    fun isKeyboardApp(packageName: String): Boolean {
        val currentTime = System.currentTimeMillis()
        if (keyboardPackages.isEmpty() || currentTime - lastKeyboardRefreshTime > 600000) {
            try {
                keyboardPackages = inputMethodManager.enabledInputMethodList.map { it.packageName }.toSet()
                lastKeyboardRefreshTime = currentTime
            } catch (_: Exception) {}
        }
        return packageName in keyboardPackages
    }

    fun isPaused(shield: ShieldEntity): Boolean {
        if (!shield.isPaused) return false
        if (shield.pauseEndTimestamp == 0L) return true
        return System.currentTimeMillis() < shield.pauseEndTimestamp
    }

    fun getMindfulShield(packageName: String, appName: String): ShieldEntity {
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
        val rechargeDurationMillis = (SharedMonitoringState.currentPreferences?.emergencyRechargeDurationMinutes ?: 60) * 60 * 1000L
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

    fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) { packageName }
    }

    fun showShieldOverlay(
        targetPackageName: String,
        shield: ShieldEntity,
        isMindfulGateway: Boolean,
        delayDurationSeconds: Int,
        totalUsageToday: Long,
        totalGlobalUsageToday: Long,
        updateShieldCache: (ShieldEntity?) -> Unit,
        getTotalUsageTodayFn: () -> Long,
    ) {
        if (!AppStateHolder.isScreenOn.value) {
            Log.d("Zenith_SCREEN", "showShieldOverlay($targetPackageName) SKIPPED: screen OFF")
            return
        }
        val shieldWithTimestamp = processDelayForShield(shield, isMindfulGateway, delayDurationSeconds, targetPackageName)

        if (targetPackageName.startsWith("zenith-web:")) {
            AppStateHolder.let { holder ->
                if (holder.lastBrowserPackage != null) {
                    scope.launch(Dispatchers.Main) {
                        sessionUsageOverlayManager.updateForegroundApp(targetPackageName)
                    }
                }
            }
        }

        scope.launch(Dispatchers.Main) {
            overlayManager.showOverlay(
                packageName = targetPackageName,
                appName = shield.appName,
                shield = shieldWithTimestamp,
                totalUsageToday = totalUsageToday,
                totalGlobalUsageToday = totalGlobalUsageToday,
                delayDurationSeconds = delayDurationSeconds,
                onAllowUse = { minutes, isEmergency ->
                    handleAllowUse(
                        targetPackageName, shieldWithTimestamp, isMindfulGateway,
                        minutes, isEmergency, updateShieldCache, getTotalUsageTodayFn
                    )
                },
                onCloseApp = {
                    handleCloseApp(targetPackageName, updateShieldCache)
                },
                onGoalDismiss = {
                    val goalAllowedEnd = System.currentTimeMillis() + (shieldWithTimestamp.timeLimitMinutes * 60 * 1000L)
                    allowedApps[targetPackageName] = goalAllowedEnd

                    val currentPrefs = SharedMonitoringState.currentPreferences
                    if (currentPrefs?.sessionUsageOverlayEnabled == true) {
                        val isGoal = shieldWithTimestamp.type == FocusType.GOAL
                        val limitMillis = (shieldWithTimestamp.timeLimitMinutes) * 60 * 1000L
                        if (isGoal) {
                            ScreenUsageHelper.clearCache()
                            SharedMonitoringState.lastDailyUsageFetchTime = 0L
                        }
                        val currentUsage = getTotalUsageTodayFn()

                        if (!(isGoal && (currentUsage >= limitMillis || SharedMonitoringState.notifiedGoals.contains(targetPackageName)) && limitMillis > 0)) {
                            val duration = shieldWithTimestamp.timeLimitMinutes
                            val currentUsageSeconds = (currentUsage / 1000).toInt()

                            scope.launch(Dispatchers.Main) {
                                sessionUsageOverlayManager.showHUD(
                                    targetPackageName,
                                    duration,
                                    currentPrefs.sessionUsageOverlaySize,
                                    currentPrefs.sessionUsageOverlayOpacity,
                                    isGoal = true,
                                    initialSeconds = currentUsageSeconds,
                                    onSessionEnd = {
                                        allowedApps.remove(targetPackageName)
                                        restoreBrowserFromWebsite()
                                    }
                                )
                                sessionUsageOverlayManager.updateHUDUsage(targetPackageName, currentUsage)
                            }
                        }
                    }
                }
            )
        }
    }

    fun restoreBrowserFromWebsite() {
        val browserPkg = AppStateHolder.lastBrowserPackage
        val fg = getForegroundAppName()
        if (browserPkg != null && (fg == browserPkg || (fg != null && com.etrisad.zenith.data.website.WebsiteRepository.isKnownBrowser(fg)))) {
            scope.launch(Dispatchers.Main) {
                sessionUsageOverlayManager.updateForegroundApp(browserPkg)
            }
            if (!resumeBrowserSession(browserPkg)) {
                recheckShield(browserPkg)
            }
        }
    }

    private fun processDelayForShield(
        shield: ShieldEntity,
        isMindfulGateway: Boolean,
        delayDurationSeconds: Int,
        targetPackageName: String,
    ): ShieldEntity {
        if (!shield.isDelayAppEnabled) return shield

        val currentTime = System.currentTimeMillis()
        val lastAction = shield.lastDelayStartTimestamp
        val lastSessionEnd = shield.lastSessionEndTimestamp
        val gracePeriodMillis = 15 * 1000L
        val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd < gracePeriodMillis)

        return if (isGracePeriodActive) {
            val updated = shield.copy(lastDelayStartTimestamp = currentTime - (delayDurationSeconds * 1000L) - 1000)
            if (!isMindfulGateway) scope.launch { shieldRepository.updateShield(updated) }
            else mindfulGatewayStates[targetPackageName] = updated
            updated
        } else if (lastAction == 0L) {
            val updated = shield.copy(lastDelayStartTimestamp = currentTime)
            if (!isMindfulGateway) scope.launch { shieldRepository.updateShield(updated) }
            else mindfulGatewayStates[targetPackageName] = updated
            updated
        } else {
            shield
        }
    }

    private fun handleAllowUse(
        targetPackageName: String,
        shieldWithTimestamp: ShieldEntity,
        isMindfulGateway: Boolean,
        minutes: Int,
        isEmergency: Boolean,
        updateShieldCache: (ShieldEntity?) -> Unit,
        getTotalUsageTodayFn: () -> Long,
    ) {
        val currentTimeOnAllow = System.currentTimeMillis()
        val endTime = currentTimeOnAllow + (minutes * 60 * 1000L)
        allowedApps[targetPackageName] = endTime
        postSessionTimer(targetPackageName, endTime, minutes * 60 * 1000L)

        // Website grant also grants the browser directly (stored as paused).
        // When user leaves the domain, the browser grant resumes automatically.
        if (targetPackageName.startsWith("zenith-web:")) {
            cancelWebsiteSessionDismiss(targetPackageName)
            val browserPkg = AppStateHolder.lastBrowserPackage
            if (browserPkg != null && !pausedBrowserSessions.containsKey(browserPkg)) {
                pausedBrowserSessions[browserPkg] = minutes * 60 * 1000L
            }
            AppStateHolder.recordWebsiteSessionStart(targetPackageName)
        }

        scope.launch {
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
                if (currentShield != null) {
                    val updatedShield = if (isEmergency) {
                        currentShield.copy(
                            emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                            lastEmergencyRechargeTimestamp = if (currentShield.emergencyUseCount == currentShield.maxEmergencyUses) System.currentTimeMillis() else currentShield.lastEmergencyRechargeTimestamp,
                            lastDelayStartTimestamp = 0L,
                            lastSessionEndTimestamp = currentTimeOnAllow
                        )
                    } else {
                        currentShield.copy(
                            currentPeriodUses = if (System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L) 1 else currentShield.currentPeriodUses + 1,
                            lastPeriodResetTimestamp = if (System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp,
                            lastDelayStartTimestamp = 0L,
                            lastSessionEndTimestamp = currentTimeOnAllow
                        )
                    }
                    shieldRepository.updateShield(updatedShield)
                    updateShieldCache(updatedShield)
                }
            }

            val currentPrefs = SharedMonitoringState.currentPreferences ?: return@launch
            if (currentPrefs.sessionUsageOverlayEnabled) {
                val isGoal = shieldWithTimestamp.type == FocusType.GOAL
                val limitMillis = (shieldWithTimestamp.timeLimitMinutes) * 60 * 1000L
                if (isGoal) {
                    ScreenUsageHelper.clearCache()
                    SharedMonitoringState.lastDailyUsageFetchTime = 0L
                }
                val currentUsage = getTotalUsageTodayFn()

                if (!(isGoal && (currentUsage >= limitMillis || SharedMonitoringState.notifiedGoals.contains(targetPackageName)) && limitMillis > 0)) {
                    val duration = if (isGoal) shieldWithTimestamp.timeLimitMinutes else minutes
                    val currentUsageSeconds = (currentUsage / 1000).toInt()

                    scope.launch(Dispatchers.Main) {
                        sessionUsageOverlayManager.showHUD(
                            targetPackageName,
                            duration,
                            currentPrefs.sessionUsageOverlaySize,
                            currentPrefs.sessionUsageOverlayOpacity,
                            isGoal = isGoal,
                            initialSeconds = if (isGoal) currentUsageSeconds else 0,
                            onSessionEnd = {
                                allowedApps.remove(targetPackageName)
                                restoreBrowserFromWebsite()
                            }
                        )
                        if (isGoal) {
                            sessionUsageOverlayManager.updateHUDUsage(targetPackageName, currentUsage)
                        }
                    }
                }
            }
        }
    }

    private fun postSessionTimer(packageName: String, endTime: Long, delayMs: Long) {
        allowedSessionHandlers[packageName]?.let { mainHandler.removeCallbacks(it) }
        val runnable = createSessionTimerRunnable(packageName, endTime)
        mainHandler.postDelayed(runnable, delayMs)
        allowedSessionHandlers[packageName] = runnable
    }

    private fun createSessionTimerRunnable(packageName: String, expectedEndTime: Long): Runnable {
        return Runnable {
            Log.d("Zenith_BT", "Timer FIRED for $packageName")
            if (!AppStateHolder.isScreenOn.value) {
                Log.d("Zenith_BT", "Timer EXIT: screen OFF for $packageName")
                return@Runnable
            }
            val entryEndTime = allowedApps[packageName]
            if (entryEndTime == null) {
                Log.d("Zenith_BT", "Timer EXIT: no allowedApps entry for $packageName")
                return@Runnable
            }
            if (allowedApps[packageName] != entryEndTime) {
                Log.d("Zenith_BT", "Timer EXIT: entry replaced for $packageName")
                return@Runnable
            }
            val fg = getForegroundAppName()
            val isWebsitePackage = packageName.startsWith("zenith-web:")
            if (isWebsitePackage) {
                val activeDomain = AppStateHolder.currentWebsiteDomain.value
                val sessionDomain = packageName.removePrefix("zenith-web:")
                if (activeDomain != sessionDomain) {
                    Log.d("Zenith_BT", "Timer EXIT: website changed for $packageName (active=$activeDomain)")
                    allowedApps.remove(packageName)
                    return@Runnable
                }
                if (fg == null || !com.etrisad.zenith.data.website.WebsiteRepository.isKnownBrowser(fg)) {
                    Log.d("Zenith_BT", "Timer EXPIRE: browser not in foreground for $packageName (fg=$fg)")
                    saveWebsiteSessionUsage(packageName)
                    allowedApps.remove(packageName)
                    scope.launch(Dispatchers.Main) {
                        sessionUsageOverlayManager.hideHUD(packageName)
                    }
                    return@Runnable
                }
            } else if (fg != packageName) {
                Log.d("Zenith_BT", "Timer EXIT: foreground mismatch for $packageName (fg=$fg)")
                return@Runnable
            }
            // If another overlay is already showing, exit (prevent double overlays)
            if (InterceptOverlayManager.isShowing && InterceptOverlayManager.currentPackage != packageName) {
                Log.d("Zenith_BT", "Timer EXIT: overlay already showing for ${InterceptOverlayManager.currentPackage}")
                return@Runnable
            }
            if (packageName.startsWith("zenith-web:")) {
                saveWebsiteSessionUsage(packageName)
            }
            allowedApps.remove(packageName)
            val s = SharedMonitoringState.allShieldsCache[packageName]
            val mindful = mindfulGatewayStates[packageName]
            val shield = s ?: mindful
            if (shield == null) {
                Log.d("Zenith_BT", "Timer EXIT: shield not found for $packageName")
                return@Runnable
            }
            Log.d("Zenith_BT", "Timer EXECUTING action for $packageName (autoQuit=${shield.isAutoQuitEnabled})")
            if (shield.isAutoQuitEnabled) {
                goToHomeScreen()
            } else {
                showShieldOverlay(
                    targetPackageName = packageName,
                    shield = shield,
                    isMindfulGateway = mindful != null,
                    delayDurationSeconds = 0,
                    totalUsageToday = getTotalUsageToday(packageName),
                    totalGlobalUsageToday = getTotalGlobalUsageToday(),
                    updateShieldCache = {},
                    getTotalUsageTodayFn = {
                        if (shield.type == FocusType.GOAL && SharedMonitoringState.notifiedGoals.contains(packageName)) {
                            Long.MAX_VALUE
                        } else {
                            getTotalUsageToday(packageName)
                        }
                    }
                )
            }
        }
    }

    fun pauseBrowserSession(browserPackage: String) {
        val endTime = allowedApps[browserPackage]
        if (endTime != null && System.currentTimeMillis() < endTime) {
            pausedBrowserSessions[browserPackage] = endTime - System.currentTimeMillis()
            allowedSessionHandlers[browserPackage]?.let { mainHandler.removeCallbacks(it) }
            allowedSessionHandlers.remove(browserPackage)
            allowedApps.remove(browserPackage)
            scope.launch(Dispatchers.Main) {
                sessionUsageOverlayManager.pauseSessionTimer(browserPackage)
            }
            Log.d("Zenith_BT", "Paused browser session for $browserPackage (endTime=$endTime)")
        }
    }

    private fun resumeBrowserSession(browserPackage: String): Boolean {
        val remaining = pausedBrowserSessions.remove(browserPackage) ?: return false
        if (remaining <= 0) return false
        val savedEndTime = System.currentTimeMillis() + remaining
        allowedApps[browserPackage] = savedEndTime
        postSessionTimer(browserPackage, savedEndTime, remaining)
        scope.launch(Dispatchers.Main) {
            if (sessionUsageOverlayManager.hasActiveSession(browserPackage)) {
                sessionUsageOverlayManager.resumeSessionTimer(browserPackage)
            } else {
                val prefs = SharedMonitoringState.currentPreferences
                if (prefs?.sessionUsageOverlayEnabled == true) {
                    sessionUsageOverlayManager.showHUD(
                        browserPackage,
                        (remaining / 60000L).toInt().coerceAtLeast(1),
                        prefs.sessionUsageOverlaySize,
                        prefs.sessionUsageOverlayOpacity,
                        onSessionEnd = {
                            allowedApps.remove(browserPackage)
                            recheckShield(browserPackage)
                        }
                    )
                }
            }
        }
        Log.d("Zenith_BT", "Resumed browser session for $browserPackage (remaining=${remaining}ms)")
        return true
    }

    fun endWebsiteSession(websitePkg: String, resumeBrowser: Boolean = true) {
        if (!websitePkg.startsWith("zenith-web:")) return
        websiteDismissHandlers.remove(websitePkg)?.let { mainHandler.removeCallbacks(it) }
        pausedWebsiteSessions.remove(websitePkg)
        saveWebsiteSessionUsage(websitePkg)
        allowedSessionHandlers[websitePkg]?.let { mainHandler.removeCallbacks(it) }
        allowedSessionHandlers.remove(websitePkg)
        allowedApps.remove(websitePkg)
        scope.launch(Dispatchers.Main) {
            sessionUsageOverlayManager.hideHUD(websitePkg)
        }
        if (resumeBrowser) {
            restoreBrowserFromWebsite()
        }
    }

    fun scheduleWebsiteSessionDismiss(websitePkg: String) {
        if (!websitePkg.startsWith("zenith-web:")) return
        val allowedUntil = allowedApps[websitePkg] ?: return
        if (System.currentTimeMillis() >= allowedUntil) return
        if (websiteDismissHandlers.containsKey(websitePkg)) return

        pausedWebsiteSessions[websitePkg] = allowedUntil - System.currentTimeMillis()
        saveWebsiteSessionUsage(websitePkg)
        allowedSessionHandlers[websitePkg]?.let { mainHandler.removeCallbacks(it) }
        allowedSessionHandlers.remove(websitePkg)
        scope.launch(Dispatchers.Main) {
            sessionUsageOverlayManager.pauseSessionTimer(websitePkg)
        }

        val runnable = Runnable {
            websiteDismissHandlers.remove(websitePkg)
            endWebsiteSession(websitePkg, resumeBrowser = true)
        }
        websiteDismissHandlers[websitePkg] = runnable
        mainHandler.postDelayed(runnable, WEBSITE_DISMISS_GRACE_MS)
        Log.d("Zenith_BT", "Website session pending dismiss for $websitePkg")
    }

    fun pauseWebsiteSession(websitePkg: String) {
        if (!websitePkg.startsWith("zenith-web:")) return
        val allowedUntil = allowedApps[websitePkg] ?: return
        if (System.currentTimeMillis() >= allowedUntil) return

        websiteDismissHandlers.remove(websitePkg)?.let { mainHandler.removeCallbacks(it) }
        pausedWebsiteSessions[websitePkg] = allowedUntil - System.currentTimeMillis()
        saveWebsiteSessionUsage(websitePkg)
        allowedSessionHandlers[websitePkg]?.let { mainHandler.removeCallbacks(it) }
        allowedSessionHandlers.remove(websitePkg)
        scope.launch(Dispatchers.Main) {
            sessionUsageOverlayManager.pauseSessionTimer(websitePkg)
        }
        Log.d("Zenith_BT", "Website session paused for $websitePkg")
    }

    fun cancelWebsiteSessionDismiss(websitePkg: String) {
        if (!websitePkg.startsWith("zenith-web:")) return
        val runnable = websiteDismissHandlers.remove(websitePkg)
        if (runnable != null) {
            mainHandler.removeCallbacks(runnable)
        }
        val remaining = pausedWebsiteSessions.remove(websitePkg) ?: return
        if (remaining > 0) {
            AppStateHolder.recordWebsiteSessionStart(websitePkg)
            val endTime = System.currentTimeMillis() + remaining
            allowedApps[websitePkg] = endTime
            postSessionTimer(websitePkg, endTime, remaining)
            scope.launch(Dispatchers.Main) {
                sessionUsageOverlayManager.resumeSessionTimer(websitePkg)
                sessionUsageOverlayManager.updateForegroundApp(websitePkg)
            }
            Log.d("Zenith_BT", "Website session dismiss cancelled for $websitePkg (remaining=${remaining}ms)")
        }
    }

    fun saveWebsiteSessionUsage(websitePkg: String): Long {
        val startTime = AppStateHolder.consumeWebsiteSessionStart(websitePkg) ?: return 0L
        val now = System.currentTimeMillis()
        val elapsed = now - startTime
        if (elapsed <= 1000) return 0L

        val domain = websitePkg.removePrefix("zenith-web:")
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))

        scope.launch {
            try {
                val existing = shieldRepository.getWebsiteUsage(date, domain)
                shieldRepository.insertWebsiteUsage(
                    WebsiteUsageEntity(
                        date = date,
                        domain = domain,
                        usageTimeMillis = (existing?.usageTimeMillis ?: 0L) + elapsed,
                        lastUpdated = now
                    )
                )

                val cal = Calendar.getInstance()
                cal.timeInMillis = startTime
                var currentHour = cal.get(Calendar.HOUR_OF_DAY)
                var currentHourStart = cal.timeInMillis
                val startCal = Calendar.getInstance().apply { timeInMillis = startTime }
                val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTime))
                var currentDate = startDate
                var remaining = elapsed

                val hourlyEntities = mutableListOf<HourlyUsageEntity>()
                var allExistingHourlies: List<HourlyUsageEntity>? = null

                while (remaining > 0) {
                    val nextHour = currentHour + 1
                    val nextHourCal = Calendar.getInstance().apply {
                        timeInMillis = currentHourStart
                        set(Calendar.HOUR_OF_DAY, nextHour % 24)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (nextHour >= 24) add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val nextHourStart = nextHourCal.timeInMillis
                    val nextDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(nextHourCal.time)

                    val periodEnd = nextHourStart.coerceAtMost(now)
                    val timeInThisHour = periodEnd - currentHourStart

                    if (timeInThisHour > 0) {
                        if (allExistingHourlies == null || currentDate != date) {
                            allExistingHourlies = shieldRepository.getHourlyUsageForDateSync(currentDate)
                        }
                        val existingEntry = allExistingHourlies.find {
                            it.hour == currentHour && it.packageName == websitePkg
                        }
                        hourlyEntities.add(
                            HourlyUsageEntity(
                                date = currentDate,
                                hour = currentHour,
                                packageName = websitePkg,
                                usageTimeMillis = (existingEntry?.usageTimeMillis ?: 0L) + timeInThisHour,
                                lastUpdated = now
                            )
                        )
                    }

                    remaining -= timeInThisHour
                    currentHourStart = periodEnd
                    currentHour = nextHour % 24
                    currentDate = if (nextHour >= 24) nextDate else currentDate
                }

                if (hourlyEntities.isNotEmpty()) {
                    shieldRepository.insertHourlyUsage(hourlyEntities)
                }
            } catch (e: Exception) {
                Log.e("Zenith_BT", "Error saving website usage: ${e.message}")
            }
        }

        return elapsed
    }

    private fun handleCloseApp(
        targetPackageName: String,
        updateShieldCache: (ShieldEntity?) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        InterceptOverlayManager.lastKickTime = now
        InterceptOverlayManager.lastKickedPackage = targetPackageName

        scope.launch {
            val s = SharedMonitoringState.allShieldsCache[targetPackageName]
                ?: shieldRepository.getShieldByPackageName(targetPackageName)
            if (s != null && s.isDelayAppEnabled) {
                val updated = s.copy(lastDelayStartTimestamp = 0L, lastSessionEndTimestamp = 0L)
                shieldRepository.updateShield(updated)
                updateShieldCache(updated)
            }
        }
        goToHomeScreen()
    }

    fun showScheduleOverlay(
        packageName: String,
        schedule: ScheduleEntity,
        totalGlobalUsageToday: Long,
        updateShieldCache: (ShieldEntity?) -> Unit,
        recheckSchedules: (String) -> Unit = {},
        onAllowUseExtra: ((minutes: Int) -> Unit)? = null,
    ) {
        if (!AppStateHolder.isScreenOn.value) {
            Log.d("Zenith_SCREEN", "showScheduleOverlay($packageName) SKIPPED: screen OFF")
            return
        }
        scope.launch(Dispatchers.Main) {
            val appName = getAppName(packageName)

            overlayManager.showScheduleOverlay(
                packageName = packageName,
                appName = appName,
                schedule = schedule,
                totalGlobalUsageToday = totalGlobalUsageToday,
                onAllowUse = { minutes, isEmergency ->
                    if (isEmergency) {
                        onAllowUseExtra?.invoke(minutes)
                        val currentTime = System.currentTimeMillis()
                        allowedApps[packageName] = currentTime + (minutes * 60 * 1000L)

                        scope.launch {
                            val currentSchedule = shieldRepository.getScheduleById(schedule.id) ?: return@launch
                            shieldRepository.updateSchedule(currentSchedule.copy(
                                emergencyUseCount = (currentSchedule.emergencyUseCount - 1).coerceAtLeast(0)
                            ))

                            val currentPrefs = SharedMonitoringState.currentPreferences ?: return@launch
                            if (currentPrefs.sessionUsageOverlayEnabled) {
                                scope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        packageName,
                                        minutes,
                                        currentPrefs.sessionUsageOverlaySize,
                                        currentPrefs.sessionUsageOverlayOpacity,
                                        onSessionEnd = {
                                            allowedApps.remove(packageName)
                                            scope.launch {
                                                checkSchedules(packageName, updateShieldCache, recheckSchedules)
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
                    goToHomeScreen()
                }
            )
        }
    }

    fun showBedtimeOverlay(packageName: String) {
        if (!AppStateHolder.isScreenOn.value) {
            Log.d("Zenith_SCREEN", "showBedtimeOverlay($packageName) SKIPPED: screen OFF")
            return
        }
        scope.launch(Dispatchers.Main) {
            val appName = getAppName(packageName)

            overlayManager.showBedtimeOverlay(
                packageName = packageName,
                appName = appName,
                onCloseApp = {
                    val now = System.currentTimeMillis()
                    InterceptOverlayManager.lastKickTime = now
                    InterceptOverlayManager.lastKickedPackage = packageName
                    goToHomeScreen()
                }
            )
        }
    }

    fun showWindDownOverlay(
        packageName: String,
        sessionUsed: Boolean,
        recheckSchedules: (String) -> Unit,
        onAllowUseExtra: ((minutes: Int) -> Unit)? = null,
    ) {
        if (!AppStateHolder.isScreenOn.value) {
            Log.d("Zenith_SCREEN", "showWindDownOverlay($packageName) SKIPPED: screen OFF")
            return
        }
        scope.launch(Dispatchers.Main) {
            val appName = getAppName(packageName)

            overlayManager.showWindDownOverlay(
                packageName = packageName,
                appName = appName,
                sessionUsed = sessionUsed,
                onAllowUse = { minutes ->
                    val currentTime = System.currentTimeMillis()
                    onAllowUseExtra?.invoke(minutes)
                    allowedApps[packageName] = currentTime + (minutes * 60 * 1000L)
                    SharedMonitoringState.windDownUsedPackages[packageName] = true

                    val currentPrefs = SharedMonitoringState.currentPreferences ?: return@showWindDownOverlay
                    if (currentPrefs.sessionUsageOverlayEnabled) {
                        scope.launch(Dispatchers.Main) {
                            sessionUsageOverlayManager.showHUD(
                                packageName,
                                minutes,
                                currentPrefs.sessionUsageOverlaySize,
                                currentPrefs.sessionUsageOverlayOpacity,
                                onSessionEnd = {
                                    allowedApps.remove(packageName)
                                    recheckSchedules(packageName)
                                }
                            )
                        }
                    }
                },
                onCloseApp = {
                    val now = System.currentTimeMillis()
                    InterceptOverlayManager.lastKickTime = now
                    InterceptOverlayManager.lastKickedPackage = packageName
                    goToHomeScreen()
                }
            )
        }
    }

    fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == contextPkg) return true

        if (SharedMonitoringState.isGracePeriodActive) return true

        if (SharedMonitoringState.isFinancialApp(packageName)) return true

        val prefs = SharedMonitoringState.currentPreferences
        val isBedtimeOrWindDown = SharedMonitoringState.isBedtimeActive || (SharedMonitoringState.isWindDownActive && prefs?.bedtimeWindDownEnabled == true)

        if (packageName in SharedMonitoringState.whitelistedPackages) return true

        if (isBedtimeOrWindDown && packageName in SharedMonitoringState.bedtimeWhitelistedPackages) return true

        if (isKeyboardApp(packageName)) return true

        if (packageName in SharedMonitoringState.CRITICAL_SYSTEM_PACKAGES) return true

        if (SharedMonitoringState.launcherPackages.contains(packageName) ||
            packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("home", ignoreCase = true)) return true

        if (packageName in SharedMonitoringState.restrictedPackages) return false

        val isSystem = SharedMonitoringState.systemAppCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } catch (_: Exception) { false }
        }

        return if (isSystem) {
            if (packageName.contains("car.mode", ignoreCase = true)) true
            else if (isBedtimeOrWindDown) packageName !in SharedMonitoringState.restrictedPackages
            else !(packageName in SharedMonitoringState.restrictedPackages || SharedMonitoringState.hasGlobalAllowSchedule)
        } else false
    }

    fun checkSchedules(
        packageName: String,
        updateShieldCache: (ShieldEntity?) -> Unit,
        recheckSchedules: (String) -> Unit,
    ): Boolean {
        if (shouldBypassBlocking(packageName)) return false

        if (SharedMonitoringState.isGracePeriodActive) return false

        val allowedUntil = allowedApps[packageName] ?: 0L
        if (System.currentTimeMillis() < allowedUntil) return false

        val prefs = SharedMonitoringState.currentPreferences ?: return false

        if (SharedMonitoringState.isBedtimeActive) {
            if (packageName !in SharedMonitoringState.bedtimeWhitelistedPackages) {
                showBedtimeOverlay(packageName)
                return true
            }
            return false
        }

        if (SharedMonitoringState.isWindDownActive && prefs.bedtimeWindDownEnabled) {
            if (packageName !in SharedMonitoringState.bedtimeWhitelistedPackages) {
                val sessionUsed = SharedMonitoringState.windDownUsedPackages[packageName] ?: false
                showWindDownOverlay(packageName, sessionUsed, recheckSchedules)
                return true
            }
            return false
        }

        val schedules = SharedMonitoringState.parsedSchedulesCache
        if (schedules.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val currentTotalMinutes = synchronized(reusableCalendar) {
                reusableCalendar.timeInMillis = now
                reusableCalendar.get(Calendar.HOUR_OF_DAY) * 60 + reusableCalendar.get(Calendar.MINUTE)
            }

            for (ps in schedules) {
                val isInInterval = if (ps.startMinutes <= ps.endMinutes) {
                    currentTotalMinutes in ps.startMinutes..ps.endMinutes
                } else {
                    currentTotalMinutes >= ps.startMinutes || currentTotalMinutes <= ps.endMinutes
                }

                if (isInInterval) {
                    when (ps.mode) {
                        ScheduleMode.BLOCK -> {
                            if (packageName in ps.packageNames) {
                                val originalSchedule = SharedMonitoringState.activeSchedules.find { it.id == ps.id } ?: return false
                                val totalGlobalUsageToday = getTotalGlobalUsageToday()
                                showScheduleOverlay(packageName, originalSchedule, totalGlobalUsageToday, updateShieldCache, recheckSchedules)
                                return true
                            }
                        }
                        ScheduleMode.ALLOW -> {
                            if (packageName !in ps.packageNames) {
                                val originalSchedule = SharedMonitoringState.activeSchedules.find { it.id == ps.id } ?: return false
                                val totalGlobalUsageToday = getTotalGlobalUsageToday()
                                showScheduleOverlay(packageName, originalSchedule, totalGlobalUsageToday, updateShieldCache, recheckSchedules)
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }
}
