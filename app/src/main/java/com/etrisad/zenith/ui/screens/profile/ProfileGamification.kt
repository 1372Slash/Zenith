package com.etrisad.zenith.ui.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector

const val PROFILE_XP_PER_LEVEL = 500
enum class ProfileTier(val value: Int, val icon: ImageVector, val title: String) {
    I(1, Icons.Outlined.Star, "Star"),
    V(5, Icons.Outlined.DarkMode, "Moon"),
    X(10, Icons.Outlined.LightMode, "Sun"),
    L(50, Icons.Outlined.RocketLaunch, "Comet"),
    C(100, Icons.Outlined.WorkspacePremium, "Crown"),
    D(500, Icons.Outlined.Diamond, "Diamond"),
    M(1000, Icons.Outlined.EmojiEvents, "Trophy");

    companion object {
        fun highestAtOrBelow(value: Long): ProfileTier? =
            entries.filter { value >= it.value }.maxByOrNull { it.value }
    }
}

/** Formats a value in custom roman numerals, e.g. 7 -> "V II", 12 -> "X II". */
fun toCustomRoman(value: Int): String {
    if (value <= 0) return "-"
    var rest = value
    val parts = mutableListOf<String>()
    for (tier in ProfileTier.entries.sortedByDescending { it.value }) {
        while (rest >= tier.value) {
            parts.add(tier.name)
            rest -= tier.value
        }
    }
    return parts.joinToString(" ")
}

/**
 * Tier level as a row of real icons, roman style: level 3 -> three stars,
 * level 7 -> moon + two stars. Additive on purpose so every level is
 * readable at a glance.
 */
fun tierIconsForLevel(level: Int): List<ImageVector> {
    if (level <= 0) return emptyList()
    var rest = level
    return buildList {
        for (tier in ProfileTier.entries.sortedByDescending { it.value }) {
            while (rest >= tier.value) {
                add(tier.icon)
                rest -= tier.value
            }
        }
    }
}

/**
 * Shield XP: staying under the limit earns XP, the less usage vs the limit
 * the more XP. Over the limit earns nothing.
 */
fun calcShieldXp(limitMillis: Long, usageMillis: Long): Int {
    if (limitMillis <= 0L) return 0
    if (usageMillis > limitMillis) return 0
    val savingsRatio = (limitMillis - usageMillis).toFloat() / limitMillis
    return 10 + (savingsRatio * 90).toInt().coerceIn(0, 90)
}

/**
 * Goal XP: usage at or above the target earns full XP plus a capped overage
 * bonus. Under the target earns proportional XP.
 */
fun calcGoalXp(targetMillis: Long, usageMillis: Long): Int {
    if (targetMillis <= 0L) return 0
    val ratio = usageMillis.toFloat() / targetMillis
    return if (ratio >= 1f) {
        100 + ((ratio - 1f) * 100).toInt().coerceIn(0, 50)
    } else {
        (ratio * 100).toInt().coerceIn(0, 99)
    }
}

fun levelForXp(xp: Long): Int = (xp / PROFILE_XP_PER_LEVEL).toInt() + 1

fun levelProgressForXp(xp: Long): Float =
    ((xp % PROFILE_XP_PER_LEVEL).toFloat() / PROFILE_XP_PER_LEVEL).coerceIn(0f, 1f)

enum class AchievementCategory { EXPLORER, ACCUMULATION }

data class TierThreshold(val tier: ProfileTier, val required: Long, val requireLabel: String)

data class AchievementDef(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val category: AchievementCategory,
    val thresholds: List<TierThreshold>,
    /** Unique name per tier level, index 0 = tier 1. Falls back to [title]. */
    val tierNames: List<String> = emptyList()
)

data class AchievementState(
    val def: AchievementDef,
    val current: Long,
    val earnedTier: ProfileTier?,
    /** How many tier thresholds are met, 0..thresholds.size. Displayed roman style. */
    val earnedLevel: Int,
    val totalLevels: Int,
    val next: TierThreshold?,
    val progressFraction: Float,
    /** Tier value -> yyyy-MM-dd unlock date, recorded when first observed met. */
    val unlockedDates: Map<Int, String> = emptyMap()
)

data class ProfileAchievementStats(
    val hasPomodoro: Boolean = false,
    val hasLockdown: Boolean = false,
    val hasBedtime: Boolean = false,
    val hasAlarm: Boolean = false,
    val hasPausePoint: Boolean = false,
    val hasEyeCare: Boolean = false,
    val hasGracePeriod: Boolean = false,
    val hasShield: Boolean = false,
    val hasGoal: Boolean = false,
    val hasSchedule: Boolean = false,
    val hasQr: Boolean = false,
    val hasPreset: Boolean = false,
    val hasCustomTheme: Boolean = false,
    val hasBackup: Boolean = false,
    val hasMindful: Boolean = false,
    val hasGlimpse: Boolean = false,
    val globalBestStreak: Int = 0,
    val totalSavedMillis: Long = 0L,
    val maxAppBestStreak: Int = 0,
    val pomodoroSessions: Int = 0,
    val pomodoroFocusMillis: Long = 0L,
    val lifetimeMillis: Long = 0L,
    val bedtimeBestStreak: Int = 0,
    val shieldCount: Int = 0
)

private fun hoursLabel(millis: Long): String {
    val h = millis / 3_600_000L
    return if (h < 1) "${millis / 60_000L}m" else "${h}h"
}

/** Compact duration for big lifetime numbers, e.g. 101572988ms -> "28.21h". */
fun formatCompactDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val hours = millis / 3_600_000.0
    if (hours < 1) return "${millis / 60_000L}m"
    val text = "%.2f".format(hours).trimEnd('0').trimEnd('.')
    return "${text}h"
}

private val MILLIS_ACHIEVEMENTS = setOf("focus_hours", "time_saver", "loyal_tracker")

fun formatProgressNumber(defId: String, value: Long): String =
    if (defId in MILLIS_ACHIEVEMENTS) formatCompactDuration(value) else value.toString()

/**
 * Unique name per tier level, e.g. "Streak in a Cup", "Streak Keeper".
 * Falls back to the base title when no custom name exists.
 */
fun tierDisplayName(def: AchievementDef, level: Int): String {
    if (level <= 0) return def.title
    return def.tierNames.getOrNull(level - 1) ?: def.title
}

/** Counts owned symbols per tier across achievements. */
fun countTierSymbols(achievements: List<AchievementState>): Map<ProfileTier, Int> {
    val counts = ProfileTier.entries.associateWith { 0 }.toMutableMap()
    achievements.forEach { state ->
        var rest = state.earnedLevel
        for (tier in ProfileTier.entries.sortedByDescending { it.value }) {
            while (rest >= tier.value) {
                counts[tier] = (counts[tier] ?: 0) + 1
                rest -= tier.value
            }
        }
    }
    return counts
}

fun buildAchievementDefs(): List<AchievementDef> = listOf(
    AchievementDef(
        id = "pomo_first", title = "Focus Starter",
        desc = "Complete a Pomodoro focus session",
        icon = Icons.Outlined.Timer, category = AchievementCategory.EXPLORER,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 session"),
            TierThreshold(ProfileTier.V, 10, "10 sessions"),
            TierThreshold(ProfileTier.X, 50, "50 sessions"),
            TierThreshold(ProfileTier.L, 100, "100 sessions"),
            TierThreshold(ProfileTier.C, 250, "250 sessions"),
            TierThreshold(ProfileTier.D, 500, "500 sessions"),
            TierThreshold(ProfileTier.M, 1000, "1000 sessions")
        ),
        tierNames = listOf(
            "First Spark", "Focus Starter", "Rhythm Finder", "Habit Former",
            "Session Machine", "Century Club", "Focus Sage"
        )
    ),
    AchievementDef(
        id = "focus_hours", title = "Deep Focus",
        desc = "Accumulate Pomodoro focus time",
        icon = Icons.Outlined.HourglassEmpty, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 3_600_000L, "1h"),
            TierThreshold(ProfileTier.V, 36_000_000L, "10h"),
            TierThreshold(ProfileTier.X, 90_000_000L, "25h"),
            TierThreshold(ProfileTier.L, 180_000_000L, "50h"),
            TierThreshold(ProfileTier.C, 360_000_000L, "100h"),
            TierThreshold(ProfileTier.D, 900_000_000L, "250h"),
            TierThreshold(ProfileTier.M, 1_800_000_000L, "500h")
        ),
        tierNames = listOf(
            "First Hour", "Deep Focus", "Flow Finder", "Flow Keeper",
            "Marathon Mind", "Centurion", "Enlightened"
        )
    ),
    AchievementDef(
        id = "streak_keeper", title = "Streak Keeper",
        desc = "Grow your best screen time streak",
        icon = Icons.Outlined.LocalFireDepartment, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 5, "5 days"),
            TierThreshold(ProfileTier.V, 15, "15 days"),
            TierThreshold(ProfileTier.X, 30, "30 days"),
            TierThreshold(ProfileTier.L, 50, "50 days"),
            TierThreshold(ProfileTier.C, 100, "100 days"),
            TierThreshold(ProfileTier.D, 200, "200 days"),
            TierThreshold(ProfileTier.M, 365, "365 days")
        ),
        tierNames = listOf(
            "Streak in a Cup", "Streak in a Bag", "Streak Keeper", "Streak Warden",
            "Streak Guardian", "Streak Legend", "Streak Eternal"
        )
    ),
    AchievementDef(
        id = "time_saver", title = "Time Saver",
        desc = "Save time under shield limits",
        icon = Icons.Outlined.Shield, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 3_600_000L, "1h saved"),
            TierThreshold(ProfileTier.V, 18_000_000L, "5h saved"),
            TierThreshold(ProfileTier.X, 72_000_000L, "20h saved"),
            TierThreshold(ProfileTier.L, 180_000_000L, "50h saved"),
            TierThreshold(ProfileTier.C, 360_000_000L, "100h saved"),
            TierThreshold(ProfileTier.D, 900_000_000L, "250h saved"),
            TierThreshold(ProfileTier.M, 1_800_000_000L, "500h saved")
        ),
        tierNames = listOf(
            "Penny Saved", "Time Saver", "Hour Hoarder", "Time Vault",
            "Time Banker", "Time Tycoon", "Time Lord"
        )
    ),
    AchievementDef(
        id = "app_streaker", title = "App Streaker",
        desc = "Best streak on any single app",
        icon = Icons.Outlined.TrackChanges, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 5, "5 days"),
            TierThreshold(ProfileTier.V, 15, "15 days"),
            TierThreshold(ProfileTier.X, 30, "30 days"),
            TierThreshold(ProfileTier.L, 50, "50 days"),
            TierThreshold(ProfileTier.C, 100, "100 days"),
            TierThreshold(ProfileTier.D, 200, "200 days"),
            TierThreshold(ProfileTier.M, 365, "365 days")
        ),
        tierNames = listOf(
            "App Sprout", "App Streaker", "App Regular", "App Devotee",
            "App Champion", "App Legend", "App Immortal"
        )
    ),
    AchievementDef(
        id = "lockdown_explorer", title = "Lockdown Explorer",
        desc = "Try Lockdown mode", icon = Icons.Outlined.Lock,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "bedtime_explorer", title = "Bedtime Explorer",
        desc = "Try Bedtime mode", icon = Icons.Outlined.Bedtime,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "alarm_explorer", title = "Alarm Explorer",
        desc = "Set an alarm", icon = Icons.Outlined.Alarm,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 alarm"))
    ),
    AchievementDef(
        id = "pausepoint_explorer", title = "Pause Point Explorer",
        desc = "Try Pause Point", icon = Icons.Outlined.PauseCircle,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "goal_setter", title = "Goal Setter",
        desc = "Create a goal app", icon = Icons.Outlined.TrackChanges,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 goal"))
    ),
    AchievementDef(
        id = "shield_setter", title = "Shield Bearer",
        desc = "Protect an app with Shield", icon = Icons.Outlined.Shield,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 shield"))
    ),
    AchievementDef(
        id = "eyecare_explorer", title = "Eye Care Explorer",
        desc = "Try Eye Care reminders", icon = Icons.Outlined.LightMode,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "grace_explorer", title = "Grace Explorer",
        desc = "Try Grace Period", icon = Icons.Outlined.HourglassEmpty,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "schedule_keeper", title = "Schedule Keeper",
        desc = "Create a focus schedule", icon = Icons.Outlined.EventRepeat,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 schedule"))
    ),
    AchievementDef(
        id = "qr_collector", title = "QR Collector",
        desc = "Save a Pause Point QR code", icon = Icons.Outlined.QrCode2,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 code"))
    ),
    AchievementDef(
        id = "preset_saver", title = "Preset Saver",
        desc = "Save a Pomodoro preset", icon = Icons.Outlined.Save,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 preset"))
    ),
    AchievementDef(
        id = "theme_stylist", title = "Theme Stylist",
        desc = "Enable expressive colors", icon = Icons.Outlined.Palette,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "backup_guardian", title = "Backup Guardian",
        desc = "Protect data with a backup", icon = Icons.Outlined.Backup,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Backed up"))
    ),
    AchievementDef(
        id = "mindful_explorer", title = "Mindful Explorer",
        desc = "Try Mindful Gateway", icon = Icons.Outlined.SelfImprovement,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "glimpse_user", title = "Glimpse User",
        desc = "Enable Usage Glimpse", icon = Icons.Outlined.Visibility,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "loyal_tracker", title = "Loyal Tracker",
        desc = "Total time tracked by Zenith",
        icon = Icons.Outlined.History, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 36_000_000L, "10h"),
            TierThreshold(ProfileTier.V, 180_000_000L, "50h"),
            TierThreshold(ProfileTier.X, 360_000_000L, "100h"),
            TierThreshold(ProfileTier.L, 900_000_000L, "250h"),
            TierThreshold(ProfileTier.C, 1_800_000_000L, "500h"),
            TierThreshold(ProfileTier.D, 3_600_000_000L, "1000h"),
            TierThreshold(ProfileTier.M, 7_200_000_000L, "2000h")
        ),
        tierNames = listOf(
            "Newcomer", "Loyal Tracker", "Loyal Regular", "Loyal Veteran",
            "Loyal Pillar", "Loyal Legend", "Zenith Soul"
        )
    ),
    AchievementDef(
        id = "night_guardian", title = "Night Guardian",
        desc = "Grow your best bedtime streak",
        icon = Icons.Outlined.Bedtime, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 7, "7 nights"),
            TierThreshold(ProfileTier.V, 14, "14 nights"),
            TierThreshold(ProfileTier.X, 30, "30 nights"),
            TierThreshold(ProfileTier.L, 60, "60 nights"),
            TierThreshold(ProfileTier.C, 100, "100 nights"),
            TierThreshold(ProfileTier.D, 200, "200 nights"),
            TierThreshold(ProfileTier.M, 365, "365 nights")
        ),
        tierNames = listOf(
            "Night Owl", "Night Guardian", "Night Watch", "Night Warden",
            "Night Sentinel", "Dream Keeper", "Sandman"
        )
    ),
    AchievementDef(
        id = "fortress_builder", title = "Fortress Builder",
        desc = "Apps protected by shields and goals",
        icon = Icons.Outlined.Security, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 app"),
            TierThreshold(ProfileTier.V, 3, "3 apps"),
            TierThreshold(ProfileTier.X, 5, "5 apps"),
            TierThreshold(ProfileTier.L, 10, "10 apps"),
            TierThreshold(ProfileTier.C, 15, "15 apps"),
            TierThreshold(ProfileTier.D, 20, "20 apps"),
            TierThreshold(ProfileTier.M, 30, "30 apps")
        ),
        tierNames = listOf(
            "First Brick", "Fortress Builder", "Wall Raiser", "Gatekeeper",
            "Bastion", "Citadel", "Eternal Fortress"
        )
    )
)

fun buildAchievementStates(stats: ProfileAchievementStats): List<AchievementState> {
    val defs = buildAchievementDefs()
    val currentById = mapOf(
        "pomo_first" to stats.pomodoroSessions.toLong(),
        "focus_hours" to stats.pomodoroFocusMillis,
        "streak_keeper" to stats.globalBestStreak.toLong(),
        "time_saver" to stats.totalSavedMillis,
        "app_streaker" to stats.maxAppBestStreak.toLong(),
        "lockdown_explorer" to if (stats.hasLockdown) 1L else 0L,
        "bedtime_explorer" to if (stats.hasBedtime) 1L else 0L,
        "alarm_explorer" to if (stats.hasAlarm) 1L else 0L,
        "pausepoint_explorer" to if (stats.hasPausePoint) 1L else 0L,
        "goal_setter" to if (stats.hasGoal) 1L else 0L,
        "shield_setter" to if (stats.hasShield) 1L else 0L,
        "eyecare_explorer" to if (stats.hasEyeCare) 1L else 0L,
        "grace_explorer" to if (stats.hasGracePeriod) 1L else 0L,
        "schedule_keeper" to if (stats.hasSchedule) 1L else 0L,
        "qr_collector" to if (stats.hasQr) 1L else 0L,
        "preset_saver" to if (stats.hasPreset) 1L else 0L,
        "theme_stylist" to if (stats.hasCustomTheme) 1L else 0L,
        "backup_guardian" to if (stats.hasBackup) 1L else 0L,
        "mindful_explorer" to if (stats.hasMindful) 1L else 0L,
        "glimpse_user" to if (stats.hasGlimpse) 1L else 0L,
        "loyal_tracker" to stats.lifetimeMillis,
        "night_guardian" to stats.bedtimeBestStreak.toLong(),
        "fortress_builder" to stats.shieldCount.toLong()
    )
    return defs.map { def ->
        val current = currentById[def.id] ?: 0L
        val earned = def.thresholds.filter { current >= it.required }.maxByOrNull { it.tier.value }
        val earnedLevel = def.thresholds.count { current >= it.required }
        val next = def.thresholds.filter { current < it.required }.minByOrNull { it.required }
        val progress = when {
            next == null -> 1f
            next.required <= 0L -> 1f
            else -> (current.toFloat() / next.required).coerceIn(0f, 1f)
        }
        AchievementState(
            def = def,
            current = current,
            earnedTier = earned?.tier,
            earnedLevel = earnedLevel,
            totalLevels = def.thresholds.size,
            next = next,
            progressFraction = progress
        )
    }
}

fun achievementProgressLabel(state: AchievementState): String {
    val next = state.next ?: return "Max tier ${state.earnedTier?.name ?: ""}"
    return if (state.def.id in MILLIS_ACHIEVEMENTS) {
        "${formatCompactDuration(state.current)} / ${next.requireLabel}"
    } else {
        "${state.current} / ${next.requireLabel}"
    }
}
