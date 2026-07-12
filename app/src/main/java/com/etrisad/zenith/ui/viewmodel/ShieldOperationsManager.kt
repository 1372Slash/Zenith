package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import android.content.Intent
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.local.database.DbLogBuffer
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ShieldOperationsManager(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) {
    fun saveFocus(
        packageName: String, appName: String, timeLimitMinutes: Int, maxEmergencyUses: Int,
        isRemindersEnabled: Boolean, isStrictModeEnabled: Boolean, isAutoQuitEnabled: Boolean,
        maxUsesPerPeriod: Int, refreshPeriodMinutes: Int, goalReminderPeriodMinutes: Int,
        isDelayAppEnabled: Boolean, isGoalCallerEnabled: Boolean = false,
        isGoalCallerSoundEnabled: Boolean = true, goalCallerSoundUri: String? = null,
        limitPeriod: LimitPeriod = LimitPeriod.DAILY,
        type: FocusType = FocusType.SHIELD,
        scope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        scope.launch {
            try {
                android.util.Log.d("ZenithGoalShield", "OPS_SAVE_START: pkg=$packageName type=$type appName=$appName timeLimit=${timeLimitMinutes}m")
                DbLogBuffer.d("ZenithGoalShield", "OPS_SAVE_START: pkg=$packageName type=$type appName=$appName timeLimit=${timeLimitMinutes}m")
                val existing = shieldRepository.getShieldByPackageName(packageName)
                android.util.Log.d("ZenithGoalShield", "OPS_SAVE_EXISTING: ${if (existing != null) "UPDATE existing ${existing.type}" else "NEW insert"}")
                DbLogBuffer.d("ZenithGoalShield", "OPS_SAVE_EXISTING: ${if (existing != null) "UPDATE existing ${existing.type}" else "NEW insert"}")
                val periodChanged = existing != null && existing.limitPeriod != limitPeriod
                val newRemainingMillis = if (periodChanged) timeLimitMinutes * 60 * 1000L else (existing?.remainingTimeMillis ?: (timeLimitMinutes * 60 * 1000L))
                val shield = existing?.copy(
                    timeLimitMinutes = timeLimitMinutes, limitPeriod = limitPeriod,
                    remainingTimeMillis = newRemainingMillis, maxEmergencyUses = maxEmergencyUses,
                    isRemindersEnabled = isRemindersEnabled, isStrictModeEnabled = isStrictModeEnabled,
                    isAutoQuitEnabled = isAutoQuitEnabled, maxUsesPerPeriod = maxUsesPerPeriod,
                    refreshPeriodMinutes = refreshPeriodMinutes, goalReminderPeriodMinutes = goalReminderPeriodMinutes,
                    isDelayAppEnabled = isDelayAppEnabled, isGoalCallerEnabled = isGoalCallerEnabled,
                    isGoalCallerSoundEnabled = isGoalCallerSoundEnabled, goalCallerSoundUri = goalCallerSoundUri
                ) ?: ShieldEntity(
                    packageName = packageName, appName = appName, type = type,
                    timeLimitMinutes = timeLimitMinutes, limitPeriod = limitPeriod,
                    remainingTimeMillis = newRemainingMillis, maxEmergencyUses = maxEmergencyUses,
                    isRemindersEnabled = isRemindersEnabled, isStrictModeEnabled = isStrictModeEnabled,
                    isAutoQuitEnabled = isAutoQuitEnabled, maxUsesPerPeriod = maxUsesPerPeriod,
                    refreshPeriodMinutes = refreshPeriodMinutes, goalReminderPeriodMinutes = goalReminderPeriodMinutes,
                    isDelayAppEnabled = isDelayAppEnabled, isGoalCallerEnabled = isGoalCallerEnabled,
                    isGoalCallerSoundEnabled = isGoalCallerSoundEnabled, goalCallerSoundUri = goalCallerSoundUri
                )
                shieldRepository.insertShield(shield)
                android.util.Log.d("ZenithGoalShield", "OPS_SAVE_DONE: pkg=$packageName type=$type - insertShield called, cache will update via Flow")
                DbLogBuffer.d("ZenithGoalShield", "OPS_SAVE_DONE: pkg=$packageName type=$type - insertShield called, cache will update via Flow")
                com.etrisad.zenith.service.SharedMonitoringState.notifiedGoals.remove(packageName)
                triggerServiceRefresh()
            } catch (e: Exception) {
                android.util.Log.e("ZenithGoalShield", "OPS_SAVE_ERROR: pkg=$packageName type=$type error=${e.message}", e)
                DbLogBuffer.e("ZenithGoalShield", "OPS_SAVE_ERROR: pkg=$packageName type=$type error=${e.message}")
                android.util.Log.e("ShieldOperations", "Error saving focus: ${e.message}")
            } finally {
                onComplete()
            }
        }
    }

    suspend fun pauseShield(shield: ShieldEntity, durationHours: Int?): ShieldEntity {
        val end = if (durationHours != null) System.currentTimeMillis() + (durationHours * 3600000L) else 0L
        val updated = shield.copy(isPaused = true, pauseEndTimestamp = end)
        shieldRepository.updateShield(updated)
        return updated
    }

    suspend fun resumeShield(shield: ShieldEntity): ShieldEntity {
        val updated = shield.copy(isPaused = false, pauseEndTimestamp = 0L)
        shieldRepository.updateShield(updated)
        return updated
    }

    suspend fun deleteShield(shield: ShieldEntity) {
        shieldRepository.deleteShield(shield)
        triggerServiceRefresh()
    }

    suspend fun deleteShieldFromDetail(shield: ShieldEntity) {
        shieldRepository.deleteShield(shield)
        triggerServiceRefresh()
    }

    fun resetAppUsage(packageName: String, userPreferencesRepository: UserPreferencesRepository, scope: CoroutineScope) {
        scope.launch {
            userPreferencesRepository.resetAppStats(packageName)
        }
    }

    fun setBatteryStatsResetEnabled(enabled: Boolean, userPreferencesRepository: UserPreferencesRepository, scope: CoroutineScope) {
        scope.launch {
            userPreferencesRepository.setBatteryStatsResetEnabled(enabled)
        }
    }

    fun triggerServiceRefresh() {
        val intent = Intent("com.etrisad.zenith.action.REFRESH_SERVICES").apply { setPackage(context.packageName) }
        context.sendBroadcast(intent)
    }

    fun formatDuration(millis: Long): String {
        val h = millis / 3600000L; val m = (millis / 60000L) % 60L; val s = (millis / 1000L) % 60L
        return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m"; else -> "${s}s" }
    }
}
