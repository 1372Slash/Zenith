package com.etrisad.zenith.ui.onboarding

import com.etrisad.zenith.ui.screens.profile.ProfileAchievementStats
import com.etrisad.zenith.ui.screens.profile.buildAchievementDefs
import com.etrisad.zenith.ui.screens.profile.buildAchievementStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingChaptersTest {

    @Test
    fun `path has six ordered chapters with unique ids`() {
        assertEquals(6, guideChapters.size)
        assertEquals((1..6).toList(), guideChapters.map { it.order })
        assertEquals(guideChapters.size, guideChapters.map { it.id }.toSet().size)
    }

    @Test
    fun `every step belongs to its chapter and has a unique id`() {
        val stepIds = mutableListOf<String>()
        guideChapters.forEach { chapter ->
            assertTrue("chapter ${chapter.id} has no steps", chapter.steps.isNotEmpty())
            chapter.steps.forEach { step ->
                assertEquals(chapter.id, step.chapterId)
                assertTrue("blank route on ${step.id}", step.route.isNotBlank())
                stepIds += step.id
            }
        }
        assertEquals(stepIds.size, stepIds.toSet().size)
        assertEquals(28, stepIds.size)
    }

    @Test
    fun `chapter chain is linear and ends at the summit`() {
        guideChapters.forEachIndexed { index, chapter ->
            val expectedNext = guideChapters.getOrNull(index + 1)?.id
            assertEquals(expectedNext, chapter.nextChapterId)
        }
        assertNull(guideChapters.last().nextChapterId)
    }

    @Test
    fun `step pointers parse and reject garbage`() {
        assertEquals("the_shield" to 2, parseGuideStep("the_shield:2"))
        assertEquals("first_light" to 0, parseGuideStep("first_light:0"))
        assertNull(parseGuideStep(""))
        assertNull(parseGuideStep("unknown_chapter:1"))
        assertNull(parseGuideStep("first_light:notanumber"))
        assertNull(parseGuideStep("first_light:-3"))
    }

    @Test
    fun `unavailable steps are skipped and the chapter end is reported`() {
        val chapter = guideChapters.first()
        val blocked = chapter.steps[1].id
        val isAvailable = { step: GuideStep -> step.id != blocked }

        assertEquals(0, chapter.steps.nextAvailableIndex(0, isAvailable))
        assertEquals(2, chapter.steps.nextAvailableIndex(1, isAvailable))
        assertEquals(2, chapter.steps.nextAvailableIndex(2, isAvailable))
        assertNull(chapter.steps.nextAvailableIndex(chapter.steps.size, isAvailable))
    }

    @Test
    fun `achievement ladder is seven tiers of pathfinder`() {
        val def = buildAchievementDefs().first { it.id == "onboarding_path" }
        assertEquals(7, def.thresholds.size)
        assertEquals(7, def.tierNames.size)
        assertEquals("Zenith Master", def.tierNames.last())
    }

    @Test
    fun `chapter count maps to one achievement level per step`() {
        val levels = (0..7).map { value ->
            buildAchievementStates(
                ProfileAchievementStats(
                    onboardingChaptersCompleted = value.coerceAtMost(6),
                    onboardingMastered = value == 7
                )
            ).first { it.def.id == "onboarding_path" }.earnedLevel
        }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), levels)
    }
}
