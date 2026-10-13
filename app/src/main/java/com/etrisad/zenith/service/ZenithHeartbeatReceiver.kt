package com.etrisad.zenith.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ZenithHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        val validActions = setOf(
            "com.etrisad.zenith.action.HEARTBEAT",
            "com.etrisad.zenith.action.REFRESH_SERVICES",
            "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK",
            "com.etrisad.zenith.action.TEST_GOAL_CALLER",
            "com.etrisad.zenith.action.TEST_GOAL_CALLER_FIRE",
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (action in validActions) {
            // Battery: BOOT/HEARTBEAT must not blindly start a foreground service.
            // Service itself calls stopSelfIfIdle(), but avoiding the start saves a wakeup.
            // Test/goal-check actions always pass through; boot/heartbeat are gated by prefs.
            val needsGate = action == Intent.ACTION_BOOT_COMPLETED ||
                Intent.ACTION_MY_PACKAGE_REPLACED == action ||
                action == "com.etrisad.zenith.action.HEARTBEAT"
            if (needsGate) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefsRepo = com.etrisad.zenith.data.preferences.UserPreferencesRepository(context.applicationContext)
                        val prefs = withTimeoutOrNull(3000) {
                            prefsRepo.userPreferencesFlow.first()
                        }
                        val needsService = prefs == null || prefs.alarmMasterEnabled ||
                            prefs.bedtimeEnabled || prefs.eyeCareEnabled ||
                            prefs.usageGlimpseEnabled || prefs.pomodoroEnabled
                        if (needsService) startMonitorLocked(context, action)
                    } catch (e: Exception) {
                        try { startMonitorLocked(context, action) } catch (_: Exception) {}
                    } finally {
                        try { pending.finish() } catch (_: Exception) {}
                    }
                }
                return
            }
            try {
                startMonitorLocked(context, action)
            } catch (e: Exception) {
            }
        }
    }

    private fun startMonitorLocked(context: Context, action: String) {
        val monitorIntent = Intent(context, AppUsageMonitorService::class.java).apply {
            this.action = when (action) {
                "com.etrisad.zenith.action.REFRESH_SERVICES" -> "com.etrisad.zenith.action.REFRESH_DATA"
                "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK" -> "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK"
                "com.etrisad.zenith.action.TEST_GOAL_CALLER" -> "com.etrisad.zenith.action.TEST_GOAL_CALLER"
                "com.etrisad.zenith.action.TEST_GOAL_CALLER_FIRE" -> "com.etrisad.zenith.action.TEST_GOAL_CALLER_FIRE"
                else -> "com.etrisad.zenith.action.HEARTBEAT"
            }
        }
        try {
            context.startForegroundService(monitorIntent)
        } catch (e: Exception) {
        }
        if (ZenithService.isServiceRunning) {
            val accessIntent = Intent(context, ZenithService::class.java).apply {
                this.action = "com.etrisad.zenith.action.REFRESH_DATA"
            }
            try {
                context.startService(accessIntent)
            } catch (_: Exception) {}
        }
    }
}
