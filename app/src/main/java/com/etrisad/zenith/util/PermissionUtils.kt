package com.etrisad.zenith.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

fun hasAllPermissions(context: Context): Boolean {
    val hasUsageStats = hasUsageStatsPermission(context)
    val hasOverlay = Settings.canDrawOverlays(context)
    val hasAccessibility = isAccessibilityServiceEnabled(context)
    
    return hasUsageStats && hasOverlay && hasAccessibility
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedId = android.content.ComponentName(context.packageName, "com.etrisad.zenith.service.ZenithAccessibilityService").flattenToString()
    val enabledServices = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    
    return enabledServices.split(':').any { it.equals(expectedId, ignoreCase = true) }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
