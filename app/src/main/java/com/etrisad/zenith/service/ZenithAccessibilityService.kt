package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
    private var whitelistedPackages = setOf<String>()

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

        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                whitelistedPackages = preferences.whitelistedPackages
            }
        }
        
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val pkg = currentPackage ?: continue
                if (shouldBypassBlocking(pkg)) continue

                // 1. Check schedules first (Always check regardless of allowedApps)
                if (checkSchedules(pkg)) continue

                // 2. Then check Shield/Shield Delay
                val allowedUntil = allowedApps[pkg] ?: 0L
                if (System.currentTimeMillis() > allowedUntil) {
                    val shield = shieldRepository.getShieldByPackageName(pkg)
                    if (shield != null) {
                        if (shield.isAutoQuitEnabled) {
                            withContext(Dispatchers.Main) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                                allowedApps.remove(pkg)
                                if (shield.isDelayAppEnabled) {
                                    serviceScope.launch {
                                        shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = System.currentTimeMillis()))
                                    }
                                }
                            }
                        } else if (!InterceptOverlayManager.isShowing) {
                            checkIfAppIsShielded(pkg)
                        }
                    }
                }
            }
        }
    }

    private fun isLauncherOrSystemHome(packageName: String): Boolean {
        // Cek via PackageManager — semua launcher yang terdaftar sebagai HOME
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
        if (launchers.any { it.activityInfo.packageName == packageName }) return true

        // Cek via CATEGORY_LAUNCHER — app yang punya launcher icon tapi bukan user app
        // (beberapa custom ROM launcher tidak register ke CATEGORY_HOME dengan benar)
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            if (isSystem) {
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                launcherIntent.setPackage(packageName)
                val activities = packageManager.queryIntentActivities(launcherIntent, 0)
                // System app yang punya launcher activity tapi nama package mengandung "launcher"/"home"
                if (activities.isNotEmpty() && (packageName.contains("launcher", ignoreCase = true)
                            || packageName.contains("home", ignoreCase = true))) {
                    return true
                }
            }
        } catch (_: PackageManager.NameNotFoundException) {}

        return false
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        // 1. App kita sendiri
        if (packageName == this.packageName) return true

        // 2. Paket system kritis (Android Framework, System UI, dll)
        val criticalSystemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller"
        )
        if (packageName in criticalSystemPackages) return true

        // 3. Whitelist dari User Preferences (cached)
        if (whitelistedPackages.contains(packageName)) {
            android.util.Log.d("ZenithService", "Bypass: $packageName is in Whitelist")
            return true
        }

        // 4. Launcher / home screen (Sangat penting untuk Ortus Launcher)
        if (isLauncherOrSystemHome(packageName) || 
            packageName.contains("launcher", ignoreCase = true) || 
            packageName.contains("home", ignoreCase = true)) {
            android.util.Log.d("ZenithService", "Bypass: $packageName identified as Launcher")
            return true
        }

        // 5. System app lainnya (kecuali yang memang sering membuat kecanduan)
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            if (isSystem) {
                val blockableSystemApps = setOf(
                    "com.google.android.youtube",
                    "com.android.chrome",
                    "com.google.android.apps.youtube.music",
                    "com.android.vending" // Play Store
                )
                if (packageName !in blockableSystemApps) return true
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return true
        }

        return false
    }

    private fun checkSchedules(packageName: String): Boolean {
        // FAST-PASS: Jika whitelist, JANGAN LANJUTKAN LOGIKA SCHEDULE
        if (shouldBypassBlocking(packageName)) {
            android.util.Log.d("ZenithService", "Fast-Pass Schedule: $packageName is Whitelisted")
            return false
        }

        // Check if app is temporarily allowed via emergency bypass
        val allowedUntil = allowedApps[packageName] ?: 0L
        if (System.currentTimeMillis() <= allowedUntil) {
            return false
        }

        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val activeSchedulesSnapshot = activeSchedules // Use snapshot to avoid concurrent modification issues

        for (schedule in activeSchedulesSnapshot) {
            if (!isTimeInInterval(currentTime, schedule.startTime, schedule.endTime)) continue

            when (schedule.mode) {
                com.etrisad.zenith.data.local.entity.ScheduleMode.BLOCK -> {
                    if (packageName in schedule.packageNames) {
                        android.util.Log.d("ZenithService", "Blocking $packageName (BLOCK mode active)")
                        showScheduleOverlay(packageName, schedule)
                        return true
                    }
                }
                com.etrisad.zenith.data.local.entity.ScheduleMode.ALLOW -> {
                    // Dalam mode ALLOW, aplikasi yang TIDAK ada di daftar schedule akan diblokir.
                    // TAPI karena kita sudah melakukan FAST-PASS di atas, 
                    // Ortus Launcher (yang di-whitelist) tidak akan pernah sampai ke baris ini.
                    if (packageName !in schedule.packageNames) {
                        android.util.Log.d("ZenithService", "Blocking $packageName (Not in ALLOW list of ${schedule.name})")
                        showScheduleOverlay(packageName, schedule)
                        return true
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

            android.util.Log.d("ZenithService", "Detected window change: $packageName")

            // 1. CEK WHITELIST/BYPASS SECARA ABSOLUT
            if (shouldBypassBlocking(packageName)) {
                android.util.Log.d("ZenithService", "Bypassing $packageName")
                return
            }

            // 2. CEK JADWAL
            if (checkSchedules(packageName)) return

            // 3. CEK SHIELD
            val currentTime = System.currentTimeMillis()
            val allowedUntil = allowedApps[packageName] ?: 0L
            if (currentTime > allowedUntil) {
                checkIfAppIsShielded(packageName)
            }
        }
    }

    private fun checkIfAppIsShielded(packageName: String) {
        if (shouldBypassBlocking(packageName)) return // Gunakan bypass yang lebih komprehensif

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
