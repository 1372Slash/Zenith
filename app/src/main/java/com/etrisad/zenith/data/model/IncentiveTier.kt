package com.etrisad.zenith.data.model

enum class IncentiveTier(
    val minProgress: Float,
    val bonusUses: Int,
    val isUnlocked: Boolean
) {
    LOCKED(0.0f, 0, false),
    LIMITED(0.25f, 1, false),
    MODERATE(0.50f, 3, false),
    ALMOST(0.75f, Int.MAX_VALUE, false),
    UNLOCKED(1.0f, Int.MAX_VALUE, true);

    companion object {
        fun fromProgress(progress: Float): IncentiveTier {
            return entries.last { progress >= it.minProgress }
        }
    }
}
