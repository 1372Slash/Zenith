package com.etrisad.zenith.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import com.etrisad.zenith.BuildConfig
import com.etrisad.zenith.crash.CrashStore
import com.etrisad.zenith.data.local.database.DbLogBuffer
import com.etrisad.zenith.data.local.database.OverlayLogBuffer
import com.etrisad.zenith.data.local.database.UsageGraphLogBuffer
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticReportUtils {
    const val GITHUB_ISSUES_URL = "https://github.com/1372Slash/Zenith/issues/new"

    private val PII_KEY_VALUE = Regex(
        "(pkg|appName|packageName|actualPkg|currentApp|newPackage|target|shield)\\s*[:=]\\s*([^\\s,\\)\\]]+)"
    )
    private val PACKAGE_URI = Regex("package:([^\\s,\\)\\]]+)")
    private val GENERIC_PKG_ID = Regex(
        "\\b(?!com\\.etrisad\\.zenith\\b)(?!android\\b)(?!java\\b)(?!kotlin\\b)(?!androidx\\b)" +
            "[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+\\b"
    )
    fun sanitizeLogText(text: String): String {
        var out = PII_KEY_VALUE.replace(text) { "${it.groupValues[1]}=<redacted>" }
        out = PACKAGE_URI.replace(out) { "package:<redacted>" }
        out = GENERIC_PKG_ID.replace(out, "<pkg>")
        return out
    }

    fun defaultFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "zenith_diagnostic_${sdf.format(Date())}.zip"
    }

    fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        }
    }

    suspend fun export(
        context: Context,
        targetUri: Uri,
        preferences: UserPreferences,
        userConfirmed: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Consent is enforced in the data layer, not just the UI checkbox:
        // programmatic callers without explicit confirmation get a failure.
        if (!userConfirmed) {
            return@withContext Result.failure(
                IllegalStateException("Export consent not confirmed")
            )
        }
        try {
            val info = buildDiagnosticInfo(context)
            val settings = buildSanitizedSettings(preferences)
            val snapshot = buildDbSnapshot(context)
            val systemStatus = buildSystemStatus(context)
            val logSummary = buildLogSummary()
            val dbLogs = try {
                sanitizeLogText(DbLogBuffer.copyAll())
            } catch (e: Exception) {
                "Log unavailable: ${e.message}"
            }
            val overlayLogs = try {
                sanitizeLogText(OverlayLogBuffer.copyAll())
            } catch (e: Exception) {
                "Log unavailable: ${e.message}"
            }
            val usageGraphLogs = try {
                sanitizeLogText(UsageGraphLogBuffer.copyAll())
            } catch (e: Exception) {
                "Log unavailable: ${e.message}"
            }
            val logcat = sanitizeLogText(collectAppLogcat())

            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                ZipOutputStream(output).use { zip ->
                    addTextEntry(zip, "diagnostic_info.json", info)
                    addTextEntry(zip, "settings_sanitized.json", settings)
                    addTextEntry(zip, "db_snapshot.json", snapshot)
                    addTextEntry(zip, "system_status.json", systemStatus)
                    addTextEntry(zip, "log_summary.json", logSummary)
                    addTextEntry(zip, "db_logs.txt", dbLogs)
                    addTextEntry(zip, "overlay_logs.txt", overlayLogs)
                    addTextEntry(zip, "usage_graph_logs.txt", usageGraphLogs)
                    addTextEntry(zip, "logcat.txt", logcat)
                    try {
                        // Caught crashes, so the report always explains an abrupt stop.
                        for ((name, content) in CrashStore.reportEntries(context)) {
                            addTextEntry(zip, name, sanitizeLogText(content))
                        }
                    } catch (_: Exception) {
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open save location"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildDiagnosticInfo(context: Context): String {
        val now = System.currentTimeMillis()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(now))
        val metrics = context.resources.displayMetrics
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

        return buildString {
            appendLine("{")
            appendLine("  \"exportedAt\": \"${esc(iso)}\",")
            appendLine("  \"exportedAtEpochMillis\": $now,")
            appendLine("  \"timeZone\": \"${esc(TimeZone.getDefault().id)}\",")
            appendLine("  \"locale\": \"${esc(Locale.getDefault().toLanguageTag())}\",")
            appendLine("  \"appVersion\": \"${esc(BuildConfig.VERSION_NAME)}\",")
            appendLine("  \"versionCode\": ${BuildConfig.VERSION_CODE},")
            appendLine("  \"showUpdates\": ${BuildConfig.SHOW_UPDATES},")
            appendLine("  \"packageName\": \"${esc(context.packageName)}\",")
            appendLine("  \"deviceManufacturer\": \"${esc(Build.MANUFACTURER ?: "unknown")}\",")
            appendLine("  \"deviceModel\": \"${esc(Build.MODEL ?: "unknown")}\",")
            appendLine("  \"deviceDevice\": \"${esc(Build.DEVICE ?: "unknown")}\",")
            appendLine("  \"deviceProduct\": \"${esc(Build.PRODUCT ?: "unknown")}\",")
            appendLine("  \"androidRelease\": \"${esc(Build.VERSION.RELEASE ?: "unknown")}\",")
            appendLine("  \"sdkInt\": ${Build.VERSION.SDK_INT},")
            appendLine("  \"supportedAbis\": \"${esc(Build.SUPPORTED_ABIS?.joinToString(",") ?: "")}\",")
            appendLine("  \"screenWidthPx\": ${metrics.widthPixels},")
            appendLine("  \"screenHeightPx\": ${metrics.heightPixels},")
            appendLine("  \"densityDpi\": ${metrics.densityDpi}")
            appendLine("}")
        }
    }
    private fun buildSanitizedSettings(p: UserPreferences): String {
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        fun days(v: Set<Int>) = v.sorted().joinToString(",")
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"appearance\": {")
        sb.appendLine("    \"theme\": \"${p.themeConfig.name}\",")
        sb.appendLine("    \"font\": \"${p.fontOption.name}\",")
        sb.appendLine("    \"dynamicColor\": ${p.dynamicColor},")
        sb.appendLine("    \"expressiveColors\": ${p.expressiveColors},")
        sb.appendLine("    \"floatingTabBar\": ${p.floatingTabBarEnabled},")
        sb.appendLine("    \"headerInfoButton\": ${p.headerInfoButtonEnabled},")
        sb.appendLine("    \"totalUsagePill\": ${p.totalUsagePillEnabled},")
        sb.appendLine("    \"foregroundNotificationMode\": \"${p.foregroundNotificationStatusMode.name}\"")
        sb.appendLine("  },")
        sb.appendLine("  \"limits\": {")
        sb.appendLine("    \"screenTimeTargetMinutes\": ${p.screenTimeTargetMinutes},")
        sb.appendLine("    \"emergencyRechargeMinutes\": ${p.emergencyRechargeDurationMinutes},")
        sb.appendLine("    \"delayAppSeconds\": ${p.delayAppDurationSeconds},")
        sb.appendLine("    \"dayStart\": \"${esc(String.format(Locale.US, "%02d:%02d", p.dayStartHour, p.dayStartMinute))}\"")
        sb.appendLine("  },")
        sb.appendLine("  \"features\": {")
        sb.appendLine("    \"mindfulGateway\": ${p.mindfulGatewayEnabled},")
        sb.appendLine("    \"earlyKick\": ${p.earlyKickEnabled},")
        sb.appendLine("    \"interceptAudioFocus\": ${p.interceptAudioFocusEnabled},")
        sb.appendLine("    \"sessionOverlay\": ${p.sessionUsageOverlayEnabled},")
        sb.appendLine("    \"sessionOverlaySize\": ${p.sessionUsageOverlaySize},")
        sb.appendLine("    \"sessionOverlayOpacity\": ${p.sessionUsageOverlayOpacity},")
        sb.appendLine("    \"usageGlimpse\": ${p.usageGlimpseEnabled},")
        sb.appendLine("    \"websiteAutoTracking\": ${p.websiteAutoTrackingEnabled},")
        sb.appendLine("    \"incentiveLock\": ${p.incentiveLockEnabled},")
        sb.appendLine("    \"dailyRecap\": ${p.dailyRecapEnabled},")
        sb.appendLine("    \"weeklyInsight\": ${p.weeklyInsightEnabled},")
        sb.appendLine("    \"trendMilestone\": ${p.trendMilestoneEnabled},")
        sb.appendLine("    \"batteryStatsReset\": ${p.batteryStatsResetEnabled},")
        sb.appendLine("    \"showCurrentEvent\": ${p.showCurrentEvent},")
        sb.appendLine("    \"refreshOnOpenStats\": ${p.refreshOnOpenUsageStats},")
        sb.appendLine("    \"checkUpdateOnStart\": ${p.checkUpdateOnStart},")
        sb.appendLine("    \"preferSystemHistory\": ${p.preferSystemUsageHistory},")
        sb.appendLine("    \"smartRepairOnRefresh\": ${p.smartRepairOnRefresh},")
        sb.appendLine("    \"accessibilityRequired\": ${p.accessibilityRequired},")
        sb.appendLine("    \"accessibilityDisabled\": ${p.accessibilityDisabled},")
        sb.appendLine("    \"disableTrackingAtUnusedHours\": ${p.disableTrackingAtUnusedHours},")
        sb.appendLine("    \"disableTrackingWindow\": \"${p.disableTrackingStartHour}-${p.disableTrackingEndHour}\",")
        sb.appendLine("    \"eyeCare\": ${p.eyeCareEnabled},")
        sb.appendLine("    \"eyeCareWorkMinutes\": ${p.eyeCareWorkMinutes},")
        sb.appendLine("    \"eyeCareRestSeconds\": ${p.eyeCareRestSeconds}")
        sb.appendLine("  },")
        sb.appendLine("  \"schedules\": {")
        sb.appendLine("    \"bedtimeEnabled\": ${p.bedtimeEnabled},")
        sb.appendLine("    \"bedtimeWindow\": \"${esc(p.bedtimeStartTime)}-${esc(p.bedtimeEndTime)}\",")
        sb.appendLine("    \"bedtimeDays\": \"${days(p.bedtimeDays)}\",")
        sb.appendLine("    \"bedtimeDnd\": ${p.bedtimeDndEnabled},")
        sb.appendLine("    \"bedtimeWindDown\": ${p.bedtimeWindDownEnabled},")
        sb.appendLine("    \"bedtimeNotification\": ${p.bedtimeNotificationEnabled},")
        sb.appendLine("    \"bedtimeWhitelistedCount\": ${p.bedtimeWhitelistedPackages.size},")
        sb.appendLine("    \"gracePeriodEnabled\": ${p.gracePeriodEnabled},")
        sb.appendLine("    \"gracePeriodWindow\": \"${esc(p.gracePeriodStartTime)}-${esc(p.gracePeriodEndTime)}\",")
        sb.appendLine("    \"gracePeriodDays\": \"${days(p.gracePeriodDays)}\",")
        sb.appendLine("    \"gracePeriodEditLock\": ${p.gracePeriodEditLockEnabled},")
        sb.appendLine("    \"lockdownEnabled\": ${p.lockdownEnabled},")
        sb.appendLine("    \"lockdownWindow\": \"${esc(p.lockdownStartTime)}-${esc(p.lockdownEndTime)}\",")
        sb.appendLine("    \"lockdownDays\": \"${days(p.lockdownDays)}\",")
        sb.appendLine("    \"pomodoroEnabled\": ${p.pomodoroEnabled},")
        sb.appendLine("    \"pomodoroSessionMinutes\": ${p.pomodoroSessionDurationMinutes},")
        sb.appendLine("    \"pomodoroBreakMinutes\": ${p.pomodoroBreakDurationMinutes},")
        sb.appendLine("    \"pomodoroAllowedCount\": ${p.pomodoroAllowedPackages.size},")
        sb.appendLine("    \"pomodoroHasPresets\": ${p.pomodoroPresets.length > 4},")
        sb.appendLine("    \"pausePointEnabled\": ${p.pausePointEnabled},")
        sb.appendLine("    \"pausePointTaskTypes\": \"${esc(p.pausePointTaskTypes.joinToString(",") { it.name })}\",")
        sb.appendLine("    \"pausePointQrCount\": ${p.pausePointQrCodes.count { it.isNotBlank() }},")
        sb.appendLine("    \"pausePointNfcCount\": ${p.pausePointNfcTagIds.count { it.isNotBlank() }}")
        sb.appendLine("  },")
        sb.appendLine("  \"performance\": {")
        sb.appendLine("    \"level\": \"${p.performanceLevel.name}\",")
        sb.appendLine("    \"a11yActiveDelay\": ${p.perfA11yActiveDelay},")
        sb.appendLine("    \"a11yInactiveDelay\": ${p.perfA11yInactiveDelay},")
        sb.appendLine("    \"screenOffDelay\": ${p.perfScreenOffDelay},")
        sb.appendLine("    \"powerSaveDelay\": ${p.perfPowerSaveDelay},")
        sb.appendLine("    \"usageStatsCacheMs\": ${p.perfUsageStatsCacheMs},")
        sb.appendLine("    \"shieldDbWriteMs\": ${p.perfShieldDbWriteMs},")
        sb.appendLine("    \"shieldDbWriteNearMs\": ${p.perfShieldDbWriteNearMs},")
        sb.appendLine("    \"monDefault\": ${p.perfMonDefault}")
        sb.appendLine("  },")
        sb.appendLine("  \"overlay\": {")
        sb.appendLine("    \"paletteId\": \"${esc(p.overlayPaletteId)}\",")
        sb.appendLine("    \"sheetOpacity\": ${p.overlaySheetOpacity},")
        sb.appendLine("    \"fullScreen\": ${p.overlayFullScreen}")
        sb.appendLine("  },")
        sb.appendLine("  \"alarms\": {")
        sb.appendLine("    \"masterEnabled\": ${p.alarmMasterEnabled},")
        sb.appendLine("    \"autoRepeat\": ${p.alarmAutoRepeatEnabled},")
        sb.appendLine("    \"hasCustomSound\": ${!p.alarmSoundUri.isNullOrEmpty()},")
        sb.appendLine("    \"legacyTime\": \"${esc(p.alarmTime)}\"")
        sb.appendLine("  },")
        sb.appendLine("  \"state\": {")
        sb.appendLine("    \"developerMode\": ${p.developerModeEnabled},")
        sb.appendLine("    \"showDatabaseIndicator\": ${p.showDatabaseIndicator},")
        sb.appendLine("    \"whitelistedCount\": ${p.whitelistedPackages.size},")
        sb.appendLine("    \"privateAppsCount\": ${p.privatePackages.size},")
        sb.appendLine("    \"excludedFromTrackingCount\": ${p.excludedFromTrackingPackages.size},")
        sb.appendLine("    \"autoBackupEnabled\": ${p.autoBackupEnabled},")
        sb.appendLine("    \"hasBackupDirectory\": ${p.backupDirectoryUri.isNotEmpty()},")
        sb.appendLine("    \"backupIntervalHours\": ${p.backupIntervalHours},")
        sb.appendLine("    \"hasLifetimeSnapshot\": ${p.lifetimeSnapshot.isNotEmpty()},")
        sb.appendLine("    \"lastSyncTimestamp\": ${p.lastSyncTimestamp},")
        sb.appendLine("    \"lastBackupTimestamp\": ${p.lastBackupTimestamp},")
        sb.appendLine("    \"globalCurrentStreak\": ${p.globalCurrentStreak},")
        sb.appendLine("    \"globalBestStreak\": ${p.globalBestStreak},")
        sb.appendLine("    \"lastKnownDailyUsage\": ${p.lastKnownDailyUsage}")
        sb.appendLine("  },")
        sb.appendLine("  \"privacyNote\": \"Identity fields, raw package lists, QR/NFC values, raw URIs and raw JSON blobs are intentionally excluded (counts/booleans only).\"")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun buildDbSnapshot(context: Context): String {
        val tables = listOf(
            "shields",
            "schedules",
            "daily_usage",
            "hourly_usage",
            "website_usage",
            "intercepted_notifications",
            "pomodoro_sessions"
        )
        val counts = mutableMapOf<String, Long>()
        var maxUsageDate: String? = null
        var schemaVersion: Long = -1L
        try {
            val db = ZenithDatabase.getDatabase(context).openHelper.readableDatabase
            schemaVersion = try {
                db.query("PRAGMA user_version").use {
                    if (it.moveToFirst()) it.getLong(0) else -1L
                }
            } catch (_: Exception) {
                -1L
            }
            for (table in tables) {
                counts[table] = try {
                    db.query("SELECT COUNT(*) FROM $table").use {
                        if (it.moveToFirst()) it.getLong(0) else -1L
                    }
                } catch (_: Exception) {
                    -1L
                }
            }
            maxUsageDate = try {
                db.query("SELECT MAX(date) FROM daily_usage").use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (_: Exception) {
                null
            }
        } catch (e: Exception) {
            DbLogBuffer.w("DiagExport", "DB_SNAPSHOT_FAILED: ${e.message}")
        }
        return buildString {
            appendLine("{")
            tables.forEachIndexed { index, table ->
                val comma = if (index < tables.size - 1) "," else ""
                appendLine("  \"$table\": ${counts[table] ?: -1}$comma")
            }
            appendLine("  ,\"maxDailyUsageDate\": ${if (maxUsageDate != null) "\"$maxUsageDate\"" else "null"},")
            appendLine("  \"schemaVersion\": $schemaVersion")
            appendLine("}")
        }
    }

    /**
     * Granted / not-granted states only, no details. Explains whole classes
     * of bugs at a glance: shields never firing (accessibility/overlay off),
     * stats stuck at zero (usage access off), bedtime DND not working,
     * alarms inexact, service killed (battery optimization), Go-device limits.
     */
    private fun buildSystemStatus(context: Context): String {
        fun check(block: () -> Boolean): Boolean = try {
            block()
        } catch (_: Exception) {
            false
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return buildString {
            appendLine("{")
            appendLine("  \"usageStatsGranted\": ${check { hasUsageStatsPermission(context) }},")
            appendLine("  \"overlayGranted\": ${check { Settings.canDrawOverlays(context) }},")
            appendLine("  \"accessibilityEnabled\": ${check { isAccessibilityServiceEnabled(context) }},")
            appendLine("  \"dndAccessGranted\": ${check { hasNotificationPolicyAccess(context) }},")
            appendLine("  \"notificationsEnabled\": ${check { hasNotificationPermission(context) }},")
            appendLine("  \"notificationListenerEnabled\": ${check { isNotificationListenerEnabled(context) }},")
            appendLine("  \"ignoringBatteryOptimizations\": ${check { isIgnoringBatteryOptimizations(context) }},")
            appendLine("  \"canScheduleExactAlarms\": ${check { canScheduleExactAlarms(context) }},")
            appendLine("  \"powerSaveMode\": ${check { powerManager?.isPowerSaveMode == true }},")
            appendLine("  \"lowRamDevice\": ${check { isAndroidGo(context) }},")
            appendLine("  \"calendarGranted\": ${check { hasCalendarPermission(context) }}")
            appendLine("}")
        }
    }

    /**
     * Counts and time range per buffer only — no message contents, so no
     * package names or other details leak. Shows at a glance whether the
     * shield/overlay pipeline was active recently and where errors cluster.
     */
    private fun buildLogSummary(): String {
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        fun iso(ts: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(ts))
        val dbEntries = try {
            DbLogBuffer.getAll()
        } catch (_: Exception) {
            emptyList()
        }
        val overlayEntries = try {
            OverlayLogBuffer.getAll()
        } catch (_: Exception) {
            emptyList()
        }
        val dbLevels = dbEntries.groupBy { it.level.name }.toSortedMap()
        val dbTags = dbEntries.groupBy { it.tag }.toSortedMap()
        val overlayLevels = overlayEntries.groupBy { it.level.name }.toSortedMap()
        val overlayTags = overlayEntries.groupBy { it.tag }.toSortedMap()
        return buildString {
            appendLine("{")
            appendLine("  \"dbLog\": {")
            appendLine("    \"total\": ${dbEntries.size},")
            dbLevels.entries.forEachIndexed { i, (level, list) ->
                val comma = if (i < dbLevels.size - 1) "," else ""
                appendLine("    \"$level\": ${list.size}$comma")
            }
            appendLine("    ,\"byTag\": {")
            dbTags.entries.forEachIndexed { i, (tag, list) ->
                val comma = if (i < dbTags.size - 1) "," else ""
                appendLine("      \"${esc(tag)}\": ${list.size}$comma")
            }
            appendLine("    },")
            appendLine("    \"oldest\": ${dbEntries.minOfOrNull { it.timestamp }?.let { "\"${iso(it)}\"" } ?: "null"},")
            appendLine("    \"newest\": ${dbEntries.maxOfOrNull { it.timestamp }?.let { "\"${iso(it)}\"" } ?: "null"}")
            appendLine("  },")
            appendLine("  \"overlayLog\": {")
            appendLine("    \"total\": ${overlayEntries.size},")
            overlayLevels.entries.forEachIndexed { i, (level, list) ->
                val comma = if (i < overlayLevels.size - 1) "," else ""
                appendLine("    \"$level\": ${list.size}$comma")
            }
            appendLine("    ,\"byTag\": {")
            overlayTags.entries.forEachIndexed { i, (tag, list) ->
                val comma = if (i < overlayTags.size - 1) "," else ""
                appendLine("      \"${esc(tag)}\": ${list.size}$comma")
            }
            appendLine("    },")
            appendLine("    \"oldest\": ${overlayEntries.minOfOrNull { it.timestamp }?.let { "\"${iso(it)}\"" } ?: "null"},")
            appendLine("    \"newest\": ${overlayEntries.maxOfOrNull { it.timestamp }?.let { "\"${iso(it)}\"" } ?: "null"}")
            appendLine("  },")
            appendLine("  \"privacyNote\": \"Counts and timestamps only; no log message contents.\"")
            appendLine("}")
        }
    }

    private suspend fun collectAppLogcat(): String {
        return try {
            withTimeoutOrNull(3000L) {
                withContext(Dispatchers.IO) {
                    val pid = android.os.Process.myPid()
                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "--pid=$pid"))
                    try {
                        val output = process.inputStream.bufferedReader().use { reader ->
                            val sb = StringBuilder()
                            var total = 0
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                sb.appendLine(line)
                                total += (line?.length ?: 0) + 1
                                if (total > 256 * 1024) {
                                    sb.appendLine("... (truncated at 256KB)")
                                    break
                                }
                            }
                            sb.toString()
                        }
                        withTimeoutOrNull(1000L) { process.waitFor() }
                        if (output.isBlank()) "(logcat returned no lines for this app)" else output
                    } finally {
                        try {
                            process.destroy()
                        } catch (_: Exception) {
                        }
                    }
                }
            } ?: "(logcat timed out)"
        } catch (e: Exception) {
            "(logcat unavailable: ${e.message})"
        }
    }

    private fun addTextEntry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
