package com.etrisad.zenith.util

import kotlin.math.roundToInt

object PomodoroXp {
    const val XP_PER_FOCUS_MINUTE = 2
    const val RELAXED_XP_REDUCTION_PERCENT = 3
    const val MAX_RELAXED_XP_REDUCTION_PERCENT = 5

    fun xpForSession(focusMinutes: Int, relaxedMode: Boolean, streakDays: Int = 0): Int {
        if (focusMinutes <= 0) return 0
        val base = (focusMinutes * XP_PER_FOCUS_MINUTE).coerceAtLeast(1)
        val afterRelaxed = if (!relaxedMode) {
            base
        } else {
            val maxReduction = base * MAX_RELAXED_XP_REDUCTION_PERCENT / 100
            if (maxReduction <= 0) {
                base
            } else {
                val reduction = (base * RELAXED_XP_REDUCTION_PERCENT / 100)
                    .coerceAtLeast(1)
                    .coerceAtMost(maxReduction)
                base - reduction
            }
        }
        return PomodoroStreak.applyMultiplier(afterRelaxed, streakDays)
    }

    fun relaxedReductionPercent(focusMinutes: Int): Int {
        val normal = xpForSession(focusMinutes, relaxedMode = false)
        val relaxed = xpForSession(focusMinutes, relaxedMode = true)
        if (normal <= 0) return 0
        return ((normal - relaxed) * 100f / normal).roundToInt()
    }

    fun totalXpForPlan(focusMinutes: Int, sessionCount: Int, relaxedMode: Boolean, streakDays: Int = 0): Int {
        if (focusMinutes <= 0 || sessionCount <= 0) return 0
        return xpForSession(focusMinutes, relaxedMode, streakDays) * sessionCount
    }
}
