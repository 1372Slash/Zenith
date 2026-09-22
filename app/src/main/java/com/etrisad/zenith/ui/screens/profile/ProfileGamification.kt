package com.etrisad.zenith.ui.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BedtimeOff
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.DataArray
import kotlin.math.pow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hive
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.HourglassFull
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector

/** Base XP cost of the first level-up (Level 1 -> 2), kept flat-compatible. */
const val PROFILE_XP_BASE = 500
/**
 * Gentle exponential curve exponent. Each level costs more than the last,
 * but softly: 1.35 keeps early levels within days and level 30 within
 * roughly a year of steady use.
 */
const val PROFILE_XP_CURVE = 1.35
enum class ProfileTier(val value: Int, val icon: ImageVector, val title: String) {
    I(1, Icons.Outlined.StarOutline, "Spark"),
    V(5, Icons.Outlined.Hive, "Nest"),
    X(10, Icons.Outlined.Token, "Mint"),
    L(50, Icons.Outlined.Cyclone, "Vortex"),
    C(100, Icons.Outlined.FilterVintage, "Bloom"),
    D(500, Icons.Outlined.MilitaryTech, "Valor"),
    M(1000, Icons.Outlined.EmojiEvents, "Legend");

    companion object {
        fun highestAtOrBelow(value: Long): ProfileTier? =
            entries.filter { value >= it.value }.maxByOrNull { it.value }
    }
}
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

/**
 * Cumulative XP required to REACH [level]. Level 1 starts at 0, level 2
 * costs PROFILE_XP_BASE, higher levels grow gently exponentially.
 */
fun xpForLevel(level: Int): Long {
    if (level <= 1) return 0L
    return (PROFILE_XP_BASE * (level - 1).toDouble().pow(PROFILE_XP_CURVE)).toLong()
}

fun levelForXp(xp: Long): Int {
    var level = 1
    while (xp >= xpForLevel(level + 1)) level++
    return level
}

fun levelProgressForXp(xp: Long): Float {
    val level = levelForXp(xp)
    val current = xpForLevel(level)
    val next = xpForLevel(level + 1)
    if (next <= current) return 1f
    return ((xp - current).toFloat() / (next - current)).coerceIn(0f, 1f)
}

/** XP still needed from [xpTotal] to reach the next level. */
fun xpToNextLevel(xpTotal: Long): Long =
    (xpForLevel(levelForXp(xpTotal) + 1) - xpTotal).coerceAtLeast(0L)

/**
 * Level milestone titles. Owned automatically once the level is reached,
 * equipped manually — shown under the profile name and on the share card.
 */
data class LevelTitle(val id: String, val name: String, val requiredLevel: Int)

/**
 * Level milestone avatar borders. Owned automatically once the level is
 * reached, equipped manually — drawn as a ring around the profile avatar.
 */
data class AvatarBorder(
    val id: String,
    val name: String,
    val requiredLevel: Int,
    val colors: List<Color>
)

val LEVEL_TITLES = listOf(
    LevelTitle("initiate", "Initiate", 2),
    LevelTitle("keeper", "Keeper", 5),
    LevelTitle("warden", "Warden", 10),
    LevelTitle("sentinel", "Sentinel", 15),
    LevelTitle("paragon", "Paragon", 20),
    LevelTitle("luminary", "Luminary", 25),
    LevelTitle("zenith", "Zenith", 30),
    LevelTitle("celestial", "Celestial", 35),
    LevelTitle("eternal", "Eternal", 40),
    LevelTitle("mythic", "Mythic", 45),
    LevelTitle("transcendent", "Transcendent", 50),
    LevelTitle("infinite", "Infinite", 55)
)

/**
 * Avatar borders, escalating in color count with level: 5 solid rings,
 * then 5 rings each blending 2, 3, 4, and finally 5 colors as a gradient.
 */
val AVATAR_BORDERS = listOf(
    // 1 color
    AvatarBorder("ember", "Ember", 3, listOf(Color(0xFFFF7043))),
    AvatarBorder("tide", "Tide", 5, listOf(Color(0xFF29B6F6))),
    AvatarBorder("leaf", "Leaf", 7, listOf(Color(0xFF66BB6A))),
    AvatarBorder("violet", "Violet", 9, listOf(Color(0xFF7E57C2))),
    AvatarBorder("rose", "Rose", 11, listOf(Color(0xFFEC407A))),
    // 2 colors
    AvatarBorder("moss", "Moss", 14, listOf(Color(0xFF9CCC65), Color(0xFF2E7D32))),
    AvatarBorder("gold", "Gold", 17, listOf(Color(0xFFFFE082), Color(0xFFFF8F00))),
    AvatarBorder("frost", "Frost", 20, listOf(Color(0xFF80DEEA), Color(0xFF00838F))),
    AvatarBorder("magma", "Magma", 23, listOf(Color(0xFFFF8A65), Color(0xFFBF360C))),
    AvatarBorder("lagoon", "Lagoon", 26, listOf(Color(0xFF4DD0E1), Color(0xFF0D47A1))),
    // 3 colors
    AvatarBorder(
        "dusk", "Dusk", 29,
        listOf(Color(0xFFCE93D8), Color(0xFF7B1FA2), Color(0xFF311B92))
    ),
    AvatarBorder(
        "sunset", "Sunset", 32,
        listOf(Color(0xFFFFD54F), Color(0xFFFF7043), Color(0xFFAD1457))
    ),
    AvatarBorder(
        "ocean", "Ocean", 35,
        listOf(Color(0xFF80DEEA), Color(0xFF1E88E5), Color(0xFF0D47A1))
    ),
    AvatarBorder(
        "forest", "Forest", 38,
        listOf(Color(0xFFAED581), Color(0xFF43A047), Color(0xFF1B5E20))
    ),
    AvatarBorder(
        "candy", "Candy", 41,
        listOf(Color(0xFFF48FB1), Color(0xFFEC407A), Color(0xFF880E4F))
    ),
    // 4 colors
    AvatarBorder(
        "treasure", "Treasure", 44,
        listOf(
            Color(0xFFFFF176), Color(0xFFFFC107),
            Color(0xFFFF8F00), Color(0xFFE65100)
        )
    ),
    AvatarBorder(
        "aurora", "Aurora", 47,
        listOf(
            Color(0xFFA7FFEB), Color(0xFF4DD0E1),
            Color(0xFF7B1FA2), Color(0xFF311B92)
        )
    ),
    AvatarBorder(
        "inferno", "Inferno", 50,
        listOf(
            Color(0xFFFFD180), Color(0xFFFF7043),
            Color(0xFFD84315), Color(0xFF3E2723)
        )
    ),
    AvatarBorder(
        "glacier", "Glacier", 53,
        listOf(
            Color(0xFFE1F5FE), Color(0xFF80DEEA),
            Color(0xFF00838F), Color(0xFF004D40)
        )
    ),
    AvatarBorder(
        "spring", "Spring", 56,
        listOf(
            Color(0xFFF0F4C3), Color(0xFFAED581),
            Color(0xFF689F38), Color(0xFF33691E)
        )
    ),
    // 5 colors
    AvatarBorder(
        "prism", "Prism", 59,
        listOf(
            Color(0xFFFF8A80), Color(0xFFFFD180), Color(0xFFA7FFEB),
            Color(0xFF82B1FF), Color(0xFFEA80FC)
        )
    ),
    AvatarBorder(
        "spectrum", "Spectrum", 62,
        listOf(
            Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE),
            Color(0xFF40C4FF), Color(0xFFE040FB)
        )
    ),
    AvatarBorder(
        "nebula", "Nebula", 65,
        listOf(
            Color(0xFFEA80FC), Color(0xFF7B1FA2), Color(0xFF29B6F6),
            Color(0xFFA7FFEB), Color(0xFFFFF176)
        )
    ),
    AvatarBorder(
        "carnival", "Carnival", 68,
        listOf(
            Color(0xFFFF8A80), Color(0xFFFFAB40), Color(0xFFB9F6CA),
            Color(0xFF84FFFF), Color(0xFFB388FF)
        )
    ),
    AvatarBorder(
        "eternity", "Eternity", 71,
        listOf(
            Color(0xFFD1C4E9), Color(0xFFB39DDB), Color(0xFF90CAF9),
            Color(0xFF80DEEA), Color(0xFFA5D6A7)
        )
    )
)

/** Next locked reward (title or border) above [level], or null when maxed. */
fun nextLevelReward(level: Int): Pair<Int, String>? {
    val upcoming = (LEVEL_TITLES.map { it.requiredLevel to it.name } +
        AVATAR_BORDERS.map { it.requiredLevel to it.name })
        .filter { it.first > level }
    return upcoming.minByOrNull { it.first }
}

fun borderBrushFor(border: AvatarBorder): Brush =
    if (border.colors.size == 1) SolidColor(border.colors.first())
    else Brush.sweepGradient(border.colors + border.colors.first())

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
    val tierNames: List<String> = emptyList(),
    /**
     * Explicit opt-in for infinite tiers. When false (default), [isUnlimited]
     * still extends ACCUMULATION ladders, which are counters by nature.
     */
    val unlimited: Boolean = false,
    /** Multiplier applied per generated tier beyond the handcrafted list. */
    val curveGrowth: Double = 2.0
)

data class AchievementState(
    val def: AchievementDef,
    val current: Long,
    val earnedTier: ProfileTier?,
    /** How many tier thresholds are met (unbounded for unlimited ladders). */
    val earnedLevel: Int,
    val next: TierThreshold?,
    val progressFraction: Float,
    /** Tier position (1-based) -> yyyy-MM-dd unlock date. See [historyKeyFor]. */
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
    val shieldCount: Int = 0,
    val hasOverlayHud: Boolean = false,
    val hasDelayShield: Boolean = false,
    val hasStrictShield: Boolean = false,
    val hasCallerShield: Boolean = false,
    val hasAutoQuitShield: Boolean = false,
    val hasTarget: Boolean = false,
    val hasWebsiteTracking: Boolean = false,
    val isDeveloper: Boolean = false,
    val isCustomPerf: Boolean = false,
    val hasRepair: Boolean = false,
    val hasExcluded: Boolean = false,
    val hasWindDown: Boolean = false,
    val hasSharedProfile: Boolean = false,
    val globalCurrentStreak: Int = 0,
    val emergencyTotal: Int = 0,
    val scheduleCount: Int = 0,
    val alarmCount: Int = 0,
    val goalCount: Int = 0,
    val qrCount: Int = 0,
    val taskTypeCount: Int = 0,
    val userXpTotal: Long = 0L,
    val hasCustomOverlay: Boolean = false,
    val hasFloatingBar: Boolean = false,
    val hasUsagePill: Boolean = false,
    val hasIncentiveLock: Boolean = false,
    val hasEarlyKick: Boolean = false,
    val hasBatteryReset: Boolean = false,
    val hasCustomDayStart: Boolean = false,
    val hasUnusedHours: Boolean = false,
    val hasRecovery: Boolean = false,
    val hasCleaned: Boolean = false,
    val hasBedtimeWhitelist: Boolean = false,
    val hasBedtimeDnd: Boolean = false,
    val hasShorts: Boolean = false,
    val widgetCount: Int = 0,
    val webDomainCount: Int = 0,
    val webTotalMillis: Long = 0L,
    val interceptedCount: Int = 0,
    val trackedDayCount: Int = 0,
    val underBudgetDays: Int = 0,
    val lifetimeAppCount: Int = 0,
    val customVariantCount: Int = 0,
    val infoSheetCount: Int = 0,
    val sevenDayStreak: Int = 0,
    val weekendDays: Int = 0,
    val overdrawDays: Int = 0,
    val distinctDatesCount: Int = 0,
    val nightNights: Int = 0,
    val earlyMornings: Int = 0,
    val profileFields: Int = 0,
    val hasCustomThemeAlt: Boolean = false,
    val fontChanged: Boolean = false,
    val contrastOff: Boolean = false,
    val hasCalendarEvent: Boolean = false,
    val hasOverlayTint: Boolean = false
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
 * Handcrafted [AchievementDef.tierNames] win; beyond them (or when missing),
 * unlimited ladders get a stable generated name; otherwise the base title.
 */
fun tierDisplayName(def: AchievementDef, level: Int): String {
    if (level <= 0) return def.title
    def.tierNames.getOrNull(level - 1)?.let { return it }
    if (!isUnlimited(def)) return def.title
    return generatedTierName(def.id, level)
}

/**
 * Accumulation ladders whose counters are naturally bounded never extend:
 * 3 profile fields, 10 task types, 5 widget slots. Everything else with a
 * counter (or explicit opt-in) grows forever.
 */
private val CAPPED_ACCUMULATION_IDS = setOf("profile_polisher", "taskmaster", "widget_wielder")

fun isUnlimited(def: AchievementDef): Boolean =
    def.unlimited || (def.category == AchievementCategory.ACCUMULATION && def.id !in CAPPED_ACCUMULATION_IDS)

/** Highest roman denomination at or below a tier position (display only). */
fun highestDenomForLevel(level: Int): ProfileTier =
    ProfileTier.entries.sortedByDescending { it.value }.firstOrNull { level >= it.value }
        ?: ProfileTier.I

/** Snap a raw curve value to a human 1-1.5-2-3-5-8-10 step. */
fun niceNumber(v: Double): Long {
    if (!v.isFinite() || v <= 0) return Long.MAX_VALUE
    val cap = Long.MAX_VALUE / 4.0
    if (v >= cap) return Long.MAX_VALUE
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(v)))
    val f = v / magnitude
    val snapped = when {
        f < 1.2 -> 1.0
        f < 1.75 -> 1.5
        f < 2.75 -> 2.0
        f < 3.75 -> 3.0
        f < 6.0 -> 5.0
        f < 8.5 -> 8.0
        else -> 10.0
    }
    return (snapped * magnitude).toLong().coerceAtLeast(1L)
}

/**
 * Required value for a 1-based tier position. The handcrafted list wins;
 * beyond it (unlimited ladders only) the curve grows from the last value.
 * Returns [Long.MAX_VALUE] when no such tier exists (capped ladders).
 */
fun requiredFor(def: AchievementDef, level: Int): Long {
    if (level < 1) return 1L
    val i = level - 1
    if (i < def.thresholds.size) return def.thresholds[i].required
    if (!isUnlimited(def)) return Long.MAX_VALUE
    val growth = if (def.curveGrowth > 1.0) def.curveGrowth else 2.0
    var v = def.thresholds.lastOrNull()?.required?.toDouble() ?: 1.0
    var k = def.thresholds.size
    val cap = Long.MAX_VALUE / 4.0
    while (k < level) {
        v *= growth
        if (v >= cap) return Long.MAX_VALUE
        k++
    }
    return niceNumber(v)
}

/**
 * Full threshold for a 1-based tier position (handcrafted or generated),
 * or null when the ladder ends there.
 */
fun thresholdAtOrNull(def: AchievementDef, level: Int): TierThreshold? {
    if (level < 1) return null
    def.thresholds.getOrNull(level - 1)?.let { return it }
    if (!isUnlimited(def)) return null
    val required = requiredFor(def, level)
    if (required == Long.MAX_VALUE) return null
    return TierThreshold(
        tier = highestDenomForLevel(level),
        required = required,
        requireLabel = generatedRequireLabel(def, required)
    )
}

/** How many tiers are met at [current]; works past any list length. */
fun tierCountFor(def: AchievementDef, current: Long): Int {
    var level = 0
    while (level < 100000) {
        val req = requiredFor(def, level + 1)
        if (req == Long.MAX_VALUE || current < req) break
        level++
    }
    return level
}

/** First unmet tier, or null when the ladder is complete (capped defs only). */
fun nextThresholdFor(def: AchievementDef, current: Long): TierThreshold? =
    thresholdAtOrNull(def, tierCountFor(def, current) + 1)

private fun generatedRequireLabel(def: AchievementDef, required: Long): String {
    val lastLabel = def.thresholds.lastOrNull()?.requireLabel ?: ""
    val suffix = lastLabel.replace(Regex("^\\d[\\d.,]*\\s*"), "")
    return if (def.id in MILLIS_ACHIEVEMENTS) {
        val unit = suffix.replace(Regex("^[a-zA-Z]+"), "").trim()
        val base = formatCompactDuration(required)
        if (unit.isEmpty()) base else "$base $unit"
    } else {
        "$required ${suffix.trim()}".trim()
    }
}

/**
 * Storage key for a tier position. Handcrafted tiers keep their legacy roman
 * value (no migration needed); generated tiers use negative levels, which
 * can never collide with positive legacy values.
 */
fun historyKeyFor(def: AchievementDef, level: Int): Int {
    if (level < 1) return level
    return def.thresholds.getOrNull(level - 1)?.tier?.value ?: -level
}

/**
 * Tier position for a storage key, or -1 when the key no longer maps to any
 * tier (def edited between versions) - callers should skip such rows.
 */
fun levelForHistoryKey(def: AchievementDef, key: Int): Int {
    if (key < 0) return -key
    val idx = def.thresholds.indexOfFirst { it.tier.value == key }
    return if (idx >= 0) idx + 1 else -1
}

private val TIER_ADJECTIVES = listOf(
    "Amber", "Arctic", "Astral", "Bouncy", "Brisk", "Cardinal",
    "Celestial", "Cheerful", "Cinder", "Clockwork", "Cosmic", "Crimson",
    "Dapper", "Drowsy", "Electric", "Ember", "Fern", "Fizz",
    "Foggy", "Gallant", "Gilded", "Ginger", "Glacier", "Gleeful",
    "Granite", "Hazel", "Honeyed", "Indigo", "Iron", "Ivory",
    "Jolly", "Juniper", "Lively", "Lunar", "Maple", "Marble",
    "Mellow", "Midnight", "Misty", "Molasses", "Mossy", "Neon",
    "Nimble", "Onyx", "Opal", "Paprika", "Pebble", "Pepper",
    "Quartz", "Ripple", "Roaring", "Ruby", "Saffron", "Sassy",
    "Scarlet", "Solar", "Stellar", "Thunder", "Topaz", "Velvet",
    "Wandering", "Whisper", "Willow", "Zephyr"
)

private val TIER_NOUNS = listOf(
    "Acorn", "Badger", "Beacon", "Bison", "Bluff", "Bonsai",
    "Boulder", "Breeze", "Brook", "Burrow", "Cabin", "Cactus",
    "Canoe", "Canyon", "Carousel", "Castle", "Cedar", "Comet",
    "Compass", "Cougar", "Coyote", "Crane", "Dune", "Eagle",
    "Elm", "Falcon", "Fox", "Galaxy", "Gecko", "Grove",
    "Harbor", "Hawk", "Hedgehog", "Heron", "Hollow", "Ibex",
    "Island", "Jackal", "Jaguar", "Jasper", "Kestrel", "Lagoon",
    "Lark", "Lighthouse", "Lynx", "Mammoth", "Manatee", "Meadow",
    "Mesa", "Meteor", "Mole", "Monarch", "Moose", "Narwhal",
    "Nugget", "Otter", "Owl", "Oxbow", "Panda", "Pine",
    "Pinnacle", "Plover", "Quail", "Raccoon"
)

private val generatedNameOrder = java.util.concurrent.ConcurrentHashMap<String, List<Int>>()

/**
 * Stable generated name for tiers past the handcrafted list: a per-achievement
 * seeded shuffle of adjective+noun pairs, so every ladder gets different names
 * that never change between runs. Practically inexhaustible (4096 combos).
 */
fun generatedTierName(defId: String, level: Int): String {
    val total = TIER_ADJECTIVES.size * TIER_NOUNS.size
    val order = generatedNameOrder.getOrPut(defId) {
        (0 until total).shuffled(kotlin.random.Random(defId.hashCode().toLong()))
    }
    val idx = order[(level - 1) % total]
    return "${TIER_ADJECTIVES[idx / TIER_NOUNS.size]} ${TIER_NOUNS[idx % TIER_NOUNS.size]}"
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
        id = "hud_pilot", title = "HUD Pilot",
        desc = "Enable the session usage overlay", icon = Icons.Outlined.PictureInPicture,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "patient_player", title = "Patient Player",
        desc = "Delay an app instead of blocking it", icon = Icons.Outlined.HourglassFull,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 delayed app"))
    ),
    AchievementDef(
        id = "strict_enforcer", title = "Strict Enforcer",
        desc = "Turn on strict mode on a shield", icon = Icons.Outlined.Gavel,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "caller_tuned", title = "Voice of Goals",
        desc = "Enable the goal caller voice", icon = Icons.Outlined.Call,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "auto_quitter", title = "Auto Quitter",
        desc = "Let Zenith auto-quit an app", icon = Icons.Outlined.ExitToApp,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "target_setter", title = "Target Setter",
        desc = "Set a daily screen time target", icon = Icons.Outlined.Adjust,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Target set"))
    ),
    AchievementDef(
        id = "web_watcher", title = "Web Watcher",
        desc = "Enable website auto-tracking", icon = Icons.Outlined.Public,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "dev_mode", title = "Developer Mode",
        desc = "Unlock developer options", icon = Icons.Outlined.Code,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "perf_tuner", title = "Performance Tuner",
        desc = "Craft a custom performance profile", icon = Icons.Outlined.Tune,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Custom profile"))
    ),
    AchievementDef(
        id = "data_doctor", title = "Data Doctor",
        desc = "Enable smart repair on refresh", icon = Icons.Outlined.Healing,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "ghost_mode", title = "Ghost Mode",
        desc = "Exclude an app from tracking", icon = Icons.Outlined.VisibilityOff,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 excluded app"))
    ),
    AchievementDef(
        id = "wind_downer", title = "Wind Downer",
        desc = "Enable bedtime wind-down", icon = Icons.Outlined.NightsStay,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "town_crier", title = "Town Crier",
        desc = "Share your profile card", icon = Icons.Outlined.Campaign,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Shared once"))
    ),
    AchievementDef(
        id = "hot_streak", title = "Hot Streak",
        desc = "Keep the current streak burning",
        icon = Icons.Outlined.Whatshot, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 3, "3 days"),
            TierThreshold(ProfileTier.V, 7, "7 days"),
            TierThreshold(ProfileTier.X, 14, "14 days"),
            TierThreshold(ProfileTier.L, 30, "30 days"),
            TierThreshold(ProfileTier.C, 60, "60 days"),
            TierThreshold(ProfileTier.D, 100, "100 days"),
            TierThreshold(ProfileTier.M, 200, "200 days")
        ),
        tierNames = listOf(
            "Warm Up", "Hot Streak", "On Fire", "Blazing",
            "Inferno", "Unstoppable", "Eternal Flame"
        )
    ),
    AchievementDef(
        id = "escape_artist", title = "Escape Artist",
        desc = "Total emergency uses spent",
        icon = Icons.Outlined.DirectionsRun, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 escape"),
            TierThreshold(ProfileTier.V, 3, "3 escapes"),
            TierThreshold(ProfileTier.X, 5, "5 escapes"),
            TierThreshold(ProfileTier.L, 10, "10 escapes"),
            TierThreshold(ProfileTier.C, 20, "20 escapes"),
            TierThreshold(ProfileTier.D, 35, "35 escapes"),
            TierThreshold(ProfileTier.M, 50, "50 escapes")
        ),
        tierNames = listOf(
            "First Escape", "Escape Artist", "Sneaky", "Houdini Act",
            "Ghost Protocol", "Phantom", "Uncontainable"
        )
    ),
    AchievementDef(
        id = "scheduler_pro", title = "Scheduler",
        desc = "Active focus schedules",
        icon = Icons.Outlined.CalendarMonth, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 schedule"),
            TierThreshold(ProfileTier.V, 2, "2 schedules"),
            TierThreshold(ProfileTier.X, 3, "3 schedules"),
            TierThreshold(ProfileTier.L, 5, "5 schedules"),
            TierThreshold(ProfileTier.M, 8, "8 schedules")
        ),
        tierNames = listOf(
            "First Plan", "Scheduler", "Planner", "Timetable Keeper", "Grand Scheduler"
        )
    ),
    AchievementDef(
        id = "alarm_collector", title = "Alarm Collector",
        desc = "Alarms ringing for you",
        icon = Icons.Outlined.AlarmOn, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 alarm"),
            TierThreshold(ProfileTier.V, 2, "2 alarms"),
            TierThreshold(ProfileTier.X, 3, "3 alarms"),
            TierThreshold(ProfileTier.L, 5, "5 alarms"),
            TierThreshold(ProfileTier.M, 8, "8 alarms")
        ),
        tierNames = listOf(
            "First Bell", "Alarm Collector", "Double Shift", "Morning Crew", "Ring Master"
        )
    ),
    AchievementDef(
        id = "goal_getter", title = "Goal Getter",
        desc = "Goals you are growing",
        icon = Icons.Outlined.Flag, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 goal"),
            TierThreshold(ProfileTier.V, 2, "2 goals"),
            TierThreshold(ProfileTier.X, 3, "3 goals"),
            TierThreshold(ProfileTier.L, 5, "5 goals"),
            TierThreshold(ProfileTier.M, 8, "8 goals")
        ),
        tierNames = listOf(
            "First Goal", "Goal Getter", "Goal Hunter", "Goal Crusher", "Goal Machine"
        )
    ),
    AchievementDef(
        id = "xp_hoarder", title = "XP Hoarder",
        desc = "Lifetime XP piled up",
        icon = Icons.Outlined.Savings, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 500, "500 XP"),
            TierThreshold(ProfileTier.V, 1000, "1000 XP"),
            TierThreshold(ProfileTier.X, 2500, "2500 XP"),
            TierThreshold(ProfileTier.L, 5000, "5000 XP"),
            TierThreshold(ProfileTier.C, 10000, "10000 XP"),
            TierThreshold(ProfileTier.D, 25000, "25000 XP"),
            TierThreshold(ProfileTier.M, 50000, "50000 XP")
        ),
        tierNames = listOf(
            "First Hoard", "XP Hoarder", "XP Stacker", "XP Vault",
            "XP Tycoon", "XP Legend", "XP Deity"
        )
    ),
    AchievementDef(
        id = "taskmaster", title = "Taskmaster",
        desc = "Pause Point task types mastered",
        icon = Icons.Outlined.Assignment, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 type"),
            TierThreshold(ProfileTier.V, 2, "2 types"),
            TierThreshold(ProfileTier.X, 4, "4 types"),
            TierThreshold(ProfileTier.M, 6, "6 types")
        ),
        tierNames = listOf(
            "First Task", "Taskmaster", "Drill Sergeant", "Ringmaster"
        )
    ),
    AchievementDef(
        id = "palette_painter", title = "Palette Painter",
        desc = "Paint overlays a custom hue", icon = Icons.Outlined.Colorize,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Custom hue"))
    ),
    AchievementDef(
        id = "floater", title = "Floater",
        desc = "Float the tab bar", icon = Icons.Outlined.Dock,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "pill_keeper", title = "Pill Keeper",
        desc = "Pin the total usage pill", icon = Icons.Outlined.Medication,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "locked_in", title = "Locked In",
        desc = "Arm the incentive lock", icon = Icons.Outlined.Lock,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "head_start", title = "Head Start",
        desc = "Enable the early kick", icon = Icons.Outlined.Bolt,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "full_charge", title = "Full Charge",
        desc = "Reset stats on full charge", icon = Icons.Outlined.BatteryChargingFull,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "day_maker", title = "Day Maker",
        desc = "Define when your day starts", icon = Icons.Outlined.WbSunny,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Custom start"))
    ),
    AchievementDef(
        id = "night_off", title = "Night Off",
        desc = "Pause tracking in unused hours", icon = Icons.Outlined.BedtimeOff,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "second_chance", title = "Second Chance",
        desc = "Recover a broken streak", icon = Icons.Outlined.Replay,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Recovered once"))
    ),
    AchievementDef(
        id = "cleaner", title = "Cleaner",
        desc = "Dismiss an uninstalled app", icon = Icons.Outlined.CleaningServices,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Cleaned once"))
    ),
    AchievementDef(
        id = "bedtime_bouncer", title = "Bedtime Bouncer",
        desc = "Curate the bedtime allowlist", icon = Icons.Outlined.Hotel,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 allowed app"))
    ),
    AchievementDef(
        id = "silent_sleeper", title = "Silent Sleeper",
        desc = "Sleep behind do-not-disturb", icon = Icons.Outlined.NotificationsOff,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Enabled once"))
    ),
    AchievementDef(
        id = "shorts_spotter", title = "Shorts Spotter",
        desc = "Catch Shorts in the act", icon = Icons.Outlined.PlayCircle,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Spotted once"))
    ),
    AchievementDef(
        id = "widget_wielder", title = "Widget Wielder",
        desc = "Pin Zenith widgets to home",
        icon = Icons.Outlined.Widgets, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 widget"),
            TierThreshold(ProfileTier.V, 2, "2 widgets"),
            TierThreshold(ProfileTier.X, 3, "3 widgets"),
            TierThreshold(ProfileTier.M, 5, "5 widgets")
        ),
        tierNames = listOf(
            "First Pin", "Widget Wielder", "Home Decorator", "Widget Wall"
        )
    ),
    AchievementDef(
        id = "web_cartographer", title = "Web Cartographer",
        desc = "Distinct domains tracked",
        icon = Icons.Outlined.Explore, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 3, "3 domains"),
            TierThreshold(ProfileTier.V, 10, "10 domains"),
            TierThreshold(ProfileTier.X, 25, "25 domains"),
            TierThreshold(ProfileTier.M, 50, "50 domains")
        ),
        tierNames = listOf(
            "First Domain", "Web Cartographer", "Domain Mapper", "Magellan"
        )
    ),
    AchievementDef(
        id = "interceptor", title = "Interceptor",
        desc = "Notifications caught by schedules",
        icon = Icons.Outlined.NotificationsActive, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 10, "10 caught"),
            TierThreshold(ProfileTier.V, 50, "50 caught"),
            TierThreshold(ProfileTier.X, 200, "200 caught"),
            TierThreshold(ProfileTier.M, 1000, "1000 caught")
        ),
        tierNames = listOf(
            "First Block", "Interceptor", "Gatekeeper", "Firewall"
        )
    ),
    AchievementDef(
        id = "historian", title = "Historian",
        desc = "Days of history preserved",
        icon = Icons.Outlined.AutoStories, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 7, "7 days"),
            TierThreshold(ProfileTier.V, 30, "30 days"),
            TierThreshold(ProfileTier.X, 90, "90 days"),
            TierThreshold(ProfileTier.L, 180, "180 days"),
            TierThreshold(ProfileTier.M, 365, "365 days")
        ),
        tierNames = listOf(
            "First Week", "Historian", "Chronicler", "Archivist", "Timekeeper"
        )
    ),
    AchievementDef(
        id = "app_atlas", title = "App Atlas",
        desc = "Distinct apps ever tracked",
        icon = Icons.Outlined.Map, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 10, "10 apps"),
            TierThreshold(ProfileTier.V, 25, "25 apps"),
            TierThreshold(ProfileTier.X, 50, "50 apps"),
            TierThreshold(ProfileTier.L, 100, "100 apps"),
            TierThreshold(ProfileTier.M, 200, "200 apps")
        ),
        tierNames = listOf(
            "First Pin", "App Atlas", "Trailblazer", "Surveyor", "Cartographer"
        )
    ),
    AchievementDef(
        id = "variant_vanguard", title = "Variant Vanguard",
        desc = "Custom Pause Point variants",
        icon = Icons.Outlined.DashboardCustomize, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 variant"),
            TierThreshold(ProfileTier.V, 2, "2 variants"),
            TierThreshold(ProfileTier.X, 4, "4 variants"),
            TierThreshold(ProfileTier.M, 6, "6 variants")
        ),
        tierNames = listOf(
            "First Twist", "Variant Vanguard", "Remixer", "Mad Scientist"
        )
    ),
    AchievementDef(
        id = "under_budget", title = "Under Budget",
        desc = "Days under the screen target",
        icon = Icons.Outlined.Savings, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 5, "5 days"),
            TierThreshold(ProfileTier.V, 10, "10 days"),
            TierThreshold(ProfileTier.X, 15, "15 days"),
            TierThreshold(ProfileTier.L, 20, "20 days"),
            TierThreshold(ProfileTier.M, 25, "25 days")
        ),
        tierNames = listOf(
            "First Save", "Under Budget", "Frugal", "Economist", "Minimalist"
        )
    ),
    // Fun tiered
    AchievementDef(
        id = "info_hunter", title = "Info Hunter",
        desc = "Tap the info button across screens",
        icon = Icons.Outlined.Explore, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 3, "3 screens"),
            TierThreshold(ProfileTier.V, 8, "8 screens"),
            TierThreshold(ProfileTier.X, 15, "15 screens"),
            TierThreshold(ProfileTier.M, 22, "22 screens")
        ),
        tierNames = listOf(
            "Peeker", "Info Hunter", "Curious Cat", "Know-It-All"
        )
    ),
    AchievementDef(
        id = "night_owl_lite", title = "Night Owl Lite",
        desc = "Active during midnight hours",
        icon = Icons.Outlined.Bedtime, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 3, "3 nights"),
            TierThreshold(ProfileTier.X, 7, "7 nights"),
            TierThreshold(ProfileTier.M, 14, "14 nights")
        ),
        tierNames = listOf(
            "Night Owl Lite", "Midnight Walker", "Insomniac"
        )
    ),
    AchievementDef(
        id = "early_bird", title = "Early Bird",
        desc = "Active in the fresh morning",
        icon = Icons.Outlined.WbSunny, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 3, "3 mornings"),
            TierThreshold(ProfileTier.X, 7, "7 mornings"),
            TierThreshold(ProfileTier.M, 14, "14 mornings")
        ),
        tierNames = listOf(
            "Early Bird", "Sunrise Chaser", "Morning Person"
        )
    ),
    AchievementDef(
        id = "weekend_warrior", title = "Weekend Warrior",
        desc = "Weekends tracked with activity",
        icon = Icons.Outlined.CalendarViewMonth, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 4, "4 weekends"),
            TierThreshold(ProfileTier.X, 8, "8 weekends"),
            TierThreshold(ProfileTier.M, 16, "16 weekends")
        ),
        tierNames = listOf(
            "Weekend Warrior", "Weekend Regular", "Weekend Veteran"
        )
    ),
    AchievementDef(
        id = "profile_polisher", title = "Profile Polisher",
        desc = "Complete your profile identity",
        icon = Icons.Outlined.Badge, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 field"),
            TierThreshold(ProfileTier.X, 2, "2 fields"),
            TierThreshold(ProfileTier.M, 3, "3 fields")
        ),
        tierNames = listOf(
            "First Touch", "Profile Polisher", "Identity Complete"
        )
    ),
    AchievementDef(
        id = "streak_roller", title = "Streak Roller",
        desc = "Keep a week-long streak rolling",
        icon = Icons.Outlined.EventRepeat, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 7, "7 days"),
            TierThreshold(ProfileTier.X, 14, "14 days"),
            TierThreshold(ProfileTier.M, 21, "21 days")
        ),
        tierNames = listOf(
            "Seven Up", "Streak Roller", "Fortnight"
        )
    ),
    AchievementDef(
        id = "overachiever", title = "Overachiever",
        desc = "Days you blew past the limit",
        icon = Icons.Outlined.LocalFireDepartment, category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 day"),
            TierThreshold(ProfileTier.X, 3, "3 days"),
            TierThreshold(ProfileTier.M, 7, "7 days")
        ),
        tierNames = listOf(
            "Guilty Pleasure", "Overachiever", "No Chill"
        )
    ),
    // Fun single-tier
    AchievementDef(
        id = "midnight_oil", title = "Midnight Oil",
        desc = "Burn after 23:00", icon = Icons.Outlined.DarkMode,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Spotted once"))
    ),
    AchievementDef(
        id = "sunrise_club", title = "Sunrise Club",
        desc = "Up before the world at 05:00", icon = Icons.Outlined.LightMode,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Spotted once"))
    ),
    AchievementDef(
        id = "web_surf", title = "Web Surfer",
        desc = "A web session sneaked into tracking", icon = Icons.Outlined.Language,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Spotted once"))
    ),
    AchievementDef(
        id = "theme_explorer", title = "Theme Explorer",
        desc = "Try a non-default theme", icon = Icons.Outlined.Palette,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Switched once"))
    ),
    AchievementDef(
        id = "font_explorer", title = "Font Explorer",
        desc = "Try a different font", icon = Icons.Outlined.TextFields,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Switched once"))
    ),
    AchievementDef(
        id = "contrast_seeker", title = "Contrast Seeker",
        desc = "Brave dynamic color off", icon = Icons.Outlined.Contrast,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Turned off"))
    ),
    AchievementDef(
        id = "calendar_seeker", title = "Calendar Seeker",
        desc = "Check the calendar overlay", icon = Icons.Outlined.DateRange,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Opened once"))
    ),
    AchievementDef(
        id = "overlay_tinter", title = "Overlay Tinter",
        desc = "Give the overlay a custom color", icon = Icons.Outlined.ColorLens,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "Custom once"))
    ),
    AchievementDef(
        id = "schedule_keeper", title = "Schedule Keeper",
        desc = "Create a focus schedule", icon = Icons.Outlined.EventRepeat,
        category = AchievementCategory.EXPLORER,
        thresholds = listOf(TierThreshold(ProfileTier.I, 1, "1 schedule"))
    ),
    AchievementDef(
        id = "qr_collector", title = "QR Collector",
        desc = "Pause Point QR codes saved", icon = Icons.Outlined.QrCode2,
        category = AchievementCategory.ACCUMULATION,
        thresholds = listOf(
            TierThreshold(ProfileTier.I, 1, "1 code"),
            TierThreshold(ProfileTier.V, 2, "2 codes"),
            TierThreshold(ProfileTier.X, 3, "3 codes"),
            TierThreshold(ProfileTier.M, 5, "5 codes")
        ),
        tierNames = listOf(
            "First Code", "QR Collector", "Code Hoarder", "QR Master"
        )
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

/**
 * Display order following a new user's likely journey: setup targets and
 * protectors first, first streaks and savings next, customization and
 * power features in the middle, long grinds and tinkerer badges last.
 * Unknown ids sink to the bottom so new defs never break the order.
 */
private val JOURNEY_ORDER = listOf(
    // Setup: targets and first protections.
    "target_setter", "shield_setter", "goal_setter",
    // First protector trials.
    "lockdown_explorer", "bedtime_explorer", "alarm_explorer",
    "pausepoint_explorer", "grace_explorer",
    // First focus and planning.
    "pomo_first", "preset_saver", "schedule_keeper",
    // Early streaks, savings, and playful daily rhythms.
    "hot_streak", "streak_keeper", "streak_roller", "under_budget", "overachiever",
    "time_saver", "night_guardian", "night_owl_lite", "early_bird",
    "midnight_oil", "sunrise_club",
    // First week of data.
    "historian", "loyal_tracker", "app_atlas", "fortress_builder", "goal_getter",
    "weekend_warrior", "web_surf",
    // Customization wave.
    "theme_stylist", "theme_explorer", "font_explorer", "contrast_seeker",
    "palette_painter", "overlay_tinter", "floater", "pill_keeper", "hud_pilot",
    "day_maker", "head_start", "info_hunter", "profile_polisher", "calendar_seeker",
    // Shield power features.
    "patient_player", "strict_enforcer", "caller_tuned", "auto_quitter",
    "locked_in", "ghost_mode", "night_off",
    // Awareness features.
    "mindful_explorer", "glimpse_user", "web_watcher", "full_charge",
    // Eye and bedtime depth.
    "eyecare_explorer", "wind_downer", "bedtime_bouncer", "silent_sleeper",
    // Planning depth (QR needs physical codes, so it sits with the
    // optional Pause Point depth instead of the early journey).
    "alarm_collector", "scheduler_pro", "qr_collector", "taskmaster", "variant_vanguard",
    // Focus depth.
    "focus_hours", "xp_hoarder",
    // Sharing and ambient delights.
    "town_crier", "shorts_spotter", "widget_wielder",
    // Recovery and maintenance.
    "second_chance", "cleaner", "backup_guardian", "data_doctor",
    // Long grinds.
    "app_streaker", "web_cartographer", "interceptor", "escape_artist",
    // Tinkerer corner.
    "dev_mode", "perf_tuner"
)

fun buildAchievementStates(stats: ProfileAchievementStats): List<AchievementState> {
    val defs = buildAchievementDefs().sortedBy {
        JOURNEY_ORDER.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
    }
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
        "qr_collector" to stats.qrCount.toLong(),
        "preset_saver" to if (stats.hasPreset) 1L else 0L,
        "hud_pilot" to if (stats.hasOverlayHud) 1L else 0L,
        "patient_player" to if (stats.hasDelayShield) 1L else 0L,
        "strict_enforcer" to if (stats.hasStrictShield) 1L else 0L,
        "caller_tuned" to if (stats.hasCallerShield) 1L else 0L,
        "auto_quitter" to if (stats.hasAutoQuitShield) 1L else 0L,
        "target_setter" to if (stats.hasTarget) 1L else 0L,
        "web_watcher" to if (stats.hasWebsiteTracking) 1L else 0L,
        "dev_mode" to if (stats.isDeveloper) 1L else 0L,
        "perf_tuner" to if (stats.isCustomPerf) 1L else 0L,
        "data_doctor" to if (stats.hasRepair) 1L else 0L,
        "ghost_mode" to if (stats.hasExcluded) 1L else 0L,
        "wind_downer" to if (stats.hasWindDown) 1L else 0L,
        "town_crier" to if (stats.hasSharedProfile) 1L else 0L,
        "hot_streak" to stats.globalCurrentStreak.toLong(),
        "escape_artist" to stats.emergencyTotal.toLong(),
        "scheduler_pro" to stats.scheduleCount.toLong(),
        "alarm_collector" to stats.alarmCount.toLong(),
        "goal_getter" to stats.goalCount.toLong(),
        "xp_hoarder" to stats.userXpTotal,
        "taskmaster" to stats.taskTypeCount.toLong(),
        "theme_explorer" to if (stats.hasCustomThemeAlt) 1L else 0L,
        "font_explorer" to if (stats.fontChanged) 1L else 0L,
        "contrast_seeker" to if (stats.contrastOff) 1L else 0L,
        "calendar_seeker" to if (stats.hasCalendarEvent) 1L else 0L,
        "overlay_tinter" to if (stats.hasOverlayTint) 1L else 0L,
        "info_hunter" to stats.infoSheetCount.toLong(),
        "night_owl_lite" to stats.nightNights.toLong(),
        "early_bird" to stats.earlyMornings.toLong(),
        "weekend_warrior" to stats.weekendDays.toLong(),
        "profile_polisher" to stats.profileFields.toLong(),
        "streak_roller" to stats.sevenDayStreak.toLong(),
        "overachiever" to stats.overdrawDays.toLong(),
        "midnight_oil" to if (stats.nightNights > 0) 1L else 0L,
        "sunrise_club" to if (stats.earlyMornings > 0) 1L else 0L,
        "web_surf" to if (stats.webTotalMillis > 0L) 1L else 0L,
        "palette_painter" to if (stats.hasCustomOverlay) 1L else 0L,
        "floater" to if (stats.hasFloatingBar) 1L else 0L,
        "pill_keeper" to if (stats.hasUsagePill) 1L else 0L,
        "locked_in" to if (stats.hasIncentiveLock) 1L else 0L,
        "head_start" to if (stats.hasEarlyKick) 1L else 0L,
        "full_charge" to if (stats.hasBatteryReset) 1L else 0L,
        "day_maker" to if (stats.hasCustomDayStart) 1L else 0L,
        "night_off" to if (stats.hasUnusedHours) 1L else 0L,
        "second_chance" to if (stats.hasRecovery) 1L else 0L,
        "cleaner" to if (stats.hasCleaned) 1L else 0L,
        "bedtime_bouncer" to if (stats.hasBedtimeWhitelist) 1L else 0L,
        "silent_sleeper" to if (stats.hasBedtimeDnd) 1L else 0L,
        "shorts_spotter" to if (stats.hasShorts) 1L else 0L,
        "widget_wielder" to stats.widgetCount.toLong(),
        "web_cartographer" to stats.webDomainCount.toLong(),
        "interceptor" to stats.interceptedCount.toLong(),
        "historian" to stats.trackedDayCount.toLong(),
        "app_atlas" to stats.lifetimeAppCount.toLong(),
        "variant_vanguard" to stats.customVariantCount.toLong(),
        "under_budget" to stats.underBudgetDays.toLong(),
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
        val earnedLevel = tierCountFor(def, current)
        val earnedTier = thresholdAtOrNull(def, earnedLevel)?.tier
        val next = nextThresholdFor(def, current)
        val progress = when {
            next == null -> 1f
            next.required <= 0L -> 1f
            else -> (current.toFloat() / next.required).coerceIn(0f, 1f)
        }
        AchievementState(
            def = def,
            current = current,
            earnedTier = earnedTier,
            earnedLevel = earnedLevel,
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
