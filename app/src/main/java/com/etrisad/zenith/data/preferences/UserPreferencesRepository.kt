package com.etrisad.zenith.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

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
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_DIRECTORY_URI = stringPreferencesKey("backup_directory_uri")
        val BACKUP_INTERVAL_HOURS = intPreferencesKey("backup_interval_hours")
        val FLOATING_TAB_BAR_ENABLED = booleanPreferencesKey("floating_tab_bar_enabled")
        val EXPRESSIVE_COLORS = booleanPreferencesKey("expressive_colors")
        val TOTAL_USAGE_PILL_ENABLED = booleanPreferencesKey("total_usage_pill_enabled")
        val LAST_KNOWN_DAILY_USAGE = longPreferencesKey("last_known_daily_usage")
        val LAST_KNOWN_DAILY_USAGE_DATE = stringPreferencesKey("last_known_daily_usage_date")
        
        // Bedtime Settings
        val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")
        val BEDTIME_START_TIME = stringPreferencesKey("bedtime_start_time")
        val BEDTIME_END_TIME = stringPreferencesKey("bedtime_end_time")
        val BEDTIME_DAYS = stringPreferencesKey("bedtime_days")
        val BEDTIME_DND_ENABLED = booleanPreferencesKey("bedtime_dnd_enabled")
        val BEDTIME_WIND_DOWN_ENABLED = booleanPreferencesKey("bedtime_wind_down_enabled")
        val BEDTIME_NOTIFICATION_ENABLED = booleanPreferencesKey("bedtime_notification_enabled")
        val BEDTIME_WHITELISTED_PACKAGES = stringPreferencesKey("bedtime_whitelisted_packages")
        val USER_NAME = stringPreferencesKey("user_name")
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
            val autoBackupEnabled = preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: false
            val backupDirectoryUri = preferences[PreferencesKeys.BACKUP_DIRECTORY_URI] ?: ""
            val backupIntervalHours = preferences[PreferencesKeys.BACKUP_INTERVAL_HOURS] ?: 3
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
                autoBackupEnabled = autoBackupEnabled,
                backupDirectoryUri = backupDirectoryUri,
                backupIntervalHours = backupIntervalHours,
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
                userName = userName
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
    val autoBackupEnabled: Boolean = false,
    val backupDirectoryUri: String = "",
    val backupIntervalHours: Int = 3,
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
    val userName: String = "User"
)
