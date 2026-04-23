package com.etrisad.zenith.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUsageMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val powerManager by lazy { getSystemService(POWER_SERVICE) as android.os.PowerManager }
    private val reusableEvent = UsageEvents.Event()
    private var lastForegroundApp: String? = null
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private var sessionStartTime = 0L
    private var baseUsageAtSessionStart = 0L
    private var lastBypassResult = false
    private var lastBypassPackage: String? = null
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = emptySet<String>()
    private var launcherPackages = emptySet<String>()
    private val systemAppCache = mutableMapOf<String, Boolean>()
    private val launcherAppCache = mutableMapOf<String, Boolean>()
    private var lastLauncherRefreshTime = 0L
    private var currentPreferences: UserPreferences? = null
    private var lastResetDate = ""
    private var allShieldsCache = listOf<ShieldEntity>()
    private var allShieldsMap = emptyMap<String, ShieldEntity>()
    private var goalShieldsCache = listOf<ShieldEntity>()
    private var nextDayTimestamp = 0L
    private var startOfDayTimestamp = 0L
    private var isScreenOn = true
    private var isPowerSaveMode = false

    private var usageTimesMapCache: Map<String, Long>? = null
    private var lastUsageCacheTime = 0L
    private var lastScheduleCheckTime = 0L

    private var lastPollingInterval = 5000L

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    currentShieldCache = null
                    usageTimesMapCache = null
                    lastForegroundApp = null // Penting: Reset agar init ulang saat nyala
                }
                android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    isPowerSaveMode = powerManager.isPowerSaveMode
                }
            }
        }
    }
    
    // Cache untuk jadwal agar tidak perlu parsing string setiap detik
    private class ParsedSchedule(
        val id: Long,
        val startMinutes: Int,
        val endMinutes: Int,
        val mode: ScheduleMode,
        val packageNames: Set<String>
    )
    private var parsedSchedulesCache = listOf<ParsedSchedule>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SEND_TEST_NOTIFICATION") {
            sendTestNotification()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        serviceScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShieldsCache = shields
                allShieldsMap = shields.associateBy { it.packageName }
                goalShieldsCache = shields.filter { 
                    it.type == FocusType.GOAL && it.goalReminderPeriodMinutes > 0 
                }
            }
        }

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
            val preferences = preferencesRepository.userPreferencesFlow.first()
            currentPreferences = preferences
            whitelistedPackages = preferences.whitelistedPackages
            lastResetDate = preferences.lastResetDate
            
            val now = System.currentTimeMillis()
            
            // Check if day changed while phone was off
            val currentDate = getCurrentDateString(now)
            if (lastResetDate.isNotEmpty() && lastResetDate != currentDate) {
                updateStreaks()
                notifiedGoals.clear()
                preferencesRepository.setLastResetDate(currentDate)
                lastResetDate = currentDate
            } else if (lastResetDate.isEmpty()) {
                preferencesRepository.setLastResetDate(currentDate)
                lastResetDate = currentDate
            }

            nextDayTimestamp = calculateNextDayTimestamp(now)
            startOfDayTimestamp = calculateStartOfDayTimestamp(now)

            startMonitoring()
            startGoalReminderCheck()

            preferencesRepository.userPreferencesFlow.collect { newPreferences ->
                currentPreferences = newPreferences
                whitelistedPackages = newPreferences.whitelistedPackages
                lastResetDate = newPreferences.lastResetDate
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        createGoalNotificationChannel()
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        registerReceiver(screenStateReceiver, filter)
        isScreenOn = powerManager.isInteractive
        isPowerSaveMode = powerManager.isPowerSaveMode
    }

    private fun startGoalReminderCheck() {
        serviceScope.launch {
            while (true) {
                if (!isScreenOn) {
                    delay(60000)
                    continue
                }

                val currentTime = System.currentTimeMillis()
                val goals = goalShieldsCache

                if (goals.isNotEmpty()) {
                    // OPTIMASI: Ambil semua statistik penggunaan hari ini satu kali saja (Map O(1) lookup)
                    val allUsage = getAllUsageToday()
                    
                    goals.forEach { goal ->
                        if (goal.packageName == lastForegroundApp) return@forEach
                        if (isPaused(goal)) return@forEach
                        
                        val usageToday = allUsage[goal.packageName] ?: 0L
                        val limitMillis = goal.timeLimitMinutes * 60 * 1000L
                        if (usageToday >= limitMillis) return@forEach

                        val periodMillis = goal.goalReminderPeriodMinutes * 60 * 1000L
                        if (periodMillis > 0 && currentTime - goal.lastGoalReminderTimestamp >= periodMillis) {
                            sendGoalSuggestionNotification(goal)
                            shieldRepository.updateShield(goal.copy(lastGoalReminderTimestamp = currentTime))
                        }
                    }
                }
                delay(60000)
            }
        }
    }

    private fun sendGoalSuggestionNotification(goal: ShieldEntity) {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(NotificationManager::class.java)
        
        val intent = packageManager.getLaunchIntentForPackage(goal.packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            goal.packageName.hashCode(), 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconBitmap = try {
            val drawable = packageManager.getApplicationIcon(goal.packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Time for ${goal.appName}?")
            .setContentText("Your goal setting suggests it's time to open ${goal.appName} and make some progress!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(iconBitmap)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(goal.packageName.hashCode() + 1000, notification)
    }

    private fun createGoalNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "zenith_goal_channel"
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId, "Goal Reminders", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies you when you reach your app usage goals"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private var lastCheckedDay = -1
    private val notifiedGoals = mutableSetOf<String>() // Simpan package goal yang sudah dikirim notif hari ini

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                if (!isScreenOn) {
                    delay(5000)
                    continue
                }

                if (ZenithAccessibilityService.isServiceRunning) {
                    currentShieldCache = null
                    usageTimesMapCache = null
                    lastUsageFetchTime = 0L
                    lastForegroundApp = null
                    delay(3000) 
                    continue
                }

                val currentApp = getForegroundApp(lastPollingInterval + 2000)
                val currentTime = System.currentTimeMillis()

                if (currentApp != null && currentApp != packageName) {
                    val isSameApp = currentApp == lastForegroundApp
                    
                    if (!isSameApp) {
                        currentShieldCache = allShieldsMap[currentApp]
                        
                        baseUsageAtSessionStart = getTotalUsageToday(currentApp)
                        sessionStartTime = currentTime
                        cachedTotalUsage = baseUsageAtSessionStart
                        lastUsageFetchTime = currentTime
                        
                        lastBypassPackage = currentApp
                        lastBypassResult = shouldBypassBlocking(currentApp)
                        
                        sessionUsageOverlayManager.updateForegroundApp(currentApp)
                        
                        if (!lastBypassResult) {
                            val allowedUntil = allowedApps[currentApp] ?: 0L
                            if (currentTime > allowedUntil) {
                                checkIfAppIsShielded(currentApp, baseUsageAtSessionStart)
                            }
                        }
                    }

                    if (lastBypassResult) {
                        lastForegroundApp = currentApp
                        delay(if (isPowerSaveMode) 2000L else 1200L)
                        continue
                    }

                    val shield = currentShieldCache
                    val isAppPaused = shield != null && isPaused(shield)
                    val allowedUntil = allowedApps[currentApp] ?: 0L

                    updateUsageTime(currentApp)

                    if (currentTime - lastScheduleCheckTime > 2500) {
                        if (!lastBypassResult && checkSchedules(currentApp, currentTime)) {
                            lastForegroundApp = currentApp
                            lastScheduleCheckTime = currentTime
                            delay(1000)
                            continue
                        }
                        lastScheduleCheckTime = currentTime
                        checkDayChange(currentTime)
                    }

                    if (!isAppPaused && allowedUntil > currentTime) {
                        val prefs = currentPreferences
                        if (prefs != null && prefs.sessionUsageOverlayEnabled) {
                            val isGoal = shield?.type == FocusType.GOAL

                            if (isGoal && cachedTotalUsage >= (shield.timeLimitMinutes * 60 * 1000L)) {
                                // Goal tercapai
                            } else if (!isSameApp) {
                                val remainingMinutes = ((allowedUntil - currentTime) / 60000L).toInt().coerceAtLeast(1)
                                val duration = if (isGoal) shield?.timeLimitMinutes ?: 0 else remainingMinutes
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        currentApp,
                                        duration,
                                        prefs.sessionUsageOverlaySize,
                                        prefs.sessionUsageOverlayOpacity,
                                        isGoal = isGoal,
                                        onSessionEnd = {
                                            allowedApps[currentApp] = 0L
                                            serviceScope.launch {
                                                val s = allShieldsCache.find { it.packageName == currentApp }
                                                if (s != null) {
                                                    val updated = s.copy(lastSessionEndTimestamp = System.currentTimeMillis())
                                                    shieldRepository.updateShield(updated)
                                                    currentShieldCache = updated
                                                }
                                                checkIfAppIsShielded(currentApp, cachedTotalUsage)
                                            }
                                        }
                                    )
                                    if (isGoal) {
                                        sessionUsageOverlayManager.updateHUDUsage(currentApp, cachedTotalUsage)
                                    }
                                }
                            }
                        }
                    }

                    if (!isAppPaused && currentTime > allowedUntil && !InterceptOverlayManager.isShowing) {
                        if (shield != null) {
                            if (shield.isAutoQuitEnabled && allowedUntil > 0) {
                                goToHomeScreen()
                                allowedApps.remove(currentApp)
                            } else if (allowedUntil > 0) {
                                checkIfAppIsShielded(currentApp, cachedTotalUsage)
                                allowedApps[currentApp] = 0L
                            }
                        }
                    }
                } else if (currentApp == null || currentApp == packageName) {
                    currentShieldCache = null
                    if (currentApp == packageName) sessionUsageOverlayManager.updateForegroundApp(packageName)
                }
                
                lastForegroundApp = currentApp
                
                lastPollingInterval = when {
                    isPowerSaveMode -> 8000L
                    currentApp == packageName -> 7000L
                    lastBypassResult -> 10000L // Hibernasi 10 detik jika di-whitelist (Sangat Ringan)
                    currentShieldCache == null -> 7000L 
                    else -> {
                        val shield = currentShieldCache
                        val remaining = shield?.remainingTimeMillis ?: 0L
                        when {
                            remaining > 3600000 -> 8000L  // > 1 jam: Cek setiap 8 detik
                            remaining > 600000 -> 5000L  // > 30 menit: Cek setiap 5 detik
                            remaining > 300000 -> 3000L   // > 10 menit
                            remaining > 60000 -> 1500L    // > 1 menit
                            else -> 1000L                 // < 1 menit (tetap aman)
                        }
                    }
                }
                delay(lastPollingInterval)
            }
        }
    }

    private suspend fun checkDayChange(currentTime: Long) {
        val currentDate = getCurrentDateString(currentTime)
        
        if (lastResetDate.isNotEmpty() && currentDate != lastResetDate) {
            updateStreaks()
            notifiedGoals.clear()
            
            preferencesRepository.setLastResetDate(currentDate)
            lastResetDate = currentDate
            
            nextDayTimestamp = calculateNextDayTimestamp(currentTime)
            startOfDayTimestamp = calculateStartOfDayTimestamp(currentTime)
        } else if (lastResetDate.isEmpty()) {
            preferencesRepository.setLastResetDate(currentDate)
            lastResetDate = currentDate
        }
    }

    private fun getCurrentDateString(time: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }

    private fun calculateStartOfDayTimestamp(currentTime: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = currentTime
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun calculateNextDayTimestamp(currentTime: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = currentTime
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private suspend fun updateUsageTime(packageName: String) {
        val shield = currentShieldCache ?: return
        val currentTime = System.currentTimeMillis()

        val sessionElapsed = currentTime - sessionStartTime
        val currentTotalUsage = baseUsageAtSessionStart + sessionElapsed
        cachedTotalUsage = currentTotalUsage

        // Adaptive HUD Update: Lebih jarang jika sisa waktu masih banyak
        if (shield.type == FocusType.GOAL) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val remaining = (limitMillis - currentTotalUsage).coerceAtLeast(0L)
            val uiUpdateInterval = when {
                remaining < 60000 -> 1000L // Setiap detik jika < 1 menit
                remaining < 300000 -> 2500L // Setiap 2.5 detik jika < 5 menit
                else -> 5000L // Setiap 5 detik jika masih lama
            }

            if (currentTime - lastUsageFetchTime >= uiUpdateInterval) {
                withContext(Dispatchers.Main) {
                    sessionUsageOverlayManager.updateHUDUsage(packageName, currentTotalUsage)
                }
                lastUsageFetchTime = currentTime
            }
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - currentTotalUsage).coerceAtLeast(0L)
        
        // Strategi Adaptive Write: Update DB setiap 30 detik atau jika sisa waktu < 1 menit
        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000 
        val shouldUpdateDB = timeSinceLastUsed > 30000 || (isNearLimit && timeSinceLastUsed > 5000)

        if (shouldUpdateDB) {
            val finalShield = shield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            shieldRepository.updateShield(finalShield)
            currentShieldCache = finalShield
            
            if (shield.type == FocusType.GOAL && !isPaused(shield)) {
                if (currentTotalUsage >= limitMillis && !notifiedGoals.contains(packageName)) {
                    sendGoalReachedNotification(shield.appName, packageName)
                    notifiedGoals.add(packageName)
                }
            }
        }
    }

    private fun sendGoalReachedNotification(appName: String, packageName: String) {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Goal Achieved! 🎯")
            .setContentText("You've reached your target usage for $appName. Keep it up!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(packageName.hashCode(), notification)
    }

    fun sendTestNotification() {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test Notification ✅")
            .setContentText("Notifications are working correctly!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(999, notification)
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String, usageToday: Long? = null) {
        val totalUsageToday = usageToday ?: getTotalUsageToday(targetPackageName)
        
        val shield = currentShieldCache ?: allShieldsMap[targetPackageName]
        if (shield != null && !InterceptOverlayManager.isShowing) {
            val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
            val delayDurationSeconds = prefs.delayAppDurationSeconds
            
            val currentTime = System.currentTimeMillis()
            val lastAction = shield.lastDelayStartTimestamp
            
            // Cek Grace Period: Jika user sudah tidak memakai aplikasi lebih dari 30 menit,
            // maka untuk pembukaan pertama kali ini delay tidak akan muncul.
            val lastSessionEnd = shield.lastSessionEndTimestamp
            val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd > 30 * 60 * 1000L)

            val shieldWithTimestamp = if (shield.isDelayAppEnabled) {
                if (isGracePeriodActive) {
                    // Beri izin langsung tanpa delay untuk pembukaan pertama setelah 30 menit
                    shield.copy(lastDelayStartTimestamp = currentTime - (delayDurationSeconds * 1000L) - 1000)
                } else if (lastAction == 0L) {
                    // Jika baru pertama kali butuh delay, set timestamp SEKARANG
                    val updated = shield.copy(lastDelayStartTimestamp = currentTime)
                    serviceScope.launch { shieldRepository.updateShield(updated) }
                    updated
                } else {
                    // Jika delay sudah ada (baik masih jalan atau sudah selesai), 
                    // JANGAN update ke currentTime agar hitungan waktu tetap berjalan maju dari titik awal.
                    shield
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
                                val currentTime = System.currentTimeMillis()
                                serviceScope.launch {
                                    val currentShield = shieldRepository.getShieldByPackageName(targetPackageName) ?: return@launch
                                    val updatedShield = if (isEmergency) {
                                        val isFirstChargeUsed = currentShield.emergencyUseCount == currentShield.maxEmergencyUses
                                        currentShield.copy(
                                            emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                            lastEmergencyRechargeTimestamp = if (isFirstChargeUsed) System.currentTimeMillis() else currentShield.lastEmergencyRechargeTimestamp,
                                            lastDelayStartTimestamp = 0L,
                                            lastSessionEndTimestamp = currentTime
                                        )
                                    } else {
                                        val periodExpired = System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                        currentShield.copy(
                                            currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                            lastPeriodResetTimestamp = if (periodExpired) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp,
                                            lastDelayStartTimestamp = 0L,
                                            lastSessionEndTimestamp = currentTime
                                        )
                                    }
                                    shieldRepository.updateShield(updatedShield)
                                    currentShieldCache = updatedShield
                                    allowedApps[targetPackageName] = currentTime + (minutes * 60 * 1000L)
                            
                            val prefs = preferencesRepository.userPreferencesFlow.first()
                            if (prefs.sessionUsageOverlayEnabled) {
                                val isGoal = updatedShield.type == FocusType.GOAL
                                val limitMillis = updatedShield.timeLimitMinutes * 60 * 1000L
                                val currentUsage = getTotalUsageToday(targetPackageName)

                                if (isGoal && currentUsage >= limitMillis && limitMillis > 0) {
                                    // Goal sudah tercapai, tidak perlu tampilkan HUD
                                } else {
                                    val duration = if (isGoal) updatedShield.timeLimitMinutes else minutes

                                    serviceScope.launch(Dispatchers.Main) {
                                        sessionUsageOverlayManager.showHUD(
                                            targetPackageName,
                                            duration,
                                            prefs.sessionUsageOverlaySize,
                                            prefs.sessionUsageOverlayOpacity,
                                            isGoal = isGoal,
                                            onSessionEnd = {
                                                allowedApps[targetPackageName] = 0L
                                                serviceScope.launch {
                                                    checkIfAppIsShielded(targetPackageName)
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
        val shields = allShieldsCache
        if (shields.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = currentTime
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis
        
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        val endTime = cal.timeInMillis
        
        val usageMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

        shields.forEach { shield ->
            val stats = usageMap[shield.packageName]
            val totalUsageToday = stats?.totalTimeVisible ?: 0L
            
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            
            // Paused apps are exempt from streak reset if mereka gagal memenuhi target,
            // tapi tetep dapet streak kalau berhasil memenuhi target? 
            // Sesuai requirement: "exempt from intercept overlays while still having their usage tracked".
            // Biasanya pause berarti "libur", jadi streak harusnya dipertahankan atau tidak diproses.
            // Namun agar simple dan tetap fair, kita proses saja usage-nya seperti biasa.
            // Jika user ingin streak aman saat libur, itu logic yang lebih kompleks.
            // Untuk sekarang, kita ikuti logic: tetap track usage.
            
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
        return getUsageTimesMap()?.get(packageName) ?: 0L
    }

    private fun getUsageTimesMap(): Map<String, Long>? {
        val currentTime = System.currentTimeMillis()
        if (usageTimesMapCache != null && currentTime - lastUsageCacheTime < 10000) { // 10 detik cache
            return usageTimesMapCache
        }

        val startTime = if (startOfDayTimestamp > 0) startOfDayTimestamp else calculateStartOfDayTimestamp(currentTime)

        usageTimesMapCache = try {
            val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, currentTime)
            // Memori Optimization: Hanya simpan waktu (Long), buang objek UsageStats yang berat
            stats.mapValues { it.value.totalTimeVisible }
        } catch (_: Exception) { null }
        
        lastUsageCacheTime = currentTime
        return usageTimesMapCache
    }

    private fun getAllUsageToday(): Map<String, Long> {
        return getUsageTimesMap() ?: emptyMap()
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun checkSchedules(packageName: String, now: Long): Boolean {
        // Gunakan math untuk hitung menit, lebih cepat dari Calendar
        val offset = java.util.TimeZone.getDefault().getOffset(now)
        val currentTotalMinutes = (((now + offset) / 60000) % 1440).toInt()
        
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

    private fun isPaused(shield: ShieldEntity): Boolean {
        if (!shield.isPaused) return false
        if (shield.pauseEndTimestamp == 0L) return true // Pause selamanya
        return System.currentTimeMillis() < shield.pauseEndTimestamp // Belum melewati batas waktu pause
    }

    private fun isLauncherOrSystemHome(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (launcherPackages.isEmpty() || now - lastLauncherRefreshTime > 300000) { // Refresh setiap 5 menit
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val launchers = packageManager.queryIntentActivities(homeIntent, android.content.pm.PackageManager.MATCH_ALL)
                launcherPackages = launchers.map { it.activityInfo.packageName }.toSet()
                lastLauncherRefreshTime = now
            } catch (_: Exception) {}
        }
        
        if (launcherPackages.contains(packageName)) return true

        return launcherAppCache[packageName] ?: run {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val isSystem = (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                
                val isHomeRelated = isSystem && (packageName.contains("launcher", ignoreCase = true) || 
                                   packageName.contains("home", ignoreCase = true) ||
                                   packageName.contains("car.mode", ignoreCase = true))
                
                launcherAppCache[packageName] = isHomeRelated
                isHomeRelated
            } catch (_: Exception) { false }
        }
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName in whitelistedPackages) return true
        if (isLauncherOrSystemHome(packageName)) return true
        if (packageName in CRITICAL_SYSTEM_PACKAGES) return true

        try {
            val isSystem = systemAppCache[packageName] ?: run {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val sys = (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                systemAppCache[packageName] = sys
                sys
            }
            
            if (isSystem) {
                return packageName !in BLOCKABLE_SYSTEM_APPS
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
                                    sessionUsageOverlayManager.showHUD(
                                        packageName,
                                        minutes,
                                        prefs.sessionUsageOverlaySize,
                                        prefs.sessionUsageOverlayOpacity,
                                        onSessionEnd = {
                                            allowedApps[packageName] = 0L
                                            serviceScope.launch {
                                                checkIfAppIsShielded(packageName)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                onCloseApp = { goToHomeScreen() }
            )
        }
    }

    private fun getForegroundApp(lookbackMillis: Long): String? {
        val time = System.currentTimeMillis()

        // FAST-PATH UNTUK SCROLLING:
        // queryUsageStats jauh lebih ringan daripada queryEvents karena tidak mengembalikan 
        // ribuan event interaksi (tipe 7) yang terjadi saat user scrolling.
        if (lastForegroundApp != null) {
            val stats = try {
                usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 3000, time)
            } catch (_: Exception) { null }

            if (!stats.isNullOrEmpty()) {
                val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
                // Jika aplikasi terakhir masih sama dan baru saja digunakan (< 3 detik lalu),
                // asumsikan belum ada perpindahan aplikasi. Langsung return untuk hemat CPU.
                if (mostRecent != null && mostRecent.packageName == lastForegroundApp && (time - mostRecent.lastTimeUsed) < 3000) {
                    return lastForegroundApp
                }
            }
        }

        val usageEvents = try {
            // Window dinamis mengikuti polling interval untuk efisiensi CPU maksimal
            usageStatsManager.queryEvents(time - lookbackMillis, time)
        } catch (_: Exception) { null } ?: return lastForegroundApp
        
        var lastPackage: String? = null
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(reusableEvent)
            // Hanya proses MOVE_TO_FOREGROUND (1). 
            // Abaikan USER_INTERACTION (7) yang bisa berjumlah ratusan per detik saat scrolling.
            if (reusableEvent.eventType == 1) {
                lastPackage = reusableEvent.packageName
            }
        }
        
        return lastPackage ?: lastForegroundApp
    }

    override fun onLowMemory() {
        super.onLowMemory()
        onTrimMemory(TRIM_MEMORY_COMPLETE)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
            allowedApps.clear()
            systemAppCache.clear()
            launcherPackages = emptySet()
            usageTimesMapCache = null
            lastUsageCacheTime = 0L
            lastLauncherRefreshTime = 0L
            try {
                ZenithDatabase.getDatabase(this).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
            } catch (_: Exception) {}
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
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        serviceJob.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private val CRITICAL_SYSTEM_PACKAGES = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.server.telecom", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller"
        )
        private val BLOCKABLE_SYSTEM_APPS = setOf(
            "com.google.android.youtube", "com.android.chrome",
            "com.google.android.apps.youtube.music", "com.android.vending"
        )
    }
}
