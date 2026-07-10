package com.etrisad.zenith.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.etrisad.zenith.ZenithApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ZenithApplication
                val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
                val enabledAlarms = app.userPreferencesRepository
                    .parseAlarms(prefs.alarmsJson)
                    .filter { it.enabled }
                AlarmBroadcastReceiver.rescheduleAllAlarms(context, enabledAlarms)
                Log.d("BootReceiver", "Rescheduled ${enabledAlarms.size} alarms after boot/update")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule alarms: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
