package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZenithAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: com.etrisad.zenith.data.preferences.UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private val allowedApps = mutableMapOf<String, Long>()
    private var currentPackage: String? = null
    private var monitoringJob: Job? = null

    companion object {
        var isServiceRunning = false
    }

    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        val database = ZenithDatabase.getDatabase(this)
        shieldRepository = ShieldRepository(database.shieldDao(), database.scheduleDao())
        preferencesRepository = com.etrisad.zenith.data.preferences.UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        
        serviceScope.launch {
            shieldRepository.allSchedules.collect { schedules ->
                activeSchedules = schedules.filter { it.isActive }
            }
        }
        
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val pkg = currentPackage ?: continue
                if (pkg == packageName) continue

                val allowedUntil = allowedApps[pkg] ?: 0L
                if (allowedUntil > 0 && System.currentTimeMillis() > allowedUntil) {
                    // Check schedules first
                    if (checkSchedules(pkg)) continue

                    val shield = shieldRepository.getShieldByPackageName(pkg)
                    if (shield != null) {
                        if (shield.isAutoQuitEnabled) {
                            withContext(Dispatchers.Main) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                                allowedApps.remove(pkg)
                                // Start delay immediately after auto-quit if enabled
                                if (shield.isDelayAppEnabled) {
                                    serviceScope.launch {
                                        shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = System.currentTimeMillis()))
                                    }
                                }
                            }
                        } else {
                            // Trigger overlay if not showing and auto-quit is off
                            if (!InterceptOverlayManager.isShowing) {
                                checkIfAppIsShielded(pkg)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkSchedules(packageName: String): Boolean {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        
        for (schedule in activeSchedules) {
            if (isTimeInInterval(currentTime, schedule.startTime, schedule.endTime)) {
                when (schedule.mode) {
                    com.etrisad.zenith.data.local.entity.ScheduleMode.BLOCK -> {
                        if (packageName in schedule.packageNames) {
                            showScheduleOverlay(packageName, schedule)
                            return true
                        }
                    }
                    com.etrisad.zenith.data.local.entity.ScheduleMode.ALLOW -> {
                        if (packageName !in schedule.packageNames && packageName != this.packageName) {
                            showScheduleOverlay(packageName, schedule)
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun isTimeInInterval(current: String, start: String, end: String): Boolean {
        return if (start <= end) {
            current in start..end
        } else {
            current >= start || current <= end
        }
    }

    private fun showScheduleOverlay(packageName: String, schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity) {
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
            
            overlayManager.showScheduleOverlay(
                packageName = packageName,
                appName = appName,
                schedule = schedule,
                onCloseApp = {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            currentPackage = packageName
            
            // Don't intercept our own app
            if (packageName == this.packageName) return

            // Check schedules first
            if (checkSchedules(packageName)) return

            val currentTime = System.currentTimeMillis()
            val allowedUntil = allowedApps[packageName] ?: 0L
            if (currentTime > allowedUntil) {
                checkIfAppIsShielded(packageName)
            }
        }
    }

    private fun checkIfAppIsShielded(packageName: String) {
        serviceScope.launch {
            val shield = shieldRepository.getShieldByPackageName(packageName)
            if (shield != null && !InterceptOverlayManager.isShowing) {
                val totalUsageToday = getTotalUsageToday(packageName)
                val delayDurationSeconds = preferencesRepository.userPreferencesFlow.first().delayAppDurationSeconds
                
                // Update lastDelayStartTimestamp if it's not set and delay is enabled
                val shieldWithTimestamp = if (shield.isDelayAppEnabled && shield.lastDelayStartTimestamp == 0L) {
                    val updated = shield.copy(lastDelayStartTimestamp = System.currentTimeMillis())
                    serviceScope.launch {
                        shieldRepository.updateShield(updated)
                    }
                    updated
                } else {
                    shield
                }

                serviceScope.launch(Dispatchers.Main) {
                    overlayManager.showOverlay(
                        packageName = packageName,
                        appName = shield.appName,
                        shield = shieldWithTimestamp,
                        totalUsageToday = totalUsageToday,
                        delayDurationSeconds = delayDurationSeconds,
                        onAllowUse = { minutes, isEmergency ->
                            serviceScope.launch {
                                val currentShield = shieldRepository.getShieldByPackageName(packageName) ?: return@launch
                                val updatedShield = if (isEmergency) {
                                    currentShield.copy(
                                        emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                        lastDelayStartTimestamp = 0L // Reset delay
                                    )
                                } else {
                                    val periodExpired = System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                    currentShield.copy(
                                        currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                        lastPeriodResetTimestamp = if (periodExpired) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp,
                                        lastDelayStartTimestamp = 0L // Reset delay
                                    )
                                }
                                shieldRepository.updateShield(updatedShield)
                                allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                            }
                        },
                        onCloseApp = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        },
                        onGoalDismiss = {
                            allowedApps[packageName] = System.currentTimeMillis() + (60 * 60 * 1000L) // 1 hour
                        }
                    )
                }
            }
        }
    }

    private fun getTotalUsageToday(packageName: String): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        return stats[packageName]?.totalTimeVisible ?: 0L
    }

    override fun onInterrupt() {
        // Handle interruptions
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceJob.cancel()
    }
}
