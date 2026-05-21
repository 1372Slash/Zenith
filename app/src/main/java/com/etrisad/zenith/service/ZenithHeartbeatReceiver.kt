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
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT
        )

        if (action in validActions) {
            try {
                val monitorIntent = Intent(context, AppUsageMonitorService::class.java).apply {
                    this.action = if (action == "com.etrisad.zenith.action.REFRESH_SERVICES") 
                        "com.etrisad.zenith.action.REFRESH_DATA" 
                    else 
                        "com.etrisad.zenith.action.HEARTBEAT"
                }
                
                try {
                    context.startForegroundService(monitorIntent)
                } catch (e: Exception) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        android.util.Log.w("ZenithHeartbeat", "Background start failed, service might be restricted: ${e.message}")
                    } else {
                        context.startService(monitorIntent)
                    }
                }

                if (ZenithAccessibilityService.isServiceRunning) {
                    val accessIntent = Intent(context, ZenithAccessibilityService::class.java).apply {
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
