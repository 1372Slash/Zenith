package com.etrisad.zenith.ui.onboarding

import android.os.SystemClock
import com.etrisad.zenith.data.local.database.DbLogBuffer

internal object OnboardingMetrics {
    private const val TAG = "GuidedPath"
    private var stepStartedAt = 0L

    fun chapterStarted(chapterId: String) = write("onboarding_chapter_started", chapterId)

    fun stepViewed(step: GuideStep) {
        stepStartedAt = SystemClock.elapsedRealtime()
        write("onboarding_step_viewed", step.chapterId, step.id)
    }

    fun stepCompleted(step: GuideStep) {
        val duration = if (stepStartedAt == 0L) 0L
        else SystemClock.elapsedRealtime() - stepStartedAt
        write("onboarding_step_completed", step.chapterId, step.id, "durationMs=$duration")
    }

    fun chapterCompleted(chapterId: String) = write("onboarding_chapter_completed", chapterId)

    fun chapterSkipped(chapterId: String) = write("onboarding_chapter_skipped", chapterId)

    fun exited(step: GuideStep?) =
        write("onboarding_guide_exited", step?.chapterId, step?.id)

    fun resumed(step: GuideStep) = write("onboarding_guide_resumed", step.chapterId, step.id)

    fun masteryEarned() = write("onboarding_mastery_earned", null)

    fun targetMissing(anchorId: String) =
        write("onboarding_target_missing", null, null, "anchorId=$anchorId")

    private fun write(event: String, chapterId: String?, stepId: String? = null, extra: String? = null) {
        val detail = buildString {
            append(event)
            if (chapterId != null) append(" chapter=").append(chapterId)
            if (stepId != null) append(" step=").append(stepId)
            if (extra != null) append(' ').append(extra)
        }
        DbLogBuffer.d(TAG, detail)
    }
}
