package com.etrisad.zenith.data.preferences

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private object StreakKeys {
    val GLOBAL_CURRENT_STREAK = intPreferencesKey("global_current_streak")
    val GLOBAL_BEST_STREAK = intPreferencesKey("global_best_streak")
    val GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP = longPreferencesKey("global_last_streak_update_timestamp")
}

class StreakCalculator(
    private val context: Context,
    private val userPreferencesFlow: Flow<UserPreferences>
) {

    private var lastSavedGlobalStreak: Triple<Int, Int, Long>? = null

    private suspend fun updateGlobalStreak(current: Int, best: Int, timestamp: Long) {
        if (lastSavedGlobalStreak == Triple(current, best, timestamp)) return
        context.runtimeDataStore.edit { preferences ->
            preferences[StreakKeys.GLOBAL_CURRENT_STREAK] = current
            preferences[StreakKeys.GLOBAL_BEST_STREAK] = best
            preferences[StreakKeys.GLOBAL_LAST_STREAK_UPDATE_TIMESTAMP] = timestamp
        }
        lastSavedGlobalStreak = Triple(current, best, timestamp)
    }
    private fun fetchSystemTotalUsageForDate(
        usm: UsageStatsManager,
        startTime: Long,
        launcherApps: Set<String>,
        excludePackages: Set<String>
    ): Long? {
        val stats = try {
            usm.queryAndAggregateUsageStats(startTime, startTime + 86400000L)
        } catch (e: Exception) {
            null
        } ?: return null
        if (stats.isEmpty()) return null
        var total = 0L
        var seen = false
        stats.forEach { (pkg, stat) ->
            if (pkg in launcherApps && pkg !in excludePackages) {
                total += stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                seen = true
            }
        }
        // No tracked package reported anything: indistinguishable from pruned
        // data, so report unknown instead of a fake 0-success.
        return if (seen) total else null
    }

    /**
     * System usage of one app for one day, or null when the OS reports no
     * entry. A missing entry is NOT proof of zero usage (pruned history,
     * permission gap), so callers must treat null as unknown and stop
     * counting, never as an automatic success.
     */
    private fun fetchSystemAppUsageForDate(
        usm: UsageStatsManager,
        packageName: String,
        startTime: Long
    ): Long? {
        val stats = try {
            usm.queryAndAggregateUsageStats(startTime, startTime + 86400000L)
        } catch (e: Exception) {
            null
        } ?: return null
        val stat = stats[packageName] ?: return null
        return stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
    }

    /**
     * Absolute creation-day bound ("yyyy-MM-dd"). Never null, never in the
     * future. Shields created before the timeAdded column existed (or restored
     * from backups without it) have timeAdded == 0, those fall back to the
     * last streak update, otherwise to today so such a shield can only ever
     * count today. Every streak loop must break at this bound: days before the
     * shield existed must never count, no matter what the DB or the OS
     * usage history claims.
     */
    private fun creationDateStr(
        timeAdded: Long,
        lastStreakUpdateTimestamp: Long,
        dateFormat: SimpleDateFormat,
        todayStr: String
    ): String {
        val anchor = when {
            timeAdded > 0L -> timeAdded
            lastStreakUpdateTimestamp > 0L -> lastStreakUpdateTimestamp
            else -> return todayStr
        }
        val s = try {
            dateFormat.format(Date(anchor))
        } catch (_: Exception) {
            return todayStr
        }
        // Clamp future-dated anchors (clock changes, restore anomalies).
        return if (s > todayStr) todayStr else s
    }

    /**
     * Resolves verified usage of one app for one past day.
     * Returns null when there is NO trustworthy evidence either way:
     * - no Zenith row for the package that day, AND
     * - no TOTAL row proving the tracker even ran that day, AND
     * - (optionally) no OS entry for the package.
     * Callers must treat null as "unknown" and stop the streak run, a missing
     * row alone is not proof of zero usage (fresh install, permission gap,
     * pruned DB), and assuming success is exactly what fabricated streaks.
     * The one safe inference: when the tracker provably ran (TOTAL row
     * exists) but recorded nothing for this package, SHIELD usage was 0.
     * For GOAL the same 0 is returned and the type comparison fails it, since
     * a goal needs affirmative usage to count.
     */
    private suspend fun resolvePastDayUsage(
        pkg: String,
        dStr: String,
        history: Map<String, Long>,
        totalCoveredDates: Set<String>,
        webHistory: Map<String, Long>?,
        usm: UsageStatsManager,
        dayStartMillis: Long,
        allowSystemFallback: Boolean
    ): Long? {
        history[dStr]?.let { return it }
        webHistory?.get(dStr)?.let { return it }
        if (dStr in totalCoveredDates) return 0L
        if (!allowSystemFallback) return null
        return withContext(Dispatchers.IO) {
            fetchSystemAppUsageForDate(usm, pkg, dayStartMillis)
        }
    }

    /**
     * Resolves a verified week total for weekly shields, or null when any day
     * of the week is unknown (see [resolvePastDayUsage]). A partially unknown
     * week must break the run instead of being counted as a success week.
     */
    private suspend fun resolvePastWeekUsage(
        pkg: String,
        weekStartMillis: Long,
        dateFormat: SimpleDateFormat,
        history: Map<String, Long>,
        totalCoveredDates: Set<String>,
        webHistory: Map<String, Long>?,
        usm: UsageStatsManager,
        allowSystemFallback: Boolean
    ): Long? {
        var total = 0L
        val cal = Calendar.getInstance()
        for (d in 0 until 7) {
            cal.timeInMillis = weekStartMillis + d * 86400000L
            val dayStart = cal.timeInMillis
            val dStr = dateFormat.format(cal.time)
            val dayUsage = resolvePastDayUsage(
                pkg, dStr, history, totalCoveredDates, webHistory,
                usm, dayStart, allowSystemFallback
            ) ?: return null
            total += dayUsage
        }
        return total
    }

    /** Max daily streak physically possible: active days in [creation, today]. */
    private fun maxPossibleDailyStreak(
        creationStr: String,
        todayStr: String,
        activeDays: Set<Int>,
        dateFormat: SimpleDateFormat
    ): Int {
        return try {
            var count = 0
            val cal = Calendar.getInstance()
            cal.time = dateFormat.parse(creationStr) ?: return 0
            val end = dateFormat.parse(todayStr) ?: return 0
            var guard = 0
            while (!cal.time.after(end) && guard++ < 3700) {
                if (cal.get(Calendar.DAY_OF_WEEK) in activeDays) count++
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    /** Max weekly streak physically possible: week-starts in [creation, today]. */
    private fun maxPossibleWeeklyStreak(
        creationStr: String,
        todayStr: String,
        dateFormat: SimpleDateFormat
    ): Int {
        return try {
            val start = dateFormat.parse(creationStr) ?: return 0
            val end = dateFormat.parse(todayStr) ?: return 0
            val cal = Calendar.getInstance().apply {
                time = start
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            var count = 0
            var guard = 0
            while (!cal.time.after(end) && guard++ < 600) {
                count++
                cal.add(Calendar.DAY_OF_YEAR, 7)
            }
            count.coerceAtLeast(1)
        } catch (_: Exception) {
            1
        }
    }

    suspend fun refreshGlobalStreak(shieldRepository: ShieldRepository): Pair<Int, Int> {
        val prefs = userPreferencesFlow.first()
        val targetMillis = prefs.screenTimeTargetMinutes * 60 * 1000L
        if (targetMillis <= 0) {
            updateGlobalStreak(0, prefs.globalBestStreak, System.currentTimeMillis())
            return Pair(0, prefs.globalBestStreak)
        }

        shieldRepository.isShieldsLoaded.first { it }
        // Wide enough that TOTAL coverage, not the query cap, decides how far
        // back a streak can be verified (~400 days of TOTAL rows).
        val dbUsage = shieldRepository.getAllUsage(10000).first()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // DB date keys are locale-independent; getDefault() can emit non-Latin
        // digits on some locales and silently break Room date matching.
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val (launcherApps, launcherPackage) = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
                val lPkg = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                apps to lPkg
            } catch (_: Exception) { emptySet<String>() to null }
        }

        val excludePackages = setOfNotNull(context.packageName, launcherPackage) + prefs.excludedFromTrackingPackages

        val todayStart = DateTimeUtils.getDayStartTime(now, prefs.dayStartHour, prefs.dayStartMinute)

        val totalToday = withContext(Dispatchers.IO) {
            val stats = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = prefs.dayStartHour, dayStartMinute = prefs.dayStartMinute).appUsageMap
            var total = 0L
            stats.forEach { (pkg, time) ->
                if (pkg !in excludePackages && pkg in launcherApps) total += time
            }
            total
        }

        val globalHistory = dbUsage.filter { it.packageName == "TOTAL" }
        val globalHistoryMap = globalHistory.associate { it.date to it.usageTimeMillis }
        val oldestHistoryDate = globalHistory.map { it.date }.minOrNull()
        val todayStr = dateFormat.format(Date(todayStart))

        // Pure recompute from verified history: every past day needs a TOTAL
        // row (tracker ran) or an OS entry within the 14-day retention window.
        // Unknown days STOP the run, they are never assumed to be successes.
        // This also makes the streak self-healing: a previously inflated value
        // is corrected downward on the next refresh instead of being ratcheted
        // upward forever by max(stored).
        var pastStreak = 0
        val c = Calendar.getInstance()
        val globalStreakLoopLimit = 730
        for (i in 1..globalStreakLoopLimit) {
            c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
            val dStr = dateFormat.format(c.time)
            var usage = globalHistoryMap[dStr]

            if (usage == null) {
                if (i <= 14) {
                    usage = withContext(Dispatchers.IO) {
                        fetchSystemTotalUsageForDate(usageStatsManager, c.timeInMillis, launcherApps, excludePackages)
                    }
                }
            }

            if (usage != null) {
                if (usage <= targetMillis) pastStreak++
                else break
            } else break
        }

        val isSuccessToday = totalToday <= targetMillis
        val liveStreak = if (isSuccessToday) pastStreak + 1 else 0

        // Best is recomputed from 0 over verified history so an inflated best
        // heals itself. Unknown days reset the running best-run (they cannot
        // prove an unbroken run) but do not abort the whole scan.
        var bestStreak = 0
        var tempStreak = 0
        val startDateStr = oldestHistoryDate ?: todayStr
        try {
            // Walk back from today so every day carries its day-start millis
            // for the OS fallback window.
            val days = ArrayDeque<Pair<String, Long>>()
            val back = Calendar.getInstance()
            back.timeInMillis = todayStart
            var guard = 0
            while (guard++ < 3700) {
                val dStr = dateFormat.format(back.time)
                days.addFirst(dStr to back.timeInMillis)
                if (dStr <= startDateStr) break
                back.add(Calendar.DAY_OF_YEAR, -1)
            }
            for ((dStr, dayStartMillis) in days) {
                val usage: Long? = if (dStr == todayStr) totalToday
                else globalHistoryMap[dStr]
                    ?: if (dayStartMillis >= todayStart - 14L * 86400000L) {
                        withContext(Dispatchers.IO) {
                            fetchSystemTotalUsageForDate(
                                usageStatsManager, dayStartMillis, launcherApps, excludePackages
                            )
                        }
                    } else null

                if (usage != null) {
                    if (usage <= targetMillis) {
                        tempStreak++
                        bestStreak = maxOf(bestStreak, tempStreak)
                    } else {
                        tempStreak = 0
                    }
                } else {
                    tempStreak = 0
                }
            }
        } catch (_: Exception) {}

        bestStreak = maxOf(bestStreak, liveStreak)
        updateGlobalStreak(liveStreak, bestStreak, now)
        return Pair(liveStreak, bestStreak)
    }

    suspend fun refreshAppStreaks(shieldRepository: ShieldRepository) {
        val prefs = userPreferencesFlow.first()
        shieldRepository.isShieldsLoaded.first { it }
        val shields = shieldRepository.allShields.first().filter { !it.isWebsite }
        val allUsage = shieldRepository.getAllUsage(10000).first()
        val usageByPackage = allUsage.groupBy { it.packageName }
            .mapValues { (_, rows) -> rows.associate { it.date to it.usageTimeMillis } }
        val totalCoveredDates = allUsage
            .filter { it.packageName == "TOTAL" }
            .map { it.date }.toSet()
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStart = DateTimeUtils.getDayStartTime(now, prefs.dayStartHour, prefs.dayStartMinute)
        val todayStr = dateFormat.format(Date(todayStart))

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val todayUsageMap = withContext(Dispatchers.IO) {
            com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = prefs.dayStartHour, dayStartMinute = prefs.dayStartMinute).appUsageMap
        }

        shields.forEach { shield ->
            val pkg = shield.packageName
            val history = usageByPackage[pkg] ?: emptyMap()
            // Absolute creation bound (never null): days before the shield
            // existed can never count, regardless of DB/OS history.
            val creationStr = creationDateStr(shield.timeAdded, shield.lastStreakUpdateTimestamp, dateFormat, todayStr)
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val isWeekly = shield.limitPeriod == LimitPeriod.WEEKLY

            if (limitMillis <= 0 && shield.type == FocusType.SHIELD) {
                shieldRepository.updateShield(shield.copy(currentStreak = 0, bestStreak = 0))
                return@forEach
            }

            val dailyTodayUsage = todayUsageMap[pkg] ?: 0L
            val todayUsage = if (isWeekly) {
                shieldRepository.getWeeklyUsageLive(pkg, dailyTodayUsage)
            } else {
                dailyTodayUsage
            }

            // Pure recompute from verified history: unknown days stop the run,
            // they are never assumed to be successes. Streaks are idempotent
            // (re-running refresh changes nothing) and self-healing (an
            // inflated stored value is corrected downward).
            var pastStreak = 0
            val todayCal = Calendar.getInstance()
            todayCal.timeInMillis = todayStart
            val isTodayActive = todayCal.get(Calendar.DAY_OF_WEEK) in shield.activeDays

            if (isWeekly) {
                val weekCal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                for (i in 1..105) {
                    weekCal.add(Calendar.DAY_OF_YEAR, -7)
                    val weekStartStr = dateFormat.format(weekCal.time)
                    if (weekStartStr < creationStr) break
                    val weekTotal = resolvePastWeekUsage(
                        pkg, weekCal.timeInMillis, dateFormat, history,
                        totalCoveredDates, null, usageStatsManager, i <= 2
                    )

                    if (weekTotal != null) {
                        val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                        if (success) pastStreak++ else break
                    } else break
                }
            } else {
                val c = Calendar.getInstance()
                for (i in 1..730) {
                    c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
                    val dayOfWeek = c.get(Calendar.DAY_OF_WEEK)
                    if (dayOfWeek !in shield.activeDays) continue
                    val dStr = dateFormat.format(c.time)
                    if (dStr < creationStr) break
                    val usage = resolvePastDayUsage(
                        pkg, dStr, history, totalCoveredDates, null,
                        usageStatsManager, c.timeInMillis, i <= 14
                    )

                    if (usage != null) {
                        val success = if (shield.type == FocusType.GOAL) usage >= limitMillis else usage <= limitMillis
                        if (success) pastStreak++ else break
                    } else break
                }
            }

            val isSuccessToday = if (!isTodayActive) {
                false
            } else if (shield.type == FocusType.GOAL) todayUsage >= limitMillis else todayUsage <= limitMillis

            if (isWeekly) {
                val thisMonday = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                // No max(stored) ratchet: current is purely past + today, so an
                // inflated value corrects itself on the next refresh.
                val rawCurrent = if (!isTodayActive) {
                    pastStreak
                } else if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) pastStreak + 1 else pastStreak
                } else {
                    if (isSuccessToday) pastStreak + 1 else 0
                }
                val currentStreak = rawCurrent.coerceAtMost(
                    maxPossibleWeeklyStreak(creationStr, todayStr, dateFormat)
                )

                // Recompute best from bounded history starting at 0 (not from the
                // stored value) so a previously inflated best self-heals.
                var bestStreak = 0
                var tempStreak = 0
                try {
                    val startD = dateFormat.parse(creationStr) ?: Date()
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    val bestWeekCal = Calendar.getInstance().apply {
                        time = startD
                        firstDayOfWeek = Calendar.MONDAY
                        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    }

                    var bestGuard = 0
                    while (!bestWeekCal.time.after(todayDate) && bestGuard++ < 160) {
                        val weekStartStr = dateFormat.format(bestWeekCal.time)

                        val weekTotal: Long? = if (weekStartStr == dateFormat.format(thisMonday)) {
                            todayUsage
                        } else {
                            resolvePastWeekUsage(
                                pkg, bestWeekCal.timeInMillis, dateFormat, history,
                                totalCoveredDates, null, usageStatsManager, false
                            )
                        }

                        if (weekTotal != null) {
                            val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                            if (success) {
                                tempStreak++
                                bestStreak = maxOf(bestStreak, tempStreak)
                            } else {
                                tempStreak = 0
                            }
                        } else {
                            // Unknown week cannot prove an unbroken run.
                            tempStreak = 0
                        }
                        bestWeekCal.add(Calendar.DAY_OF_YEAR, 7)
                    }
                } catch (_: Exception) {}

                shieldRepository.updateShield(shield.copy(
                    currentStreak = currentStreak,
                    bestStreak = maxOf(bestStreak, currentStreak),
                    remainingTimeMillis = (limitMillis - todayUsage).coerceAtLeast(0L),
                    lastStreakUpdateTimestamp = if (isTodayActive && isSuccessToday) now else shield.lastStreakUpdateTimestamp
                ))
            } else {
                // Pure recompute: no continuity ratchet on the stored value.
                val rawCurrent = if (!isTodayActive) {
                    pastStreak
                } else if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) pastStreak + 1 else pastStreak
                } else {
                    if (isSuccessToday) pastStreak + 1 else 0
                }
                val currentStreak = rawCurrent.coerceAtMost(
                    maxPossibleDailyStreak(creationStr, todayStr, shield.activeDays, dateFormat)
                )

                // Best recomputed from 0 over verified evidence so an inflated
                // best self-heals. Unknown days reset the running best-run.
                var bestStreak = 0
                var tempStreak = 0
                val calendarForBest = Calendar.getInstance()
                try {
                    val startD = dateFormat.parse(creationStr) ?: Date()
                    calendarForBest.time = startD
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    var bestGuard = 0
                    while (!calendarForBest.time.after(todayDate) && bestGuard++ < 3700) {
                        val dayOfWeek = calendarForBest.get(Calendar.DAY_OF_WEEK)
                        if (dayOfWeek !in shield.activeDays) {
                            calendarForBest.add(Calendar.DAY_OF_YEAR, 1)
                            continue
                        }
                        val dStr = dateFormat.format(calendarForBest.time)
                        val effectiveUsage: Long? = if (dStr == todayStr) todayUsage else resolvePastDayUsage(
                            pkg, dStr, history, totalCoveredDates, null,
                            usageStatsManager, calendarForBest.timeInMillis,
                            (todayStart - calendarForBest.timeInMillis) <= 14L * 86400000L
                        )
                        if (effectiveUsage != null) {
                            val success = if (shield.type == FocusType.GOAL) effectiveUsage >= limitMillis else effectiveUsage <= limitMillis
                            if (success) {
                                tempStreak++
                                bestStreak = maxOf(bestStreak, tempStreak)
                            } else {
                                tempStreak = 0
                            }
                        } else {
                            tempStreak = 0
                        }
                        calendarForBest.add(Calendar.DAY_OF_YEAR, 1)
                    }
                } catch (_: Exception) {}

                shieldRepository.updateShield(shield.copy(
                    currentStreak = currentStreak,
                    bestStreak = maxOf(bestStreak, currentStreak),
                    remainingTimeMillis = (limitMillis - todayUsage).coerceAtLeast(0L),
                    lastStreakUpdateTimestamp = if (isTodayActive && isSuccessToday) now else shield.lastStreakUpdateTimestamp
                ))
            }
        }
    }

    suspend fun refreshWebStreaks(shieldRepository: ShieldRepository) {
        val prefs = userPreferencesFlow.first()
        shieldRepository.isShieldsLoaded.first { it }
        val shields = shieldRepository.allShields.first().filter { it.isWebsite }
        val allUsage = shieldRepository.getAllUsage(10000).first()
        val usageByPackage = allUsage.groupBy { it.packageName }
            .mapValues { (_, rows) -> rows.associate { it.date to it.usageTimeMillis } }
        val totalCoveredDates = allUsage
            .filter { it.packageName == "TOTAL" }
            .map { it.date }.toSet()
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStart = DateTimeUtils.getDayStartTime(now, prefs.dayStartHour, prefs.dayStartMinute)
        val todayStr = dateFormat.format(Date(todayStart))

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        shields.forEach { shield ->
            val pkg = shield.packageName
            val history = usageByPackage[pkg] ?: emptyMap()
            // Absolute creation bound (never null, never future).
            val creationStr = creationDateStr(shield.timeAdded, shield.lastStreakUpdateTimestamp, dateFormat, todayStr)
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val isWeekly = shield.limitPeriod == LimitPeriod.WEEKLY

            if (limitMillis <= 0 && shield.type == FocusType.SHIELD) {
                shieldRepository.updateShield(shield.copy(currentStreak = 0, bestStreak = 0))
                return@forEach
            }

            // Website past-day evidence lives in the website_usage table, so
            // preload it once per shield instead of querying per day.
            val domain = WebsiteRepository.extractDomainFromPackageName(pkg)
            val webHistory = try {
                shieldRepository.getWebsiteUsageForDomain(domain).first()
                    .associate { it.date to it.usageTimeMillis }
            } catch (_: Exception) {
                emptyMap()
            }

            // Website rows are keyed by wall-clock date while streak days use
            // the day-start boundary, so check both keys for today.
            val wallClockTodayStr = dateFormat.format(Date(now))
            val dailyTodayUsage = webHistory[todayStr]
                ?: webHistory[wallClockTodayStr]
                ?: 0L
            val todayUsage = if (isWeekly) {
                shieldRepository.getWeeklyUsageLive(pkg, dailyTodayUsage)
            } else {
                dailyTodayUsage
            }

            var pastStreak = 0

            val todayCal = Calendar.getInstance()
            todayCal.timeInMillis = todayStart
            val isTodayActive = todayCal.get(Calendar.DAY_OF_WEEK) in shield.activeDays

            if (isWeekly) {
                val weekCal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                for (i in 1..105) {
                    weekCal.add(Calendar.DAY_OF_YEAR, -7)
                    val weekStartStr = dateFormat.format(weekCal.time)
                    if (weekStartStr < creationStr) break
                    val weekTotal = resolvePastWeekUsage(
                        pkg, weekCal.timeInMillis, dateFormat, history,
                        totalCoveredDates, webHistory, usageStatsManager, false
                    )

                    if (weekTotal != null) {
                        val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                        if (success) pastStreak++ else break
                    } else break
                }
            } else {
                val c = Calendar.getInstance()
                for (i in 1..730) {
                    c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
                    val dayOfWeek = c.get(Calendar.DAY_OF_WEEK)
                    if (dayOfWeek !in shield.activeDays) continue
                    val dStr = dateFormat.format(c.time)
                    if (dStr < creationStr) break
                    val usage = resolvePastDayUsage(
                        pkg, dStr, history, totalCoveredDates, webHistory,
                        usageStatsManager, c.timeInMillis, false
                    )

                    if (usage != null) {
                        val success = if (shield.type == FocusType.GOAL) usage >= limitMillis else usage <= limitMillis
                        if (success) pastStreak++ else break
                    } else break
                }
            }

            val isSuccessToday = if (!isTodayActive) {
                false
            } else if (creationStr == todayStr && todayUsage == 0L) {
                // Creation day with no visit yet: not a success, not a failure.
                false
            } else if (shield.type == FocusType.GOAL) todayUsage >= limitMillis else todayUsage <= limitMillis

            if (isWeekly) {
                val thisMonday = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val rawCurrent = if (!isTodayActive) {
                    pastStreak
                } else if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) pastStreak + 1 else pastStreak
                } else {
                    if (isSuccessToday) pastStreak + 1 else 0
                }
                val currentStreak = rawCurrent.coerceAtMost(
                    maxPossibleWeeklyStreak(creationStr, todayStr, dateFormat)
                )

                // Best recomputed from 0 over verified evidence (previously it
                // started from the stored value, so an inflated best could
                // never heal).
                var bestStreak = 0
                var tempStreak = 0
                try {
                    val startD = dateFormat.parse(creationStr) ?: Date()
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    val bestWeekCal = Calendar.getInstance().apply {
                        time = startD
                        firstDayOfWeek = Calendar.MONDAY
                        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    }

                    var bestGuard = 0
                    while (!bestWeekCal.time.after(todayDate) && bestGuard++ < 160) {
                        val weekStartStr = dateFormat.format(bestWeekCal.time)

                        val weekTotal: Long? = if (weekStartStr == dateFormat.format(thisMonday)) {
                            todayUsage
                        } else {
                            resolvePastWeekUsage(
                                pkg, bestWeekCal.timeInMillis, dateFormat, history,
                                totalCoveredDates, webHistory, usageStatsManager, false
                            )
                        }

                        if (weekTotal != null) {
                            val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                            if (success) {
                                tempStreak++
                                bestStreak = maxOf(bestStreak, tempStreak)
                            } else {
                                tempStreak = 0
                            }
                        } else {
                            tempStreak = 0
                        }
                        bestWeekCal.add(Calendar.DAY_OF_YEAR, 7)
                    }
                } catch (_: Exception) {}

                shieldRepository.updateShield(shield.copy(
                    currentStreak = currentStreak,
                    bestStreak = maxOf(bestStreak, currentStreak),
                    remainingTimeMillis = (limitMillis - todayUsage).coerceAtLeast(0L),
                    lastStreakUpdateTimestamp = if (isTodayActive && isSuccessToday) now else shield.lastStreakUpdateTimestamp
                ))
            } else {
                val rawCurrent = if (!isTodayActive) {
                    pastStreak
                } else if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) pastStreak + 1 else pastStreak
                } else {
                    if (isSuccessToday) pastStreak + 1 else 0
                }
                val currentStreak = rawCurrent.coerceAtMost(
                    maxPossibleDailyStreak(creationStr, todayStr, shield.activeDays, dateFormat)
                )

                var bestStreak = 0
                var tempStreak = 0
                val calendarForBest = Calendar.getInstance()
                try {
                    val startD = dateFormat.parse(creationStr) ?: Date()
                    calendarForBest.time = startD
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    var bestGuard = 0
                    while (!calendarForBest.time.after(todayDate) && bestGuard++ < 3700) {
                        val dayOfWeek = calendarForBest.get(Calendar.DAY_OF_WEEK)
                        if (dayOfWeek !in shield.activeDays) {
                            calendarForBest.add(Calendar.DAY_OF_YEAR, 1)
                            continue
                        }
                        val dStr = dateFormat.format(calendarForBest.time)
                        val effectiveUsage: Long? = if (dStr == todayStr) todayUsage else resolvePastDayUsage(
                            pkg, dStr, history, totalCoveredDates, webHistory,
                            usageStatsManager, calendarForBest.timeInMillis, false
                        )
                        if (effectiveUsage != null) {
                            val success = if (shield.type == FocusType.GOAL) effectiveUsage >= limitMillis else effectiveUsage <= limitMillis
                            if (success) {
                                tempStreak++
                                bestStreak = maxOf(bestStreak, tempStreak)
                            } else {
                                tempStreak = 0
                            }
                        } else {
                            tempStreak = 0
                        }
                        calendarForBest.add(Calendar.DAY_OF_YEAR, 1)
                    }
                } catch (_: Exception) {}

                shieldRepository.updateShield(shield.copy(
                    currentStreak = currentStreak,
                    bestStreak = maxOf(bestStreak, currentStreak),
                    remainingTimeMillis = (limitMillis - todayUsage).coerceAtLeast(0L),
                    lastStreakUpdateTimestamp = if (isTodayActive && isSuccessToday) now else shield.lastStreakUpdateTimestamp
                ))
            }
        }
    }

    suspend fun refreshAllAppStreaks(shieldRepository: ShieldRepository) {
        refreshAppStreaks(shieldRepository)
        refreshWebStreaks(shieldRepository)
    }

    /**
     * Manual "verify and restore" action from developer settings. This now
     * simply forces the same pure recompute as the automatic refresh: every
     * value written is backed by verified evidence (DB rows within the shield
     * lifetime, or OS entries inside the retention window), so running it can
     * never fabricate a streak, it only re-verifies and heals the state.
     */
    suspend fun runManualStreakRecovery(shieldRepository: ShieldRepository) {
        refreshGlobalStreak(shieldRepository)
        refreshAllAppStreaks(shieldRepository)
    }
}
