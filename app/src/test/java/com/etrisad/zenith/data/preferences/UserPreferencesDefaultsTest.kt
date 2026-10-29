package com.etrisad.zenith.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesDefaultsTest {
    @Test
    fun defaultForegroundNotificationStatusModeKeepsNotificationSimple() {
        assertEquals(
            ForegroundNotificationStatusMode.DEFAULT,
            UserPreferences().foregroundNotificationStatusMode
        )
    }

    @Test
    fun reelsShieldDefaultsAppSupportTypes() {
        
        assertTrue(ReelsShieldDefaults.isFullySupported("com.instagram.android"))
        assertTrue(ReelsShieldDefaults.isFullySupported("com.google.android.youtube"))
        assertFalse(ReelsShieldDefaults.isUntested("com.instagram.android"))
        assertFalse(ReelsShieldDefaults.isUntested("com.google.android.youtube"))

        
        assertTrue(ReelsShieldDefaults.isPartial("com.facebook.lite"))
        assertTrue(ReelsShieldDefaults.isPartial("com.zhiliaoapp.musically"))
        assertFalse(ReelsShieldDefaults.isUntested("com.facebook.lite"))

        
        assertTrue(ReelsShieldDefaults.isUntested("com.facebook.katana"))
        assertTrue(ReelsShieldDefaults.isUntested("com.snapchat.android"))
        assertTrue(ReelsShieldDefaults.isUntested("com.unknown.shortvideoapp"))
    }
}
