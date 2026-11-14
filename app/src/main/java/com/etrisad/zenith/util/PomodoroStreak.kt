package com.etrisad.zenith.util

import java.time.LocalDate

object PomodoroStreak {
    const val CAP_DAYS = StreakBonus.CAP_DAYS
    const val BONUS_PERCENT_PER_DAY = StreakBonus.BONUS_PERCENT_PER_DAY
    const val MAX_BONUS_PERCENT = StreakBonus.MAX_BONUS_PERCENT

    fun nextStreak(currentStreak: Int, lastStreakDate: String, todayDate: String): Int {
        val current = currentStreak.coerceAtLeast(0)
        if (todayDate.isBlank()) return current
        if (lastStreakDate == todayDate) return current.coerceAtLeast(1)
        if (lastStreakDate.isBlank()) return 1
        val isConsecutive = runCatching {
            LocalDate.parse(todayDate).minusDays(1) == LocalDate.parse(lastStreakDate)
        }.getOrDefault(false)
        return if (isConsecutive) current + 1 else 1
    }

    fun effectiveStreak(currentStreak: Int, lastStreakDate: String, todayDate: String): Int {
        if (currentStreak <= 0 || lastStreakDate.isBlank() || todayDate.isBlank()) return 0
        if (lastStreakDate == todayDate) return currentStreak
        val activeYesterday = runCatching {
            LocalDate.parse(lastStreakDate) == LocalDate.parse(todayDate).minusDays(1)
        }.getOrDefault(false)
        return if (activeYesterday) currentStreak else 0
    }

    fun projectedStreak(currentStreak: Int, lastStreakDate: String, todayDate: String): Int {
        if (lastStreakDate == todayDate) return currentStreak.coerceAtLeast(0)
        return nextStreak(currentStreak, lastStreakDate, todayDate)
    }

    fun bonusPercent(streakDays: Int): Int = StreakBonus.bonusPercent(streakDays)

    fun multiplier(streakDays: Int): Float = StreakBonus.multiplier(streakDays)

    fun multiplierLabel(streakDays: Int): String = StreakBonus.multiplierLabel(streakDays)

    fun applyMultiplier(baseXp: Int, streakDays: Int): Int =
        StreakBonus.applyMultiplier(baseXp, streakDays)

    fun isAtCap(streakDays: Int): Boolean = StreakBonus.isAtCap(streakDays)

    fun daysUntilCap(streakDays: Int): Int = StreakBonus.daysUntilCap(streakDays)
}