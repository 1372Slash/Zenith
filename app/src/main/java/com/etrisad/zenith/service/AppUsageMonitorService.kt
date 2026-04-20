package com.etrisad.zenith.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppUsageMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private var lastForegroundApp: String? = null
    private val allowedApps = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        val database = ZenithDatabase.getDatabase(this)
        shieldRepository = ShieldRepository(database.shieldDao())
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
    }

    private var lastCheckedDay = -1

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                val currentApp = getForegroundApp()
                val currentTime = System.currentTimeMillis()

                // Check for day change to update streaks
                val calendar = java.util.Calendar.getInstance()
                val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
                if (lastCheckedDay != -1 && currentDay != lastCheckedDay) {
                    updateStreaks()
                }
                lastCheckedDay = currentDay

                if (currentApp != null && currentApp != packageName) {
                    val allowedUntil = allowedApps[currentApp] ?: 0L
                    
                    // Update usage time in database if app is shielded
                    updateUsageTime(currentApp)

                    if (currentTime > allowedUntil && !InterceptOverlayManager.isShowing && !ZenithAccessibilityService.isServiceRunning) {
                        val shield = shieldRepository.getShieldByPackageName(currentApp)
                        if (shield != null) {
                            if (shield.isAutoQuitEnabled && allowedUntil > 0) {
                                // Auto-quit if time is up and setting is enabled
                                goToHomeScreen()
                                allowedApps.remove(currentApp)
                                // Start delay immediately after auto-quit if enabled
                                if (shield.isDelayAppEnabled) {
                                    serviceScope.launch {
                                        shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = System.currentTimeMillis()))
                                    }
                                }
                            } else if (currentApp != lastForegroundApp) {
                                // New app opened or just expired - show overlay
                                checkIfAppIsShielded(currentApp)
                            }
                        }
                    }
                }
                
                lastForegroundApp = currentApp
                delay(1000)
            }
        }
    }

    private suspend fun updateUsageTime(packageName: String) {
        val shield = shieldRepository.getShieldByPackageName(packageName) ?: return
        val currentTime = System.currentTimeMillis()
        
        // Recharge Emergency Uses logic
        val prefs = preferencesRepository.userPreferencesFlow.first()
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

        val totalUsageToday = getTotalUsageToday(packageName)
        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        
        // Calculate real remaining time based on system usage stats
        val remainingMillis = (limitMillis - totalUsageToday).coerceAtLeast(0L)
        
        // Only update if there's a significant change or recharge happened
        if (kotlin.math.abs(shield.remainingTimeMillis - remainingMillis) > 500 || updatedShield != shield) {
            shieldRepository.updateShield(updatedShield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime
            ))
        }
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        val shield = shieldRepository.getShieldByPackageName(targetPackageName)
        if (shield != null && !InterceptOverlayManager.isShowing) {
            val totalUsageToday = getTotalUsageToday(targetPackageName)
            val delayDurationSeconds = preferencesRepository.userPreferencesFlow.first().delayAppDurationSeconds
            
            val currentTime = System.currentTimeMillis()
            val isRecentlyUsed = (currentTime - shield.lastUsedTimestamp) < 30 * 60 * 1000L

            // Logic: No delay on first opening. Delay active after pressing time button.
            // Reset to no delay behavior after 30 minutes of inactivity.
            val shieldWithTimestamp = if (shield.isDelayAppEnabled) {
                when {
                    !isRecentlyUsed && shield.lastDelayStartTimestamp != 0L -> {
                        val updated = shield.copy(lastDelayStartTimestamp = 0L)
                        serviceScope.launch { shieldRepository.updateShield(updated) }
                        updated
                    }
                    isRecentlyUsed && shield.lastDelayStartTimestamp == 1L -> {
                        val updated = shield.copy(lastDelayStartTimestamp = currentTime)
                        serviceScope.launch { shieldRepository.updateShield(updated) }
                        updated
                    }
                    else -> shield
                }
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
                                val isFirstChargeUsed = currentShield.emergencyUseCount == currentShield.maxEmergencyUses
                                currentShield.copy(
                                    emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                    lastEmergencyRechargeTimestamp = if (isFirstChargeUsed) System.currentTimeMillis() else currentShield.lastEmergencyRechargeTimestamp,
                                    lastDelayStartTimestamp = 1L // Mark as recently used for next delay
                                )
                            } else {
                                val periodExpired = System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                currentShield.copy(
                                    currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                    lastPeriodResetTimestamp = if (periodExpired) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp,
                                    lastDelayStartTimestamp = 1L // Mark as recently used for next delay
                                )
                            }
                            shieldRepository.updateShield(updatedShield)
                            allowedApps[targetPackageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        }
                    },
                    onCloseApp = {
                        goToHomeScreen()
                    },
                    onGoalDismiss = {
                        allowedApps[targetPackageName] = System.currentTimeMillis() + (60 * 60 * 1000L) // 1 hour
                    }
                )
            }
        }
    }

    private suspend fun updateStreaks() {
        val shields = shieldRepository.allShields.first()
        val currentTime = System.currentTimeMillis()
        
        shields.forEach { shield ->
            val totalUsageToday = getTotalUsageToday(shield.packageName)
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            
            var shouldIncrement = false
            if (shield.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL) {
                // Goal: Streak if reached 100% (usage >= target)
                if (totalUsageToday >= limitMillis && limitMillis > 0) {
                    shouldIncrement = true
                }
            } else {
                // Shield: Streak if within limit (usage <= limit)
                if (totalUsageToday <= limitMillis) {
                    shouldIncrement = true
                }
            }

            if (shouldIncrement) {
                val newStreak = shield.currentStreak + 1
                shieldRepository.updateShield(shield.copy(
                    currentStreak = newStreak,
                    lastStreakUpdateTimestamp = currentTime
                ))
            } else {
                // Reset streak if not met
                shieldRepository.updateShield(shield.copy(
                    currentStreak = 0,
                    lastStreakUpdateTimestamp = currentTime
                ))
            }
        }
    }

    private fun getTotalUsageToday(packageName: String): Long {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
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

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        // Try using UsageEvents first as it's more accurate for the current foreground app
        val usageEvents = usageStatsManager.queryEvents(time - 10000, time)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        
        if (lastPackage != null) return lastPackage

        // Fallback to queryUsageStats if no recent events
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 60,
            time
        )
        if (stats != null) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            if (sortedStats.isNotEmpty()) {
                return sortedStats[0].packageName
            }
        }
        return null
    }

    private fun createNotification(): Notification {
        val channelId = "zenith_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Zenith Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring app usage to help you focus."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

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
        serviceJob.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
    }
}
