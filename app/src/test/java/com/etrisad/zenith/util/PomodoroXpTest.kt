package com.etrisad.zenith.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroXpTest {

    @Test
    fun `reward scales with focus duration`() {
        assertEquals(50, PomodoroXp.xpForSession(25, relaxedMode = false))
        assertEquals(100, PomodoroXp.xpForSession(50, relaxedMode = false))
        assertEquals(0, PomodoroXp.xpForSession(0, relaxedMode = false))
    }

    @Test
    fun `relaxed mode pays only a few percent less`() {
        (1..180).forEach { minutes ->
            val normal = PomodoroXp.xpForSession(minutes, relaxedMode = false)
            val relaxed = PomodoroXp.xpForSession(minutes, relaxedMode = true)
            assertTrue("relaxed XP should not exceed normal XP", relaxed <= normal)
            val loss = (normal - relaxed).toFloat() / normal
            assertTrue("loss for $minutes min was $loss", loss <= 0.05f)
            if (normal >= 20) {
                assertTrue("loss for $minutes min was $loss", loss >= 0.01f)
            }
        }
    }

    @Test
    fun `relaxed reduction percent matches the actual reward difference`() {
        assertEquals(2, PomodoroXp.relaxedReductionPercent(25))
        assertEquals(3, PomodoroXp.relaxedReductionPercent(50))
        assertEquals(0, PomodoroXp.relaxedReductionPercent(5))
    }

    @Test
    fun `plan total is the per-session reward times session count`() {
        assertEquals(200, PomodoroXp.totalXpForPlan(25, 4, relaxedMode = false))
        assertEquals(0, PomodoroXp.totalXpForPlan(25, 0, relaxedMode = false))
    }
}
