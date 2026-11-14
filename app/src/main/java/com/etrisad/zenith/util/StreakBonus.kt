package com.etrisad.zenith.util

import java.util.Locale

object StreakBonus {
    const val CAP_DAYS = 30
    const val BONUS_PERCENT_PER_DAY = 1
    const val MAX_BONUS_PERCENT = CAP_DAYS * BONUS_PERCENT_PER_DAY
    const val MAX_MULTIPLIER = 1f + MAX_BONUS_PERCENT / 100f

    fun bonusPercent(streakDays: Int): Int =
        streakDays.coerceIn(0, CAP_DAYS) * BONUS_PERCENT_PER_DAY

    fun multiplier(streakDays: Int): Float = 1f + bonusPercent(streakDays) / 100f

    fun multiplierLabel(streakDays: Int): String =
        String.format(Locale.US, "%.2fx", multiplier(streakDays))

    fun applyMultiplier(baseXp: Int, streakDays: Int): Int {
        if (baseXp <= 0) return 0
        val percent = 100 + bonusPercent(streakDays)
        return ((baseXp.toLong() * percent + 50L) / 100L).toInt()
    }

    fun isAtCap(streakDays: Int): Boolean = streakDays >= CAP_DAYS

    fun daysUntilCap(streakDays: Int): Int =
        (CAP_DAYS - streakDays.coerceAtLeast(0)).coerceAtLeast(0)

    fun progress(streakDays: Int): Float =
        (streakDays.coerceAtLeast(0).toFloat() / CAP_DAYS).coerceIn(0f, 1f)
}
