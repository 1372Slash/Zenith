package com.etrisad.zenith.service

import com.etrisad.zenith.data.preferences.ForegroundNotificationStatusMode

data class ActiveFocusSummary(
    val goals: Int,
    val shields: Int,
    val schedules: Int
)

object NotificationStatusFormatter {
    fun format(
        mode: ForegroundNotificationStatusMode,
        dailyUsageMillis: Long,
        activeFocusSummary: ActiveFocusSummary
    ): String {
        return when (mode) {
            ForegroundNotificationStatusMode.DAILY_USAGE ->
                "Usage today: ${formatDuration(dailyUsageMillis)}"
            ForegroundNotificationStatusMode.ACTIVE_FOCUS ->
                formatActiveFocus(activeFocusSummary)
            ForegroundNotificationStatusMode.DEFAULT ->
                "Protecting your focus..."
        }
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = (millis.coerceAtLeast(0L) / 60000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    private fun formatActiveFocus(summary: ActiveFocusSummary): String {
        val parts = buildList {
            if (summary.goals > 0) add(pluralize(summary.goals, "goal"))
            if (summary.shields > 0) add(pluralize(summary.shields, "shield"))
            if (summary.schedules > 0) add(pluralize(summary.schedules, "schedule"))
        }

        return if (parts.isEmpty()) {
            "No active focus rules"
        } else {
            "Active focus: ${parts.joinToString(", ")}"
        }
    }

    private fun pluralize(count: Int, label: String): String {
        return "$count $label${if (count == 1) "" else "s"}"
    }
}
