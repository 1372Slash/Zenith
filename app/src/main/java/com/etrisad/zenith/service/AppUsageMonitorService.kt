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
    private val reusableCalendar = java.util.Calendar.getInstance()
    private var lastForegroundApp: String? = null
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private var sessionStartTime = 0L
    private var baseUsageAtSessionStart = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<com.etrisad.zenith.data.local.entity.ScheduleEntity>()
    private var whitelistedPackages = emptySet<String>()
    private var launcherPackages = emptySet<String>()
    private val systemAppCache = mutableMapOf<String, Boolean>()
    private var lastLauncherRefreshTime = 0L
    private var currentPreferences: UserPreferences? = null
    private var allShieldsCache = listOf<ShieldEntity>()
    private var goalShieldsCache = listOf<ShieldEntity>()
    private var lastCheckedDayTimestamp = 0L
    private var isScreenOn = true
    private var isPowerSaveMode = false

    private var lastHUDUpdateTime = 0L
    private val HUD_UPDATE_INTERVAL = 1000L
    private val dailyUsageCache = mutableMapOf<String, Long>()
    private var cachedStartOfDay = 0L
    private var lastStartOfDayDate = -1

    // Global usage cache to prevent multiple system calls in one cycle
    private var usageStatsCache: List<android.app.usage.UsageStats>? = null
    private var lastUsageCacheTime = 0L

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    currentShieldCache = null
                    usageStatsCache = null
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
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                currentPreferences = preferences
                whitelistedPackages = preferences.whitelistedPackages
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

        serviceScope.launch {
            currentPreferences = preferencesRepository.userPreferencesFlow.first()
            startMonitoring()
        }
        startGoalReminderCheck()
    }

    private fun startGoalReminderCheck() {
        serviceScope.launch {
            while (true) {
                val currentTime = System.currentTimeMillis()
                // Gunakan cache terbaru dari database
                val goals = goalShieldsCache

                if (goals.isNotEmpty()) {
                    goals.forEach { goal ->
                        // Jangan ingatkan jika aplikasi sedang dibuka (lastForegroundApp adalah aplikasi ini)
                        if (goal.packageName == lastForegroundApp) return@forEach
                        
                        // Jangan ingatkan jika sedang di-pause
                        if (isPaused(goal)) return@forEach
                        
                        // Jangan ingatkan jika goal hari ini sudah tercapai
                        val usageToday = dailyUsageCache[goal.packageName] ?: getTotalUsageToday(goal.packageName)
                        val limitMillis = goal.timeLimitMinutes * 60 * 1000L
                        if (usageToday >= limitMillis) return@forEach

                        val periodMillis = goal.goalReminderPeriodMinutes * 60 * 1000L
                        if (periodMillis > 0 && currentTime - goal.lastGoalReminderTimestamp >= periodMillis) {
                            sendGoalSuggestionNotification(goal)
                            // Update timestamp agar tidak muncul berulang kali sebelum interval berikutnya
                            shieldRepository.updateShield(goal.copy(lastGoalReminderTimestamp = currentTime))
                        }
                    }
                }
                
                // Cek setiap 60 detik untuk akurasi yang lebih baik
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
            // Initial streak and reset check upon startup
            checkDayChangeOnStartup()

            while (true) {
                // 1. Matikan monitoring jika layar mati untuk menghemat CPU & baterai
                if (!isScreenOn) {
                    delay(5000)
                    continue
                }

                val isAccessibilityActive = ZenithAccessibilityService.isServiceRunning
                
                // Adaptive Polling Logic
                if (isAccessibilityActive) {
                    // Jika Accessibility aktif, polling ini melambat drastis (3 detik)
                    // karena Accessibility sudah menghandle deteksi instan via event.
                    // Ini menghemat baterai secara signifikan tanpa mengurangi performa.
                    currentShieldCache = null
                    usageStatsCache = null
                    lastUsageFetchTime = 0L
                    delay(3000) 
                    continue
                }

                val currentApp = getForegroundApp()
                val currentTime = System.currentTimeMillis()

                if (currentApp != null) {
                    overlayManager.checkAndHide(currentApp)
                }

                // Refresh launcher packages every 60s
                if (launcherPackages.isEmpty() || currentTime - lastLauncherRefreshTime > 60000) {
                    try {
                        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        val launchers = packageManager.queryIntentActivities(homeIntent, android.content.pm.PackageManager.MATCH_ALL)
                        launcherPackages = launchers.map { it.activityInfo.packageName }.toSet()
                        lastLauncherRefreshTime = currentTime
                    } catch (_: Exception) {}
                }

                // Check for day change efficiently
                if (currentTime - lastCheckedDayTimestamp > 60000) {
                    reusableCalendar.timeInMillis = currentTime
                    val currentDay = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
                    if (lastCheckedDay != -1 && currentDay != lastCheckedDay) {
                        updateStreaks()
                        shieldRepository.resetAllRemainingTimes()
                        notifiedGoals.clear()
                        dailyUsageCache.clear()
                        // Reset usage caches and force refresh on day change
                        usageStatsCache = null
                        lastUsageCacheTime = 0L
                        lastUsageFetchTime = 0L
                        cachedTotalUsage = 0L
                        currentShieldCache = null
                    }
                    lastCheckedDay = currentDay
                    lastCheckedDayTimestamp = currentTime
                }

                if (currentApp != null && currentApp != packageName) {
                    sessionUsageOverlayManager.updateForegroundApp(currentApp)
                    
                    if (currentApp != lastForegroundApp || currentShieldCache == null) {
                        currentShieldCache = allShieldsCache.find { it.packageName == currentApp }
                        lastUsageFetchTime = 0L 
                        sessionStartTime = currentTime
                        
                        val systemUsage = getTotalUsageToday(currentApp)
                        val startOfDay = getStartOfDay()
                        val shield = currentShieldCache
                        val dbUsage = if (shield != null && shield.lastUsedTimestamp >= startOfDay) {
                            val limitMillis = (shield.timeLimitMinutes * 60 * 1000L)
                            (limitMillis - shield.remainingTimeMillis).coerceAtLeast(0L)
                        } else 0L
                        
                        baseUsageAtSessionStart = maxOf(systemUsage, dbUsage)
                        cachedTotalUsage = baseUsageAtSessionStart
                    }

                    if (shouldBypassBlocking(currentApp)) {
                        lastForegroundApp = currentApp
                        delay(if (isPowerSaveMode) 2000L else 1200L)
                        continue
                    }

                    val allowedUntil = allowedApps[currentApp] ?: 0L
                    updateUsageTime(currentApp)
                    
                    // Jika aplikasi di-pause, jangan tampilkan HUD atau Intercept Overlay
                    val shieldForPauseCheck = currentShieldCache
                    val isAppPaused = shieldForPauseCheck != null && isPaused(shieldForPauseCheck)

                    if (!isAppPaused && allowedUntil > currentTime) {
                        val prefs = currentPreferences ?: return@launch
                        if (prefs.sessionUsageOverlayEnabled) {
                            val remainingMinutes = ((allowedUntil - currentTime) / 60000L).toInt().coerceAtLeast(1)
                            val shield = currentShieldCache
                            val isGoal = shield?.type == FocusType.GOAL

                            // Jika goal sudah tercapai hari ini, jangan tampilkan HUD
                            val limitMillis = (shield?.timeLimitMinutes ?: 0) * 60 * 1000L
                            if (isGoal && cachedTotalUsage >= limitMillis && limitMillis > 0) {
                                // Lanjutkan monitoring tanpa menampilkan HUD
                            } else {
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

                                                    if (updated.isAutoQuitEnabled) {
                                                        goToHomeScreen()
                                                    } else {
                                                        checkIfAppIsShielded(currentApp)
                                                    }
                                                } else {
                                                    checkIfAppIsShielded(currentApp)
                                                }
                                            }
                                        }
                                    )
                                    if (isGoal && currentTime - lastHUDUpdateTime > HUD_UPDATE_INTERVAL) {
                                        lastHUDUpdateTime = currentTime
                                        sessionUsageOverlayManager.updateHUDUsage(currentApp, cachedTotalUsage)
                                    }
                                }
                            }
                        }
                    }

                    if (!isAppPaused && currentTime > allowedUntil && !InterceptOverlayManager.isShowing) {
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
                                    serviceScope.launch {
                                        shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L))
                                    }
                                    currentShieldCache = currentShieldCache?.copy(lastDelayStartTimestamp = 0L)
                                }
                            } else if (currentApp != lastForegroundApp || allowedUntil > 0) {
                                checkIfAppIsShielded(currentApp)
                                if (allowedUntil > 0) allowedApps[currentApp] = 0L
                            }
                        }
                    }
                } else if (currentApp == null || currentApp == packageName) {
                    currentShieldCache = null
                }
                
                lastForegroundApp = currentApp
                
                // Adaptive Delay: Tetap responsif jika tanpa Accessibility
                val delayTime = when {
                    isPowerSaveMode -> 1500L
                    currentShieldCache != null -> 600L  // lebih agresif hanya saat ada shield aktif
                    else -> 1200L                        // lebih lambat saat idle
                }
                delay(delayTime)
            }
        }
    }

    private suspend fun updateUsageTime(packageName: String) {
        val shield = currentShieldCache ?: return
        val currentTime = System.currentTimeMillis()

        val sessionElapsed = currentTime - sessionStartTime
        val currentTotalUsage = baseUsageAtSessionStart + sessionElapsed
        cachedTotalUsage = currentTotalUsage

        // Sync with HUD for Goal-type apps every 2 seconds (or 1s if near limit)
        if (shield.type == FocusType.GOAL) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val remaining = (limitMillis - currentTotalUsage).coerceAtLeast(0L)
            val uiUpdateInterval = if (remaining < 60000) 1000L else 2000L

            if (currentTime - lastHUDUpdateTime > uiUpdateInterval) {
                lastHUDUpdateTime = currentTime
                serviceScope.launch(Dispatchers.Main) {
                    sessionUsageOverlayManager.updateHUDUsage(packageName, currentTotalUsage)
                }
            }
        }

        // Periodic system sync every 30 seconds to keep base usage anchored
        if (currentTime - lastUsageFetchTime > 30000) {
            val systemUsage = getTotalUsageToday(packageName)
            val startOfDay = getStartOfDay()
            
            val dbUsage = if (shield.lastUsedTimestamp >= startOfDay) {
                val limitMillis = (shield.timeLimitMinutes * 60 * 1000L)
                (limitMillis - shield.remainingTimeMillis).coerceAtLeast(0L)
            } else 0L
            
            baseUsageAtSessionStart = maxOf(systemUsage, dbUsage) - (currentTime - sessionStartTime)
            lastUsageFetchTime = currentTime
        }

        val prefs = currentPreferences ?: return
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
        
        // Strategi Adaptive Write:
        // Update DB setiap 10 detik, ATAU jika sisa waktu < 1 menit (agar blokir sigap)
        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000 
        val shouldUpdateDB = timeSinceLastUsed > 10000 || (isNearLimit && timeSinceLastUsed > 2000) || updatedShield != shield

        if (shouldUpdateDB) {
            val finalShield = updatedShield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                // PENTING: Update lastGoalReminderTimestamp saat aplikasi digunakan
                // agar interval pengingat dihitung sejak aplikasi terakhir ditutup.
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            shieldRepository.updateShield(finalShield)
            currentShieldCache = finalShield
            
            // CEK GOAL REMINDER
            if (shield.type == FocusType.GOAL && !isPaused(shield)) {
                if (cachedTotalUsage >= limitMillis && !notifiedGoals.contains(packageName)) {
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

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        // Pastikan aplikasi yang akan diblokir benar-benar sedang di foreground
        val currentForeground = getForegroundApp()
        if (targetPackageName != currentForeground) return

        val shield = currentShieldCache ?: allShieldsCache.find { it.packageName == targetPackageName }
        val prefs = currentPreferences ?: return
        if (shield != null && !InterceptOverlayManager.isShowing) {
            val totalUsageToday = getTotalUsageToday(targetPackageName)
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
                            
                            val currentPrefs = currentPreferences ?: return@launch
                            if (currentPrefs.sessionUsageOverlayEnabled) {
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
                                            currentPrefs.sessionUsageOverlaySize,
                                            currentPrefs.sessionUsageOverlayOpacity,
                                            isGoal = isGoal,
                                            onSessionEnd = {
                                                allowedApps[targetPackageName] = 0L
                                                serviceScope.launch {
                                                    val shield = currentShieldCache ?: shieldRepository.getShieldByPackageName(targetPackageName)
                                                    if (shield?.isAutoQuitEnabled == true) {
                                                        goToHomeScreen()
                                                    } else {
                                                        checkIfAppIsShielded(targetPackageName)
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

    private suspend fun checkDayChangeOnStartup() {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val lastCheckStr = prefs.lastStreakCheckDate
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayStr = dateFormat.format(java.util.Date())

        if (lastCheckStr.isNotEmpty() && lastCheckStr != todayStr) {
            // Day has changed since last time service was running
            updateStreaks()
            shieldRepository.resetAllRemainingTimes()
            notifiedGoals.clear()
            dailyUsageCache.clear()
        }
        
        if (lastCheckStr != todayStr) {
            preferencesRepository.setLastStreakCheckDate(todayStr)
        }
        
        reusableCalendar.timeInMillis = System.currentTimeMillis()
        lastCheckedDay = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private suspend fun updateStreaks() {
        val shields = shieldRepository.allShields.first()
        if (shields.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val todayCal = java.util.Calendar.getInstance().apply { timeInMillis = currentTime }
        
        // Evaluasi penggunaan kemarin untuk menentukan streak
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
            // SAFETY: Jangan update jika sudah diupdate pada hari kalender yang sama
            val lastUpdateCal = java.util.Calendar.getInstance().apply { 
                timeInMillis = shield.lastStreakUpdateTimestamp 
            }
            
            val isAlreadyUpdatedToday = lastUpdateCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                    lastUpdateCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
            
            if (isAlreadyUpdatedToday) return@forEach

            val stats = usageMap[shield.packageName]
            val totalUsageYesterday = stats?.let { 
                it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) 
            } ?: 0L

            val limitMillis = shield.timeLimitMinutes * 60 * 1000L

            var shouldIncrement = false
            if (shield.type == FocusType.GOAL) {
                if (totalUsageYesterday >= limitMillis && limitMillis > 0) {
                    shouldIncrement = true
                }
            } else {
                if (totalUsageYesterday <= limitMillis) {
                    shouldIncrement = true
                }
            }

            if (shouldIncrement) {
                val newStreak = shield.currentStreak + 1
                val newBest = maxOf(shield.bestStreak, newStreak)
                shieldRepository.updateShield(shield.copy(
                    currentStreak = newStreak,
                    bestStreak = newBest,
                    lastStreakUpdateTimestamp = currentTime
                ))
            } else {
                shieldRepository.updateShield(shield.copy(
                    currentStreak = 0,
                    lastStreakUpdateTimestamp = currentTime
                ))
            }
        }
        
        // Update preference date after successful update
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        preferencesRepository.setLastStreakCheckDate(dateFormat.format(java.util.Date(currentTime)))
    }

    private fun getTotalUsageToday(packageName: String): Long {
        getUsageStatsList() // Ensures dailyUsageCache is fresh
        return dailyUsageCache[packageName] ?: 0L
    }

    private fun getUsageStatsList(): List<android.app.usage.UsageStats>? {
        val currentTime = System.currentTimeMillis()
        // Cache usage stats list for 3 seconds to avoid redundant system calls
        if (usageStatsCache != null && currentTime - lastUsageCacheTime < 3000) {
            return usageStatsCache
        }

        val startTime = getStartOfDay()

        // Menggunakan queryEvents untuk menghitung penggunaan secara manual lebih akurat daripada queryUsageStats(INTERVAL_DAILY)
        // Hal ini karena INTERVAL_DAILY seringkali mengalami bug reset lebih awal di sekitar jam 23:00-00:00 (tergantung sistem Android)
        try {
            val events = usageStatsManager.queryEvents(startTime, currentTime)
            val event = android.app.usage.UsageEvents.Event()
            val tempUsageMap = mutableMapOf<String, Long>()
            val lastAppOpenTime = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue

                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastAppOpenTime[pkg] = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val openTime = lastAppOpenTime[pkg]
                        if (openTime != null) {
                            val duration = event.timeStamp - openTime
                            if (duration > 0) {
                                tempUsageMap[pkg] = (tempUsageMap[pkg] ?: 0L) + duration
                            }
                            lastAppOpenTime.remove(pkg)
                        }
                    }
                }
            }

            // Tambahkan durasi untuk aplikasi yang masih di foreground saat ini
            lastAppOpenTime.forEach { (pkg, openTime) ->
                val duration = currentTime - openTime
                if (duration > 0) {
                    tempUsageMap[pkg] = (tempUsageMap[pkg] ?: 0L) + duration
                }
            }

            // Update dailyUsageCache dengan data hasil perhitungan manual yang lebih stabil
            tempUsageMap.forEach { (pkg, usage) ->
                dailyUsageCache[pkg] = usage
            }
            
            // Tetap panggil queryUsageStats hanya untuk memenuhi return type jika diperlukan fungsi lain,
            // namun dailyUsageCache sudah diisi oleh data yang lebih akurat di atas.
            usageStatsCache = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
        } catch (e: Exception) {
            usageStatsCache = null
        }

        lastUsageCacheTime = currentTime
        return usageStatsCache
    }

    private fun getStartOfDay(): Long {
        val currentTime = System.currentTimeMillis()
        reusableCalendar.timeInMillis = currentTime
        val today = reusableCalendar.get(java.util.Calendar.DAY_OF_YEAR)
        if (today != lastStartOfDayDate) {
            reusableCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            reusableCalendar.set(java.util.Calendar.MINUTE, 0)
            reusableCalendar.set(java.util.Calendar.SECOND, 0)
            reusableCalendar.set(java.util.Calendar.MILLISECOND, 0)
            cachedStartOfDay = reusableCalendar.timeInMillis
            lastStartOfDayDate = today
        }
        return cachedStartOfDay
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

    private fun isPaused(shield: ShieldEntity): Boolean {
        if (!shield.isPaused) return false
        if (shield.pauseEndTimestamp == 0L) return true // Pause selamanya
        return System.currentTimeMillis() < shield.pauseEndTimestamp // Belum melewati batas waktu pause
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName in whitelistedPackages) return true
        if (packageName in CRITICAL_SYSTEM_PACKAGES) return true

        // Cek launcher cache dulu (murah)
        if (launcherPackages.contains(packageName)) return true

        // Satu kali getApplicationInfo untuk semua cek system app
        val isSystem = systemAppCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } catch (_: Exception) { true }
        }

        if (isSystem) {
            if (packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("home", ignoreCase = true) ||
                packageName.contains("car.mode", ignoreCase = true)) {
                return true
            }
            return packageName !in BLOCKABLE_SYSTEM_APPS
        }

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
                            
                            val currentPrefs = currentPreferences ?: return@launch
                            if (currentPrefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        packageName,
                                        minutes,
                                        currentPrefs.sessionUsageOverlaySize,
                                        currentPrefs.sessionUsageOverlayOpacity,
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

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        // Kurangi lookback window ke 3 detik untuk efisiensi
        val usageEvents = try {
            usageStatsManager.queryEvents(time - 3000, time)
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
            usageStatsCache = null
            lastUsageCacheTime = 0L
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
