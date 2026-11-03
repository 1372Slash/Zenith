package com.etrisad.zenith.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectShieldControllerTest {

    private fun policy(
        enabled: Boolean = true,
        shielded: Set<String> = setOf(SHIELDED),
        whitelisted: Set<String> = emptySet(),
        triggers: Set<String> = setOf(TRIGGER)
    ): RedirectShieldPolicy = RedirectShieldPolicy().apply {
        update(
            enabled = enabled,
            shieldedApps = shielded,
            whitelistedApps = whitelisted,
            triggerApps = triggers
        )
    }

    @Test
    fun `shielded app opened while trigger is in the foreground is redirected`() {
        val policy = policy()

        assertFalse(policy.onForegroundChanged(TRIGGER).redirect)
        val redirect = policy.onForegroundChanged(SHIELDED)

        assertTrue(redirect.redirect)
        assertEquals(TRIGGER, redirect.returnToPackage)
        assertEquals("shielded", redirect.reason)
    }

    @Test
    fun `whitelisted app may open a shielded app`() {
        val policy = policy(whitelisted = setOf(WHITELISTED))

        assertFalse(policy.onForegroundChanged(TRIGGER).redirect)
        assertFalse(policy.onForegroundChanged(WHITELISTED).redirect)
        assertFalse(policy.onForegroundChanged(SHIELDED).redirect)
    }

    @Test
    fun `shielded app opened without a trigger is allowed`() {
        val policy = policy()

        val result = policy.onForegroundChanged(SHIELDED)
        assertFalse(result.redirect)
        assertEquals("not_armed", result.reason)
        assertNull(result.returnToPackage)
    }

    @Test
    fun `disabled shield never redirects`() {
        val policy = policy(enabled = false)

        assertFalse(policy.onForegroundChanged(TRIGGER).redirect)
        val result = policy.onForegroundChanged(SHIELDED)
        assertFalse(result.redirect)
        assertEquals("not_configured", result.reason)
    }

    @Test
    fun `missing configuration never redirects`() {
        val withoutTriggers = policy(triggers = emptySet())
        assertFalse(withoutTriggers.onForegroundChanged(TRIGGER).redirect)
        assertFalse(withoutTriggers.onForegroundChanged(SHIELDED).redirect)

        val withoutShielded = policy(shielded = emptySet())
        assertFalse(withoutShielded.onForegroundChanged(TRIGGER).redirect)
        assertFalse(withoutShielded.onForegroundChanged(SHIELDED).redirect)
    }

    @Test
    fun `leaving the trigger keeps the shield armed for the grace window`() {
        val policy = policy()

        assertFalse(policy.onForegroundChanged(TRIGGER).redirect)
        assertFalse(policy.onForegroundChanged(LAUNCHER).redirect)

        val redirect = policy.onForegroundChanged(SHIELDED)
        assertTrue(redirect.redirect)
        assertEquals(LAUNCHER, redirect.returnToPackage)
    }

    @Test
    fun `an intermediary app does not break the redirect chain`() {
        val policy = policy()

        assertFalse(policy.onForegroundChanged(TRIGGER).redirect)
        assertFalse(policy.onForegroundChanged(AD_INTERSTITIAL).redirect)
        assertFalse(policy.onForegroundChanged(AD_INTERSTITIAL).redirect)

        val redirect = policy.onForegroundChanged(SHIELDED)
        assertTrue(redirect.redirect)
        assertEquals(AD_INTERSTITIAL, redirect.returnToPackage)
    }

    @Test
    fun `consecutive redirects respect the cooldown`() {
        val policy = policy()

        assertFalse(policy.onForegroundChanged(TRIGGER).redirect)
        assertTrue(policy.onForegroundChanged(SHIELDED).redirect)
        assertFalse(policy.onForegroundChanged(TRIGGER).redirect)
        assertFalse(policy.onForegroundChanged(SHIELDED).redirect)
    }

    private companion object {
        const val TRIGGER = "com.example.trigger"
        const val SHIELDED = "com.example.shielded"
        const val WHITELISTED = "com.example.whitelisted"
        const val LAUNCHER = "com.example.launcher"
        const val AD_INTERSTITIAL = "com.example.ad"
    }
}
