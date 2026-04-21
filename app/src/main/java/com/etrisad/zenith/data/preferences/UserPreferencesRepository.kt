package com.etrisad.zenith.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCESSIBILITY_DISABLED = booleanPreferencesKey("accessibility_disabled")
        val SCREEN_TIME_TARGET = intPreferencesKey("screen_time_target")
        val EMERGENCY_RECHARGE_DURATION_MINUTES = intPreferencesKey("emergency_recharge_duration_minutes")
        val DELAY_APP_DURATION_SECONDS = intPreferencesKey("delay_app_duration_seconds")
        val SESSION_USAGE_OVERLAY_ENABLED = booleanPreferencesKey("session_usage_overlay_enabled")
        val SESSION_USAGE_OVERLAY_SIZE = intPreferencesKey("session_usage_overlay_size")
        val SESSION_USAGE_OVERLAY_OPACITY = intPreferencesKey("session_usage_overlay_opacity")
        val WHITELISTED_PACKAGES = stringPreferencesKey("whitelisted_packages")
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
            val dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
            val accessibilityDisabled = preferences[PreferencesKeys.ACCESSIBILITY_DISABLED] ?: false
            val screenTimeTarget = preferences[PreferencesKeys.SCREEN_TIME_TARGET] ?: 0
            val emergencyRechargeDuration = preferences[PreferencesKeys.EMERGENCY_RECHARGE_DURATION_MINUTES] ?: 60
            val delayAppDuration = preferences[PreferencesKeys.DELAY_APP_DURATION_SECONDS] ?: 30
            val sessionUsageOverlayEnabled = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_ENABLED] ?: false
            val sessionUsageOverlaySize = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_SIZE] ?: 100
            val sessionUsageOverlayOpacity = preferences[PreferencesKeys.SESSION_USAGE_OVERLAY_OPACITY] ?: 90
            val whitelistedPackages = preferences[PreferencesKeys.WHITELISTED_PACKAGES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            UserPreferences(
                themeConfig = themeConfig,
                dynamicColor = dynamicColor,
                accessibilityDisabled = accessibilityDisabled,
                screenTimeTargetMinutes = screenTimeTarget,
                emergencyRechargeDurationMinutes = emergencyRechargeDuration,
                delayAppDurationSeconds = delayAppDuration,
                sessionUsageOverlayEnabled = sessionUsageOverlayEnabled,
                sessionUsageOverlaySize = sessionUsageOverlaySize,
                sessionUsageOverlayOpacity = sessionUsageOverlayOpacity,
                whitelistedPackages = whitelistedPackages
            )
        }

    suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_CONFIG] = themeConfig.name
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
}

data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val accessibilityDisabled: Boolean = false,
    val screenTimeTargetMinutes: Int = 0,
    val emergencyRechargeDurationMinutes: Int = 60,
    val delayAppDurationSeconds: Int = 30,
    val sessionUsageOverlayEnabled: Boolean = false,
    val sessionUsageOverlaySize: Int = 100,
    val sessionUsageOverlayOpacity: Int = 90,
    val whitelistedPackages: Set<String> = emptySet()
)
