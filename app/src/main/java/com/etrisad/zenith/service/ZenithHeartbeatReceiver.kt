package com.etrisad.zenith.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ZenithHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        val validActions = setOf(
            "com.etrisad.zenith.action.HEARTBEAT",
            "com.etrisad.zenith.action.REFRESH_SERVICES",
            "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK",
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (action in validActions) {
            try {
                val monitorIntent = Intent(context, AppUsageMonitorService::class.java).apply {
                    this.action = when (action) {
                        "com.etrisad.zenith.action.REFRESH_SERVICES" -> "com.etrisad.zenith.action.REFRESH_DATA"
                        "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK" -> "com.etrisad.zenith.action.SCREEN_OFF_GOAL_CHECK"
                        else -> "com.etrisad.zenith.action.HEARTBEAT"
                    }
                }
                
                try {
                    context.startService(monitorIntent)
                } catch (e: Exception) {
                    android.util.Log.w("ZenithHeartbeat", "Failed to start service: ${e.message}")
                }

                if (ZenithService.isServiceRunning) {
                    val accessIntent = Intent(context, ZenithService::class.java).apply {
                        this.action = "com.etrisad.zenith.action.REFRESH_DATA"
                    }
                    try {
                        context.startService(accessIntent)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                android.util.Log.e("ZenithHeartbeat", "Failed to process heartbeat: ${e.message}")
            }
        }
    }
}
