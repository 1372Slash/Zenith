package com.etrisad.zenith.service

import android.app.NotificationManager
import android.content.Context
import java.util.Calendar

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
    private fun releaseOwnership(context: Context) {
        owned = false
        previous = null
        persist(context)
    }

    @Synchronized
    fun applyBedtimeDnd(context: Context, nm: NotificationManager, wantDnd: Boolean) {
        val appContext = context.applicationContext
        load(appContext)
        try {
            if (!nm.isNotificationPolicyAccessGranted) {
                if (owned == true) releaseOwnership(appContext)
                return
            }
            val current = try {
                nm.currentInterruptionFilter
            } catch (_: Exception) {
                return
            }
            if (wantDnd) {
                if (owned == true) {
                    if (current != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                        releaseOwnership(appContext)
                    }
                    return
                }
                if (current != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    return
                }
                previous = current
                owned = true
                persist(appContext)
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } catch (_: Exception) {}
            } else if (owned == true) {
                owned = false
                val toRestore = previous ?: NotificationManager.INTERRUPTION_FILTER_ALL
                previous = null
                persist(appContext)
                if (current == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    try {
                        nm.setInterruptionFilter(toRestore)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    fun isBedtimeDndActiveNow(
        bedtimeEnabled: Boolean,
        bedtimeDndEnabled: Boolean,
        startTime: String,
        endTime: String,
        days: Set<Int>,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!bedtimeEnabled || !bedtimeDndEnabled) return false
        return try {
            val cal = Calendar.getInstance()
            cal.timeInMillis = nowMillis
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val sp = startTime.split(":")
            val ep = endTime.split(":")
            val startMinutes = sp[0].toInt() * 60 + sp[1].toInt()
            val endMinutes = ep[0].toInt() * 60 + ep[1].toInt()
            if (endMinutes > startMinutes) {
                day in days && nowMinutes in startMinutes until endMinutes
            } else {
                if (nowMinutes >= startMinutes) {
                    day in days
                } else if (nowMinutes < endMinutes) {
                    cal.add(Calendar.DATE, -1)
                    cal.get(Calendar.DAY_OF_WEEK) in days
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun reconcileWithPrefs(
        context: Context,
        bedtimeEnabled: Boolean,
        bedtimeDndEnabled: Boolean,
        startTime: String,
        endTime: String,
        days: Set<Int>
    ) {
        val appContext = context.applicationContext
        load(appContext)
        if (owned != true) return
        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm == null || !nm.isNotificationPolicyAccessGranted) {
                releaseOwnership(appContext)
                return
            }
            if (isBedtimeDndActiveNow(bedtimeEnabled, bedtimeDndEnabled, startTime, endTime, days)) {
                return
            }
            val current = try {
                nm.currentInterruptionFilter
            } catch (_: Exception) {
                return
            }
            owned = false
            val toRestore = previous ?: NotificationManager.INTERRUPTION_FILTER_ALL
            previous = null
            persist(appContext)
            if (current == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                try {
                    nm.setInterruptionFilter(toRestore)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
