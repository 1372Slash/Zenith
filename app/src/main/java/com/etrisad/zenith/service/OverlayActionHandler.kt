package com.etrisad.zenith.service

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.ui.components.overlay.SessionUsageOverlayManager
import com.etrisad.zenith.service.AppStateHolder
import com.etrisad.zenith.util.ScreenUsageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
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

    fun cancelPendingTimers() {
        Log.d("Zenith_SCREEN", "OverlayActionHandler: cancelling ${allowedSessionHandlers.size} pending timers")
        allowedSessionHandlers.values.forEach { mainHandler.removeCallbacks(it) }
        allowedSessionHandlers.clear()
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
                                        this@OverlayActionHandler.scope.launch {
                                            val s = SharedMonitoringState.allShieldsCache[targetPackageName]
                                                ?: mindfulGatewayStates[targetPackageName]
                                                ?: shieldRepository.getShieldByPackageName(targetPackageName)
                                            if (s?.isAutoQuitEnabled == true) {
                                                if (getForegroundAppName() == targetPackageName) {
                                                    goToHomeScreen()
                                                }
                                            } else {
                                                recheckShield(targetPackageName)
                                            }
                                        }
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

        allowedSessionHandlers[targetPackageName]?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            Log.d("Zenith_BT", "Timer FIRED for $targetPackageName")
            if (!AppStateHolder.isScreenOn.value) {
                Log.d("Zenith_BT", "Timer EXIT: screen OFF for $targetPackageName")
                return@Runnable
            }
            val entryEndTime = allowedApps[targetPackageName]
            if (entryEndTime == null) {
                Log.d("Zenith_BT", "Timer EXIT: no allowedApps entry for $targetPackageName")
                return@Runnable
            }
            if (allowedApps[targetPackageName] != entryEndTime) {
                Log.d("Zenith_BT", "Timer EXIT: entry replaced for $targetPackageName")
                return@Runnable
            }
            val fg = getForegroundAppName()
            if (fg != targetPackageName) {
                Log.d("Zenith_BT", "Timer EXIT: foreground mismatch for $targetPackageName (fg=$fg)")
                return@Runnable
            }
            allowedApps.remove(targetPackageName)
            val s = SharedMonitoringState.allShieldsCache[targetPackageName]
            val mindful = mindfulGatewayStates[targetPackageName]
            val shield = s ?: mindful
            if (shield == null) {
                Log.d("Zenith_BT", "Timer EXIT: shield not found for $targetPackageName")
                return@Runnable
            }
            Log.d("Zenith_BT", "Timer EXECUTING action for $targetPackageName (autoQuit=${shield.isAutoQuitEnabled})")
            if (shield.isAutoQuitEnabled) {
                goToHomeScreen()
            } else {
                showShieldOverlay(
                    targetPackageName = targetPackageName,
                    shield = shield,
                    isMindfulGateway = mindful != null,
                    delayDurationSeconds = 0,
                    totalUsageToday = getTotalUsageToday(targetPackageName),
                    totalGlobalUsageToday = getTotalGlobalUsageToday(),
                    updateShieldCache = {},
                    getTotalUsageTodayFn = {
                        if (shield.type == FocusType.GOAL && SharedMonitoringState.notifiedGoals.contains(targetPackageName)) {
                            Long.MAX_VALUE
                        } else {
                            getTotalUsageToday(targetPackageName)
                        }
                    }
                )
            }
        }
        allowedSessionHandlers[targetPackageName] = runnable
        mainHandler.postDelayed(runnable, minutes * 60 * 1000L)

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
                                scope.launch {
                                    val s = SharedMonitoringState.allShieldsCache[targetPackageName]
                                        ?: mindfulGatewayStates[targetPackageName]
                                        ?: shieldRepository.getShieldByPackageName(targetPackageName)
                                    if (s?.isAutoQuitEnabled == true) {
                                        if (getForegroundAppName() == targetPackageName) {
                                            goToHomeScreen()
                                        }
                                    } else {
                                        recheckShield(targetPackageName)
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
