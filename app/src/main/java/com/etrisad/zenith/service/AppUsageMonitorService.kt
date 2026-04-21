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
    private val usageStatsManager by lazy { getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }
    private var lastForegroundApp: String? = null
    private var currentShieldCache: com.etrisad.zenith.data.local.entity.ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = setOf<String>()

    override fun onCreate() {
        super.onCreate()
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

        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
    }

    private var lastCheckedDay = -1

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                // REDUCED POLLING: If Accessibility Service is running, this service backs off
                // to avoid double memory/CPU usage.
                if (ZenithAccessibilityService.isServiceRunning) {
                    delay(3000)
                    continue
                }

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
                    // Reset cache if app changed
                    if (currentApp != lastForegroundApp) {
                        currentShieldCache = shieldRepository.getShieldByPackageName(currentApp)
                        lastUsageFetchTime = 0L 
                    }

                    if (shouldBypassBlocking(currentApp)) {
                        lastForegroundApp = currentApp
                        delay(1000)
                        continue
                    }

                    val allowedUntil = allowedApps[currentApp] ?: 0L
                    updateUsageTime(currentApp)

                    if (currentTime > allowedUntil && !InterceptOverlayManager.isShowing) {
                        if (checkSchedules(currentApp)) {
                            lastForegroundApp = currentApp
                            delay(1000)
                            continue
                        }

                        val shield = currentShieldCache
                        if (shield != null) {
                            if (shield.isAutoQuitEnabled && allowedUntil > 0) {
                                goToHomeScreen()
                                allowedApps.remove(currentApp)
                                if (shield.isDelayAppEnabled) {
                                    val delayTimestamp = System.currentTimeMillis()
                                    serviceScope.launch {
                                        shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = delayTimestamp))
                                    }
                                    currentShieldCache = currentShieldCache?.copy(lastDelayStartTimestamp = delayTimestamp)
                                }
                            } else if (currentApp != lastForegroundApp) {
                                checkIfAppIsShielded(currentApp)
                            }
                        }
                    }
                } else if (currentApp == null || currentApp == packageName) {
                    currentShieldCache = null
                }
                
                lastForegroundApp = currentApp
                delay(1000)
            }
        }
    }

    private suspend fun updateUsageTime(packageName: String) {
        val shield = currentShieldCache ?: return
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastUsageFetchTime > 5000) {
            cachedTotalUsage = getTotalUsageToday(packageName)
            lastUsageFetchTime = currentTime
        }

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

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
        
        if (kotlin.math.abs(shield.remainingTimeMillis - remainingMillis) > 10000 || updatedShield != shield) {
            val finalShield = updatedShield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime
            )
            shieldRepository.updateShield(finalShield)
            currentShieldCache = finalShield
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
                if (shield.isDelayAppEnabled && lastAction != 0L && !isRecentlyUsed) {
                    serviceScope.launch { shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L)) }
                }
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
                                    lastDelayStartTimestamp = System.currentTimeMillis() 
                                )
                            } else {
                                val periodExpired = System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                currentShield.copy(
                                    currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                    lastPeriodResetTimestamp = if (periodExpired) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp,
                                    lastDelayStartTimestamp = System.currentTimeMillis() 
                                )
                            }
                            shieldRepository.updateShield(updatedShield)
                            currentShieldCache = updatedShield
                            allowedApps[targetPackageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        }
                    },
                    onCloseApp = {
                        goToHomeScreen()
                    },
                    onGoalDismiss = {
                        allowedApps[targetPackageName] = System.currentTimeMillis() + (60 * 60 * 1000L) 
                    }
                )
            }
        }
    }

    private suspend fun updateStreaks() {
        val shields = shieldRepository.allShields.first()
        val currentTime = System.currentTimeMillis()
        
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        val allStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
        val usageMap = allStats?.associate { it.packageName to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.totalTimeVisible else it.totalTimeInForeground) } ?: emptyMap()

        shields.forEach { shield ->
            val totalUsageToday = usageMap[shield.packageName] ?: 0L
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            
            var shouldIncrement = false
            if (shield.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL) {
                if (totalUsageToday >= limitMillis && limitMillis > 0) {
                    shouldIncrement = true
                }
            } else {
                if (totalUsageToday <= limitMillis) {
                    shouldIncrement = true
                }
            }

            if (shouldIncrement) {
                shieldRepository.updateShield(shield.copy(
                    currentStreak = shield.currentStreak + 1,
                    lastStreakUpdateTimestamp = currentTime
                ))
            } else {
                shieldRepository.updateShield(shield.copy(
                    currentStreak = 0,
                    lastStreakUpdateTimestamp = currentTime
                ))
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
                when (schedule.mode) {
                    com.etrisad.zenith.data.local.entity.ScheduleMode.BLOCK -> {
                        if (packageName in schedule.packageNames) {
                            showScheduleOverlay(packageName, schedule)
                            return true
                        }
                    }
                    com.etrisad.zenith.data.local.entity.ScheduleMode.ALLOW -> {
                        if (packageName !in schedule.packageNames) {
                            showScheduleOverlay(packageName, schedule)
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun isLauncherOrSystemHome(packageName: String): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = packageManager.queryIntentActivities(homeIntent, android.content.pm.PackageManager.MATCH_ALL)
        if (launchers.any { it.activityInfo.packageName == packageName }) return true

        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            if (isSystem) {
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                launcherIntent.setPackage(packageName)
                val activities = packageManager.queryIntentActivities(launcherIntent, 0)
                if (activities.isNotEmpty() && (packageName.contains("launcher", ignoreCase = true)
                            || packageName.contains("home", ignoreCase = true))) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName in whitelistedPackages) return true
        if (isLauncherOrSystemHome(packageName) || 
            packageName.contains("launcher", ignoreCase = true) || 
            packageName.contains("home", ignoreCase = true)) return true

        val criticalSystemPackages = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.server.telecom", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller"
        )
        if (packageName in criticalSystemPackages) return true

        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            if (isSystem) {
                val blockableSystemApps = setOf(
                    "com.google.android.youtube", "com.android.chrome",
                    "com.google.android.apps.youtube.music", "com.android.vending"
                )
                return packageName !in blockableSystemApps
            }
        } catch (_: Exception) { return true }
        return false
    }

    private fun isTimeInInterval(current: String, start: String, end: String): Boolean {
        return if (start <= end) current in start..end else current >= start || current <= end
    }

    private fun showScheduleOverlay(packageName: String, schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity) {
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) { packageName }
            
            overlayManager.showScheduleOverlay(
                packageName = packageName,
                appName = appName,
                schedule = schedule,
                onAllowUse = { minutes, isEmergency ->
                    serviceScope.launch {
                        if (isEmergency) {
                            val currentSchedule = shieldRepository.getScheduleById(schedule.id) ?: return@launch
                            val updatedSchedule = currentSchedule.copy(
                                emergencyUseCount = (currentSchedule.emergencyUseCount - 1).coerceAtLeast(0)
                            )
                            shieldRepository.updateSchedule(updatedSchedule)
                            allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        }
                    }
                },
                onCloseApp = { goToHomeScreen() }
            )
        }
    }

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 3000, time)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        if (lastPackage != null) return lastPackage

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 2000, time)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
        }
    }

    private fun createNotification(): Notification {
        val channelId = "zenith_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Zenith Monitor Service", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
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
