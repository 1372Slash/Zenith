package com.etrisad.zenith.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroStreakTest {

    @Test
    fun `first completed day starts a one day streak`() {
        assertEquals(1, PomodoroStreak.nextStreak(0, "", "2026-11-13"))
    }

    @Test
    fun `consecutive days grow the streak`() {
        assertEquals(4, PomodoroStreak.nextStreak(3, "2026-11-12", "2026-11-13"))
    }

    @Test
    fun `more sessions on the same day do not double count`() {
        assertEquals(7, PomodoroStreak.nextStreak(7, "2026-11-13", "2026-11-13"))
    }

    @Test
    fun `a missed day resets the streak`() {
        assertEquals(1, PomodoroStreak.nextStreak(12, "2026-11-10", "2026-11-13"))
        assertEquals(1, PomodoroStreak.nextStreak(12, "not-a-date", "2026-11-13"))
    }

    @Test
    fun `month and year boundaries keep the streak`() {
        assertEquals(9, PomodoroStreak.nextStreak(8, "2026-10-31", "2026-11-01"))
        assertEquals(3, PomodoroStreak.nextStreak(2, "2025-12-31", "2026-01-01"))
    }

    @Test
    fun `effective streak stays alive until a full day is missed`() {
        assertEquals(5, PomodoroStreak.effectiveStreak(5, "2026-11-13", "2026-11-13"))
        assertEquals(5, PomodoroStreak.effectiveStreak(5, "2026-11-12", "2026-11-13"))
        assertEquals(0, PomodoroStreak.effectiveStreak(5, "2026-11-11", "2026-11-13"))
        assertEquals(0, PomodoroStreak.effectiveStreak(0, "2026-11-13", "2026-11-13"))
    }

    @Test
    fun `projected streak previews the next session reward`() {
        assertEquals(6, PomodoroStreak.projectedStreak(5, "2026-11-12", "2026-11-13"))
        assertEquals(5, PomodoroStreak.projectedStreak(5, "2026-11-13", "2026-11-13"))
        assertEquals(1, PomodoroStreak.projectedStreak(5, "2026-11-10", "2026-11-13"))
        assertEquals(1, PomodoroStreak.projectedStreak(0, "", "2026-11-13"))
    }

    @Test
    fun `multiplier grows one percent per day and caps at thirty`() {
        assertEquals(0, PomodoroStreak.bonusPercent(0))
        assertEquals(1, PomodoroStreak.bonusPercent(1))
        assertEquals(7, PomodoroStreak.bonusPercent(7))
        assertEquals(30, PomodoroStreak.bonusPercent(30))
        assertEquals(30, PomodoroStreak.bonusPercent(99))
        assertEquals("1.00x", PomodoroStreak.multiplierLabel(0))
        assertEquals("1.07x", PomodoroStreak.multiplierLabel(7))
        assertEquals("1.30x", PomodoroStreak.multiplierLabel(30))
        assertFalse(PomodoroStreak.isAtCap(29))
        assertTrue(PomodoroStreak.isAtCap(30))
        assertEquals(30, PomodoroStreak.daysUntilCap(0))
        assertEquals(1, PomodoroStreak.daysUntilCap(29))
        assertEquals(0, PomodoroStreak.daysUntilCap(30))
    }

    @Test
    fun `streak applies to the XP left after the relaxed reduction`() {
        assertEquals(50, PomodoroXp.xpForSession(25, relaxedMode = false, streakDays = 0))
        assertEquals(51, PomodoroXp.xpForSession(25, relaxedMode = false, streakDays = 1))
        assertEquals(65, PomodoroXp.xpForSession(25, relaxedMode = false, streakDays = 30))
        assertEquals(49, PomodoroXp.xpForSession(25, relaxedMode = true, streakDays = 0))
        assertEquals(64, PomodoroXp.xpForSession(25, relaxedMode = true, streakDays = 30))
        assertEquals(260, PomodoroXp.totalXpForPlan(25, 4, relaxedMode = false, streakDays = 30))
    }

    @Test
    fun `reward never shrinks as the streak grows`() {
        var previous = 0
        (0..40).forEach { streak ->
            val xp = PomodoroXp.xpForSession(25, relaxedMode = true, streakDays = streak)
            assertTrue("XP dropped at streak $streak", xp >= previous)
            previous = xp
        }
    }
}