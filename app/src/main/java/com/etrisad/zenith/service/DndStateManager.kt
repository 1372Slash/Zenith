package com.etrisad.zenith.service

import android.app.NotificationManager
import android.content.Context

object DndStateManager {
    private const val PREFS = "zenith_dnd_state"
    private const val KEY_OWNED = "dnd_owned_by_app"
    private const val KEY_PREVIOUS = "dnd_previous_filter"

    @Volatile
    private var owned: Boolean? = null

    @Volatile
    private var previous: Int? = null
    private var loaded = false

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        try {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            owned = sp.getBoolean(KEY_OWNED, false)
            val prev = sp.getInt(KEY_PREVIOUS, Int.MIN_VALUE)
            previous = if (prev == Int.MIN_VALUE) null else prev
        } catch (_: Exception) {
            owned = false
            previous = null
        }
        loaded = true
    }

    @Synchronized
    private fun persist(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val e = sp.edit()
            e.putBoolean(KEY_OWNED, owned == true)
            val p = previous
            if (p == null) e.remove(KEY_PREVIOUS) else e.putInt(KEY_PREVIOUS, p)
            e.apply()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun applyBedtimeDnd(context: Context, nm: NotificationManager, wantDnd: Boolean) {
        load(context.applicationContext)
        try {
            if (!nm.isNotificationPolicyAccessGranted) return
            val current = try {
                nm.currentInterruptionFilter
            } catch (_: Exception) {
                return
            }
            if (wantDnd) {
                if (owned == true) {
                    if (current != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                        try {
                            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        } catch (_: Exception) {}
                    }
                    return
                }
                if (current != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    return
                }
                previous = current
                owned = true
                persist(context.applicationContext)
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } catch (_: Exception) {}
            } else if (owned == true) {
                owned = false
                val toRestore = previous ?: NotificationManager.INTERRUPTION_FILTER_ALL
                previous = null
                persist(context.applicationContext)
                if (current == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    try {
                        nm.setInterruptionFilter(toRestore)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }
}
