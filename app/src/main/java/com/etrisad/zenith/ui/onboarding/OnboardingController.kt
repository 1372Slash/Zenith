package com.etrisad.zenith.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.etrisad.zenith.BuildConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.util.isAccessibilityServiceEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OnboardingController(
    private val context: Context,
    private val repository: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val navigateTo: (String) -> Unit,
    private val prefsProvider: () -> UserPreferences?,
    private val onProgressChanged: () -> Unit
) {
    var state: GuideState by mutableStateOf(GuideState.Idle)
        private set

    val isActive: Boolean
        get() = state is GuideState.Active

    val currentChapter: GuideChapter?
        get() = when (val s = state) {
            is GuideState.Active -> guideChapterById(s.chapterId)
            is GuideState.ChapterComplete -> guideChapterById(s.chapterId)
            else -> null
        }

    val currentStep: GuideStep?
        get() = (state as? GuideState.Active)?.let { s ->
            guideChapterById(s.chapterId)?.steps?.getOrNull(s.stepIndex)
        }

    val currentStepIndex: Int
        get() = (state as? GuideState.Active)?.stepIndex ?: 0

    fun isChapterCompleted(chapterId: String?): Boolean =
        chapterId != null && prefsProvider()?.onboardingGuideChapters?.contains(chapterId) == true

    fun completedChapterCount(): Int = prefsProvider()?.onboardingGuideChapters?.size ?: 0

    fun completedChapterIds(): Set<String> = prefsProvider()?.onboardingGuideChapters.orEmpty()

    fun isMasteryAvailable(): Boolean {
        val prefs = prefsProvider() ?: return false
        return !prefs.onboardingGuideMastered &&
            prefs.onboardingGuideChapters.containsAll(guideChapters.map { it.id })
    }

    fun startFirstChapter() {
        startChapter(guideChapters.first().id, 0)
    }

    fun startChapter(chapterId: String, stepIndex: Int = 0) {
        val chapter = guideChapterById(chapterId) ?: return
        val start = stepIndex.coerceIn(0, chapter.steps.lastIndex)
        enter(chapter, chapter.steps.nextAvailableIndex(start, ::isStepAvailable) ?: start)
    }

    fun resume() {
        val prefs = prefsProvider() ?: return
        val (chapterId, index) = parseGuideStep(prefs.onboardingGuideStep)
            ?: (guideChapters.first().id to 0)
        startChapter(chapterId, index)
        currentStep?.let { OnboardingMetrics.resumed(it) }
    }

    fun next() {
        val chapter = currentChapter ?: return
        val index = currentStepIndex
        currentStep?.let { OnboardingMetrics.stepCompleted(it) }
        val nextIndex = chapter.steps.nextAvailableIndex(index + 1, ::isStepAvailable)
        if (nextIndex != null) {
            enter(chapter, nextIndex)
        } else {
            completeChapter(chapter)
        }
    }

    fun back(): Boolean {
        val chapter = currentChapter ?: return false
        val index = currentStepIndex
        if (index > 0) {
            enter(chapter, index - 1)
            return true
        }
        return false
    }

    fun close() {
        val prefs = prefsProvider()
        val step = currentStep
        OnboardingMetrics.exited(step)
        state = GuideState.Dismissed
        scope.launch {
            if (step != null) {
                repository.saveOnboardingGuideStep(step.chapterId, currentStepIndex)
            } else if (prefs?.onboardingGuideStep.isNullOrBlank()) {
                repository.clearOnboardingGuideStep()
            }
        }
    }

    fun skipChapter() {
        val chapter = currentChapter ?: run {
            state = GuideState.Idle
            return
        }
        OnboardingMetrics.chapterSkipped(chapter.id)
        scope.launch {
            repository.skipOnboardingChapter(chapter.id)
            onProgressChanged()
        }
        advanceAfter(chapter)
    }

    fun continueAfterChapter() {
        val chapterId = (state as? GuideState.ChapterComplete)?.chapterId ?: return
        val chapter = guideChapterById(chapterId) ?: return
        if (chapter.nextChapterId != null) {
            advanceAfter(chapter)
            return
        }
        scope.launch {
            val prefs = repository.userPreferencesFlow.first()
            val allDone = prefs.onboardingGuideChapters.containsAll(guideChapters.map { it.id })
            state = if (allDone && !prefs.onboardingGuideMastered) {
                GuideState.MasteryReady
            } else {
                GuideState.Idle
            }
        }
    }

    private fun advanceAfter(chapter: GuideChapter) {
        val nextId = chapter.nextChapterId
        if (nextId == null) {
            if (isMasteryAvailable()) {
                state = GuideState.MasteryReady
            } else {
                state = GuideState.Idle
            }
            return
        }
        startChapter(nextId, 0)
    }

    fun claimMastery() {
        if (!isMasteryAvailable()) {
            state = GuideState.Mastered
            return
        }
        OnboardingMetrics.masteryEarned()
        state = GuideState.Mastered
        scope.launch {
            repository.claimOnboardingMastery(MASTERY_XP, todayKey())
            onProgressChanged()
        }
    }

    fun replayChapter(chapterId: String) = startChapter(chapterId, 0)

    fun replayAll() = startChapter(guideChapters.first().id, 0)

    fun dismissCelebration() {
        state = GuideState.Idle
    }

    private fun enter(chapter: GuideChapter, index: Int) {
        val step = chapter.steps.getOrNull(index) ?: return
        if (index == 0) OnboardingMetrics.chapterStarted(chapter.id)
        OnboardingMetrics.stepViewed(step)
        state = GuideState.Active(chapter.id, index)
        navigateTo(step.route)
        scope.launch { repository.saveOnboardingGuideStep(chapter.id, index) }
    }

    private fun completeChapter(chapter: GuideChapter) {
        val alreadyCompleted = isChapterCompleted(chapter.id)
        OnboardingMetrics.chapterCompleted(chapter.id)
        state = GuideState.ChapterComplete(chapter.id, isReplay = alreadyCompleted)
        scope.launch {
            if (!alreadyCompleted) {
                repository.completeOnboardingChapter(chapter.id, CHAPTER_XP, todayKey())
                val nextId = chapter.nextChapterId
                if (nextId != null) {
                    repository.saveOnboardingGuideStep(nextId, 0)
                } else {
                    repository.clearOnboardingGuideStep()
                }
            } else {
                repository.clearOnboardingGuideStep()
            }
            onProgressChanged()
        }
    }

    fun isStepAvailable(step: GuideStep): Boolean = when (step.requirement) {
        StepRequirement.Always -> true
        StepRequirement.UpdatesVisible -> BuildConfig.SHOW_UPDATES
        StepRequirement.DeveloperMode -> prefsProvider()?.developerModeEnabled == true
        StepRequirement.AccessibilityEnabled -> isAccessibilityServiceEnabled(context)
        StepRequirement.NfcHardware ->
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    suspend fun firstPrefs(): UserPreferences = repository.userPreferencesFlow.first()
}
