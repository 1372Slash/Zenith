package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ZenithAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private val usageStatsManager by lazy { getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }

    private var lastForegroundApp: String? = null
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<ScheduleEntity>()
    private var whitelistedPackages = setOf<String>()

    private class ParsedSchedule(
        val id: Long,
        val startMinutes: Int,
        val endMinutes: Int,
        val mode: ScheduleMode,
        val packageNames: Set<String>
    )
    private var parsedSchedulesCache = listOf<ParsedSchedule>()

    companion object {
        var isServiceRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        val database = ZenithDatabase.getDatabase(this)
        shieldRepository = ShieldRepository(database.shieldDao(), database.scheduleDao())
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

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
            }
        }

        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                whitelistedPackages = preferences.whitelistedPackages
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        serviceScope.launch {
            handlePackageChange(packageName)
        }
    }

    private suspend fun handlePackageChange(currentApp: String) {
        if (shouldBypassBlocking(currentApp)) {
            lastForegroundApp = currentApp
            return
        }

        if (currentApp != lastForegroundApp) {
            currentShieldCache = shieldRepository.getShieldByPackageName(currentApp)
            lastUsageFetchTime = 0L
        }

        val currentTime = System.currentTimeMillis()
        val allowedUntil = allowedApps[currentApp] ?: 0L
        
        updateUsageTime(currentApp)

        if (currentTime > allowedUntil && !InterceptOverlayManager.isShowing) {
            if (checkSchedules(currentApp)) {
                lastForegroundApp = currentApp
                return
            }

            val shield = currentShieldCache
            if (shield != null) {
                if (shield.isAutoQuitEnabled && allowedUntil > 0) {
                    goToHomeScreen()
                    allowedApps.remove(currentApp)
                } else if (currentApp != lastForegroundApp) {
                    checkIfAppIsShielded(currentApp)
                }
            }
        }
        lastForegroundApp = currentApp
    }

    private suspend fun updateUsageTime(packageName: String) {
        val shield = currentShieldCache ?: return
        val currentTime = System.currentTimeMillis()

        // Naikkan interval fetch dari 5 detik ke 15 detik untuk mengurangi beban CPU/RAM
        if (currentTime - lastUsageFetchTime > 15000) {
            cachedTotalUsage = getTotalUsageToday(packageName)
            lastUsageFetchTime = currentTime
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
        
        // Hanya update database jika perubahan signifikan (> 30 detik) untuk mengurangi I/O
        if (kotlin.math.abs(shield.remainingTimeMillis - remainingMillis) > 30000) {
            val updatedShield = shield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime
            )
            shieldRepository.updateShield(updatedShield)
            currentShieldCache = updatedShield
        }
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        val shield = currentShieldCache ?: shieldRepository.getShieldByPackageName(targetPackageName)
        if (shield != null && !InterceptOverlayManager.isShowing) {
            val totalUsageToday = getTotalUsageToday(targetPackageName)
            val delayDurationSeconds = preferencesRepository.userPreferencesFlow.first().delayAppDurationSeconds
            
            val currentTime = System.currentTimeMillis()
            val lastAction = shield.lastDelayStartTimestamp
            val isRecentlyUsed = lastAction != 0L && (currentTime - lastAction) < 30 * 60 * 1000L

            val shieldWithTimestamp = if (shield.isDelayAppEnabled && isRecentlyUsed) {
                val updated = shield.copy(lastDelayStartTimestamp = currentTime)
                serviceScope.launch { shieldRepository.updateShield(updated) }
                updated
            } else {
                shield
            }

            serviceScope.launch(Dispatchers.Main) {
                overlayManager.showOverlay(
                    packageName = targetPackageName,
                    appName = shield.appName,
                    shield = shieldWithTimestamp,
                    totalUsageToday = totalUsageToday,
                    delayDurationSeconds = delayDurationSeconds,
                    onAllowUse = { minutes, isEmergency ->
                        serviceScope.launch {
                            val currentShield = shieldRepository.getShieldByPackageName(targetPackageName) ?: return@launch
                            val updatedShield = if (isEmergency) {
                                currentShield.copy(
                                    emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                    lastDelayStartTimestamp = System.currentTimeMillis()
                                )
                            } else {
                                currentShield.copy(
                                    currentPeriodUses = currentShield.currentPeriodUses + 1,
                                    lastDelayStartTimestamp = System.currentTimeMillis()
                                )
                            }
                            shieldRepository.updateShield(updatedShield)
                            currentShieldCache = updatedShield
                            allowedApps[targetPackageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)

                            val prefs = preferencesRepository.userPreferencesFlow.first()
                            if (prefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(minutes, prefs.sessionUsageOverlaySize)
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

    private fun getTotalUsageToday(packageName: String): Long {
        val calendar = java.util.Calendar.getInstance()
        val endTime = System.currentTimeMillis()
        
        calendar.timeInMillis = endTime
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats?.find { it.packageName == packageName }?.totalTimeVisible ?: 0L
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun checkSchedules(packageName: String): Boolean {
        if (shouldBypassBlocking(packageName)) return false
        
        val now = java.util.Calendar.getInstance()
        val currentTotalMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        
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

    private fun showScheduleOverlayFromParsed(packageName: String, ps: ParsedSchedule) {
        val originalSchedule = activeSchedules.find { it.id == ps.id } ?: return
        showScheduleOverlay(packageName, originalSchedule)
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName || packageName in whitelistedPackages) return true
        if (packageName.contains("launcher", ignoreCase = true) || packageName.contains("home", ignoreCase = true)) return true
        return false
    }

    private fun isTimeInInterval(current: String, start: String, end: String): Boolean {
        return if (start <= end) current in start..end else current >= start || current <= end
    }

    private fun showScheduleOverlay(packageName: String, schedule: ScheduleEntity) {
        serviceScope.launch(Dispatchers.Main) {
            overlayManager.showScheduleOverlay(
                packageName = packageName,
                appName = packageName, 
                schedule = schedule,
                onAllowUse = { minutes, isEmergency ->
                    if (isEmergency) {
                        serviceScope.launch {
                            val currentSchedule = shieldRepository.getScheduleById(schedule.id) ?: return@launch
                            shieldRepository.updateSchedule(currentSchedule.copy(emergencyUseCount = currentSchedule.emergencyUseCount - 1))
                            allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)

                            val prefs = preferencesRepository.userPreferencesFlow.first()
                            if (prefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(minutes, prefs.sessionUsageOverlaySize)
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
            allowedApps.clear()
            // Paksa SQLite melepaskan cache memorinya
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
