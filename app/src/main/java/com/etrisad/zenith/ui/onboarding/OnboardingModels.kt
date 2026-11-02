package com.etrisad.zenith.ui.onboarding

import androidx.annotation.StringRes
import com.etrisad.zenith.ui.screens.profile.ProfileTier

const val CURRENT_GUIDE_VERSION = 1

const val GUIDED_PATH_ENABLED = true

data class GuideChapter(
    val id: String,
    val order: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val badgeNameRes: Int,
    val tier: ProfileTier,
    val steps: List<GuideStep>,
    @StringRes val closingMessageRes: Int,
    @StringRes val closingAlsoRes: Int,
    val nextChapterId: String?,
)

data class GuideStep(
    val id: String,
    val chapterId: String,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val route: String,
    val targetId: String?,
    val scrollKey: String? = null,
    val interaction: StepInteraction = StepInteraction.BLOCKED,
    val action: GuideAction = GuideAction.None,
    val requirement: StepRequirement = StepRequirement.Always
)

enum class StepInteraction {
    BLOCKED,

    TAP_THROUGH,

    SHOW_ME
}

sealed interface GuideAction {
    data object None : GuideAction
    data object ExpandFocusFab : GuideAction
    data object CollapseFocusFab : GuideAction
}

enum class StepRequirement {
    Always,
    AccessibilityEnabled,
    UpdatesVisible,
    DeveloperMode,
    NfcHardware
}

sealed interface GuideState {
    data object Idle : GuideState
    data class Active(val chapterId: String, val stepIndex: Int) : GuideState
    data class ChapterComplete(
        val chapterId: String,
        val isReplay: Boolean = false
    ) : GuideState
    data object MasteryReady : GuideState
    data object Mastered : GuideState
    data object Dismissed : GuideState
}

data class ChapterReward(val chapterId: String, val xp: Int)

const val CHAPTER_XP = 25
const val MASTERY_XP = 100

fun GuideChapter.reward(): ChapterReward = ChapterReward(id, CHAPTER_XP)

internal fun List<GuideStep>.nextAvailableIndex(
    from: Int,
    isAvailable: (GuideStep) -> Boolean
): Int? {
    for (index in from.coerceAtLeast(0)..lastIndex) {
        if (isAvailable(this[index])) return index
    }
    return null
}
