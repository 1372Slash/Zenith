package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.etrisad.zenith.data.local.database.ZenithDatabase
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
    private val usageStatsManager by lazy { getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }
    
    private var lastForegroundApp: String? = null
    private var currentShieldCache: com.etrisad.zenith.data.local.entity.ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = setOf<String>()

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

        serviceScope.launch {
            shieldRepository.allSchedules.collect { schedules ->
                activeSchedules = schedules.filter { it.isActive }
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

        if (currentTime - lastUsageFetchTime > 5000) {
            cachedTotalUsage = getTotalUsageToday(packageName)
            lastUsageFetchTime = currentTime
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
        
        if (kotlin.math.abs(shield.remainingTimeMillis - remainingMillis) > 10000) {
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
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats?.find { it.packageName == packageName }?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.totalTimeVisible else it.totalTimeInForeground
        } ?: 0L
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun checkSchedules(packageName: String): Boolean {
        if (shouldBypassBlocking(packageName)) return false
        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        
        for (schedule in activeSchedules) {
            if (isTimeInInterval(currentTime, schedule.startTime, schedule.endTime)) {
                if ((schedule.mode == com.etrisad.zenith.data.local.entity.ScheduleMode.BLOCK && packageName in schedule.packageNames) ||
                    (schedule.mode == com.etrisad.zenith.data.local.entity.ScheduleMode.ALLOW && packageName !in schedule.packageNames)) {
                    showScheduleOverlay(packageName, schedule)
                    return true
                }
            }
        }
        return false
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName || packageName in whitelistedPackages) return true
        if (packageName.contains("launcher", ignoreCase = true) || packageName.contains("home", ignoreCase = true)) return true
        return false
    }

    private fun isTimeInInterval(current: String, start: String, end: String): Boolean {
        return if (start <= end) current in start..end else current >= start || current <= end
    }

    private fun showScheduleOverlay(packageName: String, schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity) {
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
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceJob.cancel()
    }
}
