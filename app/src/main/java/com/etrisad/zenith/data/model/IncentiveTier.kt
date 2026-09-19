package com.etrisad.zenith.data.model

enum class IncentiveTier(
    val minProgress: Float,
    val bonusUses: Int,
    val isUnlocked: Boolean
) {
    LOCKED(0.0f, 0, false),
    LIMITED(0.25f, 1, false),
    MODERATE(0.50f, 3, false),
    // Finite step (not unlimited): the card still shows "Almost Unlocked" with a
    // lock icon here, so granting unlimited bonus at this tier contradicts it.
    ALMOST(0.75f, 5, false),
    UNLOCKED(1.0f, Int.MAX_VALUE, true);

    companion object {
        fun fromProgress(progress: Float): IncentiveTier {
            return entries.last { progress >= it.minProgress }
        }
    }
}
