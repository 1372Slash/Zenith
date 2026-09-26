package com.etrisad.zenith.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LifetimeApp(
    val packageName: String,
    val appName: String,
    val totalMillis: Long
)

data class PendingUnlock(
    val defId: String,
    val tierLevel: Int,
    val tierValue: Int,
    val date: String,
    val prevLevel: Int = 0
)

sealed interface ProfileBannerEvent {
    val key: String

    data class Unlock(
        val defId: String,
        val tierLevel: Int,
        val tierValue: Int,
        val prevLevel: Int,
        val date: String
    ) : ProfileBannerEvent {
        override val key = "u:$defId:$tierValue"
    }

    data class Progress(
        val defId: String,
        val before: Long,
        val after: Long,
        val date: String
    ) : ProfileBannerEvent {
        override val key = "p:$defId:$date:$after"
    }
}

data class XpDay(
    val date: String,
    val xp: Int,
    val savedMillis: Long
)
data class DailyCount(
    val count: Int,
    val lastDate: String
)
fun com.etrisad.zenith.data.preferences.UserPreferences.achievementTrackingKey(): String {
    return listOf(
        globalBestStreak, globalCurrentStreak, bedtimeBestStreak,
        alarmsJson, pausePointEnabled, pausePointQrCodes.size, pausePointNfcTagIds.size,
        eyeCareEnabled, gracePeriodEnabled, lockdownEnabled,
        pomodoroSessionEndTimestamp, pomodoroPresets,
        expressiveColors, autoBackupEnabled, lastBackupTimestamp,
        mindfulGatewayEnabled, usageGlimpseEnabled,
        userXpLastAwardDate, achievementHistory, achievementLastValues,
        sessionUsageOverlayEnabled, screenTimeTargetMinutes,
        websiteAutoTrackingEnabled, developerModeEnabled,
        performanceLevel.name, smartRepairOnRefresh,
        excludedFromTrackingPackages.size, bedtimeWindDownEnabled,
        pausePointTaskTypes.size, pausePointQrCodes.size, pausePointNfcTagIds.size,
        userSharedProfile, globalCurrentStreak, userXpTotal,
        overlayPaletteId, floatingTabBarEnabled, totalUsagePillEnabled,
        incentiveLockEnabled, earlyKickEnabled, batteryStatsResetEnabled,
        dayStartHour, dayStartMinute, disableTrackingAtUnusedHours,
        streakRecoveryPerformed, dismissedUninstalledApps.size,
        bedtimeWhitelistedPackages.size, bedtimeDndEnabled,
        shortsScreenTimeMs > 0,
        infoVisitedRoutes.size, userBio, userAvatarUri, userBannerUri,
        themeConfig.name, fontOption.name, dynamicColor, overlayCustomHue
    ).joinToString("|")
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val topApps: List<LifetimeApp> = emptyList(),
    val lifetimeTotal: Long = 0L,
    val lifetimeAppCount: Int = 0,
    val streakCurrent: Int = 0,
    val streakBest: Int = 0,
    val xpTotal: Long = 0L,
    val xpToday: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val totalSavedMillis: Long = 0L,
    val pomodoroSessions: Int = 0,
    val pomodoroFocusMillis: Long = 0L,
    val achievements: List<AchievementState> = emptyList(),
    val xpHistory: List<XpDay> = emptyList(),
    val lastSyncDate: String = ""
)

private val AGGREGATE_EXCLUDE = setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL")

class ProfileViewModel(
    context: Context,
    private val shieldRepository: ShieldRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val appContext = context.applicationContext
    private val appNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()

    private val _pendingUnlocks = MutableStateFlow<List<PendingUnlock>>(emptyList())
    val pendingUnlocks: StateFlow<List<PendingUnlock>> = _pendingUnlocks.asStateFlow()

    private val _pendingBanners = MutableStateFlow<List<ProfileBannerEvent>>(emptyList())
    val pendingBanners: StateFlow<List<ProfileBannerEvent>> = _pendingBanners.asStateFlow()

    fun consumeUnlock(defId: String, tierValue: Int) {
        _pendingUnlocks.update { list ->
            list.filterNot { it.defId == defId && it.tierValue == tierValue }
        }
    }

    /**
     * Banner keys enqueued by the developer test hook. They must never
     * pollute the persistent seen-set, otherwise testing would silently
     * burn the real future notification for those achievement ids.
     */
    private val testBannerKeys = mutableSetOf<String>()

    fun consumeBanner(key: String) {
        val removed = _pendingBanners.value.filter { it.key == key }
        _pendingBanners.update { list -> list.filterNot { it.key == key } }
        if (removed.any { it.key in testBannerKeys }) {
            testBannerKeys.remove(key)
        } else {
            testBannerKeys.remove(key)
            markBannersSeen(removed.mapNotNull { seenKeyFor(it) })
        }
    }

    fun clearAllBanners() {
        val queued = _pendingBanners.value
        _pendingBanners.update { emptyList() }
        markBannersSeen(queued.filterNot { it.key in testBannerKeys }.mapNotNull { seenKeyFor(it) })
        testBannerKeys.clear()
    }
    fun testUnlockBanner() {
        val today = dateStr(System.currentTimeMillis())
        _pendingUnlocks.update {
            it + PendingUnlock(defId = "streak_keeper", tierLevel = 1, tierValue = 1, date = today)
        }
        val testEvents = listOf<ProfileBannerEvent>(
            ProfileBannerEvent.Unlock(
                defId = "streak_keeper", tierLevel = 1, tierValue = 1,
                prevLevel = 0, date = today
            ),
            ProfileBannerEvent.Progress(
                defId = "loyal_tracker",
                before = 32_400_000L,
                after = 36_000_000L,
                date = today
            )
        )
        testBannerKeys.addAll(testEvents.map { it.key })
        _pendingBanners.update { it + testEvents }
    }

    /**
     * Seen keys are scoped per achievement AND tier band so every new tier
     * is still celebrated, but the same banner can never repeat:
     * - unlock: "$defId#u#$tierLevel" (tierLevel = 1-based position)
     * - progress: "$defId#p#$band" where band = tiers earned so far
     *   (i.e. one progress banner per approach toward the next tier).
     */
    private fun unlockSeenKey(defId: String, tierLevel: Int) = "$defId#u#$tierLevel"

    private fun progressSeenKey(defId: String, band: Int) = "$defId#p#$band"

    private fun seenKeyFor(event: ProfileBannerEvent): String? = when (event) {
        is ProfileBannerEvent.Unlock -> unlockSeenKey(event.defId, event.tierLevel)
        is ProfileBannerEvent.Progress -> {
            val band = _uiState.value.achievements.find { it.def.id == event.defId }?.earnedLevel
                ?: return null
            progressSeenKey(event.defId, band)
        }
    }

    /**
     * Persists banner-seen keys so the same banner never shows twice:
     * re-entry, resume, and Clear all can never resurrect it, while a
     * newly reached tier still earns its own celebration.
     */
    private fun markBannersSeen(defIds: List<String>) {
        val fresh = defIds.filter { it.isNotBlank() }.toSet()
        if (fresh.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val seen =
                    decodeSeen(userPreferencesRepository.userPreferencesFlow.first().achievementBannersSeen)
                        .toMutableSet()
                if (seen.addAll(fresh)) {
                    userPreferencesRepository.setAchievementBannersSeen(encodeSeen(seen))
                }
            } catch (_: Exception) {
            }
        }
    }

    init {
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                if (!silent) {
                    _uiState.update { it.copy(isLoading = true) }
                }
            try {
                val lifetime = syncLifetime()
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val todayStr = dateStr(System.currentTimeMillis())
                val xpToday = awardXpIfNeeded(todayStr)
                val freshPrefs = userPreferencesRepository.userPreferencesFlow.first()
                val pomoSessions = shieldRepository.getPomodoroTotalCount()
                val pomoFocus = shieldRepository.getPomodoroTotalFocusMillis()
                val shields = try {
                    shieldRepository.allShields.first()
                } catch (_: Exception) { emptyList() }
                val schedules = try {
                    shieldRepository.allSchedules.first()
                } catch (_: Exception) { emptyList() }
                // Day-gated counters for day-based achievements (historian,
                // weekend_warrior, night_owl_lite, early_bird). Each id
                // increments at most once per calendar day, and only when
                // that day's condition holds, cold starts never inflate them.
                val trackedDates = try {
                    shieldRepository.getAllTrackedDates().toSet()
                } catch (_: Exception) { emptySet() }
                val hourlyToday = try {
                    shieldRepository.getHourlyUsageForDateSync(todayStr)
                } catch (_: Exception) { emptyList() }
                val todayCal = java.util.Calendar.getInstance()
                val isWeekendToday = todayCal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SATURDAY ||
                    todayCal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
                val hasAnyUsageToday = todayStr in trackedDates
                val nightToday = hourlyToday.any { it.hour in 0..4 && it.usageTimeMillis > 0 }
                val earlyToday = hourlyToday.any { it.hour in 5..7 && it.usageTimeMillis > 0 }
                val dbDayCounts = mapOf(
                    "historian" to try { shieldRepository.getTrackedDayCount() } catch (_: Exception) { 0 },
                    "weekend_warrior" to weekendTrackedDays(),
                    "night_owl_lite" to try { shieldRepository.getHourlyActiveDayCount(0, 4) } catch (_: Exception) { 0 },
                    "early_bird" to try { shieldRepository.getHourlyActiveDayCount(5, 7) } catch (_: Exception) { 0 }
                )
                val dayConditions = mapOf(
                    "historian" to hasAnyUsageToday,
                    "weekend_warrior" to (isWeekendToday && hasAnyUsageToday),
                    "night_owl_lite" to nightToday,
                    "early_bird" to earlyToday
                )
                val todayCounted = mapOf(
                    "historian" to hasAnyUsageToday,
                    "weekend_warrior" to (isWeekendToday && hasAnyUsageToday),
                    "night_owl_lite" to nightToday,
                    "early_bird" to earlyToday
                )
                val dayCounts = refreshDailyCounts(todayStr, dayConditions, dbDayCounts, todayCounted)
                val stats = ProfileAchievementStats(
                    hasPomodoro = pomoSessions > 0 || freshPrefs.pomodoroSessionEndTimestamp > 0L,
                    hasLockdown = freshPrefs.lockdownEnabled,
                    hasBedtime = freshPrefs.bedtimeEnabled || freshPrefs.bedtimeBestStreak > 0,
                    hasAlarm = freshPrefs.alarmsJson.trim() != "[]",
                    hasPausePoint = freshPrefs.pausePointEnabled,
                    hasEyeCare = freshPrefs.eyeCareEnabled,
                    hasGracePeriod = freshPrefs.gracePeriodEnabled,
                    hasShield = shields.any { it.type == FocusType.SHIELD },
                    hasGoal = shields.any { it.type == FocusType.GOAL },
                    hasSchedule = schedules.isNotEmpty(),
                    hasQr = freshPrefs.pausePointQrCodes.isNotEmpty(),
                    hasNfc = freshPrefs.pausePointNfcTagIds.isNotEmpty(),
                    hasPreset = freshPrefs.pomodoroPresets.trim() != "{}",
                    hasCustomTheme = freshPrefs.expressiveColors,
                    hasBackup = freshPrefs.autoBackupEnabled || freshPrefs.lastBackupTimestamp > 0L,
                    hasMindful = freshPrefs.mindfulGatewayEnabled,
                    hasGlimpse = freshPrefs.usageGlimpseEnabled,
                    globalBestStreak = freshPrefs.globalBestStreak,
                    totalSavedMillis = freshPrefs.userTotalSavedMillis,
                    maxAppBestStreak = shields.maxOfOrNull { it.bestStreak } ?: 0,
                    pomodoroSessions = pomoSessions,
                    pomodoroFocusMillis = pomoFocus,
                    lifetimeMillis = lifetime.total,
                    bedtimeBestStreak = freshPrefs.bedtimeBestStreak,
                    shieldCount = shields.size,
                    hasOverlayHud = freshPrefs.sessionUsageOverlayEnabled,
                    hasDelayShield = shields.any { it.isDelayAppEnabled },
                    hasStrictShield = shields.any { it.isStrictModeEnabled },
                    hasCallerShield = shields.any { it.isGoalCallerEnabled },
                    hasAutoQuitShield = shields.any { it.isAutoQuitEnabled },
                    hasTarget = freshPrefs.screenTimeTargetMinutes > 0,
                    hasWebsiteTracking = freshPrefs.websiteAutoTrackingEnabled,
                    isDeveloper = freshPrefs.developerModeEnabled,
                    isCustomPerf = freshPrefs.performanceLevel ==
                        com.etrisad.zenith.data.preferences.PerformanceLevel.CUSTOM,
                    hasRepair = freshPrefs.smartRepairOnRefresh,
                    hasExcluded = freshPrefs.excludedFromTrackingPackages.isNotEmpty(),
                    hasWindDown = freshPrefs.bedtimeWindDownEnabled,
                    hasSharedProfile = freshPrefs.userSharedProfile,
                    globalCurrentStreak = freshPrefs.globalCurrentStreak,
                    emergencyTotal = shields.sumOf { it.emergencyUseCount },
                    scheduleCount = schedules.size,
                    alarmCount = try {
                        userPreferencesRepository.parseAlarms(freshPrefs.alarmsJson).size
                    } catch (_: Exception) { 0 },
                    goalCount = shields.count { it.type == FocusType.GOAL },
                    qrCount = freshPrefs.pausePointQrCodes.size,
                    nfcCount = freshPrefs.pausePointNfcTagIds.size,
                    taskTypeCount = freshPrefs.pausePointTaskTypes.size,
                    // While a debug XP override is active, achievements
                    // compute from the frozen pre-debug base so debugging
                    // never unlocks real XP achievements. Level display
                    // keeps following the override total.
                    userXpTotal = if (freshPrefs.userXpDebugBase >= 0L) freshPrefs.userXpDebugBase
                    else freshPrefs.userXpTotal,
                    hasCustomOverlay = freshPrefs.overlayPaletteId != "dynamic",
                    hasFloatingBar = freshPrefs.floatingTabBarEnabled,
                    hasUsagePill = freshPrefs.totalUsagePillEnabled,
                    hasIncentiveLock = freshPrefs.incentiveLockEnabled,
                    hasEarlyKick = freshPrefs.earlyKickEnabled,
                    hasBatteryReset = freshPrefs.batteryStatsResetEnabled,
                    hasCustomDayStart = freshPrefs.dayStartHour != 0 || freshPrefs.dayStartMinute != 0,
                    hasUnusedHours = freshPrefs.disableTrackingAtUnusedHours,
                    hasRecovery = freshPrefs.streakRecoveryPerformed,
                    hasCleaned = freshPrefs.dismissedUninstalledApps.isNotEmpty(),
                    hasBedtimeWhitelist = freshPrefs.bedtimeWhitelistedPackages.isNotEmpty(),
                    hasBedtimeDnd = freshPrefs.bedtimeDndEnabled,
                    hasShorts = freshPrefs.shortsScreenTimeMs > 0L,
                    widgetCount = placedWidgetCount(),
                    webDomainCount = shieldRepository.getWebsiteDomainCount(),
                    webTotalMillis = shieldRepository.getWebsiteTotalMillis(),
                    interceptedCount = shieldRepository.getInterceptedNotificationCount(),
                    trackedDayCount = dayCounts["historian"]
                        ?: try { shieldRepository.getTrackedDayCount() } catch (_: Exception) { 0 },
                    underBudgetDays = underBudgetDaysThisMonth(todayStr),
                    overdrawDays = overdrawDaysThisMonth(todayStr),
                    lifetimeAppCount = lifetime.appCount,
                    customVariantCount = customVariantCount(freshPrefs),
                    infoSheetCount = freshPrefs.infoVisitedRoutes.size,
                    weekendDays = dayCounts["weekend_warrior"] ?: weekendTrackedDays(),
                    nightNights = dayCounts["night_owl_lite"]
                        ?: try { shieldRepository.getHourlyActiveDayCount(0, 4) } catch (_: Exception) { 0 },
                    earlyMornings = dayCounts["early_bird"]
                        ?: try { shieldRepository.getHourlyActiveDayCount(5, 7) } catch (_: Exception) { 0 },
                    profileFields = listOf(
                        freshPrefs.userBio.isNotBlank(),
                        freshPrefs.userAvatarUri.isNotBlank(),
                        freshPrefs.userBannerUri.isNotBlank()
                    ).count { it },
                    sevenDayStreak = freshPrefs.globalBestStreak,
                    distinctDatesCount = shieldRepository.getTrackedDayCount(),
                    hasCustomThemeAlt = freshPrefs.themeConfig != com.etrisad.zenith.data.preferences.ThemeConfig.FOLLOW_SYSTEM,
                    fontChanged = freshPrefs.fontOption != com.etrisad.zenith.data.preferences.FontOption.GOOGLE_SANS_FLEX ||
                        freshPrefs.gsFlexSettings != com.etrisad.zenith.ui.theme.GSFlexSettings(),
                    contrastOff = !freshPrefs.dynamicColor,
                    hasCalendarEvent = try {
                        val cr = appContext.contentResolver
                        cr.query(
                            android.provider.CalendarContract.Instances.CONTENT_URI,
                            arrayOf(android.provider.CalendarContract.Instances.EVENT_ID),
                            null, null, null
                        )?.use { it.count > 0 } ?: false
                    } catch (_: Exception) { false },
                    hasOverlayTint = freshPrefs.overlayCustomHue != 270f
                )
                val baseAchievements = buildAchievementStates(stats)
                val (achievements, newlyTieredIds) = recordUnlockDates(baseAchievements, todayStr)
                detectProgressEvents(achievements, newlyTieredIds, todayStr)
                val xpHistory = decodeXpHistory(freshPrefs.userXpHistory)
                _uiState.update {
                    it.copy(
                        isLoading = if (silent) it.isLoading else false,
                        topApps = lifetime.top3,
                        lifetimeTotal = lifetime.total,
                        lifetimeAppCount = lifetime.appCount,
                        streakCurrent = freshPrefs.globalCurrentStreak,
                        streakBest = freshPrefs.globalBestStreak,
                        xpTotal = freshPrefs.userXpTotal,
                        xpToday = xpToday,
                        level = levelForXp(freshPrefs.userXpTotal),
                        levelProgress = levelProgressForXp(freshPrefs.userXpTotal),
                        totalSavedMillis = freshPrefs.userTotalSavedMillis,
                        pomodoroSessions = pomoSessions,
                        pomodoroFocusMillis = pomoFocus,
                        achievements = achievements,
                        xpHistory = xpHistory,
                        lastSyncDate = prefs.lifetimeLastSyncDate
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
            }
        }
    }

    private data class LifetimeResult(
        val top3: List<LifetimeApp>,
        val total: Long,
        val appCount: Int
    )

    /**
     * Incremental lifetime compaction: only rows newer than the last sync
     * date are aggregated and merged into the persisted snapshot, instead of
     * re-summing the whole table on every open.
     */
    private suspend fun syncLifetime(): LifetimeResult = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val totals = decodeSnapshot(prefs.lifetimeSnapshot).toMutableMap()
        val since = prefs.lifetimeLastSyncDate.ifEmpty { "0000-00-00" }
        try {
            val rows = shieldRepository.getDailyUsagesSinceSync(since)
            var changed = rows.isNotEmpty()
            rows.forEach { row ->
                if (row.packageName in AGGREGATE_EXCLUDE) return@forEach
                totals[row.packageName] = (totals[row.packageName] ?: 0L) + row.usageTimeMillis
            }
            if (changed) {
                userPreferencesRepository.setLifetimeSnapshot(
                    encodeSnapshot(totals),
                    dateStr(System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
        }
        val sorted = totals.entries.sortedByDescending { it.value }
        val top3 = sorted.take(3).map { (pkg, total) ->
            LifetimeApp(pkg, resolveAppName(pkg), total)
        }
        LifetimeResult(top3, sorted.sumOf { it.value }, totals.size)
    }

    /**
     * Awards today's XP once per day: shields earn for staying under the
     * limit (less usage = more XP), goals earn for reaching/over target.
     */
    private suspend fun awardXpIfNeeded(todayStr: String): Int {
        return try {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val shields = try {
                shieldRepository.allShields.first()
            } catch (_: Exception) { emptyList() }
            val todayRows = try {
                shieldRepository.getDailyUsagesForDateSync(todayStr)
            } catch (_: Exception) { emptyList() }
            val usageByPkg = todayRows.associate { it.packageName to it.usageTimeMillis }
            var xp = 0
            var saved = 0L
            shields.forEach { shield ->
                val limit = shield.timeLimitMinutes * 60_000L
                val usage = usageByPkg[shield.packageName] ?: 0L
                when (shield.type) {
                    FocusType.SHIELD -> {
                        xp += calcShieldXp(limit, usage)
                        if (usage <= limit) saved += (limit - usage).coerceAtLeast(0L)
                    }
                    FocusType.GOAL -> xp += calcGoalXp(limit, usage)
                }
            }
            if (prefs.userXpLastAwardDate != todayStr) {
                userPreferencesRepository.awardDailyXp(todayStr, xp, saved)
            }
            xp
        } catch (_: Exception) { 0 }
    }

    private fun resolveAppName(pkg: String): String {
        appNameCache[pkg]?.let { return it }
        return try {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val name = pm.getApplicationLabel(info).toString()
            appNameCache[pkg] = name
            name
        } catch (_: Exception) {
            if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(pkg)) {
                val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(pkg)
                com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
            } else pkg
        }
    }

    private fun dateStr(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))

    /** Widgets currently pinned to the launcher, counted per provider. */
    private fun placedWidgetCount(): Int {
        return try {
            val manager = appContext.getSystemService(Context.APPWIDGET_SERVICE)
                as android.appwidget.AppWidgetManager
            val pkg = appContext.packageName
            listOf(
                "GlobalStreakWidgetReceiver",
                "AppStreakWidgetReceiver",
                "TotalScreenTimeWidgetReceiver",
                "RemainingTargetWidgetReceiver",
                "PhoneFreeTimeWidgetReceiver"
            ).sumOf { cls ->
                manager.getAppWidgetIds(android.content.ComponentName(pkg, "$pkg.ui.widget.$cls")).size
            }
        } catch (_: Exception) { 0 }
    }

    /** Pause Point variant lists the user customized away from defaults. */
    private fun customVariantCount(
        prefs: com.etrisad.zenith.data.preferences.UserPreferences
    ): Int {
        val defaults = com.etrisad.zenith.ui.components.pausepoint.PausePointDefaults
        return listOf(
            prefs.pausePointWaitingVariants != defaults.waitingVariants,
            prefs.pausePointBreathingVariants != defaults.breathingVariants,
            prefs.pausePointWalkVariants != defaults.walkVariants,
            prefs.pausePointNumberSlideVariants != defaults.numberSlideVariants,
            prefs.pausePointSwitchVariants != defaults.switchVariants,
            prefs.pausePointMathVariants != defaults.mathVariants,
            prefs.pausePointCountingVariants != defaults.countingVariants,
            prefs.pausePointTypingVariants != defaults.typingVariants
        ).count { it }
    }

    /** Days since month start with total usage at or under the target. */
    private suspend fun underBudgetDaysThisMonth(todayStr: String): Int {
        return try {
            val target = userPreferencesRepository.userPreferencesFlow.first()
                .screenTimeTargetMinutes * 60_000L
            if (target <= 0L) return 0
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val monthStart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(cal.time)
            shieldRepository.getUsageBetween(monthStart, todayStr).first()
                .count { it.packageName == "TOTAL" && it.usageTimeMillis <= target }
        } catch (_: Exception) { 0 }
    }

    private suspend fun overdrawDaysThisMonth(todayStr: String): Int {
        return try {
            val target = userPreferencesRepository.userPreferencesFlow.first()
                .screenTimeTargetMinutes * 60_000L
            if (target <= 0L) return 0
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val monthStart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(cal.time)
            shieldRepository.getUsageBetween(monthStart, todayStr).first()
                .count { it.packageName == "TOTAL" && it.usageTimeMillis > target }
        } catch (_: Exception) { 0 }
    }

    private suspend fun weekendTrackedDays(): Int {
        return try {
            val dates = shieldRepository.getAllTrackedDates()
            dates.count { dateStr ->
                try {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        .parse(dateStr) ?: return@count false
                    val cal = java.util.Calendar.getInstance().apply { time = parsed }
                    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY
                } catch (_: Exception) { false }
            }
        } catch (_: Exception) { 0 }
    }

    /**
     * Day-gated counters for day-based achievements.
     *
     * Each id increments at most once per calendar day, and only when that
     * day's condition holds. Unknown ids are seeded once from the DB: if
     * today is already counted in the seeded value, today is marked counted
     * so it can't be counted twice; otherwise today stays uncounted so the
     * normal gate below counts it exactly once.
     *
     * @return id to counted days, for every id in [conditions].
     */
    private suspend fun refreshDailyCounts(
        todayStr: String,
        conditions: Map<String, Boolean>,
        dbCounts: Map<String, Int>,
        todayCounted: Map<String, Boolean>
    ): Map<String, Int> {
        return try {
            val stored = decodeDailyCounts(
                userPreferencesRepository.userPreferencesFlow.first().achievementDailyCounts
            ).toMutableMap()
            var changed = false
            conditions.keys.forEach { id ->
                val entry = stored[id]
                if (entry == null) {
                    val seedDate = if (todayCounted[id] == true) todayStr else ""
                    stored[id] = DailyCount(dbCounts[id] ?: 0, seedDate)
                    changed = true
                }
            }
            conditions.forEach { (id, qualifies) ->
                val entry = stored[id] ?: return@forEach
                if (qualifies && entry.lastDate != todayStr) {
                    stored[id] = entry.copy(count = entry.count + 1, lastDate = todayStr)
                    changed = true
                }
            }
            if (changed) {
                userPreferencesRepository.setAchievementDailyCounts(encodeDailyCounts(stored))
            }
            stored.filterKeys { it in conditions }.mapValues { it.value.count }
        } catch (_: Exception) {
            dbCounts
        }
    }

    /**
     * Records the first-observed date for every newly met achievement tier
     * so the detail sheet can show an unlock history.
     */
    private suspend fun recordUnlockDates(
        states: List<AchievementState>,
        todayStr: String
    ): Pair<List<AchievementState>, Set<String>> {
        return try {
            val prefsSnapshot = userPreferencesRepository.userPreferencesFlow.first()
            val rawBefore = prefsSnapshot.achievementHistory
            // Per-tier banner keys already shown, never enqueue the same one again.
            val seen = decodeSeen(prefsSnapshot.achievementBannersSeen).toMutableSet()
            var seenChanged = false
            // First run seeds the baseline silently so existing progress
            // never floods the unlock notification queue.
            val isBaseline = rawBefore.isBlank()
            val stored = decodeHistory(rawBefore)
                .mapValues { it.value.toMutableMap() }.toMutableMap()
            var changed = false
            val freshUnlocks = mutableListOf<PendingUnlock>()
            val freshBannerUnlocks = mutableListOf<ProfileBannerEvent.Unlock>()
            val newlyTieredIds = mutableSetOf<String>()
            // Snapshot before the loop so several tiers earned in the SAME
            // refresh still stack their banners; only later refreshes are gated.
            val seenAtStart = seen.toSet()
            val enriched = states.map { state ->
                val dates = stored.getOrPut(state.def.id) { mutableMapOf() }
                // One-time migration: legacy keys are roman-denomination values
                // (1, 5, 10, ...), new keys are 1-based positions. A stored map
                // is only migrated when it does NOT already look new: new maps
                // always hold exactly {1..k} (plus maybe negative generated
                // keys), while no legacy map can take that shape (there is no
                // denomination 2..4, 6..9, ...). Without this gate, a legit
                // new key like position 5 would be mistaken for tier V.
                val positives = dates.keys.filter { it > 0 }
                val looksNew = positives.isEmpty() ||
                    (positives.minOrNull() == 1 && positives.size == positives.maxOrNull())
                if (!looksNew) {
                    positives.toList().forEach { oldKey ->
                        val pos = state.def.thresholds.indexOfFirst { it.tier.value == oldKey } + 1
                        if (pos < 1 || oldKey == pos) return@forEach
                        val existing = dates[oldKey] ?: return@forEach
                        if (!dates.containsKey(pos)) {
                            dates[pos] = existing
                        }
                        dates.remove(oldKey)
                        changed = true
                    }
                }
                for (level in 1..state.earnedLevel) {
                    val key = historyKeyFor(state.def, level)
                    if (!dates.containsKey(key)) {
                        dates[key] = todayStr
                        changed = true
                        if (!isBaseline) {
                            newlyTieredIds.add(state.def.id)
                            freshUnlocks.add(
                                PendingUnlock(
                                    defId = state.def.id,
                                    tierLevel = level,
                                    tierValue = key,
                                    date = todayStr,
                                    prevLevel = level - 1
                                )
                            )
                            val unlockKey = unlockSeenKey(state.def.id, level)
                            if (unlockKey !in seenAtStart) {
                                if (seen.add(unlockKey)) seenChanged = true
                                freshBannerUnlocks.add(
                                    ProfileBannerEvent.Unlock(
                                        defId = state.def.id,
                                        tierLevel = level,
                                        tierValue = key,
                                        prevLevel = level - 1,
                                        date = todayStr
                                    )
                                )
                            }
                        }
                    }
                }
                state.copy(unlockedDates = dates.toMap())
            }
            if (changed) {
                userPreferencesRepository.setAchievementHistory(encodeHistory(stored))
            }
            if (seenChanged) {
                userPreferencesRepository.setAchievementBannersSeen(encodeSeen(seen))
            }
            if (freshUnlocks.isNotEmpty()) {
                _pendingUnlocks.update { it + freshUnlocks }
            }
            if (freshBannerUnlocks.isNotEmpty()) {
                _pendingBanners.update { it + freshBannerUnlocks }
            }
            enriched to newlyTieredIds
        } catch (_: Exception) {
            states to emptySet()
        }
    }

    /**
     * Quick progress banners for accumulation achievements: every value
     * increase emits one unless a tier-up banner already covers it.
     * Maxed achievements stay silent.
     */
    private suspend fun detectProgressEvents(
        states: List<AchievementState>,
        newlyTieredIds: Set<String>,
        todayStr: String
    ) {
        try {
            val prefsSnapshot = userPreferencesRepository.userPreferencesFlow.first()
            val lastVals = decodeLastValues(prefsSnapshot.achievementLastValues).toMutableMap()
            // Achievements whose banner already showed once ever stay silent.
            val seen = decodeSeen(prefsSnapshot.achievementBannersSeen).toMutableSet()
            var seenChanged = false
            var changed = false
            val events = mutableListOf<ProfileBannerEvent.Progress>()
            states.forEach { state ->
                if (state.def.category != AchievementCategory.ACCUMULATION) return@forEach
                if (state.next == null) {
                    if (lastVals[state.def.id] != state.current) {
                        lastVals[state.def.id] = state.current
                        changed = true
                    }
                    return@forEach
                }
                val last = lastVals[state.def.id]
                if (last == null || last != state.current) {
                    lastVals[state.def.id] = state.current
                    changed = true
                }
                val progressKey = progressSeenKey(state.def.id, state.earnedLevel)
                if (last != null && state.current > last && state.def.id !in newlyTieredIds &&
                    progressKey !in seen
                ) {
                    seen.add(progressKey)
                    seenChanged = true
                    events.add(
                        ProfileBannerEvent.Progress(
                            defId = state.def.id,
                            before = last,
                            after = state.current,
                            date = todayStr
                        )
                    )
                }
            }
            if (changed) {
                userPreferencesRepository.setAchievementLastValues(encodeLastValues(lastVals))
            }
            if (seenChanged) {
                userPreferencesRepository.setAchievementBannersSeen(encodeSeen(seen))
            }
            if (events.isNotEmpty()) {
                _pendingBanners.update { it + events }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        fun decodeSnapshot(raw: String): Map<String, Long> {
            if (raw.isBlank()) return emptyMap()
            return buildMap {
                raw.lines().forEach { line ->
                    val tab = line.lastIndexOf('\t')
                    if (tab <= 0) return@forEach
                    val pkg = line.substring(0, tab)
                    val total = line.substring(tab + 1).toLongOrNull() ?: return@forEach
                    if (pkg.isNotEmpty() && total > 0) put(pkg, total)
                }
            }
        }

        fun encodeSnapshot(map: Map<String, Long>): String =
            map.entries.sortedByDescending { it.value }
                .joinToString("\n") { "${it.key}\t${it.value}" }

        fun decodeHistory(raw: String): Map<String, Map<Int, String>> {
            if (raw.isBlank()) return emptyMap()
            return buildMap {
                raw.lines().forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size != 3) return@forEach
                    val tier = parts[1].toIntOrNull() ?: return@forEach
                    if (parts[0].isEmpty() || parts[2].isEmpty()) return@forEach
                    val dates = getOrPut(parts[0]) { mutableMapOf() } as MutableMap<Int, String>
                    dates[tier] = parts[2]
                }
            }
        }

        fun encodeHistory(map: Map<String, Map<Int, String>>): String =
            map.entries.sortedBy { it.key }.flatMap { (id, dates) ->
                dates.entries.sortedBy { it.key }.map { "$id\t${it.key}\t${it.value}" }
            }.joinToString("\n")

        fun decodeXpHistory(raw: String): List<XpDay> {
            if (raw.isBlank()) return emptyList()
            return raw.lines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return@mapNotNull null
                val xp = parts[1].toIntOrNull() ?: return@mapNotNull null
                val saved = parts[2].toLongOrNull() ?: return@mapNotNull null
                if (parts[0].isEmpty()) return@mapNotNull null
                XpDay(parts[0], xp, saved)
            }
        }

        fun decodeLastValues(raw: String): Map<String, Long> {
            if (raw.isBlank()) return emptyMap()
            return buildMap {
                raw.lines().forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size != 2) return@forEach
                    val value = parts[1].toLongOrNull() ?: return@forEach
                    if (parts[0].isNotEmpty()) put(parts[0], value)
                }
            }
        }

        fun encodeLastValues(map: Map<String, Long>): String =
            map.entries.sortedBy { it.key }
                .joinToString("\n") { "${it.key}\t${it.value}" }

        fun decodeSeen(raw: String): Set<String> =
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        fun encodeSeen(seen: Set<String>): String =
            seen.sorted().joinToString(",")

        fun decodeDailyCounts(raw: String): Map<String, DailyCount> {
            if (raw.isBlank()) return emptyMap()
            return buildMap {
                raw.lines().forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size != 3) return@forEach
                    val count = parts[1].toIntOrNull() ?: return@forEach
                    if (parts[0].isEmpty()) return@forEach
                    put(parts[0], DailyCount(count.coerceAtLeast(0), parts[2]))
                }
            }
        }

        fun encodeDailyCounts(map: Map<String, DailyCount>): String =
            map.entries.sortedBy { it.key }
                .joinToString("\n") { "${it.key}\t${it.value.count}\t${it.value.lastDate}" }
    }
}
