package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.usage.UsageStats
import android.view.accessibility.AccessibilityEvent
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ZenithAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val packageChangeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private val usageStatsManager by lazy { getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }
    private val reusableCalendar = java.util.Calendar.getInstance()

    private var lastForegroundApp: String? = null
    private var lastEvaluationTime = 0L
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private val allowedApps = mutableMapOf<String, Long>()
    private var activeSchedules = listOf<ScheduleEntity>()
    private var whitelistedPackages = emptySet<String>()
    private var currentPreferences: UserPreferences? = null
    private var allShieldsCache = listOf<ShieldEntity>()
    private var usageStatsCache: List<UsageStats>? = null
    private var lastUsageCacheTime = 0L

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
        val app = application as com.etrisad.zenith.ZenithApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = UserPreferencesRepository(this)
        overlayManager = InterceptOverlayManager(this)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        serviceScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShieldsCache = shields
            }
        }

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
                currentPreferences = preferences
                whitelistedPackages = preferences.whitelistedPackages
            }
        }

        // Debounce package changes to avoid redundant processing
        serviceScope.launch {
            packageChangeFlow.collectLatest { packageName ->
                handlePackageChange(packageName)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        packageChangeFlow.tryEmit(packageName)
    }

    private suspend fun handlePackageChange(currentApp: String) {
        val currentTime = System.currentTimeMillis()

        // Evaluasi lebih sering (setiap 500ms) agar lebih sigap
        if (currentApp == lastForegroundApp && currentTime - lastEvaluationTime < 500) {
            return
        }
        lastEvaluationTime = currentTime

        if (currentApp != lastForegroundApp) {
            sessionUsageOverlayManager.updateForegroundApp(currentApp)
            currentShieldCache = allShieldsCache.find { it.packageName == currentApp }
            lastUsageFetchTime = 0L
        }

        if (shouldBypassBlocking(currentApp)) {
            lastForegroundApp = currentApp
            return
        }

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
                } else if (currentApp != lastForegroundApp || allowedUntil > 0) {
                    checkIfAppIsShielded(currentApp)
                    if (allowedUntil > 0) allowedApps[currentApp] = 0L
                }
            }
        }
        lastForegroundApp = currentApp
    }

    private suspend fun updateUsageTime(packageName: String) {
        val shield = currentShieldCache ?: return
        val currentTime = System.currentTimeMillis()

        // Fetch dari sistem setiap 5 detik (seimbang antara akurasi & CPU)
        if (currentTime - lastUsageFetchTime > 5000) {
            cachedTotalUsage = getTotalUsageToday(packageName)
            lastUsageFetchTime = currentTime
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
        
        // Strategi Adaptive Write:
        // Update DB setiap 10 detik, ATAU jika sisa waktu < 1 menit (agar blokir sigap)
        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000 
        val shouldUpdateDB = timeSinceLastUsed > 10000 || (isNearLimit && timeSinceLastUsed > 2000)

        if (shouldUpdateDB) {
            val updatedShield = shield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime
            )
            shieldRepository.updateShield(updatedShield)
            currentShieldCache = updatedShield
        }
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        // Double check foreground app to prevent overlay appearing over wrong app
        if (targetPackageName != lastForegroundApp) return

        val shield = currentShieldCache ?: allShieldsCache.find { it.packageName == targetPackageName }
        if (shield != null && !InterceptOverlayManager.isShowing) {
            val totalUsageToday = getTotalUsageToday(targetPackageName)
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
                    val updated = shield.copy(lastDelayStartTimestamp = currentTime - (delayDurationSeconds * 1000L) - 1000)
                    updated
                } else if (lastAction == 0L) {
                    // Jika baru pertama kali butuh delay, set timestamp SEKARANG
                    val updated = shield.copy(lastDelayStartTimestamp = currentTime)
                    serviceScope.launch { shieldRepository.updateShield(updated) }
                    updated
                } else {
                    // Jika delay masih aktif atau sudah selesai, biarkan timestamp yang lama.
                    // Jangan update ke currentTime agar hitungan waktu tetap berjalan maju dari titik awal.
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
                        val currentTimeOnUnlock = System.currentTimeMillis()
                        serviceScope.launch {
                            val currentShield = shieldRepository.getShieldByPackageName(targetPackageName) ?: return@launch
                            val updatedShield = if (isEmergency) {
                                currentShield.copy(
                                    emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                    lastDelayStartTimestamp = 0L,
                                    lastSessionEndTimestamp = currentTimeOnUnlock
                                )
                            } else {
                                currentShield.copy(
                                    currentPeriodUses = currentShield.currentPeriodUses + 1,
                                    lastDelayStartTimestamp = 0L,
                                    lastSessionEndTimestamp = currentTimeOnUnlock
                                )
                            }
                            shieldRepository.updateShield(updatedShield)
                            currentShieldCache = updatedShield
                            allowedApps[targetPackageName] = currentTimeOnUnlock + (minutes * 60 * 1000L)

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
                                                val shield = shieldRepository.getShieldByPackageName(packageName)
                                                if (shield != null) {
                                                    val updated = shield.copy(lastSessionEndTimestamp = System.currentTimeMillis())
                                                    shieldRepository.updateShield(updated)
                                                    currentShieldCache = updated
                                                }
                                                checkIfAppIsShielded(packageName)
                                            }
                                        }
                                    )
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
        val currentTime = System.currentTimeMillis()
        
        // Cache usage stats list for 3 seconds to avoid redundant system calls
        var stats = usageStatsCache
        if (stats == null || currentTime - lastUsageCacheTime > 3000) {
            synchronized(reusableCalendar) {
                reusableCalendar.timeInMillis = currentTime
                reusableCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                reusableCalendar.set(java.util.Calendar.MINUTE, 0)
                reusableCalendar.set(java.util.Calendar.SECOND, 0)
                reusableCalendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = reusableCalendar.timeInMillis

                stats = try {
                    usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
                } catch (_: Exception) { null }
                usageStatsCache = stats
                lastUsageCacheTime = currentTime
            }
        }
        
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
        
        val now = System.currentTimeMillis()
        val currentTotalMinutes = synchronized(reusableCalendar) {
            reusableCalendar.timeInMillis = now
            reusableCalendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + reusableCalendar.get(java.util.Calendar.MINUTE)
        }
        
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
                                    sessionUsageOverlayManager.showHUD(
                                        packageName,
                                        minutes,
                                        prefs.sessionUsageOverlaySize,
                                        prefs.sessionUsageOverlayOpacity,
                                        onSessionEnd = {
                                            allowedApps[packageName] = 0L
                                            serviceScope.launch {
                                                val shield = shieldRepository.getShieldByPackageName(packageName)
                                                if (shield != null) {
                                                    val updated = shield.copy(lastSessionEndTimestamp = System.currentTimeMillis())
                                                    shieldRepository.updateShield(updated)
                                                    currentShieldCache = updated
                                                }
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

    override fun onInterrupt() {}

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
            allowedApps.clear()
            allShieldsCache = emptyList<ShieldEntity>()
            activeSchedules = emptyList<ScheduleEntity>()
            parsedSchedulesCache = emptyList<ParsedSchedule>()
            usageStatsCache = null
            lastUsageCacheTime = 0L
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
