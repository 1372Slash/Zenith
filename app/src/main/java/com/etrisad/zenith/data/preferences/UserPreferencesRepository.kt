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
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.util.DateTimeUtils
import com.etrisad.zenith.ui.theme.FontAxes
import com.etrisad.zenith.ui.theme.GSFlexSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val Context.runtimeDataStore: DataStore<Preferences> by preferencesDataStore(name = "runtime_state")

enum class ThemeConfig {
    FOLLOW_SYSTEM, LIGHT, DARK
}

enum class FontOption {
    SYSTEM, GOOGLE_SANS_FLEX, NUNITO
}

enum class GSFlexPreset {
    ZENITH, NEO, COMPACT, AIRY, CUSTOM
}

enum class PerformanceLevel(val labelRes: String, val descriptionRes: String) {
    MAX_RESPONSIVENESS("Max Responsiveness", "Maximum speed for gaming and time-sensitive moments."),
    RESPONSIVE("Responsive", "A snappier experience than the default profile."),
    BALANCED("Balanced", "Tuned for everyday use."),
    BATTERY_SAVER("Battery Saver", "Gentler on battery with reduced background checks."),
    MAX_BATTERY("Max Battery", "Maximum battery savings with minimal background activity."),
    CUSTOM("Custom", "Your personalized configuration.")
}

fun PerformanceLevel.isPreset() = this != PerformanceLevel.CUSTOM

data class PerformanceConfig(
    val a11yActiveDelay: Long = 120_000L,
    val a11yInactiveDelay: Long = 3_000L,
    val screenOffDelay: Long = 300_000L,
    val powerSaveDelay: Long = 300_000L,
    val usageStatsCacheMs: Long = 600_000L,
    val shieldDbWriteMs: Long = 300_000L,
    val shieldDbWriteNearMs: Long = 120_000L,
    val launcherCacheMs: Long = 3_600_000L,
    val goalReminderTick: Long = 2L,
    val dayChangeTick: Long = 2L,
    val monPowerSave: Long = 5000L,
    val monOverlayShowing: Long = 8000L,
    val monGoalNear: Long = 600L,
    val monGoalMid: Long = 1200L,
    val monGoalFar: Long = 1800L,
    val monShieldNear: Long = 600L,
    val monShieldMid: Long = 1500L,
    val monShieldFar: Long = 3000L,
    val monShieldVeryFar: Long = 5000L,
    val monDefault: Long = 1200L,
)

fun PerformanceLevel.toConfig(): PerformanceConfig = when (this) {
    PerformanceLevel.MAX_RESPONSIVENESS -> PerformanceConfig(
        a11yActiveDelay = 30_000L,
        a11yInactiveDelay = 1_000L,
        screenOffDelay = 60_000L,
        powerSaveDelay = 60_000L,
        usageStatsCacheMs = 10_000L,
        shieldDbWriteMs = 60_000L,
        shieldDbWriteNearMs = 30_000L,
        launcherCacheMs = 1_800_000L,
        goalReminderTick = 1L,
        dayChangeTick = 1L,
        monPowerSave = 2000L,
        monOverlayShowing = 3000L,
        monGoalNear = 300L,
        monGoalMid = 600L,
        monGoalFar = 900L,
        monShieldNear = 300L,
        monShieldMid = 800L,
        monShieldFar = 1500L,
        monShieldVeryFar = 2500L,
        monDefault = 600L,
    )
    PerformanceLevel.RESPONSIVE -> PerformanceConfig(
        a11yActiveDelay = 60_000L,
        a11yInactiveDelay = 2_000L,
        screenOffDelay = 120_000L,
        powerSaveDelay = 120_000L,
        usageStatsCacheMs = 30_000L,
        shieldDbWriteMs = 120_000L,
        shieldDbWriteNearMs = 60_000L,
        launcherCacheMs = 3_600_000L,
        goalReminderTick = 1L,
        dayChangeTick = 1L,
        monPowerSave = 3000L,
        monOverlayShowing = 5000L,
        monGoalNear = 400L,
        monGoalMid = 800L,
        monGoalFar = 1200L,
        monShieldNear = 400L,
        monShieldMid = 1000L,
        monShieldFar = 2000L,
        monShieldVeryFar = 3000L,
        monDefault = 800L,
    )
    PerformanceLevel.BALANCED -> PerformanceConfig(
        a11yActiveDelay = 120_000L,
        a11yInactiveDelay = 5_000L,
        screenOffDelay = 600_000L,
        powerSaveDelay = 600_000L,
        usageStatsCacheMs = 900_000L,
        shieldDbWriteMs = 600_000L,
        shieldDbWriteNearMs = 300_000L,
        launcherCacheMs = 3_600_000L,
        goalReminderTick = 3L,
        dayChangeTick = 3L,
        monPowerSave = 7000L,
        monOverlayShowing = 10000L,
        monGoalNear = 1000L,
        monGoalMid = 2000L,
        monGoalFar = 3000L,
        monShieldNear = 1000L,
        monShieldMid = 2500L,
        monShieldFar = 5000L,
        monShieldVeryFar = 10000L,
        monDefault = 2000L,
    )
    PerformanceLevel.BATTERY_SAVER -> PerformanceConfig(
        a11yActiveDelay = 300_000L,
        a11yInactiveDelay = 15_000L,
        screenOffDelay = 900_000L,
        powerSaveDelay = 900_000L,
        usageStatsCacheMs = 1_800_000L,
        shieldDbWriteMs = 900_000L,
        shieldDbWriteNearMs = 450_000L,
        launcherCacheMs = 7_200_000L,
        goalReminderTick = 10L,
        dayChangeTick = 10L,
        monPowerSave = 15000L,
        monOverlayShowing = 15000L,
        monGoalNear = 2000L,
        monGoalMid = 5000L,
        monGoalFar = 10000L,
        monShieldNear = 2000L,
        monShieldMid = 5000L,
        monShieldFar = 10000L,
        monShieldVeryFar = 20000L,
        monDefault = 15000L,
    )
    PerformanceLevel.MAX_BATTERY -> PerformanceConfig(
        a11yActiveDelay = 900_000L,
        a11yInactiveDelay = 60_000L,
        screenOffDelay = 3_600_000L,
        powerSaveDelay = 3_600_000L,
        usageStatsCacheMs = 7_200_000L,
        shieldDbWriteMs = 3_600_000L,
        shieldDbWriteNearMs = 1_800_000L,
        launcherCacheMs = 28_800_000L,
        goalReminderTick = 30L,
        dayChangeTick = 30L,
        monPowerSave = 30000L,
        monOverlayShowing = 45000L,
        monGoalNear = 5000L,
        monGoalMid = 15000L,
        monGoalFar = 30000L,
        monShieldNear = 5000L,
        monShieldMid = 15000L,
        monShieldFar = 30000L,
        monShieldVeryFar = 60000L,
        monDefault = 30000L,
    )
    PerformanceLevel.CUSTOM -> PerformanceConfig()
}

enum class ForegroundNotificationStatusMode {
    DAILY_USAGE, ACTIVE_FOCUS, DEFAULT
}

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val FONT_OPTION = stringPreferencesKey("font_option")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCESSIBILITY_DISABLED = booleanPreferencesKey("accessibility_disabled")
        val ACCESSIBILITY_REQUIRED = booleanPreferencesKey("accessibility_required")
        val SCREEN_TIME_TARGET = intPreferencesKey("screen_time_target")
        val EMERGENCY_RECHARGE_DURATION_MINUTES = intPreferencesKey("emergency_recharge_duration_minutes")
        val DELAY_APP_DURATION_SECONDS = intPreferencesKey("delay_app_duration_seconds")
        val SESSION_USAGE_OVERLAY_ENABLED = booleanPreferencesKey("session_usage_overlay_enabled")
        val SESSION_USAGE_OVERLAY_SIZE = intPreferencesKey("session_usage_overlay_size")
        val SESSION_USAGE_OVERLAY_OPACITY = intPreferencesKey("session_usage_overlay_opacity")
        val WHITELISTED_PACKAGES = stringPreferencesKey("whitelisted_packages")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_DIRECTORY_URI = stringPreferencesKey("backup_directory_uri")
        val BACKUP_INTERVAL_HOURS = intPreferencesKey("backup_interval_hours")
        val FLOATING_TAB_BAR_ENABLED = booleanPreferencesKey("floating_tab_bar_enabled")
        val EXPRESSIVE_COLORS = booleanPreferencesKey("expressive_colors")
        val TOTAL_USAGE_PILL_ENABLED = booleanPreferencesKey("total_usage_pill_enabled")
        val FOREGROUND_NOTIFICATION_STATUS_MODE = stringPreferencesKey("foreground_notification_status_mode")

        val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")
        val BEDTIME_START_TIME = stringPreferencesKey("bedtime_start_time")
        val BEDTIME_END_TIME = stringPreferencesKey("bedtime_end_time")
        val BEDTIME_DAYS = stringPreferencesKey("bedtime_days")
        val BEDTIME_DND_ENABLED = booleanPreferencesKey("bedtime_dnd_enabled")
        val BEDTIME_WIND_DOWN_ENABLED = booleanPreferencesKey("bedtime_wind_down_enabled")
        val BEDTIME_NOTIFICATION_ENABLED = booleanPreferencesKey("bedtime_notification_enabled")
        val BEDTIME_WHITELISTED_PACKAGES = stringPreferencesKey("bedtime_whitelisted_packages")
        val GRACE_PERIOD_ENABLED = booleanPreferencesKey("grace_period_enabled")
        val GRACE_PERIOD_START_TIME = stringPreferencesKey("grace_period_start_time")
        val GRACE_PERIOD_END_TIME = stringPreferencesKey("grace_period_end_time")
        val GRACE_PERIOD_DAYS = stringPreferencesKey("grace_period_days")
        val USER_NAME = stringPreferencesKey("user_name")
        val EARLY_KICK_ENABLED = booleanPreferencesKey("early_kick_enabled")
        val INTERCEPT_AUDIO_FOCUS_ENABLED = booleanPreferencesKey("intercept_audio_focus_enabled")
        val SHOW_DATABASE_INDICATOR = booleanPreferencesKey("show_database_indicator")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val PREFER_SYSTEM_USAGE_HISTORY = booleanPreferencesKey("prefer_system_usage_history")
        val ONBOARDING_STATS_COMPLETED = booleanPreferencesKey("onboarding_stats_completed")
        val ONBOARDING_UPDATE_COMPLETED = booleanPreferencesKey("onboarding_update_completed")
        val HUD_HIDE_FEATURE_LEARNED = booleanPreferencesKey("hud_hide_feature_learned")
        val SMART_REPAIR_ON_REFRESH = booleanPreferencesKey("smart_repair_on_refresh")
        val ALLOW_REPAIR_NON_UNAVAILABLE = booleanPreferencesKey("allow_repair_non_unavailable")
        val MINDFUL_GATEWAY_ENABLED = booleanPreferencesKey("mindful_gateway_enabled")
        val REFRESH_ON_OPEN_USAGE_STATS = booleanPreferencesKey("refresh_on_open_usage_stats")
        val CHECK_UPDATE_ON_START = booleanPreferencesKey("check_update_on_start")
        val BATTERY_STATS_RESET_ENABLED = booleanPreferencesKey("battery_stats_reset_enabled")
        val BANKING_WARNING_DISMISSED = booleanPreferencesKey("banking_warning_dismissed")
        val SHOW_CURRENT_EVENT = booleanPreferencesKey("show_current_event")
        val DAILY_RECAP_ENABLED = booleanPreferencesKey("daily_recap_enabled")
        val WEEKLY_INSIGHT_ENABLED = booleanPreferencesKey("weekly_insight_enabled")
        val TREND_MILESTONE_ENABLED = booleanPreferencesKey("trend_milestone_enabled")
        val INCENTIVE_LOCK_ENABLED = booleanPreferencesKey("incentive_lock_enabled")
        val EYE_CARE_ENABLED = booleanPreferencesKey("eye_care_enabled")
        val EYE_CARE_WORK_MINUTES = intPreferencesKey("eye_care_work_minutes")
        val EYE_CARE_REST_SECONDS = intPreferencesKey("eye_care_rest_seconds")
        val USAGE_GLIMPSE_ENABLED = booleanPreferencesKey("usage_glimpse_enabled")
        val WEBSITE_AUTO_TRACKING = booleanPreferencesKey("website_auto_tracking")
        val LAST_BANKING_APPS_COUNT = intPreferencesKey("last_banking_apps_count")

        val OVERLAY_PALETTE_ID = stringPreferencesKey("overlay_palette_id")
        val OVERLAY_SHEET_OPACITY = floatPreferencesKey("overlay_sheet_opacity")
        val OVERLAY_FULL_SCREEN = booleanPreferencesKey("overlay_full_screen")
        val OVERLAY_CUSTOM_HUE = floatPreferencesKey("overlay_custom_hue")

        val PERFORMANCE_LEVEL = stringPreferencesKey("performance_level")
        val PERF_A11Y_ACTIVE_DELAY = longPreferencesKey("perf_a11y_active_delay")
        val PERF_A11Y_INACTIVE_DELAY = longPreferencesKey("perf_a11y_inactive_delay")
        val PERF_SCREEN_OFF_DELAY = longPreferencesKey("perf_screen_off_delay")
        val PERF_POWER_SAVE_DELAY = longPreferencesKey("perf_power_save_delay")
        val PERF_USAGE_STATS_CACHE = longPreferencesKey("perf_usage_stats_cache")
        val PERF_SHIELD_DB_WRITE = longPreferencesKey("perf_shield_db_write")
        val PERF_SHIELD_DB_WRITE_NEAR = longPreferencesKey("perf_shield_db_write_near")
        val PERF_LAUNCHER_CACHE = longPreferencesKey("perf_launcher_cache")
        val PERF_GOAL_REMINDER_TICK = longPreferencesKey("perf_goal_reminder_tick")
        val PERF_DAY_CHANGE_TICK = longPreferencesKey("perf_day_change_tick")
        val PERF_MON_POWER_SAVE = longPreferencesKey("perf_mon_power_save")
        val PERF_MON_OVERLAY_SHOWING = longPreferencesKey("perf_mon_overlay_showing")
        val PERF_MON_GOAL_NEAR = longPreferencesKey("perf_mon_goal_near")
        val PERF_MON_GOAL_MID = longPreferencesKey("perf_mon_goal_mid")
        val PERF_MON_GOAL_FAR = longPreferencesKey("perf_mon_goal_far")
        val PERF_MON_SHIELD_NEAR = longPreferencesKey("perf_mon_shield_near")
        val PERF_MON_SHIELD_MID = longPreferencesKey("perf_mon_shield_mid")
        val PERF_MON_SHIELD_FAR = longPreferencesKey("perf_mon_shield_far")
        val PERF_MON_SHIELD_VERY_FAR = longPreferencesKey("perf_mon_shield_very_far")
        val PERF_MON_DEFAULT = longPreferencesKey("perf_mon_default")

        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val DAY_START_MINUTE = intPreferencesKey("day_start_minute")

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

        val DISABLE_TRACKING_AT_UNUSED_HOURS = booleanPreferencesKey("disable_tracking_at_unused_hours")
        val DISABLE_TRACKING_START_HOUR = intPreferencesKey("disable_tracking_start_hour")
        val DISABLE_TRACKING_END_HOUR = intPreferencesKey("disable_tracking_end_hour")
    }

    private object RuntimeKeys {
        val LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
        val LAST_STREAK_CHECK_DATE = stringPreferencesKey("last_streak_check_date")
        val GLOBAL_CURRENT_STREAK = intPreferencesKey("global_current_streak")
        val GLOBAL_BEST_STREAK = intPreferencesKey("global_best_streak")
        val GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP = longPreferencesKey("global_last_streak_update_timestamp")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val LAST_KNOWN_DAILY_USAGE = longPreferencesKey("last_known_daily_usage")
        val LAST_KNOWN_DAILY_USAGE_DATE = stringPreferencesKey("last_known_daily_usage_date")
        val BEDTIME_CURRENT_STREAK = intPreferencesKey("bedtime_mode_streak_current")
        val BEDTIME_BEST_STREAK = intPreferencesKey("bedtime_mode_streak_best")
        val BEDTIME_STREAK_RESET_DATE = stringPreferencesKey("bedtime_streak_reset_date")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val WHITELIST_INITIALIZED = booleanPreferencesKey("whitelist_initialized")
        val SHORTS_SCREEN_TIME_MS = longPreferencesKey("shorts_screen_time_ms")
        val LAST_CHARGE_TIMESTAMP = longPreferencesKey("last_charge_timestamp")
        val MANUAL_RESET_TIMESTAMPS = stringPreferencesKey("manual_reset_timestamps")
        val STREAK_RECOVERY_PERFORMED = booleanPreferencesKey("streak_recovery_performed")
        val INCENTIVE_LOCK_DISABLE_REQUEST_TIMESTAMP = longPreferencesKey("incentive_lock_disable_request_timestamp")
        val INCENTIVE_LOCK_GOALS_MET_TODAY = booleanPreferencesKey("incentive_lock_goals_met_today")
        val INCENTIVE_LOCK_GOALS_MET_DATE = stringPreferencesKey("incentive_lock_goals_met_date")
        val INCENTIVE_BONUS_USES_USED = intPreferencesKey("incentive_bonus_uses_used")
        val INCENTIVE_BONUS_USES_DATE = stringPreferencesKey("incentive_bonus_uses_date")
        val LAST_WEEKLY_RESET_DATE = longPreferencesKey("last_weekly_reset_date")
        val DISMISSED_UNINSTALLED_APPS = stringPreferencesKey("dismissed_uninstalled_apps")
    }

    val userPreferencesFlow: Flow<UserPreferences> = combine(
        context.dataStore.data.catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception },
        context.runtimeDataStore.data.catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
    ) { settings, runtime ->
        UserPreferences(
            themeConfig = ThemeConfig.valueOf(settings[PreferencesKeys.THEME_CONFIG] ?: ThemeConfig.FOLLOW_SYSTEM.name),
            fontOption = FontOption.valueOf(settings[PreferencesKeys.FONT_OPTION] ?: FontOption.GOOGLE_SANS_FLEX.name),
            dynamicColor = settings[PreferencesKeys.DYNAMIC_COLOR] ?: true,
            accessibilityDisabled = settings[PreferencesKeys.ACCESSIBILITY_DISABLED] ?: false,
            accessibilityRequired = settings[PreferencesKeys.ACCESSIBILITY_REQUIRED] ?: false,
            screenTimeTargetMinutes = settings[PreferencesKeys.SCREEN_TIME_TARGET] ?: 0,
            emergencyRechargeDurationMinutes = settings[PreferencesKeys.EMERGENCY_RECHARGE_DURATION_MINUTES] ?: 60,
            delayAppDurationSeconds = settings[PreferencesKeys.DELAY_APP_DURATION_SECONDS] ?: 30,
            sessionUsageOverlayEnabled = settings[PreferencesKeys.SESSION_USAGE_OVERLAY_ENABLED] ?: false,
            sessionUsageOverlaySize = settings[PreferencesKeys.SESSION_USAGE_OVERLAY_SIZE] ?: 100,
            sessionUsageOverlayOpacity = settings[PreferencesKeys.SESSION_USAGE_OVERLAY_OPACITY] ?: 90,
            whitelistedPackages = settings[PreferencesKeys.WHITELISTED_PACKAGES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
            lastResetDate = runtime[RuntimeKeys.LAST_RESET_DATE] ?: "",
            lastWeeklyResetDate = runtime[RuntimeKeys.LAST_WEEKLY_RESET_DATE] ?: 0L,
            lastStreakCheckDate = runtime[RuntimeKeys.LAST_STREAK_CHECK_DATE] ?: "",
            globalCurrentStreak = runtime[RuntimeKeys.GLOBAL_CURRENT_STREAK] ?: 0,
            globalBestStreak = runtime[RuntimeKeys.GLOBAL_BEST_STREAK] ?: 0,
            globalLastStreakUpdateTimestamp = runtime[RuntimeKeys.GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP] ?: 0L,
            autoBackupEnabled = settings[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: false,
            backupDirectoryUri = settings[PreferencesKeys.BACKUP_DIRECTORY_URI] ?: "",
            backupIntervalHours = settings[PreferencesKeys.BACKUP_INTERVAL_HOURS] ?: 3,
            lastBackupTimestamp = runtime[RuntimeKeys.LAST_BACKUP_TIMESTAMP] ?: 0L,
            floatingTabBarEnabled = settings[PreferencesKeys.FLOATING_TAB_BAR_ENABLED] ?: false,
            expressiveColors = settings[PreferencesKeys.EXPRESSIVE_COLORS] ?: false,
            totalUsagePillEnabled = settings[PreferencesKeys.TOTAL_USAGE_PILL_ENABLED] ?: false,
            foregroundNotificationStatusMode = settings[PreferencesKeys.FOREGROUND_NOTIFICATION_STATUS_MODE]
                ?.let { runCatching { ForegroundNotificationStatusMode.valueOf(it) }.getOrNull() }
                ?: ForegroundNotificationStatusMode.DEFAULT,
            lastKnownDailyUsage = runtime[RuntimeKeys.LAST_KNOWN_DAILY_USAGE] ?: 0L,
            lastKnownDailyUsageDate = runtime[RuntimeKeys.LAST_KNOWN_DAILY_USAGE_DATE] ?: "",
            bedtimeEnabled = settings[PreferencesKeys.BEDTIME_ENABLED] ?: false,
            bedtimeStartTime = settings[PreferencesKeys.BEDTIME_START_TIME] ?: "22:00",
            bedtimeEndTime = settings[PreferencesKeys.BEDTIME_END_TIME] ?: "07:00",
            bedtimeDays = settings[PreferencesKeys.BEDTIME_DAYS]?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7),
            gracePeriodEnabled = settings[PreferencesKeys.GRACE_PERIOD_ENABLED] ?: false,
            gracePeriodStartTime = settings[PreferencesKeys.GRACE_PERIOD_START_TIME] ?: "12:00",
            gracePeriodEndTime = settings[PreferencesKeys.GRACE_PERIOD_END_TIME] ?: "13:00",
            gracePeriodDays = settings[PreferencesKeys.GRACE_PERIOD_DAYS]?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7),
            bedtimeDndEnabled = settings[PreferencesKeys.BEDTIME_DND_ENABLED] ?: false,
            bedtimeWindDownEnabled = settings[PreferencesKeys.BEDTIME_WIND_DOWN_ENABLED] ?: false,
            bedtimeNotificationEnabled = settings[PreferencesKeys.BEDTIME_NOTIFICATION_ENABLED] ?: true,
            bedtimeWhitelistedPackages = settings[PreferencesKeys.BEDTIME_WHITELISTED_PACKAGES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
            bedtimeCurrentStreak = runtime[RuntimeKeys.BEDTIME_CURRENT_STREAK] ?: 0,
            bedtimeBestStreak = runtime[RuntimeKeys.BEDTIME_BEST_STREAK] ?: 0,
            bedtimeStreakResetDate = runtime[RuntimeKeys.BEDTIME_STREAK_RESET_DATE] ?: "",
            userName = settings[PreferencesKeys.USER_NAME] ?: "User",
            earlyKickEnabled = settings[PreferencesKeys.EARLY_KICK_ENABLED] ?: false,
            interceptAudioFocusEnabled = settings[PreferencesKeys.INTERCEPT_AUDIO_FOCUS_ENABLED] ?: true,
            showDatabaseIndicator = settings[PreferencesKeys.SHOW_DATABASE_INDICATOR] ?: false,
            developerModeEnabled = settings[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false,
            lastSyncTimestamp = runtime[RuntimeKeys.LAST_SYNC_TIMESTAMP] ?: 0L,
            preferSystemUsageHistory = settings[PreferencesKeys.PREFER_SYSTEM_USAGE_HISTORY] ?: true,
            onboardingStatsCompleted = settings[PreferencesKeys.ONBOARDING_STATS_COMPLETED] ?: false,
            onboardingUpdateCompleted = settings[PreferencesKeys.ONBOARDING_UPDATE_COMPLETED] ?: false,
            whitelistInitialized = runtime[RuntimeKeys.WHITELIST_INITIALIZED] ?: false,
            hudHideFeatureLearned = settings[PreferencesKeys.HUD_HIDE_FEATURE_LEARNED] ?: false,
            smartRepairOnRefresh = settings[PreferencesKeys.SMART_REPAIR_ON_REFRESH] ?: false,
            allowRepairNonUnavailable = settings[PreferencesKeys.ALLOW_REPAIR_NON_UNAVAILABLE] ?: false,
            shortsScreenTimeMs = runtime[RuntimeKeys.SHORTS_SCREEN_TIME_MS] ?: 0L,
            mindfulGatewayEnabled = settings[PreferencesKeys.MINDFUL_GATEWAY_ENABLED] ?: false,
            refreshOnOpenUsageStats = settings[PreferencesKeys.REFRESH_ON_OPEN_USAGE_STATS] ?: false,
            checkUpdateOnStart = settings[PreferencesKeys.CHECK_UPDATE_ON_START] ?: false,
            batteryStatsResetEnabled = settings[PreferencesKeys.BATTERY_STATS_RESET_ENABLED] ?: false,
            bankingWarningDismissed = settings[PreferencesKeys.BANKING_WARNING_DISMISSED] ?: false,
            showCurrentEvent = settings[PreferencesKeys.SHOW_CURRENT_EVENT] ?: false,
            dailyRecapEnabled = settings[PreferencesKeys.DAILY_RECAP_ENABLED] ?: true,
            weeklyInsightEnabled = settings[PreferencesKeys.WEEKLY_INSIGHT_ENABLED] ?: true,
            trendMilestoneEnabled = settings[PreferencesKeys.TREND_MILESTONE_ENABLED] ?: true,
            incentiveLockEnabled = settings[PreferencesKeys.INCENTIVE_LOCK_ENABLED] ?: false,
            eyeCareEnabled = settings[PreferencesKeys.EYE_CARE_ENABLED] ?: false,
            eyeCareWorkMinutes = settings[PreferencesKeys.EYE_CARE_WORK_MINUTES] ?: 20,
            eyeCareRestSeconds = settings[PreferencesKeys.EYE_CARE_REST_SECONDS] ?: 20,
            dayStartHour = settings[PreferencesKeys.DAY_START_HOUR] ?: 0,
            dayStartMinute = settings[PreferencesKeys.DAY_START_MINUTE] ?: 0,
            usageGlimpseEnabled = settings[PreferencesKeys.USAGE_GLIMPSE_ENABLED] ?: false,
            websiteAutoTrackingEnabled = settings[PreferencesKeys.WEBSITE_AUTO_TRACKING] ?: false,
            lastBankingAppsCount = settings[PreferencesKeys.LAST_BANKING_APPS_COUNT] ?: -1,
            incentiveLockDisableRequestTimestamp = runtime[RuntimeKeys.INCENTIVE_LOCK_DISABLE_REQUEST_TIMESTAMP] ?: 0L,
            incentiveLockGoalsMetToday = if (runtime[RuntimeKeys.INCENTIVE_LOCK_GOALS_MET_TODAY] != true) false
                else runtime[RuntimeKeys.INCENTIVE_LOCK_GOALS_MET_DATE] == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            incentiveBonusUsesUsed = {
                val date = runtime[RuntimeKeys.INCENTIVE_BONUS_USES_DATE] ?: ""
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                if (date != today) 0 else (runtime[RuntimeKeys.INCENTIVE_BONUS_USES_USED] ?: 0)
            }(),
            performanceLevel = PerformanceLevel.valueOf(settings[PreferencesKeys.PERFORMANCE_LEVEL] ?: PerformanceLevel.BALANCED.name),
            perfA11yActiveDelay = settings[PreferencesKeys.PERF_A11Y_ACTIVE_DELAY] ?: PerformanceConfig().a11yActiveDelay,
            perfA11yInactiveDelay = settings[PreferencesKeys.PERF_A11Y_INACTIVE_DELAY] ?: PerformanceConfig().a11yInactiveDelay,
            perfScreenOffDelay = settings[PreferencesKeys.PERF_SCREEN_OFF_DELAY] ?: PerformanceConfig().screenOffDelay,
            perfPowerSaveDelay = settings[PreferencesKeys.PERF_POWER_SAVE_DELAY] ?: PerformanceConfig().powerSaveDelay,
            perfUsageStatsCacheMs = settings[PreferencesKeys.PERF_USAGE_STATS_CACHE] ?: PerformanceConfig().usageStatsCacheMs,
            perfShieldDbWriteMs = settings[PreferencesKeys.PERF_SHIELD_DB_WRITE] ?: PerformanceConfig().shieldDbWriteMs,
            perfShieldDbWriteNearMs = settings[PreferencesKeys.PERF_SHIELD_DB_WRITE_NEAR] ?: PerformanceConfig().shieldDbWriteNearMs,
            perfLauncherCacheMs = settings[PreferencesKeys.PERF_LAUNCHER_CACHE] ?: PerformanceConfig().launcherCacheMs,
            perfGoalReminderTick = settings[PreferencesKeys.PERF_GOAL_REMINDER_TICK] ?: PerformanceConfig().goalReminderTick,
            perfDayChangeTick = settings[PreferencesKeys.PERF_DAY_CHANGE_TICK] ?: PerformanceConfig().dayChangeTick,
            perfMonPowerSave = settings[PreferencesKeys.PERF_MON_POWER_SAVE] ?: PerformanceConfig().monPowerSave,
            perfMonOverlayShowing = settings[PreferencesKeys.PERF_MON_OVERLAY_SHOWING] ?: PerformanceConfig().monOverlayShowing,
            perfMonGoalNear = settings[PreferencesKeys.PERF_MON_GOAL_NEAR] ?: PerformanceConfig().monGoalNear,
            perfMonGoalMid = settings[PreferencesKeys.PERF_MON_GOAL_MID] ?: PerformanceConfig().monGoalMid,
            perfMonGoalFar = settings[PreferencesKeys.PERF_MON_GOAL_FAR] ?: PerformanceConfig().monGoalFar,
            perfMonShieldNear = settings[PreferencesKeys.PERF_MON_SHIELD_NEAR] ?: PerformanceConfig().monShieldNear,
            perfMonShieldMid = settings[PreferencesKeys.PERF_MON_SHIELD_MID] ?: PerformanceConfig().monShieldMid,
            perfMonShieldFar = settings[PreferencesKeys.PERF_MON_SHIELD_FAR] ?: PerformanceConfig().monShieldFar,
            perfMonShieldVeryFar = settings[PreferencesKeys.PERF_MON_SHIELD_VERY_FAR] ?: PerformanceConfig().monShieldVeryFar,
            perfMonDefault = settings[PreferencesKeys.PERF_MON_DEFAULT] ?: PerformanceConfig().monDefault,
            lastChargeTimestamp = runtime[RuntimeKeys.LAST_CHARGE_TIMESTAMP] ?: 0L,
            manualResetTimestamps = runtime[RuntimeKeys.MANUAL_RESET_TIMESTAMPS]?.split(",")
                ?.filter { it.contains(":") }
                ?.associate { 
                    val parts = it.split(":")
                    parts[0] to (parts[1].toLongOrNull() ?: 0L)
                } ?: emptyMap(),
            gsFlexSettings = GSFlexSettings(
                preset = GSFlexPreset.valueOf(settings[PreferencesKeys.GS_FLEX_PRESET] ?: GSFlexPreset.ZENITH.name),
                display = FontAxes(
                    weight = settings[PreferencesKeys.GS_D_WGHT] ?: 400f,
                    width = settings[PreferencesKeys.GS_D_WDTH] ?: 100f,
                    opsz = settings[PreferencesKeys.GS_D_OPSZ] ?: 72f,
                    grade = settings[PreferencesKeys.GS_D_GRAD] ?: 0f,
                    slant = settings[PreferencesKeys.GS_D_SLNT] ?: 0f,
                    roundness = settings[PreferencesKeys.GS_D_ROND] ?: 0f
                ),
                headline = FontAxes(
                    weight = settings[PreferencesKeys.GS_H_WGHT] ?: 400f,
                    width = settings[PreferencesKeys.GS_H_WDTH] ?: 100f,
                    opsz = settings[PreferencesKeys.GS_H_OPSZ] ?: 32f,
                    grade = settings[PreferencesKeys.GS_H_GRAD] ?: 0f,
                    slant = settings[PreferencesKeys.GS_H_SLNT] ?: 0f,
                    roundness = settings[PreferencesKeys.GS_H_ROND] ?: 0f
                ),
                body = FontAxes(
                    weight = settings[PreferencesKeys.GS_B_WGHT] ?: 400f,
                    width = settings[PreferencesKeys.GS_B_WDTH] ?: 100f,
                    opsz = settings[PreferencesKeys.GS_B_OPSZ] ?: 16f,
                    grade = settings[PreferencesKeys.GS_B_GRAD] ?: 0f,
                    slant = settings[PreferencesKeys.GS_B_SLNT] ?: 0f,
                    roundness = settings[PreferencesKeys.GS_B_ROND] ?: 0f
                ),
            ),
            overlayPaletteId = settings[PreferencesKeys.OVERLAY_PALETTE_ID] ?: "dynamic",
            overlaySheetOpacity = settings[PreferencesKeys.OVERLAY_SHEET_OPACITY] ?: 1f,
            overlayFullScreen = settings[PreferencesKeys.OVERLAY_FULL_SCREEN] ?: false,
            overlayCustomHue = settings[PreferencesKeys.OVERLAY_CUSTOM_HUE] ?: 270f,
            disableTrackingAtUnusedHours = settings[PreferencesKeys.DISABLE_TRACKING_AT_UNUSED_HOURS] ?: false,
            disableTrackingStartHour = settings[PreferencesKeys.DISABLE_TRACKING_START_HOUR] ?: 2,
            disableTrackingEndHour = settings[PreferencesKeys.DISABLE_TRACKING_END_HOUR] ?: 4,
            streakRecoveryPerformed = runtime[RuntimeKeys.STREAK_RECOVERY_PERFORMED] ?: false,
            dismissedUninstalledApps = runtime[RuntimeKeys.DISMISSED_UNINSTALLED_APPS]
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?.associate { entry ->
                    val parts = entry.split(":")
                    parts[0] to parts.getOrElse(1) { "" }
                } ?: emptyMap()
        )
    }.distinctUntilChanged()

    val streakCalculator = StreakCalculator(context, userPreferencesFlow)

    suspend fun refreshGlobalStreak(shieldRepository: ShieldRepository): Pair<Int, Int> =
        streakCalculator.refreshGlobalStreak(shieldRepository)

    suspend fun refreshAppStreaks(shieldRepository: ShieldRepository) =
        streakCalculator.refreshAppStreaks(shieldRepository)

    suspend fun refreshWebStreaks(shieldRepository: ShieldRepository) =
        streakCalculator.refreshWebStreaks(shieldRepository)

    suspend fun refreshAllAppStreaks(shieldRepository: ShieldRepository) =
        streakCalculator.refreshAllAppStreaks(shieldRepository)

    suspend fun runManualStreakRecovery(shieldRepository: ShieldRepository) =
        streakCalculator.runManualStreakRecovery(shieldRepository)

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

    suspend fun setAccessibilityRequired(required: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.ACCESSIBILITY_REQUIRED] = required }
    }

    suspend fun setScreenTimeTarget(minutes: Int) {
        val currentTarget = userPreferencesFlow.first().screenTimeTargetMinutes
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCREEN_TIME_TARGET] = minutes
        }
        if (minutes > currentTarget && currentTarget > 0) {
            context.runtimeDataStore.edit { preferences ->
                preferences[RuntimeKeys.GLOBAL_CURRENT_STREAK] = 0
            }
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

    suspend fun setIncentiveLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.INCENTIVE_LOCK_ENABLED] = enabled }
    }

    suspend fun setEyeCareEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.EYE_CARE_ENABLED] = enabled }
    }

    suspend fun setEyeCareWorkMinutes(minutes: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.EYE_CARE_WORK_MINUTES] = minutes }
    }

    suspend fun setEyeCareRestSeconds(seconds: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.EYE_CARE_REST_SECONDS] = seconds }
    }

    suspend fun setDayStartTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAY_START_HOUR] = hour
            preferences[PreferencesKeys.DAY_START_MINUTE] = minute
        }
    }

    suspend fun setUsageGlimpseEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.USAGE_GLIMPSE_ENABLED] = enabled }
    }

    suspend fun setWebsiteAutoTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.WEBSITE_AUTO_TRACKING] = enabled }
    }

    suspend fun setIncentiveLockDisableRequestTimestamp(timestamp: Long) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.INCENTIVE_LOCK_DISABLE_REQUEST_TIMESTAMP] = timestamp }
    }

    suspend fun setIncentiveLockGoalsMetToday(met: Boolean) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.INCENTIVE_LOCK_GOALS_MET_TODAY] = met }
    }

    suspend fun setIncentiveLockGoalsMetDate(date: String) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.INCENTIVE_LOCK_GOALS_MET_DATE] = date }
    }

    suspend fun setIncentiveBonusUsesUsed(uses: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        context.runtimeDataStore.edit { preferences ->
            preferences[RuntimeKeys.INCENTIVE_BONUS_USES_USED] = uses
            preferences[RuntimeKeys.INCENTIVE_BONUS_USES_DATE] = today
        }
    }

    suspend fun resetIncentiveBonusUsesIfNeeded() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        context.runtimeDataStore.edit { preferences ->
            val savedDate = preferences[RuntimeKeys.INCENTIVE_BONUS_USES_DATE] ?: ""
            if (savedDate != today) {
                preferences[RuntimeKeys.INCENTIVE_BONUS_USES_USED] = 0
                preferences[RuntimeKeys.INCENTIVE_BONUS_USES_DATE] = today
            }
        }
    }

    suspend fun setDismissedUninstalledApp(packageName: String, date: String) {
        context.runtimeDataStore.edit { preferences ->
            val current = preferences[RuntimeKeys.DISMISSED_UNINSTALLED_APPS] ?: ""
            val entries = current.split(",").filter { it.isNotEmpty() }.toMutableList()
            entries.removeAll { it.startsWith("$packageName:") }
            entries.add("$packageName:$date")
            preferences[RuntimeKeys.DISMISSED_UNINSTALLED_APPS] = entries.joinToString(",")
        }
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

                context.runtimeDataStore.edit { preferences ->
                    preferences[RuntimeKeys.WHITELIST_INITIALIZED] = true
                }
            }
        }
    }

    suspend fun setLastResetDate(date: String) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.LAST_RESET_DATE] = date }
    }

    suspend fun setLastWeeklyResetDate(timestamp: Long) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.LAST_WEEKLY_RESET_DATE] = timestamp }
    }

    suspend fun setLastStreakCheckDate(date: String) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.LAST_STREAK_CHECK_DATE] = date }
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

        val (launcherPackage, launcherApps) = withContext(Dispatchers.IO) {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val lPkg = try {
                context.packageManager.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (_: Exception) { null }
            val lApps = context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()
            lPkg to lApps
        }

        val excludePackages = setOfNotNull(context.packageName, launcherPackage) + prefs.whitelistedPackages + prefs.bedtimeWhitelistedPackages

        var liveStreak = 0
        var currentBest = prefs.bedtimeBestStreak

        val bedtimeLoopLimit = (prefs.bedtimeBestStreak + 15).coerceAtMost(30)
        for (i in 0..bedtimeLoopLimit) {
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

            val events = withContext(Dispatchers.IO) {
                try {
                    usageStatsManager.queryEvents(startTime - 30 * 60 * 1000L, actualEnd)
                } catch (e: Exception) {
                    null
                }
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
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.LAST_BACKUP_TIMESTAMP] = timestamp }
    }

    suspend fun setFloatingTabBarEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.FLOATING_TAB_BAR_ENABLED] = enabled }
    }

    suspend fun setExpressiveColors(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.EXPRESSIVE_COLORS] = enabled }
    }

    suspend fun setOverlayPaletteId(paletteId: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.OVERLAY_PALETTE_ID] = paletteId }
    }

    suspend fun setOverlaySheetOpacity(opacity: Float) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.OVERLAY_SHEET_OPACITY] = opacity }
    }

    suspend fun setOverlayFullScreen(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.OVERLAY_FULL_SCREEN] = enabled }
    }

    suspend fun setOverlayCustomHue(hue: Float) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.OVERLAY_CUSTOM_HUE] = hue }
    }

    suspend fun setTotalUsagePillEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.TOTAL_USAGE_PILL_ENABLED] = enabled }
    }

    private var lastSavedDailyUsage: Pair<Long, String>? = null

    suspend fun setForegroundNotificationStatusMode(mode: ForegroundNotificationStatusMode) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.FOREGROUND_NOTIFICATION_STATUS_MODE] = mode.name }
    }

    suspend fun setLastKnownDailyUsage(usage: Long, date: String) {
        if (lastSavedDailyUsage == Pair(usage, date)) return
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.LAST_KNOWN_DAILY_USAGE] = usage; preferences[RuntimeKeys.LAST_KNOWN_DAILY_USAGE_DATE] = date }
        lastSavedDailyUsage = Pair(usage, date)
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

    suspend fun setGracePeriodEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.GRACE_PERIOD_ENABLED] = enabled }
    }

    suspend fun setGracePeriodStartTime(time: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.GRACE_PERIOD_START_TIME] = time }
    }

    suspend fun setGracePeriodEndTime(time: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.GRACE_PERIOD_END_TIME] = time }
    }

    suspend fun setGracePeriodDays(days: Set<Int>) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.GRACE_PERIOD_DAYS] = days.joinToString(",") }
    }

    private var lastSavedBedtimeStreak: Pair<Int, Int>? = null

    suspend fun updateBedtimeStreak(current: Int, best: Int) {
        if (lastSavedBedtimeStreak == Pair(current, best)) return
        context.runtimeDataStore.edit { preferences ->
            preferences[RuntimeKeys.BEDTIME_CURRENT_STREAK] = current
            preferences[RuntimeKeys.BEDTIME_BEST_STREAK] = best
        }
        lastSavedBedtimeStreak = Pair(current, best)
    }

    suspend fun resetBedtimeStreak() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
        context.runtimeDataStore.edit { preferences ->
            preferences[RuntimeKeys.BEDTIME_CURRENT_STREAK] = 0
            preferences[RuntimeKeys.BEDTIME_BEST_STREAK] = 0
            preferences[RuntimeKeys.BEDTIME_STREAK_RESET_DATE] = today
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

    private var lastSavedSyncTimestamp = 0L

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        if (lastSavedSyncTimestamp == timestamp) return
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.LAST_SYNC_TIMESTAMP] = timestamp }
        lastSavedSyncTimestamp = timestamp
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
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.SHORTS_SCREEN_TIME_MS] = ms }
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

    suspend fun setBankingWarningDismissed(dismissed: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.BANKING_WARNING_DISMISSED] = dismissed }
    }

    suspend fun setLastBankingAppsCount(count: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.LAST_BANKING_APPS_COUNT] = count }
    }

    suspend fun setShowCurrentEvent(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SHOW_CURRENT_EVENT] = enabled }
    }

    suspend fun setDailyRecapEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DAILY_RECAP_ENABLED] = enabled }
    }

    suspend fun setWeeklyInsightEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.WEEKLY_INSIGHT_ENABLED] = enabled }
    }

    suspend fun setTrendMilestoneEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.TREND_MILESTONE_ENABLED] = enabled }
    }

    suspend fun setPerformanceLevel(level: PerformanceLevel) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PERFORMANCE_LEVEL] = level.name
        }
    }

    suspend fun applyPerformanceSettings(config: PerformanceConfig) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PERF_A11Y_ACTIVE_DELAY] = config.a11yActiveDelay
            prefs[PreferencesKeys.PERF_A11Y_INACTIVE_DELAY] = config.a11yInactiveDelay
            prefs[PreferencesKeys.PERF_SCREEN_OFF_DELAY] = config.screenOffDelay
            prefs[PreferencesKeys.PERF_POWER_SAVE_DELAY] = config.powerSaveDelay
            prefs[PreferencesKeys.PERF_USAGE_STATS_CACHE] = config.usageStatsCacheMs
            prefs[PreferencesKeys.PERF_SHIELD_DB_WRITE] = config.shieldDbWriteMs
            prefs[PreferencesKeys.PERF_SHIELD_DB_WRITE_NEAR] = config.shieldDbWriteNearMs
            prefs[PreferencesKeys.PERF_LAUNCHER_CACHE] = config.launcherCacheMs
            prefs[PreferencesKeys.PERF_MON_POWER_SAVE] = config.monPowerSave
            prefs[PreferencesKeys.PERF_MON_OVERLAY_SHOWING] = config.monOverlayShowing
            prefs[PreferencesKeys.PERF_MON_GOAL_NEAR] = config.monGoalNear
            prefs[PreferencesKeys.PERF_MON_GOAL_MID] = config.monGoalMid
            prefs[PreferencesKeys.PERF_MON_GOAL_FAR] = config.monGoalFar
            prefs[PreferencesKeys.PERF_MON_SHIELD_NEAR] = config.monShieldNear
            prefs[PreferencesKeys.PERF_MON_SHIELD_MID] = config.monShieldMid
            prefs[PreferencesKeys.PERF_MON_SHIELD_FAR] = config.monShieldFar
            prefs[PreferencesKeys.PERF_MON_SHIELD_VERY_FAR] = config.monShieldVeryFar
            prefs[PreferencesKeys.PERF_MON_DEFAULT] = config.monDefault
        }
    }

    suspend fun setPerfMonPowerSave(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_POWER_SAVE] = delay }
    }
    suspend fun setPerfMonOverlayShowing(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_OVERLAY_SHOWING] = delay }
    }
    suspend fun setPerfMonGoalNear(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_GOAL_NEAR] = delay }
    }
    suspend fun setPerfMonGoalMid(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_GOAL_MID] = delay }
    }
    suspend fun setPerfMonGoalFar(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_GOAL_FAR] = delay }
    }
    suspend fun setPerfMonShieldNear(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_SHIELD_NEAR] = delay }
    }
    suspend fun setPerfMonShieldMid(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_SHIELD_MID] = delay }
    }
    suspend fun setPerfMonShieldFar(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_SHIELD_FAR] = delay }
    }
    suspend fun setPerfMonShieldVeryFar(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_SHIELD_VERY_FAR] = delay }
    }
    suspend fun setPerfMonDefault(delay: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.PERF_MON_DEFAULT] = delay }
    }
    suspend fun resetPerfMonDelays() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.PERF_MON_POWER_SAVE)
            preferences.remove(PreferencesKeys.PERF_MON_OVERLAY_SHOWING)
            preferences.remove(PreferencesKeys.PERF_MON_GOAL_NEAR)
            preferences.remove(PreferencesKeys.PERF_MON_GOAL_MID)
            preferences.remove(PreferencesKeys.PERF_MON_GOAL_FAR)
            preferences.remove(PreferencesKeys.PERF_MON_SHIELD_NEAR)
            preferences.remove(PreferencesKeys.PERF_MON_SHIELD_MID)
            preferences.remove(PreferencesKeys.PERF_MON_SHIELD_FAR)
            preferences.remove(PreferencesKeys.PERF_MON_SHIELD_VERY_FAR)
            preferences.remove(PreferencesKeys.PERF_MON_DEFAULT)
        }
    }

    suspend fun updateLastChargeTimestamp(timestamp: Long) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.LAST_CHARGE_TIMESTAMP] = timestamp }
    }

    suspend fun resetAppStats(packageName: String) {
        context.runtimeDataStore.edit { preferences ->
            val currentMap = preferences[RuntimeKeys.MANUAL_RESET_TIMESTAMPS]?.split(",")
                ?.filter { it.contains(":") }
                ?.associate { 
                    val parts = it.split(":")
                    parts[0] to parts[1]
                }                ?.toMutableMap() ?: hashMapOf()
            
            currentMap[packageName] = System.currentTimeMillis().toString()
            preferences[RuntimeKeys.MANUAL_RESET_TIMESTAMPS] = currentMap.entries.joinToString(",") { "${it.key}:${it.value}" }
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

    suspend fun setStreakRecoveryPerformed(performed: Boolean) {
        context.runtimeDataStore.edit { preferences -> preferences[RuntimeKeys.STREAK_RECOVERY_PERFORMED] = performed }
    }

    suspend fun setDisableTrackingAtUnusedHours(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DISABLE_TRACKING_AT_UNUSED_HOURS] = enabled }
    }

    suspend fun setDisableTrackingStartHour(hour: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DISABLE_TRACKING_START_HOUR] = hour }
    }

    suspend fun setDisableTrackingEndHour(hour: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.DISABLE_TRACKING_END_HOUR] = hour }
    }



}

data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val fontOption: FontOption = FontOption.GOOGLE_SANS_FLEX,
    val dynamicColor: Boolean = true,
    val accessibilityDisabled: Boolean = false,
    val accessibilityRequired: Boolean = false,
    val screenTimeTargetMinutes: Int = 0,
    val emergencyRechargeDurationMinutes: Int = 60,
    val delayAppDurationSeconds: Int = 30,
    val sessionUsageOverlayEnabled: Boolean = false,
    val sessionUsageOverlaySize: Int = 100,
    val sessionUsageOverlayOpacity: Int = 90,
    val whitelistedPackages: Set<String> = emptySet(),
    val lastResetDate: String = "",
    val lastWeeklyResetDate: Long = 0L,
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
    val foregroundNotificationStatusMode: ForegroundNotificationStatusMode = ForegroundNotificationStatusMode.DEFAULT,
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
    val gracePeriodEnabled: Boolean = false,
    val gracePeriodStartTime: String = "12:00",
    val gracePeriodEndTime: String = "13:00",
    val gracePeriodDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
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
    val mindfulGatewayEnabled: Boolean = false,
    val refreshOnOpenUsageStats: Boolean = false,
    val checkUpdateOnStart: Boolean = false,
    val batteryStatsResetEnabled: Boolean = false,
    val showCurrentEvent: Boolean = false,
    val dailyRecapEnabled: Boolean = true,
    val weeklyInsightEnabled: Boolean = true,
    val trendMilestoneEnabled: Boolean = true,
    val performanceLevel: PerformanceLevel = PerformanceLevel.BALANCED,
    val perfA11yActiveDelay: Long = PerformanceConfig().a11yActiveDelay,
    val perfA11yInactiveDelay: Long = PerformanceConfig().a11yInactiveDelay,
    val perfScreenOffDelay: Long = PerformanceConfig().screenOffDelay,
    val perfPowerSaveDelay: Long = PerformanceConfig().powerSaveDelay,
    val perfUsageStatsCacheMs: Long = PerformanceConfig().usageStatsCacheMs,
    val perfShieldDbWriteMs: Long = PerformanceConfig().shieldDbWriteMs,
    val perfShieldDbWriteNearMs: Long = PerformanceConfig().shieldDbWriteNearMs,
    val perfLauncherCacheMs: Long = PerformanceConfig().launcherCacheMs,
    val perfGoalReminderTick: Long = PerformanceConfig().goalReminderTick,
    val perfDayChangeTick: Long = PerformanceConfig().dayChangeTick,
    val perfMonPowerSave: Long = PerformanceConfig().monPowerSave,
    val perfMonOverlayShowing: Long = PerformanceConfig().monOverlayShowing,
    val perfMonGoalNear: Long = PerformanceConfig().monGoalNear,
    val perfMonGoalMid: Long = PerformanceConfig().monGoalMid,
    val perfMonGoalFar: Long = PerformanceConfig().monGoalFar,
    val perfMonShieldNear: Long = PerformanceConfig().monShieldNear,
    val perfMonShieldMid: Long = PerformanceConfig().monShieldMid,
    val perfMonShieldFar: Long = PerformanceConfig().monShieldFar,
    val perfMonShieldVeryFar: Long = PerformanceConfig().monShieldVeryFar,
    val perfMonDefault: Long = PerformanceConfig().monDefault,
    val lastChargeTimestamp: Long = 0L,
    val manualResetTimestamps: Map<String, Long> = emptyMap(),
    val gsFlexSettings: GSFlexSettings = GSFlexSettings(),
    val streakRecoveryPerformed: Boolean = false,
    val dismissedUninstalledApps: Map<String, String> = emptyMap(),
    val incentiveLockEnabled: Boolean = false,
    val incentiveLockDisableRequestTimestamp: Long = 0L,
    val incentiveLockGoalsMetToday: Boolean = false,
    val incentiveBonusUsesUsed: Int = 0,
    val bankingWarningDismissed: Boolean = false,
    val eyeCareEnabled: Boolean = false,
    val eyeCareWorkMinutes: Int = 20,
    val eyeCareRestSeconds: Int = 20,
    val dayStartHour: Int = 0,
    val dayStartMinute: Int = 0,
    val usageGlimpseEnabled: Boolean = false,
    val websiteAutoTrackingEnabled: Boolean = false,
    val lastBankingAppsCount: Int = -1,
    val overlayPaletteId: String = "dynamic",
    val overlaySheetOpacity: Float = 1f,
    val overlayFullScreen: Boolean = false,
    val overlayCustomHue: Float = 270f,
    val disableTrackingAtUnusedHours: Boolean = false,
    val disableTrackingStartHour: Int = 2,
    val disableTrackingEndHour: Int = 4,
) {
    fun buildPerformanceConfig(): PerformanceConfig {
        if (performanceLevel.isPreset()) return performanceLevel.toConfig()
        return PerformanceConfig(
            a11yActiveDelay = perfA11yActiveDelay,
            a11yInactiveDelay = perfA11yInactiveDelay,
            screenOffDelay = perfScreenOffDelay,
            powerSaveDelay = perfPowerSaveDelay,
            usageStatsCacheMs = perfUsageStatsCacheMs,
            shieldDbWriteMs = perfShieldDbWriteMs,
            shieldDbWriteNearMs = perfShieldDbWriteNearMs,
            launcherCacheMs = perfLauncherCacheMs,
            goalReminderTick = perfGoalReminderTick,
            dayChangeTick = perfDayChangeTick,
            monPowerSave = perfMonPowerSave,
            monOverlayShowing = perfMonOverlayShowing,
            monGoalNear = perfMonGoalNear,
            monGoalMid = perfMonGoalMid,
            monGoalFar = perfMonGoalFar,
            monShieldNear = perfMonShieldNear,
            monShieldMid = perfMonShieldMid,
            monShieldFar = perfMonShieldFar,
            monShieldVeryFar = perfMonShieldVeryFar,
            monDefault = perfMonDefault,
        )
    }
}

fun PerformanceConfig.detectPreset(): PerformanceLevel {
    PerformanceLevel.entries.forEach { level ->
        if (level.isPreset()) {
            val preset = level.toConfig()
            if (a11yActiveDelay == preset.a11yActiveDelay &&
                a11yInactiveDelay == preset.a11yInactiveDelay &&
                screenOffDelay == preset.screenOffDelay &&
                powerSaveDelay == preset.powerSaveDelay &&
                usageStatsCacheMs == preset.usageStatsCacheMs &&
                shieldDbWriteMs == preset.shieldDbWriteMs &&
                shieldDbWriteNearMs == preset.shieldDbWriteNearMs &&
                launcherCacheMs == preset.launcherCacheMs &&
                monPowerSave == preset.monPowerSave &&
                monOverlayShowing == preset.monOverlayShowing &&
                monGoalNear == preset.monGoalNear &&
                monGoalMid == preset.monGoalMid &&
                monGoalFar == preset.monGoalFar &&
                monShieldNear == preset.monShieldNear &&
                monShieldMid == preset.monShieldMid &&
                monShieldFar == preset.monShieldFar &&
                monShieldVeryFar == preset.monShieldVeryFar &&
                monDefault == preset.monDefault) return level
        }
    }
    return PerformanceLevel.CUSTOM
}
