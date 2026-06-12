package com.etrisad.zenith.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesDefaultsTest {
    @Test
    fun defaultForegroundNotificationStatusModeKeepsNotificationSimple() {
        assertEquals(
            ForegroundNotificationStatusMode.DEFAULT,
            UserPreferences().foregroundNotificationStatusMode
        )
    }
}
