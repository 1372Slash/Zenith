package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.*

class ZenithAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private val allowedApps = mutableMapOf<String, Long>()
    private var currentPackage: String? = null
    private var monitoringJob: Job? = null

    companion object {
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        val database = ZenithDatabase.getDatabase(this)
        shieldRepository = ShieldRepository(database.shieldDao())
        overlayManager = InterceptOverlayManager(this)
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
                    val shield = shieldRepository.getShieldByPackageName(pkg)
                    if (shield != null) {
                        if (shield.isAutoQuitEnabled) {
                            withContext(Dispatchers.Main) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                                allowedApps.remove(pkg)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            currentPackage = packageName
            
            // Don't intercept our own app
            if (packageName == this.packageName) return

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
                serviceScope.launch(Dispatchers.Main) {
                    overlayManager.showOverlay(
                        packageName = packageName,
                        appName = shield.appName,
                        shield = shield,
                        totalUsageToday = totalUsageToday,
                        onAllowUse = { minutes, isEmergency ->
                            serviceScope.launch {
                                val currentShield = shieldRepository.getShieldByPackageName(packageName) ?: return@launch
                                val updatedShield = if (isEmergency) {
                                    currentShield.copy(
                                        emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0)
                                    )
                                } else {
                                    val periodExpired = System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                    currentShield.copy(
                                        currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                        lastPeriodResetTimestamp = if (periodExpired) System.currentTimeMillis() else currentShield.lastPeriodResetTimestamp
                                    )
                                }
                                shieldRepository.updateShield(updatedShield)
                                allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                            }
                        },
                        onCloseApp = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
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
