package com.etrisad.zenith.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ZenithHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "com.etrisad.zenith.action.HEARTBEAT" || action == "com.etrisad.zenith.action.REFRESH_SERVICES") {
            try {
                val monitorIntent = Intent(context, AppUsageMonitorService::class.java).apply {
                    this.action = if (action == "com.etrisad.zenith.action.REFRESH_SERVICES") 
                        "com.etrisad.zenith.action.REFRESH_DATA" 
                    else 
                        "com.etrisad.zenith.action.HEARTBEAT"
                }
                context.startForegroundService(monitorIntent)

                if (ZenithAccessibilityService.isServiceRunning) {
                    val accessIntent = Intent(context, ZenithAccessibilityService::class.java).apply {
                        this.action = "com.etrisad.zenith.action.REFRESH_DATA"
                    }
                    context.startService(accessIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("ZenithHeartbeat", "Failed to start service: ${e.message}")
            }
        }
    }
}
