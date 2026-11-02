package com.etrisad.zenith.ui.onboarding

import com.etrisad.zenith.R
import com.etrisad.zenith.ui.navigation.Screen
import com.etrisad.zenith.ui.screens.profile.ProfileTier

private const val FEATURES_ROUTE = "settings_category/Features"
private const val APPEARANCE_ROUTE = "settings_category/Appearance"
private const val DATA_ROUTE = "settings_category/Data Management"

val guideChapters: List<GuideChapter> = listOf(
    GuideChapter(
        id = "first_light",
        order = 1,
        titleRes = R.string.onboarding_chapter_first_light,
        subtitleRes = R.string.onboarding_chapter_first_light_sub,
        badgeNameRes = R.string.onboarding_chapter_first_light_badge,
        tier = ProfileTier.I,
        steps = listOf(
            GuideStep(
                id = "first_light.dashboard",
                chapterId = "first_light",
                titleRes = R.string.onboarding_step_dashboard_title,
                bodyRes = R.string.onboarding_step_dashboard_body,
                route = Screen.Home.route,
                targetId = "home.dashboard.target",
                scrollKey = "usage_dashboard"
            ),
            GuideStep(
                id = "first_light.trends",
                chapterId = "first_light",
                titleRes = R.string.onboarding_step_trends_title,
                bodyRes = R.string.onboarding_step_trends_body,
                route = Screen.Home.route,
                targetId = "home.trends",
                scrollKey = "usage_trends"
            ),
            GuideStep(
                id = "first_light.focus_list",
                chapterId = "first_light",
                titleRes = R.string.onboarding_step_focus_list_title,
                bodyRes = R.string.onboarding_step_focus_list_body,
                route = Screen.Home.route,
                targetId = "home.focus_list",
                scrollKey = "focus_list"
            ),
            GuideStep(
                id = "first_light.quick_actions",
                chapterId = "first_light",
                titleRes = R.string.onboarding_step_quick_actions_title,
                bodyRes = R.string.onboarding_step_quick_actions_body,
                route = Screen.Home.route,
                targetId = "home.quick_actions",
                scrollKey = "quick_actions"
            )
        ),
        closingMessageRes = R.string.onboarding_chapter_first_light_close,
        closingAlsoRes = R.string.onboarding_chapter_first_light_also,
        nextChapterId = "the_shield"
    ),
    GuideChapter(
        id = "the_shield",
        order = 2,
        titleRes = R.string.onboarding_chapter_shield,
        subtitleRes = R.string.onboarding_chapter_shield_sub,
        badgeNameRes = R.string.onboarding_chapter_shield_badge,
        tier = ProfileTier.V,
        steps = listOf(
            GuideStep(
                id = "the_shield.tabs",
                chapterId = "the_shield",
                titleRes = R.string.onboarding_step_focus_tabs_title,
                bodyRes = R.string.onboarding_step_focus_tabs_body,
                route = Screen.Focus.route,
                targetId = "focus.tabs"
            ),
            GuideStep(
                id = "the_shield.fab",
                chapterId = "the_shield",
                titleRes = R.string.onboarding_step_fab_title,
                bodyRes = R.string.onboarding_step_fab_body,
                route = Screen.Focus.route,
                targetId = "focus.fab",
                interaction = StepInteraction.SHOW_ME,
                action = GuideAction.ExpandFocusFab
            ),
            GuideStep(
                id = "the_shield.add_shield",
                chapterId = "the_shield",
                titleRes = R.string.onboarding_step_add_shield_title,
                bodyRes = R.string.onboarding_step_add_shield_body,
                route = Screen.Focus.route,
                targetId = "focus.fab.add_shield",
                action = GuideAction.ExpandFocusFab
            ),
            GuideStep(
                id = "the_shield.add_goal",
                chapterId = "the_shield",
                titleRes = R.string.onboarding_step_add_goal_title,
                bodyRes = R.string.onboarding_step_add_goal_body,
                route = Screen.Focus.route,
                targetId = "focus.fab.add_goal",
                action = GuideAction.ExpandFocusFab
            ),
            GuideStep(
                id = "the_shield.add_schedule",
                chapterId = "the_shield",
                titleRes = R.string.onboarding_step_add_schedule_title,
                bodyRes = R.string.onboarding_step_add_schedule_body,
                route = Screen.Focus.route,
                targetId = "focus.fab.add_schedule",
                action = GuideAction.ExpandFocusFab
            )
        ),
        closingMessageRes = R.string.onboarding_chapter_shield_close,
        closingAlsoRes = R.string.onboarding_chapter_shield_also,
        nextChapterId = "the_pause"
    ),
    GuideChapter(
        id = "the_pause",
        order = 3,
        titleRes = R.string.onboarding_chapter_pause,
        subtitleRes = R.string.onboarding_chapter_pause_sub,
        badgeNameRes = R.string.onboarding_chapter_pause_badge,
        tier = ProfileTier.X,
        steps = listOf(
            GuideStep(
                id = "the_pause.mindful_gateway",
                chapterId = "the_pause",
                titleRes = R.string.onboarding_step_mindful_title,
                bodyRes = R.string.onboarding_step_mindful_body,
                route = FEATURES_ROUTE,
                targetId = "features.mindful_gateway"
            ),
            GuideStep(
                id = "the_pause.pause_point",
                chapterId = "the_pause",
                titleRes = R.string.onboarding_step_pause_point_title,
                bodyRes = R.string.onboarding_step_pause_point_body,
                route = FEATURES_ROUTE,
                targetId = "features.pause_point"
            ),
            GuideStep(
                id = "the_pause.reels_shield",
                chapterId = "the_pause",
                titleRes = R.string.onboarding_step_reels_title,
                bodyRes = R.string.onboarding_step_reels_body,
                route = FEATURES_ROUTE,
                targetId = "features.reels_shield"
            ),
            GuideStep(
                id = "the_pause.early_kick",
                chapterId = "the_pause",
                titleRes = R.string.onboarding_step_early_kick_title,
                bodyRes = R.string.onboarding_step_early_kick_body,
                route = FEATURES_ROUTE,
                targetId = "features.early_kick"
            ),
            GuideStep(
                id = "the_pause.eye_care",
                chapterId = "the_pause",
                titleRes = R.string.onboarding_step_eye_care_title,
                bodyRes = R.string.onboarding_step_eye_care_body,
                route = FEATURES_ROUTE,
                targetId = "features.eye_care"
            )
        ),
        closingMessageRes = R.string.onboarding_chapter_pause_close,
        closingAlsoRes = R.string.onboarding_chapter_pause_also,
        nextChapterId = "the_clock"
    ),
    GuideChapter(
        id = "the_clock",
        order = 4,
        titleRes = R.string.onboarding_chapter_clock,
        subtitleRes = R.string.onboarding_chapter_clock_sub,
        badgeNameRes = R.string.onboarding_chapter_clock_badge,
        tier = ProfileTier.L,
        steps = listOf(
            GuideStep(
                id = "the_clock.bedtime",
                chapterId = "the_clock",
                titleRes = R.string.onboarding_step_bedtime_title,
                bodyRes = R.string.onboarding_step_bedtime_body,
                route = Screen.Bedtime.route,
                targetId = "bedtime.hero"
            ),
            GuideStep(
                id = "the_clock.pomodoro",
                chapterId = "the_clock",
                titleRes = R.string.onboarding_step_pomodoro_title,
                bodyRes = R.string.onboarding_step_pomodoro_body,
                route = FEATURES_ROUTE,
                targetId = "features.pomodoro"
            ),
            GuideStep(
                id = "the_clock.grace",
                chapterId = "the_clock",
                titleRes = R.string.onboarding_step_grace_title,
                bodyRes = R.string.onboarding_step_grace_body,
                route = FEATURES_ROUTE,
                targetId = "features.grace_period"
            ),
            GuideStep(
                id = "the_clock.lockdown",
                chapterId = "the_clock",
                titleRes = R.string.onboarding_step_lockdown_title,
                bodyRes = R.string.onboarding_step_lockdown_body,
                route = FEATURES_ROUTE,
                targetId = "features.lockdown"
            ),
            GuideStep(
                id = "the_clock.alarm",
                chapterId = "the_clock",
                titleRes = R.string.onboarding_step_alarm_title,
                bodyRes = R.string.onboarding_step_alarm_body,
                route = Screen.Alarm.route,
                targetId = "alarm.master_switch"
            )
        ),
        closingMessageRes = R.string.onboarding_chapter_clock_close,
        closingAlsoRes = R.string.onboarding_chapter_clock_also,
        nextChapterId = "the_mirror"
    ),
    GuideChapter(
        id = "the_mirror",
        order = 5,
        titleRes = R.string.onboarding_chapter_mirror,
        subtitleRes = R.string.onboarding_chapter_mirror_sub,
        badgeNameRes = R.string.onboarding_chapter_mirror_badge,
        tier = ProfileTier.C,
        steps = listOf(
            GuideStep(
                id = "the_mirror.donut",
                chapterId = "the_mirror",
                titleRes = R.string.onboarding_step_donut_title,
                bodyRes = R.string.onboarding_step_donut_body,
                route = Screen.UsageStats.route,
                targetId = "usage_stats.donut"
            ),
            GuideStep(
                id = "the_mirror.heatmap",
                chapterId = "the_mirror",
                titleRes = R.string.onboarding_step_heatmap_title,
                bodyRes = R.string.onboarding_step_heatmap_body,
                route = Screen.UsageStats.route,
                targetId = "usage_stats.heatmap"
            ),
            GuideStep(
                id = "the_mirror.top_apps",
                chapterId = "the_mirror",
                titleRes = R.string.onboarding_step_top_apps_title,
                bodyRes = R.string.onboarding_step_top_apps_body,
                route = Screen.Home.route,
                targetId = "home.top_apps",
                scrollKey = "top_apps"
            ),
            GuideStep(
                id = "the_mirror.websites",
                chapterId = "the_mirror",
                titleRes = R.string.onboarding_step_websites_title,
                bodyRes = R.string.onboarding_step_websites_body,
                route = FEATURES_ROUTE,
                targetId = "features.website_tracking"
            )
        ),
        closingMessageRes = R.string.onboarding_chapter_mirror_close,
        closingAlsoRes = R.string.onboarding_chapter_mirror_also,
        nextChapterId = "the_summit"
    ),
    GuideChapter(
        id = "the_summit",
        order = 6,
        titleRes = R.string.onboarding_chapter_summit,
        subtitleRes = R.string.onboarding_chapter_summit_sub,
        badgeNameRes = R.string.onboarding_chapter_summit_badge,
        tier = ProfileTier.D,
        steps = listOf(
            GuideStep(
                id = "the_summit.identity",
                chapterId = "the_summit",
                titleRes = R.string.onboarding_step_identity_title,
                bodyRes = R.string.onboarding_step_identity_body,
                route = Screen.Profile.route,
                targetId = "profile.identity"
            ),
            GuideStep(
                id = "the_summit.xp",
                chapterId = "the_summit",
                titleRes = R.string.onboarding_step_xp_title,
                bodyRes = R.string.onboarding_step_xp_body,
                route = Screen.Profile.route,
                targetId = "profile.xp"
            ),
            GuideStep(
                id = "the_summit.achievements",
                chapterId = "the_summit",
                titleRes = R.string.onboarding_step_achievements_title,
                bodyRes = R.string.onboarding_step_achievements_body,
                route = Screen.Profile.route,
                targetId = "profile.achievements"
            ),
            GuideStep(
                id = "the_summit.appearance",
                chapterId = "the_summit",
                titleRes = R.string.onboarding_step_appearance_title,
                bodyRes = R.string.onboarding_step_appearance_body,
                route = APPEARANCE_ROUTE,
                targetId = "settings.appearance"
            ),
            GuideStep(
                id = "the_summit.data",
                chapterId = "the_summit",
                titleRes = R.string.onboarding_step_data_title,
                bodyRes = R.string.onboarding_step_data_body,
                route = DATA_ROUTE,
                targetId = "settings.data"
            )
        ),
        closingMessageRes = R.string.onboarding_chapter_summit_close,
        closingAlsoRes = R.string.onboarding_chapter_summit_also,
        nextChapterId = null
    )
)

fun guideChapterById(id: String?): GuideChapter? = guideChapters.find { it.id == id }

fun guideNextChapterId(id: String?): String? = guideChapterById(id)?.nextChapterId

fun parseGuideStep(pointer: String): Pair<String, Int>? {
    if (pointer.isBlank()) return null
    val chapter = pointer.substringBeforeLast(':', "")
    val index = pointer.substringAfterLast(':', "").toIntOrNull() ?: return null
    if (guideChapterById(chapter) == null) return null
    if (index < 0) return null
    return chapter to index
}
