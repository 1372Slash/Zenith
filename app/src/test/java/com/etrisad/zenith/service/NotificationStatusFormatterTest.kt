package com.etrisad.zenith.service

import com.etrisad.zenith.data.preferences.ForegroundNotificationStatusMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationStatusFormatterTest {
    @Test
    fun dailyUsageModeFormatsUsageTime() {
        val text = NotificationStatusFormatter.format(
            mode = ForegroundNotificationStatusMode.DAILY_USAGE,
            dailyUsageMillis = 2 * 60 * 60 * 1000L + 15 * 60 * 1000L,
            activeFocusSummary = ActiveFocusSummary(goals = 0, shields = 0, schedules = 0)
        )

        assertEquals("Usage today: 2h 15m", text)
    }

    @Test
    fun activeFocusModeFormatsConfiguredFocusCounts() {
        val text = NotificationStatusFormatter.format(
            mode = ForegroundNotificationStatusMode.ACTIVE_FOCUS,
            dailyUsageMillis = 0L,
            activeFocusSummary = ActiveFocusSummary(goals = 1, shields = 2, schedules = 1)
        )

        assertEquals("Active focus: 1 goal, 2 shields, 1 schedule", text)
    }

    @Test
    fun defaultModeKeepsExistingNotificationText() {
        val text = NotificationStatusFormatter.format(
            mode = ForegroundNotificationStatusMode.DEFAULT,
            dailyUsageMillis = 0L,
            activeFocusSummary = ActiveFocusSummary(goals = 0, shields = 0, schedules = 0)
        )

        assertEquals("Protecting your focus...", text)
    }
}
