package com.etrisad.zenith.data.preferences

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.ui.theme.FontAxes
import com.etrisad.zenith.ui.theme.GSFlexSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeConfig {
    FOLLOW_SYSTEM, LIGHT, DARK
}

enum class FontOption {
    SYSTEM, GOOGLE_SANS_FLEX, NUNITO
}

enum class GSFlexPreset {
    ZENITH, NEO, COMPACT, AIRY, CUSTOM
}

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val FONT_OPTION = stringPreferencesKey("font_option")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCESSIBILITY_DISABLED = booleanPreferencesKey("accessibility_disabled")
        val SCREEN_TIME_TARGET = intPreferencesKey("screen_time_target")
        val EMERGENCY_RECHARGE_DURATION_MINUTES = intPreferencesKey("emergency_recharge_duration_minutes")
        val DELAY_APP_DURATION_SECONDS = intPreferencesKey("delay_app_duration_seconds")
        val SESSION_USAGE_OVERLAY_ENABLED = booleanPreferencesKey("session_usage_overlay_enabled")
        val SESSION_USAGE_OVERLAY_SIZE = intPreferencesKey("session_usage_overlay_size")
        val SESSION_USAGE_OVERLAY_OPACITY = intPreferencesKey("session_usage_overlay_opacity")
        val WHITELISTED_PACKAGES = stringPreferencesKey("whitelisted_packages")
        val LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
        val LAST_STREAK_CHECK_DATE = stringPreferencesKey("last_streak_check_date")
        val GLOBAL_CURRENT_STREAK = intPreferencesKey("global_current_streak")
        val GLOBAL_BEST_STREAK = intPreferencesKey("global_best_streak")
        val GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP = longPreferencesKey("global_last_streak_update_timestamp")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_DIRECTORY_URI = stringPreferencesKey("backup_directory_uri")
        val BACKUP_INTERVAL_HOURS = intPreferencesKey("backup_interval_hours")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val FLOATING_TAB_BAR_ENABLED = booleanPreferencesKey("floating_tab_bar_enabled")
        val EXPRESSIVE_COLORS = booleanPreferencesKey("expressive_colors")
        val TOTAL_USAGE_PILL_ENABLED = booleanPreferencesKey("total_usage_pill_enabled")
        val LAST_KNOWN_DAILY_USAGE = longPreferencesKey("last_known_daily_usage")
        val LAST_KNOWN_DAILY_USAGE_DATE = stringPreferencesKey("last_known_daily_usage_date")

        val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")
        val BEDTIME_START_TIME = stringPreferencesKey("bedtime_start_time")
        val BEDTIME_END_TIME = stringPreferencesKey("bedtime_end_time")
        val BEDTIME_DAYS = stringPreferencesKey("bedtime_days")
        val BEDTIME_DND_ENABLED = booleanPreferencesKey("bedtime_dnd_enabled")
        val BEDTIME_WIND_DOWN_ENABLED = booleanPreferencesKey("bedtime_wind_down_enabled")
        val BEDTIME_NOTIFICATION_ENABLED = booleanPreferencesKey("bedtime_notification_enabled")
        val BEDTIME_WHITELISTED_PACKAGES = stringPreferencesKey("bedtime_whitelisted_packages")
        val BEDTIME_CURRENT_STREAK = intPreferencesKey("bedtime_mode_streak_current")
        val BEDTIME_BEST_STREAK = intPreferencesKey("bedtime_mode_streak_best")
        val BEDTIME_STREAK_RESET_DATE = stringPreferencesKey("bedtime_streak_reset_date")
        val USER_NAME = stringPreferencesKey("user_name")
        val EARLY_KICK_ENABLED = booleanPreferencesKey("early_kick_enabled")
        val INTERCEPT_AUDIO_FOCUS_ENABLED = booleanPreferencesKey("intercept_audio_focus_enabled")
        val SHOW_DATABASE_INDICATOR = booleanPreferencesKey("show_database_indicator")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val PREFER_SYSTEM_USAGE_HISTORY = booleanPreferencesKey("prefer_system_usage_history")
        val ONBOARDING_STATS_COMPLETED = booleanPreferencesKey("onboarding_stats_completed")
        val ONBOARDING_UPDATE_COMPLETED = booleanPreferencesKey("onboarding_update_completed")
        val WHITELIST_INITIALIZED = booleanPreferencesKey("whitelist_initialized")
        val HUD_HIDE_FEATURE_LEARNED = booleanPreferencesKey("hud_hide_feature_learned")
        val SMART_REPAIR_ON_REFRESH = booleanPreferencesKey("smart_repair_on_refresh")
        val ALLOW_REPAIR_NON_UNAVAILABLE = booleanPreferencesKey("allow_repair_non_unavailable")
        val SHORTS_SCREEN_TIME_MS = longPreferencesKey("shorts_screen_time_ms")
        val CUSTOM_DELAY_ENABLED = booleanPreferencesKey("custom_delay_enabled")
        val DELAY_POWER_SAVE = longPreferencesKey("delay_power_save")
        val DELAY_OVERLAY_SHOWING = longPreferencesKey("delay_overlay_showing")
        val DELAY_GOAL_NEAR = longPreferencesKey("delay_goal_near")
        val DELAY_GOAL_MID = longPreferencesKey("delay_goal_mid")
        val DELAY_GOAL_FAR = longPreferencesKey("delay_goal_far")
        val DELAY_SHIELD_VERY_FAR = longPreferencesKey("delay_shield_very_far")
        val DELAY_SHIELD_FAR = longPreferencesKey("delay_shield_far")
        val DELAY_SHIELD_MID = longPreferencesKey("delay_shield_mid")
        val DELAY_SHIELD_NEAR = longPreferencesKey("delay_shield_near")
        val DELAY_DEFAULT = longPreferencesKey("delay_default")
        val MINDFUL_GATEWAY_ENABLED = booleanPreferencesKey("mindful_gateway_enabled")
        val REFRESH_ON_OPEN_USAGE_STATS = booleanPreferencesKey("refresh_on_open_usage_stats")
        val CHECK_UPDATE_ON_START = booleanPreferencesKey("check_update_on_start")
        val BATTERY_STATS_RESET_ENABLED = booleanPreferencesKey("battery_stats_reset_enabled")
        val LAST_CHARGE_TIMESTAMP = longPreferencesKey("last_charge_timestamp")
        val MANUAL_RESET_TIMESTAMPS = stringPreferencesKey("manual_reset_timestamps")

        val GS_FLEX_PRESET = stringPreferencesKey("gs_flex_preset")
        val GS_D_WGHT = floatPreferencesKey("gs_d_wght")
        val GS_D_WDTH = floatPreferencesKey("gs_d_wdth")
        val GS_D_OPSZ = floatPreferencesKey("gs_d_opsz")
        val GS_D_GRAD = floatPreferencesKey("gs_d_grad")
        val GS_D_SLNT = floatPreferencesKey("gs_d_slnt")
        val GS_D_ROND = floatPreferencesKey("gs_d_rond")
        val GS_H_WGHT = floatPreferencesKey("gs_h_wght")
        val GS_H_WDTH = floatPreferencesKey("gs_h_wdth")
        val GS_H_OPSZ = floatPreferencesKey("gs_h_opsz")
        val GS_H_GRAD = floatPreferencesKey("gs_h_grad")
        val GS_H_SLNT = floatPreferencesKey("gs_h_slnt")
        val GS_H_ROND = floatPreferencesKey("gs_h_rond")
        val GS_B_WGHT = floatPreferencesKey("gs_b_wght")
        val GS_B_WDTH = floatPreferencesKey("gs_b_wdth")
        val GS_B_OPSZ = floatPreferencesKey("gs_b_opsz")
        val GS_B_GRAD = floatPreferencesKey("gs_b_grad")
        val GS_B_SLNT = floatPreferencesKey("gs_b_slnt")
        val GS_B_ROND = floatPreferencesKey("gs_b_rond")
        val STREAK_RECOVERY_PERFORMED = booleanPreferencesKey("streak_recovery_performed")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            UserPreferences(
                themeConfig = ThemeConfig.valueOf(preferences[PreferencesKeys.THEME_CONFIG] ?: ThemeConfig.FOLLOW_SYSTEM.name),
                fontOption = FontOption.valueOf(preferences[PreferencesKeys.FONT_OPTION] ?: FontOption.GOOGLE_SANS_FLEX.name),
                dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true,
                accessibilityDisabled = preferences[PreferencesKeys.ACCESSIBILITY_DISABLED] ?: false,
                screenTimeTargetMinutes = preferences[PreferencesKeys.SCREEN_TIME_TARGET] ?: 0,
                emergencyRechargeDurationMinutes = preferences[PreferencesKeys.EMERGENCY_RECHARGE_DURATION_MINUTES] ?: 60,
                delayAppDurationSeconds = preferences[PreferencesKeys.DELAY_APP_DURATION_SECONDS] ?: 30,
                sessionUsageOverlayEnabled = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_ENABLED] ?: false,
                sessionUsageOverlaySize = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_SIZE] ?: 100,
                sessionUsageOverlayOpacity = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_OPACITY] ?: 90,
                whitelistedPackages = preferences[PreferencesKeys.WHITELISTED_PACKAGES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                lastResetDate = preferences[PreferencesKeys.LAST_RESET_DATE] ?: "",
                lastStreakCheckDate = preferences[PreferencesKeys.LAST_STREAK_CHECK_DATE] ?: "",
                globalCurrentStreak = preferences[PreferencesKeys.GLOBAL_CURRENT_STREAK] ?: 0,
                globalBestStreak = preferences[PreferencesKeys.GLOBAL_BEST_STREAK] ?: 0,
                globalLastStreakUpdateTimestamp = preferences[PreferencesKeys.GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP] ?: 0L,
                autoBackupEnabled = preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: false,
                backupDirectoryUri = preferences[PreferencesKeys.BACKUP_DIRECTORY_URI] ?: "",
                backupIntervalHours = preferences[PreferencesKeys.BACKUP_INTERVAL_HOURS] ?: 3,
                lastBackupTimestamp = preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] ?: 0L,
                floatingTabBarEnabled = preferences[PreferencesKeys.FLOATING_TAB_BAR_ENABLED] ?: false,
                expressiveColors = preferences[PreferencesKeys.EXPRESSIVE_COLORS] ?: false,
                totalUsagePillEnabled = preferences[PreferencesKeys.TOTAL_USAGE_PILL_ENABLED] ?: false,
                lastKnownDailyUsage = preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE] ?: 0L,
                lastKnownDailyUsageDate = preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE_DATE] ?: "",
                bedtimeEnabled = preferences[PreferencesKeys.BEDTIME_ENABLED] ?: false,
                bedtimeStartTime = preferences[PreferencesKeys.BEDTIME_START_TIME] ?: "22:00",
                bedtimeEndTime = preferences[PreferencesKeys.BEDTIME_END_TIME] ?: "07:00",
                bedtimeDays = preferences[PreferencesKeys.BEDTIME_DAYS]?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7),
                bedtimeDndEnabled = preferences[PreferencesKeys.BEDTIME_DND_ENABLED] ?: false,
                bedtimeWindDownEnabled = preferences[PreferencesKeys.BEDTIME_WIND_DOWN_ENABLED] ?: false,
                bedtimeNotificationEnabled = preferences[PreferencesKeys.BEDTIME_NOTIFICATION_ENABLED] ?: true,
                bedtimeWhitelistedPackages = preferences[PreferencesKeys.BEDTIME_WHITELISTED_PACKAGES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                bedtimeCurrentStreak = preferences[PreferencesKeys.BEDTIME_CURRENT_STREAK] ?: 0,
                bedtimeBestStreak = preferences[PreferencesKeys.BEDTIME_BEST_STREAK] ?: 0,
                bedtimeStreakResetDate = preferences[PreferencesKeys.BEDTIME_STREAK_RESET_DATE] ?: "",
                userName = preferences[PreferencesKeys.USER_NAME] ?: "User",
                earlyKickEnabled = preferences[PreferencesKeys.EARLY_KICK_ENABLED] ?: false,
                interceptAudioFocusEnabled = preferences[PreferencesKeys.INTERCEPT_AUDIO_FOCUS_ENABLED] ?: true,
                showDatabaseIndicator = preferences[PreferencesKeys.SHOW_DATABASE_INDICATOR] ?: false,
                developerModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false,
                lastSyncTimestamp = preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L,
                preferSystemUsageHistory = preferences[PreferencesKeys.PREFER_SYSTEM_USAGE_HISTORY] ?: true,
                onboardingStatsCompleted = preferences[PreferencesKeys.ONBOARDING_STATS_COMPLETED] ?: false,
                onboardingUpdateCompleted = preferences[PreferencesKeys.ONBOARDING_UPDATE_COMPLETED] ?: false,
                whitelistInitialized = preferences[PreferencesKeys.WHITELIST_INITIALIZED] ?: false,
                hudHideFeatureLearned = preferences[PreferencesKeys.HUD_HIDE_FEATURE_LEARNED] ?: false,
                smartRepairOnRefresh = preferences[PreferencesKeys.SMART_REPAIR_ON_REFRESH] ?: false,
                allowRepairNonUnavailable = preferences[PreferencesKeys.ALLOW_REPAIR_NON_UNAVAILABLE] ?: false,
                shortsScreenTimeMs = preferences[PreferencesKeys.SHORTS_SCREEN_TIME_MS] ?: 0L,
                customDelayEnabled = preferences[PreferencesKeys.CUSTOM_DELAY_ENABLED] ?: false,
                delayPowerSave = preferences[PreferencesKeys.DELAY_POWER_SAVE] ?: 5000L,
                delayOverlayShowing = preferences[PreferencesKeys.DELAY_OVERLAY_SHOWING] ?: 8000L,
                delayGoalNear = preferences[PreferencesKeys.DELAY_GOAL_NEAR] ?: 600L,
                delayGoalMid = preferences[PreferencesKeys.DELAY_GOAL_MID] ?: 1200L,
                delayGoalFar = preferences[PreferencesKeys.DELAY_GOAL_FAR] ?: 1800L,
                delayShieldVeryFar = preferences[PreferencesKeys.DELAY_SHIELD_VERY_FAR] ?: 5000L,
                delayShieldFar = preferences[PreferencesKeys.DELAY_SHIELD_FAR] ?: 3000L,
                delayShieldMid = preferences[PreferencesKeys.DELAY_SHIELD_MID] ?: 1500L,
                delayShieldNear = preferences[PreferencesKeys.DELAY_SHIELD_NEAR] ?: 600L,
                delayDefault = preferences[PreferencesKeys.DELAY_DEFAULT] ?: 1200L,
                mindfulGatewayEnabled = preferences[PreferencesKeys.MINDFUL_GATEWAY_ENABLED] ?: false,
                refreshOnOpenUsageStats = preferences[PreferencesKeys.REFRESH_ON_OPEN_USAGE_STATS] ?: false,
                checkUpdateOnStart = preferences[PreferencesKeys.CHECK_UPDATE_ON_START] ?: false,
                batteryStatsResetEnabled = preferences[PreferencesKeys.BATTERY_STATS_RESET_ENABLED] ?: false,
                lastChargeTimestamp = preferences[PreferencesKeys.LAST_CHARGE_TIMESTAMP] ?: 0L,
                manualResetTimestamps = preferences[PreferencesKeys.MANUAL_RESET_TIMESTAMPS]?.split(",")
                    ?.filter { it.contains(":") }
                    ?.associate { 
                        val parts = it.split(":")
                        parts[0] to (parts[1].toLongOrNull() ?: 0L)
                    } ?: emptyMap(),
                gsFlexSettings = GSFlexSettings(
                    preset = GSFlexPreset.valueOf(preferences[PreferencesKeys.GS_FLEX_PRESET] ?: GSFlexPreset.ZENITH.name),
                    display = FontAxes(
                        weight = preferences[PreferencesKeys.GS_D_WGHT] ?: 400f,
                        width = preferences[PreferencesKeys.GS_D_WDTH] ?: 100f,
                        opsz = preferences[PreferencesKeys.GS_D_OPSZ] ?: 72f,
                        grade = preferences[PreferencesKeys.GS_D_GRAD] ?: 0f,
                        slant = preferences[PreferencesKeys.GS_D_SLNT] ?: 0f,
                        roundness = preferences[PreferencesKeys.GS_D_ROND] ?: 0f
                    ),
                    headline = FontAxes(
                        weight = preferences[PreferencesKeys.GS_H_WGHT] ?: 400f,
                        width = preferences[PreferencesKeys.GS_H_WDTH] ?: 100f,
                        opsz = preferences[PreferencesKeys.GS_H_OPSZ] ?: 32f,
                        grade = preferences[PreferencesKeys.GS_H_GRAD] ?: 0f,
                        slant = preferences[PreferencesKeys.GS_H_SLNT] ?: 0f,
                        roundness = preferences[PreferencesKeys.GS_H_ROND] ?: 0f
                    ),
                    body = FontAxes(
                        weight = preferences[PreferencesKeys.GS_B_WGHT] ?: 400f,
                        width = preferences[PreferencesKeys.GS_B_WDTH] ?: 100f,
                        opsz = preferences[PreferencesKeys.GS_B_OPSZ] ?: 16f,
                        grade = preferences[PreferencesKeys.GS_B_GRAD] ?: 0f,
                        slant = preferences[PreferencesKeys.GS_B_SLNT] ?: 0f,
                        roundness = preferences[PreferencesKeys.GS_B_ROND] ?: 0f
                    ),
                ),
                streakRecoveryPerformed = preferences[PreferencesKeys.STREAK_RECOVERY_PERFORMED] ?: false
            )
        }


    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.USER_NAME] = name }
    }

    suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.THEME_CONFIG] = themeConfig.name }
    }

    suspend fun setFontOption(fontOption: FontOption) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.FONT_OPTION] = fontOption.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAccessibilityDisabled(disabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.ACCESSIBILITY_DISABLED] = disabled }
    }

    suspend fun setScreenTimeTarget(minutes: Int) {
        context.dataStore.edit { preferences ->
            val currentTarget = preferences[PreferencesKeys.SCREEN_TIME_TARGET] ?: 0
            if (minutes > currentTarget && currentTarget > 0) preferences[PreferencesKeys.GLOBAL_CURRENT_STREAK] = 0
            preferences[PreferencesKeys.SCREEN_TIME_TARGET] = minutes
        }
    }

    suspend fun setEmergencyRechargeDuration(minutes: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.EMERGENCY_RECHARGE_DURATION_MINUTES] = minutes }
    }

    suspend fun setDelayAppDuration(seconds: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_APP_DURATION_SECONDS] = seconds }
    }

    suspend fun setSessionUsageOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setSessionUsageOverlaySize(size: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_SIZE] = size }
    }

    suspend fun setSessionUsageOverlayOpacity(opacity: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_OPACITY] = opacity }
    }

    suspend fun setWhitelistedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.WHITELISTED_PACKAGES] = packages.joinToString(",") }
    }

    suspend fun initializeDefaultWhitelist() {
        val prefs = userPreferencesFlow.first()
        if (!prefs.whitelistInitialized && prefs.whitelistedPackages.isEmpty()) {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val installedApps = try {
                    pm.getInstalledApplications(0)
                } catch (e: Exception) {
                    emptyList()
                }

                val systemApps = installedApps.filter {
                    val isSystemFlag = (it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    val hasLauncher = pm.getLaunchIntentForPackage(it.packageName) != null
                    val pkg = it.packageName

                    val isCoreComponent = pkg == "android" ||
                            pkg.startsWith("com.android.settings") ||
                            pkg.startsWith("com.android.systemui") ||
                            pkg.startsWith("com.android.shell") ||
                            pkg.startsWith("com.android.phone") ||
                            pkg.startsWith("com.android.angle") ||
                            pkg.startsWith("com.android.providers") ||
                            pkg.startsWith("com.google.android.angle") ||
                            pkg.startsWith("com.google.android.setupwizard") ||
                            pkg.contains("restore") ||
                            pkg.contains("overlay") ||
                            pkg.contains("documentsui")

                    isSystemFlag && (!hasLauncher || isCoreComponent)
                }.map { it.packageName }.toSet()

                if (systemApps.isNotEmpty()) {
                    setWhitelistedPackages(systemApps)
                }

                context.dataStore.edit { preferences ->
                    preferences[PreferencesKeys.WHITELIST_INITIALIZED] = true
                }
            }
        }
    }

    suspend fun setLastResetDate(date: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.LAST_RESET_DATE] = date }
    }

    suspend fun setLastStreakCheckDate(date: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.LAST_STREAK_CHECK_DATE] = date }
    }

    suspend fun updateGlobalStreak(current: Int, best: Int, timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GLOBAL_CURRENT_STREAK] = current
            preferences[PreferencesKeys.GLOBAL_BEST_STREAK] = best
            preferences[PreferencesKeys.GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP] = timestamp
        }
    }

    suspend fun refreshGlobalStreak(shieldRepository: ShieldRepository): Pair<Int, Int> {
        val prefs = userPreferencesFlow.first()
        val targetMillis = prefs.screenTimeTargetMinutes * 60 * 1000L
        if (targetMillis <= 0) {
            updateGlobalStreak(0, prefs.globalBestStreak, System.currentTimeMillis())
            return Pair(0, prefs.globalBestStreak)
        }

        shieldRepository.isShieldsLoaded.first { it }
        val dbUsage = shieldRepository.getAllUsage().first()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val (launcherApps, launcherPackage) = try {
            val pm = context.packageManager
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
            val lPkg = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            apps to lPkg
        } catch (_: Exception) { emptySet<String>() to null }

        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

        val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val todayStart = calendar.timeInMillis

        val stats = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager).appUsageMap
        var totalToday = 0L
        stats.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in launcherApps) totalToday += time
        }

        val globalHistory = dbUsage.filter { it.packageName == "TOTAL" }
        val oldestHistoryDate = globalHistory.map { it.date }.minOrNull()

        var pastStreak = 0
        var foundDefiniteFailure = false
        val c = Calendar.getInstance()
        for (i in 1..365) {
            c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
            val dStr = dateFormat.format(c.time)
            var usage = globalHistory.find { it.date == dStr }?.usageTimeMillis

            if (usage == null) {
                if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                    usage = 0L
                } else if (i <= 30) {
                    usage = fetchSystemTotalUsageForDate(usageStatsManager, c.timeInMillis, launcherApps, excludePackages)
                }
            }

            if (usage != null) {
                if (usage <= targetMillis) pastStreak++
                else { foundDefiniteFailure = true; break }
            } else break
        }

        val lastUpdateDayStart = Calendar.getInstance().apply {
            timeInMillis = prefs.globalLastStreakUpdateTimestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val isLastUpdateYesterday = lastUpdateDayStart == todayStart - 86400000L
        val isLastUpdateToday = lastUpdateDayStart == todayStart

        val isSuccessToday = totalToday <= targetMillis
        val liveStreak = if (isSuccessToday) {
            if (!foundDefiniteFailure && (isLastUpdateYesterday || isLastUpdateToday)) {
                maxOf(pastStreak + 1, prefs.globalCurrentStreak + (if (isLastUpdateYesterday) 1 else 0))
            } else {
                pastStreak + 1
            }
        } else 0

        var bestStreak = prefs.globalBestStreak
        var tempStreak = 0
        val todayStr = dateFormat.format(Date(todayStart))
        val startDateStr = oldestHistoryDate ?: todayStr
        val calendarForBest = Calendar.getInstance()
        try {
            val startD = dateFormat.parse(startDateStr) ?: Date()
            calendarForBest.time = startD
            val todayDate = dateFormat.parse(todayStr) ?: Date()
            
            while (!calendarForBest.time.after(todayDate)) {
                val dStr = dateFormat.format(calendarForBest.time)
                val usage = if (dStr == todayStr) totalToday else globalHistory.find { it.date == dStr }?.usageTimeMillis
                
                val effectiveUsage = usage ?: 0L
                if (effectiveUsage <= targetMillis) {
                    tempStreak++
                    bestStreak = maxOf(bestStreak, tempStreak)
                } else {
                    tempStreak = 0
                }
                calendarForBest.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (_: Exception) {}

        bestStreak = maxOf(bestStreak, liveStreak)
        updateGlobalStreak(liveStreak, bestStreak, now)
        return Pair(liveStreak, bestStreak)
    }

    suspend fun refreshAllAppStreaks(shieldRepository: ShieldRepository) {
        shieldRepository.isShieldsLoaded.first { it }
        val shields = shieldRepository.allShields.first()
        val allUsage = shieldRepository.getAllUsage().first().groupBy { it.packageName }
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date(now))

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val todayUsageMap = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager).appUsageMap

        shields.forEach { shield ->
            val pkg = shield.packageName
            val history = allUsage[pkg] ?: emptyList()
            val oldestHistoryDate = history.map { it.date }.minOrNull()
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L

            if (limitMillis <= 0 && shield.type == FocusType.SHIELD) {
                shieldRepository.updateShield(shield.copy(currentStreak = 0, bestStreak = 0))
                return@forEach
            }

            val todayUsage = todayUsageMap[pkg] ?: 0L

            var pastStreak = 0
            var foundDefiniteFailure = false
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val c = Calendar.getInstance()
            for (i in 1..365) {
                c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
                val dStr = dateFormat.format(c.time)
                var usage = history.find { it.date == dStr }?.usageTimeMillis

                if (usage == null) {
                    if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                        if (shield.type == FocusType.SHIELD) usage = 0L else break
                    } else if (i <= 30) {
                        usage = fetchSystemAppUsageForDate(usageStatsManager, pkg, c.timeInMillis)
                    }
                }

                if (usage != null) {
                    val success = if (shield.type == FocusType.GOAL) usage >= limitMillis else usage <= limitMillis
                    if (success) pastStreak++ else { foundDefiniteFailure = true; break }
                } else break
            }

            val isSuccessToday = if (shield.type == FocusType.GOAL) todayUsage >= limitMillis else todayUsage <= limitMillis

            val lastUpdateDayStart = Calendar.getInstance().apply {
                timeInMillis = shield.lastStreakUpdateTimestamp
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val isLastUpdateYesterday = lastUpdateDayStart == todayStart - 86400000L
            val isLastUpdateToday = lastUpdateDayStart == todayStart

            val currentStreak = if (shield.type == FocusType.GOAL) {
                if (isSuccessToday) {
                    if (!foundDefiniteFailure && (isLastUpdateYesterday || isLastUpdateToday)) {
                        maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateYesterday) 1 else 0))
                    } else pastStreak + 1
                } else pastStreak
            } else {
                if (isSuccessToday) {
                    if (!foundDefiniteFailure && (isLastUpdateYesterday || isLastUpdateToday)) {
                        maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateYesterday) 1 else 0))
                    } else pastStreak + 1
                } else 0
            }

            var bestStreak = shield.bestStreak
            var tempStreak = 0
            val calendarForBest = Calendar.getInstance()
            try {
                val startDateStr = oldestHistoryDate ?: todayStr
                val startD = dateFormat.parse(startDateStr) ?: Date()
                calendarForBest.time = startD
                val todayDate = dateFormat.parse(todayStr) ?: Date()

                while (!calendarForBest.time.after(todayDate)) {
                    val dStr = dateFormat.format(calendarForBest.time)
                    val usage = if (dStr == todayStr) todayUsage else history.find { it.date == dStr }?.usageTimeMillis
                    
                    val effectiveUsage = if (usage == null && shield.type == FocusType.SHIELD) 0L else usage
                    if (effectiveUsage != null) {
                        val success = if (shield.type == FocusType.GOAL) effectiveUsage >= limitMillis else effectiveUsage <= limitMillis
                        if (success) {
                            tempStreak++
                            bestStreak = maxOf(bestStreak, tempStreak)
                        } else {
                            tempStreak = 0
                        }
                    } else {
                        tempStreak = 0
                    }
                    calendarForBest.add(Calendar.DAY_OF_YEAR, 1)
                }
            } catch (_: Exception) {}

            shieldRepository.updateShield(shield.copy(
                currentStreak = currentStreak,
                bestStreak = maxOf(bestStreak, currentStreak),
                remainingTimeMillis = if (shield.type == FocusType.GOAL) (limitMillis - todayUsage).coerceAtLeast(0L) else shield.remainingTimeMillis,
                lastStreakUpdateTimestamp = if (isSuccessToday && (shield.type == FocusType.GOAL || todayUsage > 0)) now else shield.lastStreakUpdateTimestamp
            ))
        }
    }

    suspend fun refreshBedtimeStreak(): Pair<Int, Int> {
        val prefs = userPreferencesFlow.first()
        if (!prefs.bedtimeEnabled) return Pair(0, prefs.bedtimeBestStreak)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val startH = try { prefs.bedtimeStartTime.split(":")[0].toInt() } catch(_: Exception) { 22 }
        val startM = try { prefs.bedtimeStartTime.split(":")[1].toInt() } catch(_: Exception) { 0 }
        val endH = try { prefs.bedtimeEndTime.split(":")[0].toInt() } catch(_: Exception) { 7 }
        val endM = try { prefs.bedtimeEndTime.split(":")[1].toInt() } catch(_: Exception) { 0 }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = try {
            context.packageManager.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (_: Exception) { null }

        val launcherApps = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()

        val excludePackages = setOfNotNull(context.packageName, launcherPackage) + prefs.whitelistedPackages + prefs.bedtimeWhitelistedPackages

        var liveStreak = 0
        var currentBest = prefs.bedtimeBestStreak

        for (i in 0..60) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)

            if (prefs.bedtimeStreakResetDate.isNotEmpty()) {
                val dayStr = sdf.format(cal.time)
                if (dayStr < prefs.bedtimeStreakResetDate) break
            }

            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek !in prefs.bedtimeDays) continue

            val startCal = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                set(Calendar.HOUR_OF_DAY, startH)
                set(Calendar.MINUTE, startM)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val endCal = Calendar.getInstance().apply {
                timeInMillis = startCal.timeInMillis
                if (endH < startH || (endH == startH && endM <= startM)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                set(Calendar.HOUR_OF_DAY, endH)
                set(Calendar.MINUTE, endM)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val startTime = startCal.timeInMillis
            val endTime = endCal.timeInMillis

            if (startTime > now) continue

            val isSessionCompleted = endTime <= now
            val actualEnd = if (isSessionCompleted) endTime else now
            if (actualEnd <= startTime) continue

            val totalDuration = endTime - startTime
            val targetMillis = (totalDuration * 0.1).toLong()

            val events = try {
                usageStatsManager.queryEvents(startTime - 30 * 60 * 1000L, actualEnd)
            } catch (e: Exception) {
                null
            }
            val event = android.app.usage.UsageEvents.Event()
            val usageMap = mutableMapOf<String, Long>()
            var activePkg: String? = null
            var activeStartTime = startTime

            while (events?.hasNextEvent() == true) {
                events.getNextEvent(event)
                val pkg = event.packageName
                val time = event.timeStamp

                if (time < startTime) {
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            activePkg = pkg
                            activeStartTime = startTime
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            activePkg = null
                        }
                    }
                    continue
                }

                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (activePkg != null) {
                            val duration = time - activeStartTime
                            if (duration > 0) usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + duration
                        }
                        activePkg = pkg
                        activeStartTime = time
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (activePkg == pkg) {
                            val duration = time - activeStartTime
                            if (duration > 0) usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                            activePkg = null
                        }
                    }
                }
            }
            activePkg?.let { pkg ->
                val duration = actualEnd - activeStartTime
                if (duration > 0) usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
            }

            var usage = 0L
            usageMap.forEach { (pkg, time) ->
                if (pkg !in excludePackages && pkg in launcherApps) {
                    usage += time
                }
            }

            if (usage > targetMillis) {
                break
            } else if (isSessionCompleted) {
                liveStreak++
            }
        }

        currentBest = maxOf(currentBest, liveStreak)
        updateBedtimeStreak(liveStreak, currentBest)
        return Pair(liveStreak, currentBest)
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setBackupDirectoryUri(uri: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BACKUP_DIRECTORY_URI] = uri }
    }

    suspend fun setBackupIntervalHours(hours: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BACKUP_INTERVAL_HOURS] = hours }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] = timestamp }
    }

    suspend fun setFloatingTabBarEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.FLOATING_TAB_BAR_ENABLED] = enabled }
    }

    suspend fun setExpressiveColors(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.EXPRESSIVE_COLORS] = enabled }
    }

    suspend fun setTotalUsagePillEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.TOTAL_USAGE_PILL_ENABLED] = enabled }
    }

    suspend fun setLastKnownDailyUsage(usage: Long, date: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE] = usage; preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE_DATE] = date }
    }

    suspend fun setBedtimeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_ENABLED] = enabled }
    }

    suspend fun setBedtimeStartTime(time: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_START_TIME] = time }
    }

    suspend fun setBedtimeEndTime(time: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_END_TIME] = time }
    }

    suspend fun setBedtimeDays(days: Set<Int>) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_DAYS] = days.joinToString(",") }
    }

    suspend fun setBedtimeDndEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_DND_ENABLED] = enabled }
    }

    suspend fun setBedtimeWindDownEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_WIND_DOWN_ENABLED] = enabled }
    }

    suspend fun setBedtimeNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setBedtimeWhitelistedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BEDTIME_WHITELISTED_PACKAGES] = packages.joinToString(",") }
    }

    suspend fun updateBedtimeStreak(current: Int, best: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_CURRENT_STREAK] = current
            preferences[PreferencesKeys.BEDTIME_BEST_STREAK] = best
        }
    }

    suspend fun resetBedtimeStreak() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_CURRENT_STREAK] = 0
            preferences[PreferencesKeys.BEDTIME_BEST_STREAK] = 0
            preferences[PreferencesKeys.BEDTIME_STREAK_RESET_DATE] = today
        }
    }

    suspend fun setEarlyKickEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.EARLY_KICK_ENABLED] = enabled }
    }

    suspend fun setInterceptAudioFocusEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.INTERCEPT_AUDIO_FOCUS_ENABLED] = enabled }
    }

    suspend fun setShowDatabaseIndicator(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SHOW_DATABASE_INDICATOR] = enabled }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp }
    }

    suspend fun setPreferSystemUsageHistory(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PREFER_SYSTEM_USAGE_HISTORY] = enabled }
    }

    suspend fun setOnboardingStatsCompleted(completed: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.ONBOARDING_STATS_COMPLETED] = completed }
    }

    suspend fun setOnboardingUpdateCompleted(completed: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.ONBOARDING_UPDATE_COMPLETED] = completed }
    }

    suspend fun setHudHideFeatureLearned(learned: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.HUD_HIDE_FEATURE_LEARNED] = learned }
    }

    suspend fun setSmartRepairOnRefresh(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SMART_REPAIR_ON_REFRESH] = enabled }
    }

    suspend fun setAllowRepairNonUnavailable(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.ALLOW_REPAIR_NON_UNAVAILABLE] = enabled }
    }

    suspend fun setShortsScreenTimeMs(ms: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SHORTS_SCREEN_TIME_MS] = ms }
    }

    suspend fun setCustomDelayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.CUSTOM_DELAY_ENABLED] = enabled }
    }

    suspend fun setDelayPowerSave(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_POWER_SAVE] = delay }
    }

    suspend fun setDelayOverlayShowing(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_OVERLAY_SHOWING] = delay }
    }

    suspend fun setDelayGoalNear(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_GOAL_NEAR] = delay }
    }

    suspend fun setDelayGoalMid(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_GOAL_MID] = delay }
    }

    suspend fun setDelayGoalFar(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_GOAL_FAR] = delay }
    }

    suspend fun setDelayShieldVeryFar(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_SHIELD_VERY_FAR] = delay }
    }

    suspend fun setDelayShieldFar(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_SHIELD_FAR] = delay }
    }

    suspend fun setDelayShieldMid(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_SHIELD_MID] = delay }
    }

    suspend fun setDelayShieldNear(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_SHIELD_NEAR] = delay }
    }

    suspend fun setDelayDefault(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DELAY_DEFAULT] = delay }
    }

    suspend fun setMindfulGatewayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.MINDFUL_GATEWAY_ENABLED] = enabled }
    }

    suspend fun setRefreshOnOpenUsageStats(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.REFRESH_ON_OPEN_USAGE_STATS] = enabled }
    }

    suspend fun setCheckUpdateOnStart(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.CHECK_UPDATE_ON_START] = enabled }
    }

    suspend fun setBatteryStatsResetEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BATTERY_STATS_RESET_ENABLED] = enabled }
    }

    suspend fun updateLastChargeTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.LAST_CHARGE_TIMESTAMP] = timestamp }
    }

    suspend fun resetAppStats(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentMap = preferences[PreferencesKeys.MANUAL_RESET_TIMESTAMPS]?.split(",")
                ?.filter { it.contains(":") }
                ?.associate { 
                    val parts = it.split(":")
                    parts[0] to parts[1]
                }?.toMutableMap() ?: mutableMapOf()
            
            currentMap[packageName] = System.currentTimeMillis().toString()
            preferences[PreferencesKeys.MANUAL_RESET_TIMESTAMPS] = currentMap.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    suspend fun setGSFlexSettings(settings: GSFlexSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GS_FLEX_PRESET] = settings.preset.name
            preferences[PreferencesKeys.GS_D_WGHT] = settings.display.weight
            preferences[PreferencesKeys.GS_D_WDTH] = settings.display.width
            preferences[PreferencesKeys.GS_D_OPSZ] = settings.display.opsz
            preferences[PreferencesKeys.GS_D_GRAD] = settings.display.grade
            preferences[PreferencesKeys.GS_D_SLNT] = settings.display.slant
            preferences[PreferencesKeys.GS_D_ROND] = settings.display.roundness
            preferences[PreferencesKeys.GS_H_WGHT] = settings.headline.weight
            preferences[PreferencesKeys.GS_H_WDTH] = settings.headline.width
            preferences[PreferencesKeys.GS_H_OPSZ] = settings.headline.opsz
            preferences[PreferencesKeys.GS_H_GRAD] = settings.headline.grade
            preferences[PreferencesKeys.GS_H_SLNT] = settings.headline.slant
            preferences[PreferencesKeys.GS_H_ROND] = settings.headline.roundness
            preferences[PreferencesKeys.GS_B_WGHT] = settings.body.weight
            preferences[PreferencesKeys.GS_B_WDTH] = settings.body.width
            preferences[PreferencesKeys.GS_B_OPSZ] = settings.body.opsz
            preferences[PreferencesKeys.GS_B_GRAD] = settings.body.grade
            preferences[PreferencesKeys.GS_B_SLNT] = settings.body.slant
            preferences[PreferencesKeys.GS_B_ROND] = settings.body.roundness
        }
    }

    suspend fun runManualStreakRecovery(shieldRepository: ShieldRepository) {
        val prefs = userPreferencesFlow.first()
        val targetMillis = prefs.screenTimeTargetMinutes * 60 * 1000L
        val dbUsage = shieldRepository.getAllUsage().first()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val todayStart = calendar.timeInMillis
        if (targetMillis > 0) {
            val (launcherApps, launcherPackage) = try {
                val pm = context.packageManager
                val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
                val lPkg = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                apps to lPkg
            } catch (_: Exception) { emptySet<String>() to null }
            val excludePackages = setOfNotNull(context.packageName, launcherPackage)

            val globalHistory = dbUsage.filter { it.packageName == "TOTAL" }
            val oldestHistoryDate = globalHistory.map { it.date }.minOrNull()
            var pastStreak = 0
            for (i in 1..365) {
                val c = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -i) }
                val dStr = dateFormat.format(c.time)
                var usage = globalHistory.find { it.date == dStr }?.usageTimeMillis
                if (usage == null) {
                    if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                        usage = 0L
                    } else if (i <= 30) {
                        usage = fetchSystemTotalUsageForDate(usageStatsManager, c.timeInMillis, launcherApps, excludePackages)
                    }
                }

                if (usage != null) {
                    if (usage <= targetMillis) pastStreak++ else break
                } else break
            }

            val stats = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager).appUsageMap
            var totalToday = 0L
            stats.forEach { (pkg, time) -> if (pkg !in excludePackages && pkg in launcherApps) totalToday += time }

            val isSuccessToday = totalToday <= targetMillis
            if (isSuccessToday && (pastStreak + 1) < prefs.globalBestStreak) {
                var provenDays = 1
                for (j in 1..3) {
                    val cal = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -j) }
                    val u = globalHistory.find { it.date == dateFormat.format(cal.time) }?.usageTimeMillis
                        ?: fetchSystemTotalUsageForDate(usageStatsManager, cal.timeInMillis, launcherApps, excludePackages)
                    if (u <= targetMillis) provenDays++ else break
                }
                if (provenDays >= 3) {
                    updateGlobalStreak(maxOf(pastStreak + 1, prefs.globalBestStreak), prefs.globalBestStreak, System.currentTimeMillis())
                }
            }
        }
        val shields = shieldRepository.allShields.first()
        val allUsage = dbUsage.groupBy { it.packageName }
        val todayUsageMap = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager).appUsageMap

        shields.forEach { shield ->
            val pkg = shield.packageName
            val history = allUsage[pkg] ?: emptyList()
            val oldestHistoryDate = history.map { it.date }.minOrNull()
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            if (limitMillis <= 0 && shield.type == FocusType.SHIELD) return@forEach

            var pastStreak = 0
            for (i in 1..365) {
                val c = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -i) }
                val dStr = dateFormat.format(c.time)
                var usage = history.find { it.date == dStr }?.usageTimeMillis
                if (usage == null) {
                    if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                        if (shield.type == FocusType.SHIELD) usage = 0L else break
                    } else if (i <= 30) {
                        usage = fetchSystemAppUsageForDate(usageStatsManager, pkg, c.timeInMillis)
                    }
                }

                if (usage != null) {
                    val success = if (shield.type == FocusType.GOAL) usage >= limitMillis else usage <= limitMillis
                    if (success) pastStreak++ else break
                } else break
            }

            val todayUsage = todayUsageMap[pkg] ?: 0L
            val isSuccessToday = if (shield.type == FocusType.GOAL) todayUsage >= limitMillis else todayUsage <= limitMillis
            val currentStreak = if (shield.type == FocusType.GOAL) (if (isSuccessToday) pastStreak + 1 else pastStreak) else (if (isSuccessToday) pastStreak + 1 else 0)

            if (isSuccessToday && currentStreak < shield.bestStreak) {
                var provenDays = 1
                for (j in 1..3) {
                    val cal = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -j) }
                    val u = history.find { it.date == dateFormat.format(cal.time) }?.usageTimeMillis
                        ?: fetchSystemAppUsageForDate(usageStatsManager, pkg, cal.timeInMillis)
                    val success = if (shield.type == FocusType.GOAL) u >= limitMillis else u <= limitMillis
                    if (success) provenDays++ else break
                }
                if (provenDays >= 3) {
                    shieldRepository.updateShield(shield.copy(currentStreak = maxOf(currentStreak, shield.bestStreak)))
                }
            }
        }
    }

    suspend fun setStreakRecoveryPerformed(performed: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.STREAK_RECOVERY_PERFORMED] = performed }
    }

    private fun fetchSystemTotalUsageForDate(
        usm: UsageStatsManager,
        startTime: Long,
        launcherApps: Set<String>,
        excludePackages: Set<String>
    ): Long {
        val stats = try {
            usm.queryAndAggregateUsageStats(startTime, startTime + 86400000L)
        } catch (e: Exception) {
            null
        }
        var total = 0L
        stats?.forEach { (pkg, stat) ->
            if (pkg in launcherApps && pkg !in excludePackages) {
                total += stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            }
        }
        return total
    }

    private fun fetchSystemAppUsageForDate(
        usm: UsageStatsManager,
        packageName: String,
        startTime: Long
    ): Long {
        val stats = try {
            usm.queryAndAggregateUsageStats(startTime, startTime + 86400000L)
        } catch (e: Exception) {
            null
        }
        val stat = stats?.get(packageName) ?: return 0L
        return stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
    }

    suspend fun resetCustomDelays() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.DELAY_POWER_SAVE); preferences.remove(PreferencesKeys.DELAY_OVERLAY_SHOWING); preferences.remove(PreferencesKeys.DELAY_GOAL_NEAR)
            preferences.remove(PreferencesKeys.DELAY_GOAL_MID); preferences.remove(PreferencesKeys.DELAY_GOAL_FAR); preferences.remove(PreferencesKeys.DELAY_SHIELD_VERY_FAR)
            preferences.remove(PreferencesKeys.DELAY_SHIELD_FAR); preferences.remove(PreferencesKeys.DELAY_SHIELD_MID); preferences.remove(PreferencesKeys.DELAY_SHIELD_NEAR)
            preferences.remove(PreferencesKeys.DELAY_DEFAULT)
        }
    }
}

data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val fontOption: FontOption = FontOption.GOOGLE_SANS_FLEX,
    val dynamicColor: Boolean = true,
    val accessibilityDisabled: Boolean = false,
    val screenTimeTargetMinutes: Int = 0,
    val emergencyRechargeDurationMinutes: Int = 60,
    val delayAppDurationSeconds: Int = 30,
    val sessionUsageOverlayEnabled: Boolean = false,
    val sessionUsageOverlaySize: Int = 100,
    val sessionUsageOverlayOpacity: Int = 90,
    val whitelistedPackages: Set<String> = emptySet(),
    val lastResetDate: String = "",
    val lastStreakCheckDate: String = "",
    val globalCurrentStreak: Int = 0,
    val globalBestStreak: Int = 0,
    val globalLastStreakUpdateTimestamp: Long = 0L,
    val autoBackupEnabled: Boolean = false,
    val backupDirectoryUri: String = "",
    val backupIntervalHours: Int = 3,
    val lastBackupTimestamp: Long = 0L,
    val floatingTabBarEnabled: Boolean = false,
    val expressiveColors: Boolean = false,
    val totalUsagePillEnabled: Boolean = false,
    val lastKnownDailyUsage: Long = 0L,
    val lastKnownDailyUsageDate: String = "",
    val bedtimeEnabled: Boolean = false,
    val bedtimeStartTime: String = "22:00",
    val bedtimeEndTime: String = "07:00",
    val bedtimeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val bedtimeDndEnabled: Boolean = false,
    val bedtimeWindDownEnabled: Boolean = false,
    val bedtimeNotificationEnabled: Boolean = true,
    val bedtimeWhitelistedPackages: Set<String> = emptySet(),
    val bedtimeCurrentStreak: Int = 0,
    val bedtimeBestStreak: Int = 0,
    val bedtimeStreakResetDate: String = "",
    val userName: String = "User",
    val earlyKickEnabled: Boolean = false,
    val interceptAudioFocusEnabled: Boolean = true,
    val showDatabaseIndicator: Boolean = false,
    val developerModeEnabled: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val preferSystemUsageHistory: Boolean = true,
    val onboardingStatsCompleted: Boolean = false,
    val onboardingUpdateCompleted: Boolean = false,
    val whitelistInitialized: Boolean = false,
    val hudHideFeatureLearned: Boolean = false,
    val smartRepairOnRefresh: Boolean = false,
    val allowRepairNonUnavailable: Boolean = false,
    val shortsScreenTimeMs: Long = 0L,
    val customDelayEnabled: Boolean = false,
    val delayPowerSave: Long = 5000L,
    val delayOverlayShowing: Long = 8000L,
    val delayGoalNear: Long = 600L,
    val delayGoalMid: Long = 1200L,
    val delayGoalFar: Long = 1800L,
    val delayShieldVeryFar: Long = 5000L,
    val delayShieldFar: Long = 3000L,
    val delayShieldMid: Long = 1500L,
    val delayShieldNear: Long = 600L,
    val delayDefault: Long = 1200L,
    val mindfulGatewayEnabled: Boolean = false,
    val refreshOnOpenUsageStats: Boolean = false,
    val checkUpdateOnStart: Boolean = false,
    val batteryStatsResetEnabled: Boolean = false,
    val lastChargeTimestamp: Long = 0L,
    val manualResetTimestamps: Map<String, Long> = emptyMap(),
    val gsFlexSettings: GSFlexSettings = GSFlexSettings(),
    val streakRecoveryPerformed: Boolean = false
)