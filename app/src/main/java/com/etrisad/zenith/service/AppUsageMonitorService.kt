package com.etrisad.zenith.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
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
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val reusableEvent = UsageEvents.Event()
    private val reusableCalendar = java.util.Calendar.getInstance()
    private var lastForegroundApp: String? = null
    private var currentShieldCache: com.etrisad.zenith.data.local.entity.ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = mutableSetOf<String>()
    private val launcherPackages = mutableSetOf<String>()
    private val systemAppCache = mutableMapOf<String, Boolean>()
    private var lastLauncherRefreshTime = 0L
    
    // Cache untuk jadwal agar tidak perlu parsing string setiap detik
    private class ParsedSchedule(
        val id: Long,
        val startMinutes: Int,
        val endMinutes: Int,
        val mode: com.etrisad.zenith.data.local.entity.ScheduleMode,
        val packageNames: Set<String>
    )
    private var parsedSchedulesCache = listOf<ParsedSchedule>()

    override fun onCreate() {
        super.onCreate()
        val database = ZenithDatabase.getDatabase(this)
        shieldRepository = ShieldRepository(database.shieldDao(), database.scheduleDao())
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        serviceScope.launch {
            shieldRepository.allSchedules.collect { schedules ->
                activeSchedules = schedules.filter { it.isActive }
                // Pre-parse jadwal ke menit
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
                whitelistedPackages.clear()
                whitelistedPackages.addAll(preferences.whitelistedPackages)
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
    }

    private var lastCheckedDay = -1

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                if (ZenithAccessibilityService.isServiceRunning) {
                    currentShieldCache = null
                    lastUsageFetchTime = 0L
                    delay(10000)
                    continue
                }

                val currentApp = getForegroundApp()
                val currentTime = System.currentTimeMillis()

                // Check for day change only every 60 seconds to save RAM
                if (currentTime % 60000 < 2000) {
                    reusableCalendar.timeInMillis = currentTime
                    val currentDay = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
                    if (lastCheckedDay != -1 && currentDay != lastCheckedDay) {
                        updateStreaks()
                    }
                    lastCheckedDay = currentDay
                }

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
                // Tingkatkan delay polling sedikit jika tidak ada aplikasi yang sedang di-shield
                val pollDelay = if (currentShieldCache != null) 1000L else 1500L
                delay(pollDelay)
            }
        }
    }

    private suspend fun updateUsageTime(packageName: String) {
        val shield = currentShieldCache ?: return
        val currentTime = System.currentTimeMillis()

        // Naikkan interval fetch ke 15 detik (sama seperti di Accessibility Service)
        if (currentTime - lastUsageFetchTime > 15000) {
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
                if (shield.isDelayAppEnabled && lastAction != 0L) {
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
                            
                            val prefs = preferencesRepository.userPreferencesFlow.first()
                            if (prefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(minutes, prefs.sessionUsageOverlaySize)
                                }
                            }
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
        if (shields.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        reusableCalendar.timeInMillis = currentTime
        reusableCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        reusableCalendar.set(java.util.Calendar.MINUTE, 0)
        reusableCalendar.set(java.util.Calendar.SECOND, 0)
        reusableCalendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = reusableCalendar.timeInMillis
        
        val usageMap = usageStatsManager.queryAndAggregateUsageStats(startTime, currentTime)

        shields.forEach { shield ->
            val stats = usageMap[shield.packageName]
            val totalUsageToday = stats?.totalTimeVisible ?: 0L
            
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
        val endTime = System.currentTimeMillis()
        reusableCalendar.timeInMillis = endTime
        reusableCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        reusableCalendar.set(java.util.Calendar.MINUTE, 0)
        reusableCalendar.set(java.util.Calendar.SECOND, 0)
        reusableCalendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = reusableCalendar.timeInMillis

        // Gunakan queryUsageStats biasa tapi langsung cari package-nya
        // Ini jauh lebih ringan daripada queryAndAggregate yang membuat Map raksasa
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        if (stats.isNullOrEmpty()) return 0L
        
        for (stat in stats) {
            if (stat.packageName == packageName) {
                return stat.totalTimeVisible
            }
        }
        
        return 0L
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun checkSchedules(packageName: String): Boolean {
        if (shouldBypassBlocking(packageName)) return false
        
        val now = System.currentTimeMillis()
        reusableCalendar.timeInMillis = now
        val currentTotalMinutes = reusableCalendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + 
                                 reusableCalendar.get(java.util.Calendar.MINUTE)
        
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

    private fun isLauncherOrSystemHome(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (launcherPackages.isEmpty() || now - lastLauncherRefreshTime > 60000) {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val launchers = packageManager.queryIntentActivities(homeIntent, android.content.pm.PackageManager.MATCH_ALL)
                launcherPackages.clear()
                for (resolveInfo in launchers) {
                    launcherPackages.add(resolveInfo.activityInfo.packageName)
                }
                lastLauncherRefreshTime = now
            } catch (_: Exception) {}
        }
        
        if (launcherPackages.contains(packageName)) return true

        try {
            val isSystem = systemAppCache[packageName] ?: run {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val sys = (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                systemAppCache[packageName] = sys
                sys
            }
            
            if (isSystem) {
                // Gunakan pencocokan string sederhana daripada query PackageManager lagi
                if (packageName.contains("launcher", ignoreCase = true) || 
                    packageName.contains("home", ignoreCase = true) ||
                    packageName.contains("car.mode", ignoreCase = true)) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName in whitelistedPackages) return true
        if (isLauncherOrSystemHome(packageName)) return true

        val criticalSystemPackages = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.server.telecom", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller"
        )
        if (packageName in criticalSystemPackages) return true

        try {
            val isSystem = systemAppCache[packageName] ?: run {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val sys = (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                systemAppCache[packageName] = sys
                sys
            }
            
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

    private fun showScheduleOverlay(packageName: String, schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity) {
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }
            
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

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val usageEvents = try {
            usageStatsManager.queryEvents(time - 5000, time)
        } catch (_: Exception) { null } ?: return lastForegroundApp
        
        var lastPackage: String? = null
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(reusableEvent)
            if (reusableEvent.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = reusableEvent.packageName
            }
        }
        
        return lastPackage ?: lastForegroundApp
    }

    override fun onLowMemory() {
        super.onLowMemory()
        currentShieldCache = null
        allowedApps.clear()
        systemAppCache.clear()
        launcherPackages.clear()
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
            allowedApps.clear()
            systemAppCache.clear()
            launcherPackages.clear()
            lastLauncherRefreshTime = 0L
            try {
                ZenithDatabase.getDatabase(this).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
            } catch (_: Exception) {}
            System.gc()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "zenith_monitor_channel"
        val channel = NotificationChannel(
            channelId, "Zenith Monitor Service", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

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
