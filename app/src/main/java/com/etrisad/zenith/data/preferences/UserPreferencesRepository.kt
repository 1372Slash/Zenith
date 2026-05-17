package com.etrisad.zenith.data.preferences

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        val USER_NAME = stringPreferencesKey("user_name")
        val EARLY_KICK_ENABLED = booleanPreferencesKey("early_kick_enabled")
        val INTERCEPT_AUDIO_FOCUS_ENABLED = booleanPreferencesKey("intercept_audio_focus_enabled")
        val SHOW_DATABASE_INDICATOR = booleanPreferencesKey("show_database_indicator")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val PREFER_SYSTEM_USAGE_HISTORY = booleanPreferencesKey("prefer_system_usage_history")
        val ONBOARDING_STATS_COMPLETED = booleanPreferencesKey("onboarding_stats_completed")
        val HUD_HIDE_FEATURE_LEARNED = booleanPreferencesKey("hud_hide_feature_learned")
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
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeConfig = ThemeConfig.valueOf(
                preferences[PreferencesKeys.THEME_CONFIG] ?: ThemeConfig.FOLLOW_SYSTEM.name
            )
            val fontOption = FontOption.valueOf(
                preferences[PreferencesKeys.FONT_OPTION] ?: FontOption.NUNITO.name
            )
            val dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
            val accessibilityDisabled = preferences[PreferencesKeys.ACCESSIBILITY_DISABLED] ?: false
            val screenTimeTarget = preferences[PreferencesKeys.SCREEN_TIME_TARGET] ?: 0
            val emergencyRechargeDuration = preferences[PreferencesKeys.EMERGENCY_RECHARGE_DURATION_MINUTES] ?: 60
            val delayAppDuration = preferences[PreferencesKeys.DELAY_APP_DURATION_SECONDS] ?: 30
            val sessionUsageOverlayEnabled = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_ENABLED] ?: false
            val sessionUsageOverlaySize = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_SIZE] ?: 100
            val sessionUsageOverlayOpacity = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_OPACITY] ?: 90
            val whitelistedPackages = preferences[PreferencesKeys.WHITELISTED_PACKAGES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            val lastResetDate = preferences[PreferencesKeys.LAST_RESET_DATE] ?: ""
            val lastStreakCheckDate = preferences[PreferencesKeys.LAST_STREAK_CHECK_DATE] ?: ""
            val globalCurrentStreak = preferences[PreferencesKeys.GLOBAL_CURRENT_STREAK] ?: 0
            val globalBestStreak = preferences[PreferencesKeys.GLOBAL_BEST_STREAK] ?: 0
            val globalLastStreakUpdateTimestamp = preferences[PreferencesKeys.GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP] ?: 0L
            val autoBackupEnabled = preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: false
            val backupDirectoryUri = preferences[PreferencesKeys.BACKUP_DIRECTORY_URI] ?: ""
            val backupIntervalHours = preferences[PreferencesKeys.BACKUP_INTERVAL_HOURS] ?: 3
            val lastBackupTimestamp = preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] ?: 0L
            val floatingTabBarEnabled = preferences[PreferencesKeys.FLOATING_TAB_BAR_ENABLED] ?: false
            val expressiveColors = preferences[PreferencesKeys.EXPRESSIVE_COLORS] ?: false
            val totalUsagePillEnabled = preferences[PreferencesKeys.TOTAL_USAGE_PILL_ENABLED] ?: false
            val lastKnownDailyUsage = preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE] ?: 0L
            val lastKnownDailyUsageDate = preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE_DATE] ?: ""
            
            val bedtimeEnabled = preferences[PreferencesKeys.BEDTIME_ENABLED] ?: false
            val bedtimeStartTime = preferences[PreferencesKeys.BEDTIME_START_TIME] ?: "22:00"
            val bedtimeEndTime = preferences[PreferencesKeys.BEDTIME_END_TIME] ?: "07:00"
            val bedtimeDays = preferences[PreferencesKeys.BEDTIME_DAYS]?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7)
            val bedtimeDndEnabled = preferences[PreferencesKeys.BEDTIME_DND_ENABLED] ?: false
            val bedtimeWindDownEnabled = preferences[PreferencesKeys.BEDTIME_WIND_DOWN_ENABLED] ?: false
            val bedtimeNotificationEnabled = preferences[PreferencesKeys.BEDTIME_NOTIFICATION_ENABLED] ?: true
            val bedtimeWhitelistedPackages = preferences[PreferencesKeys.BEDTIME_WHITELISTED_PACKAGES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            val userName = preferences[PreferencesKeys.USER_NAME] ?: "User"
            val earlyKickEnabled = preferences[PreferencesKeys.EARLY_KICK_ENABLED] ?: false
            val interceptAudioFocusEnabled = preferences[PreferencesKeys.INTERCEPT_AUDIO_FOCUS_ENABLED] ?: true
            val showDatabaseIndicator = preferences[PreferencesKeys.SHOW_DATABASE_INDICATOR] ?: false
            val developerModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false
            val lastSyncTimestamp = preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: (System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
            val preferSystemUsageHistory = preferences[PreferencesKeys.PREFER_SYSTEM_USAGE_HISTORY] ?: true
            val onboardingStatsCompleted = preferences[PreferencesKeys.ONBOARDING_STATS_COMPLETED] ?: false
            val hudHideFeatureLearned = preferences[PreferencesKeys.HUD_HIDE_FEATURE_LEARNED] ?: false
            val shortsScreenTimeMs = preferences[PreferencesKeys.SHORTS_SCREEN_TIME_MS] ?: 0L
            val customDelayEnabled = preferences[PreferencesKeys.CUSTOM_DELAY_ENABLED] ?: false
            val delayPowerSave = preferences[PreferencesKeys.DELAY_POWER_SAVE] ?: 5000L
            val delayOverlayShowing = preferences[PreferencesKeys.DELAY_OVERLAY_SHOWING] ?: 8000L
            val delayGoalNear = preferences[PreferencesKeys.DELAY_GOAL_NEAR] ?: 600L
            val delayGoalMid = preferences[PreferencesKeys.DELAY_GOAL_MID] ?: 1200L
            val delayGoalFar = preferences[PreferencesKeys.DELAY_GOAL_FAR] ?: 1800L
            val delayShieldVeryFar = preferences[PreferencesKeys.DELAY_SHIELD_VERY_FAR] ?: 5000L
            val delayShieldFar = preferences[PreferencesKeys.DELAY_SHIELD_FAR] ?: 3000L
            val delayShieldMid = preferences[PreferencesKeys.DELAY_SHIELD_MID] ?: 1500L
            val delayShieldNear = preferences[PreferencesKeys.DELAY_SHIELD_NEAR] ?: 600L
            val delayDefault = preferences[PreferencesKeys.DELAY_DEFAULT] ?: 1200L
            val mindfulGatewayEnabled = preferences[PreferencesKeys.MINDFUL_GATEWAY_ENABLED] ?: false

            UserPreferences(
                themeConfig = themeConfig,
                fontOption = fontOption,
                dynamicColor = dynamicColor,
                accessibilityDisabled = accessibilityDisabled,
                screenTimeTargetMinutes = screenTimeTarget,
                emergencyRechargeDurationMinutes = emergencyRechargeDuration,
                delayAppDurationSeconds = delayAppDuration,
                sessionUsageOverlayEnabled = sessionUsageOverlayEnabled,
                sessionUsageOverlaySize = sessionUsageOverlaySize,
                sessionUsageOverlayOpacity = sessionUsageOverlayOpacity,
                whitelistedPackages = whitelistedPackages,
                lastResetDate = lastResetDate,
                lastStreakCheckDate = lastStreakCheckDate,
                globalCurrentStreak = globalCurrentStreak,
                globalBestStreak = globalBestStreak,
                globalLastStreakUpdateTimestamp = globalLastStreakUpdateTimestamp,
                autoBackupEnabled = autoBackupEnabled,
                backupDirectoryUri = backupDirectoryUri,
                backupIntervalHours = backupIntervalHours,
                lastBackupTimestamp = lastBackupTimestamp,
                floatingTabBarEnabled = floatingTabBarEnabled,
                expressiveColors = expressiveColors,
                totalUsagePillEnabled = totalUsagePillEnabled,
                lastKnownDailyUsage = lastKnownDailyUsage,
                lastKnownDailyUsageDate = lastKnownDailyUsageDate,
                bedtimeEnabled = bedtimeEnabled,
                bedtimeStartTime = bedtimeStartTime,
                bedtimeEndTime = bedtimeEndTime,
                bedtimeDays = bedtimeDays,
                bedtimeDndEnabled = bedtimeDndEnabled,
                bedtimeWindDownEnabled = bedtimeWindDownEnabled,
                bedtimeNotificationEnabled = bedtimeNotificationEnabled,
                bedtimeWhitelistedPackages = bedtimeWhitelistedPackages,
                userName = userName,
                earlyKickEnabled = earlyKickEnabled,
                interceptAudioFocusEnabled = interceptAudioFocusEnabled,
                showDatabaseIndicator = showDatabaseIndicator,
                developerModeEnabled = developerModeEnabled,
                lastSyncTimestamp = lastSyncTimestamp,
                preferSystemUsageHistory = preferSystemUsageHistory,
                onboardingStatsCompleted = onboardingStatsCompleted,
                hudHideFeatureLearned = hudHideFeatureLearned,
                shortsScreenTimeMs = shortsScreenTimeMs,
                customDelayEnabled = customDelayEnabled,
                delayPowerSave = delayPowerSave,
                delayOverlayShowing = delayOverlayShowing,
                delayGoalNear = delayGoalNear,
                delayGoalMid = delayGoalMid,
                delayGoalFar = delayGoalFar,
                delayShieldVeryFar = delayShieldVeryFar,
                delayShieldFar = delayShieldFar,
                delayShieldMid = delayShieldMid,
                delayShieldNear = delayShieldNear,
                delayDefault = delayDefault,
                mindfulGatewayEnabled = mindfulGatewayEnabled
            )
        }


    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
        }
    }

    suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_CONFIG] = themeConfig.name
        }
    }

    suspend fun setFontOption(fontOption: FontOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_OPTION] = fontOption.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setAccessibilityDisabled(disabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESSIBILITY_DISABLED] = disabled
        }
    }

    suspend fun setScreenTimeTarget(minutes: Int) {
        context.dataStore.edit { preferences ->
            val currentTarget = preferences[PreferencesKeys.SCREEN_TIME_TARGET] ?: 0
            if (minutes > currentTarget && currentTarget > 0) {
                preferences[PreferencesKeys.GLOBAL_CURRENT_STREAK] = 0
            }
            preferences[PreferencesKeys.SCREEN_TIME_TARGET] = minutes
        }
    }

    suspend fun setEmergencyRechargeDuration(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EMERGENCY_RECHARGE_DURATION_MINUTES] = minutes
        }
    }

    suspend fun setDelayAppDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_APP_DURATION_SECONDS] = seconds
        }
    }

    suspend fun setSessionUsageOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setSessionUsageOverlaySize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_SIZE] = size
        }
    }

    suspend fun setSessionUsageOverlayOpacity(opacity: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_OPACITY] = opacity
        }
    }

    suspend fun setWhitelistedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WHITELISTED_PACKAGES] = packages.joinToString(",")
        }
    }

    suspend fun setLastResetDate(date: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_RESET_DATE] = date
        }
    }

    suspend fun setLastStreakCheckDate(date: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_STREAK_CHECK_DATE] = date
        }
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
        if (targetMillis <= 0) return Pair(0, 0)

        val dbUsage = shieldRepository.getAllUsage().first()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // 1. Calculate Today's usage
        val launcherApps = try {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) { emptySet<String>() }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = try {
            context.packageManager.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        } catch (_: Exception) { null }

        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis

        val stats = usageStatsManager.queryAndAggregateUsageStats(todayStart, now)
        var totalToday = 0L
        stats.forEach { (pkg, stat) ->
            if (pkg !in excludePackages && pkg in launcherApps) {
                totalToday += stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            }
        }

        val globalHistory = dbUsage.filter { it.packageName == "TOTAL" }

        // 2. Calculate Live Streak
        var liveStreak = 0
        if (totalToday <= targetMillis) {
            liveStreak = 1
            val c = Calendar.getInstance()
            for (i in 1..60) {
                c.timeInMillis = now
                c.add(Calendar.DAY_OF_YEAR, -i)
                val dStr = dateFormat.format(c.time)
                val usage = globalHistory.find { it.date == dStr }?.usageTimeMillis

                if (usage != null && usage <= targetMillis) {
                    liveStreak++
                } else if (usage != null) {
                    break
                } else {
                    break
                }
            }
        }

        // 3. Calculate Best Streak
        var bestStreak = prefs.globalBestStreak
        var currentTempStreak = 0
        for (i in 60 downTo 0) {
            val checkCal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dStr = dateFormat.format(checkCal.time)
            val usage = if (i == 0) totalToday else globalHistory.find { it.date == dStr }?.usageTimeMillis

            if (usage != null && usage <= targetMillis) {
                currentTempStreak++
            } else {
                bestStreak = maxOf(bestStreak, currentTempStreak)
                currentTempStreak = 0
            }
        }
        bestStreak = maxOf(bestStreak, currentTempStreak)

        updateGlobalStreak(liveStreak, bestStreak, now)
        return Pair(liveStreak, bestStreak)
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setBackupDirectoryUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_DIRECTORY_URI] = uri
        }
    }

    suspend fun setBackupIntervalHours(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_INTERVAL_HOURS] = hours
        }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] = timestamp
        }
    }

    suspend fun setFloatingTabBarEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FLOATING_TAB_BAR_ENABLED] = enabled
        }
    }

    suspend fun setExpressiveColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXPRESSIVE_COLORS] = enabled
        }
    }

    suspend fun setTotalUsagePillEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOTAL_USAGE_PILL_ENABLED] = enabled
        }
    }

    suspend fun setLastKnownDailyUsage(usage: Long, date: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE] = usage
            preferences[PreferencesKeys.LAST_KNOWN_DAILY_USAGE_DATE] = date
        }
    }

    suspend fun setBedtimeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_ENABLED] = enabled
        }
    }

    suspend fun setBedtimeStartTime(time: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_START_TIME] = time
        }
    }

    suspend fun setBedtimeEndTime(time: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_END_TIME] = time
        }
    }

    suspend fun setBedtimeDays(days: Set<Int>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_DAYS] = days.joinToString(",")
        }
    }

    suspend fun setBedtimeDndEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_DND_ENABLED] = enabled
        }
    }

    suspend fun setBedtimeWindDownEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_WIND_DOWN_ENABLED] = enabled
        }
    }

    suspend fun setBedtimeNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setBedtimeWhitelistedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BEDTIME_WHITELISTED_PACKAGES] = packages.joinToString(",")
        }
    }

    suspend fun setEarlyKickEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EARLY_KICK_ENABLED] = enabled
        }
    }

    suspend fun setInterceptAudioFocusEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INTERCEPT_AUDIO_FOCUS_ENABLED] = enabled
        }
    }

    suspend fun setShowDatabaseIndicator(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_DATABASE_INDICATOR] = enabled
        }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun setPreferSystemUsageHistory(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFER_SYSTEM_USAGE_HISTORY] = enabled
        }
    }

    suspend fun setOnboardingStatsCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_STATS_COMPLETED] = completed
        }
    }

    suspend fun setHudHideFeatureLearned(learned: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HUD_HIDE_FEATURE_LEARNED] = learned
        }
    }

    suspend fun setShortsScreenTimeMs(ms: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHORTS_SCREEN_TIME_MS] = ms
        }
    }

    suspend fun setCustomDelayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_DELAY_ENABLED] = enabled
        }
    }

    suspend fun setDelayPowerSave(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_POWER_SAVE] = delay
        }
    }

    suspend fun setDelayOverlayShowing(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_OVERLAY_SHOWING] = delay
        }
    }

    suspend fun setDelayGoalNear(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_GOAL_NEAR] = delay
        }
    }

    suspend fun setDelayGoalMid(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_GOAL_MID] = delay
        }
    }

    suspend fun setDelayGoalFar(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_GOAL_FAR] = delay
        }
    }

    suspend fun setDelayShieldVeryFar(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_SHIELD_VERY_FAR] = delay
        }
    }

    suspend fun setDelayShieldFar(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_SHIELD_FAR] = delay
        }
    }

    suspend fun setDelayShieldMid(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_SHIELD_MID] = delay
        }
    }

    suspend fun setDelayShieldNear(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_SHIELD_NEAR] = delay
        }
    }

    suspend fun setDelayDefault(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_DEFAULT] = delay
        }
    }

    suspend fun setMindfulGatewayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MINDFUL_GATEWAY_ENABLED] = enabled
        }
    }

    suspend fun resetCustomDelays() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.DELAY_POWER_SAVE)
            preferences.remove(PreferencesKeys.DELAY_OVERLAY_SHOWING)
            preferences.remove(PreferencesKeys.DELAY_GOAL_NEAR)
            preferences.remove(PreferencesKeys.DELAY_GOAL_MID)
            preferences.remove(PreferencesKeys.DELAY_GOAL_FAR)
            preferences.remove(PreferencesKeys.DELAY_SHIELD_VERY_FAR)
            preferences.remove(PreferencesKeys.DELAY_SHIELD_FAR)
            preferences.remove(PreferencesKeys.DELAY_SHIELD_MID)
            preferences.remove(PreferencesKeys.DELAY_SHIELD_NEAR)
            preferences.remove(PreferencesKeys.DELAY_DEFAULT)
        }
    }
}

data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val fontOption: FontOption = FontOption.NUNITO,
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
    val userName: String = "User",
    val earlyKickEnabled: Boolean = false,
    val interceptAudioFocusEnabled: Boolean = true,
    val showDatabaseIndicator: Boolean = false,
    val developerModeEnabled: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val preferSystemUsageHistory: Boolean = true,
    val onboardingStatsCompleted: Boolean = false,
    val hudHideFeatureLearned: Boolean = false,
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
    val mindfulGatewayEnabled: Boolean = false
)
