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
    ): Long {
        val stats = try {
            usm.queryAndAggregateUsageStats(startTime, startTime + 86400000L)
        } catch (e: Exception) {
            null
        }
        var total = 0L
        stats?.forEach { (pkg, stat) ->
            if (pkg in launcherApps && pkg !in excludePackages) {
                total += stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            }
        }
        return total
    }

    private fun fetchSystemAppUsageForDate(
        usm: UsageStatsManager,
        packageName: String,
        startTime: Long
    ): Long {
        val stats = try {
            usm.queryAndAggregateUsageStats(startTime, startTime + 86400000L)
        } catch (e: Exception) {
            null
        }
        val stat = stats?.get(packageName) ?: return 0L
        return stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
    }

    suspend fun refreshGlobalStreak(shieldRepository: ShieldRepository): Pair<Int, Int> {
        val prefs = userPreferencesFlow.first()
        val targetMillis = prefs.screenTimeTargetMinutes * 60 * 1000L
        if (targetMillis <= 0) {
            updateGlobalStreak(0, prefs.globalBestStreak, System.currentTimeMillis())
            return Pair(0, prefs.globalBestStreak)
        }

        shieldRepository.isShieldsLoaded.first { it }
        val dbUsage = shieldRepository.getAllUsage().first()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val (launcherApps, launcherPackage) = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
                val lPkg = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                apps to lPkg
            } catch (_: Exception) { emptySet<String>() to null }
        }

        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

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
        val oldestHistoryDate = globalHistory.map { it.date }.minOrNull()

        var pastStreak = 0
        var foundDefiniteFailure = false
        val c = Calendar.getInstance()
        val globalStreakLoopLimit = (prefs.globalCurrentStreak + 30).coerceAtMost(90)
        for (i in 1..globalStreakLoopLimit) {
            c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
            val dStr = dateFormat.format(c.time)
            var usage = globalHistory.find { it.date == dStr }?.usageTimeMillis

            if (usage == null) {
                if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                    usage = 0L
                } else if (i <= 14) {
                    usage = withContext(Dispatchers.IO) {
                        fetchSystemTotalUsageForDate(usageStatsManager, c.timeInMillis, launcherApps, excludePackages)
                    }
                }
            }

            if (usage != null) {
                if (usage <= targetMillis) pastStreak++
                else { foundDefiniteFailure = true; break }
            } else break
        }

        val lastUpdateDayStart = DateTimeUtils.getDayStartTime(prefs.globalLastStreakUpdateTimestamp, prefs.dayStartHour, prefs.dayStartMinute)
        val yesterdayStart = DateTimeUtils.getDayStartTime(todayStart - 1, prefs.dayStartHour, prefs.dayStartMinute)
        val isLastUpdateYesterday = lastUpdateDayStart == yesterdayStart
        val isLastUpdateToday = lastUpdateDayStart == todayStart

        val isSuccessToday = totalToday <= targetMillis
        val liveStreak = if (isSuccessToday) {
            if (!foundDefiniteFailure && (isLastUpdateYesterday || isLastUpdateToday)) {
                maxOf(pastStreak + 1, prefs.globalCurrentStreak + (if (isLastUpdateYesterday) 1 else 0))
            } else {
                pastStreak + 1
            }
        } else 0

        var bestStreak = prefs.globalBestStreak
        var tempStreak = 0
        val todayStr = dateFormat.format(Date(todayStart))
        val startDateStr = oldestHistoryDate ?: todayStr
        val calendarForBest = Calendar.getInstance()
        try {
            val startD = dateFormat.parse(startDateStr) ?: Date()
            calendarForBest.time = startD
            val todayDate = dateFormat.parse(todayStr) ?: Date()

            while (!calendarForBest.time.after(todayDate)) {
                val dStr = dateFormat.format(calendarForBest.time)
                val usage = if (dStr == todayStr) totalToday else globalHistory.find { it.date == dStr }?.usageTimeMillis

                val effectiveUsage = usage ?: 0L
                if (effectiveUsage <= targetMillis) {
                    tempStreak++
                    bestStreak = maxOf(bestStreak, tempStreak)
                } else {
                    tempStreak = 0
                }
                calendarForBest.add(Calendar.DAY_OF_YEAR, 1)
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
        val allUsage = shieldRepository.getAllUsage().first().groupBy { it.packageName }
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date(now))

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val todayUsageMap = withContext(Dispatchers.IO) {
            com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = prefs.dayStartHour, dayStartMinute = prefs.dayStartMinute).appUsageMap
        }

        shields.forEach { shield ->
            val pkg = shield.packageName
            val history = allUsage[pkg] ?: emptyList()
            val oldestHistoryDate = history.map { it.date }.minOrNull()
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

            var pastStreak = 0
            var foundDefiniteFailure = false
            val todayStart = DateTimeUtils.getDayStartTime(now, prefs.dayStartHour, prefs.dayStartMinute)

            val shieldStreakLimit = (shield.currentStreak + 30).coerceAtMost(90)

            if (isWeekly) {
                val weekCal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                for (i in 1..shieldStreakLimit) {
                    weekCal.add(Calendar.DAY_OF_YEAR, -7)
                    if (shield.lastStreakUpdateTimestamp == 0L && shield.currentStreak == 0) break
                    val weekStartStr = dateFormat.format(weekCal.time)
                    val weekEnd = Calendar.getInstance().apply { timeInMillis = weekCal.timeInMillis; add(Calendar.DAY_OF_YEAR, 6) }
                    val weekEndStr = dateFormat.format(weekEnd.time)
                    var weekTotal = history.filter { it.date >= weekStartStr && it.date <= weekEndStr }.sumOf { it.usageTimeMillis }

                    if (weekTotal == 0L && oldestHistoryDate != null && weekStartStr >= oldestHistoryDate) {
                        if (shield.type == FocusType.SHIELD) weekTotal = 0L else { foundDefiniteFailure = true; break }
                    } else if (weekTotal == 0L && i <= 2) {
                        var sysTotal = 0L
                        val sysCal = Calendar.getInstance().apply { timeInMillis = weekCal.timeInMillis }
                        for (d in 0 until 7) {
                            val dayUsage = withContext(Dispatchers.IO) {
                                fetchSystemAppUsageForDate(usageStatsManager, pkg, sysCal.timeInMillis)
                            }
                            if (dayUsage != null) sysTotal += dayUsage
                            sysCal.add(Calendar.DAY_OF_YEAR, 1)
                        }
                        if (sysTotal > 0) weekTotal = sysTotal
                    }

                    val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                    if (success) pastStreak++ else { foundDefiniteFailure = true; break }
                }
            } else {
                val c = Calendar.getInstance()
                for (i in 1..shieldStreakLimit) {
                    c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
                    if (shield.lastStreakUpdateTimestamp == 0L && shield.currentStreak == 0) break
                    val dStr = dateFormat.format(c.time)
                    var usage = history.find { it.date == dStr }?.usageTimeMillis

                    if (usage == null) {
                        if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                            if (shield.type == FocusType.SHIELD) usage = 0L else break
                        } else if (i <= 14) {
                            usage = withContext(Dispatchers.IO) {
                                fetchSystemAppUsageForDate(usageStatsManager, pkg, c.timeInMillis)
                            }
                        }
                    }

                    if (usage != null) {
                        val success = if (shield.type == FocusType.GOAL) usage >= limitMillis else usage <= limitMillis
                        if (success) pastStreak++ else { foundDefiniteFailure = true; break }
                    } else break
                }
            }

            val isSuccessToday = if (shield.type == FocusType.GOAL) todayUsage >= limitMillis else todayUsage <= limitMillis

            if (isWeekly) {
                val thisMonday = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val lastUpdateMonday = Calendar.getInstance().apply {
                    timeInMillis = shield.lastStreakUpdateTimestamp
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val isLastUpdateThisWeek = lastUpdateMonday == thisMonday
                val isLastUpdateLastWeek = lastUpdateMonday == thisMonday - 7L * 24 * 60 * 60 * 1000L

                val currentStreak = if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) {
                        if (isLastUpdateLastWeek || isLastUpdateThisWeek) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateLastWeek) 1 else 0))
                        } else pastStreak + 1
                    } else pastStreak
                } else {
                    if (isSuccessToday) {
                        if (isLastUpdateLastWeek || isLastUpdateThisWeek) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateLastWeek) 1 else 0))
                        } else pastStreak + 1
                    } else 0
                }

                var bestStreak = shield.bestStreak
                var tempStreak = 0
                try {
                    val startDateStr = oldestHistoryDate ?: todayStr
                    val startD = dateFormat.parse(startDateStr) ?: Date()
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    val bestWeekCal = Calendar.getInstance().apply {
                        time = startD
                        firstDayOfWeek = Calendar.MONDAY
                        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    }

                    while (!bestWeekCal.time.after(todayDate)) {
                        val weekStartStr = dateFormat.format(bestWeekCal.time)
                        val weekEndBest = Calendar.getInstance().apply { timeInMillis = bestWeekCal.timeInMillis; add(Calendar.DAY_OF_YEAR, 6) }
                        val weekEndStr = dateFormat.format(weekEndBest.time)

                        val weekTotal = if (weekStartStr == dateFormat.format(thisMonday)) {
                            todayUsage
                        } else {
                            history.filter { it.date >= weekStartStr && it.date <= weekEndStr }.sumOf { it.usageTimeMillis }
                        }

                        val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                        if (success) {
                            tempStreak++
                            bestStreak = maxOf(bestStreak, tempStreak)
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
                    lastStreakUpdateTimestamp = if (isSuccessToday && (shield.type == FocusType.GOAL || todayUsage > 0)) now else shield.lastStreakUpdateTimestamp
                ))
            } else {
                val lastUpdateDayStart = DateTimeUtils.getDayStartTime(shield.lastStreakUpdateTimestamp, prefs.dayStartHour, prefs.dayStartMinute)
                val yesterdayStart = DateTimeUtils.getDayStartTime(todayStart - 1, prefs.dayStartHour, prefs.dayStartMinute)
                val isLastUpdateYesterday = lastUpdateDayStart == yesterdayStart
                val isLastUpdateToday = lastUpdateDayStart == todayStart

                val currentStreak = if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) {
                        if (isLastUpdateYesterday || isLastUpdateToday) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateYesterday) 1 else 0))
                        } else pastStreak + 1
                    } else pastStreak
                } else {
                    if (isSuccessToday) {
                        if (isLastUpdateYesterday || isLastUpdateToday) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateYesterday) 1 else 0))
                        } else pastStreak + 1
                    } else 0
                }

                var bestStreak = shield.bestStreak
                var tempStreak = 0
                val calendarForBest = Calendar.getInstance()
                try {
                    val startDateStr = oldestHistoryDate ?: todayStr
                    val startD = dateFormat.parse(startDateStr) ?: Date()
                    calendarForBest.time = startD
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    while (!calendarForBest.time.after(todayDate)) {
                        val dStr = dateFormat.format(calendarForBest.time)
                        val usage = if (dStr == todayStr) todayUsage else history.find { it.date == dStr }?.usageTimeMillis

                        val effectiveUsage = if (usage == null && shield.type == FocusType.SHIELD) 0L else usage
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
                    lastStreakUpdateTimestamp = if (isSuccessToday && (shield.type == FocusType.GOAL || todayUsage > 0)) now else shield.lastStreakUpdateTimestamp
                ))
            }
        }
    }

    suspend fun refreshWebStreaks(shieldRepository: ShieldRepository) {
        val prefs = userPreferencesFlow.first()
        shieldRepository.isShieldsLoaded.first { it }
        val shields = shieldRepository.allShields.first().filter { it.isWebsite }
        val allUsage = shieldRepository.getAllUsage().first().groupBy { it.packageName }
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date(now))

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val todayUsageMap = withContext(Dispatchers.IO) {
            com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = prefs.dayStartHour, dayStartMinute = prefs.dayStartMinute).appUsageMap
        }

        shields.forEach { shield ->
            val pkg = shield.packageName
            val history = allUsage[pkg] ?: emptyList()
            val oldestHistoryDate = history.map { it.date }.minOrNull()
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val isWeekly = shield.limitPeriod == LimitPeriod.WEEKLY
            val timeAddedDateStr = dateFormat.format(Date(shield.timeAdded))

            if (limitMillis <= 0 && shield.type == FocusType.SHIELD) {
                shieldRepository.updateShield(shield.copy(currentStreak = 0, bestStreak = 0))
                return@forEach
            }

            val dailyTodayUsage = if (timeAddedDateStr != null) {
                val domain = WebsiteRepository.extractDomainFromPackageName(pkg)
                shieldRepository.getWebsiteUsage(todayStr, domain)?.usageTimeMillis ?: 0L
            } else {
                todayUsageMap[pkg] ?: 0L
            }
            val todayUsage = if (isWeekly) {
                shieldRepository.getWeeklyUsageLive(pkg, dailyTodayUsage)
            } else {
                dailyTodayUsage
            }

            var pastStreak = 0
            var foundDefiniteFailure = false
            val todayStart = DateTimeUtils.getDayStartTime(now, prefs.dayStartHour, prefs.dayStartMinute)

            val shieldStreakLimit = (shield.currentStreak + 30).coerceAtMost(90)

            if (isWeekly) {
                val weekCal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                for (i in 1..shieldStreakLimit) {
                    weekCal.add(Calendar.DAY_OF_YEAR, -7)
                    if (shield.lastStreakUpdateTimestamp == 0L && shield.currentStreak == 0) break
                    val weekStartStr = dateFormat.format(weekCal.time)
                    if (timeAddedDateStr != null && weekStartStr.compareTo(timeAddedDateStr) < 0) break
                    val weekEnd = Calendar.getInstance().apply { timeInMillis = weekCal.timeInMillis; add(Calendar.DAY_OF_YEAR, 6) }
                    val weekEndStr = dateFormat.format(weekEnd.time)
                    var weekTotal = history.filter { it.date >= weekStartStr && it.date <= weekEndStr }.sumOf { it.usageTimeMillis }

                    if (weekTotal == 0L && oldestHistoryDate != null && weekStartStr >= oldestHistoryDate) {
                        if (shield.type == FocusType.SHIELD) weekTotal = 0L else { foundDefiniteFailure = true; break }
                    } else if (weekTotal == 0L && i <= 2) {
                        var sysTotal = 0L
                        val sysCal = Calendar.getInstance().apply { timeInMillis = weekCal.timeInMillis }
                        for (d in 0 until 7) {
                            val dayUsage = withContext(Dispatchers.IO) {
                                fetchSystemAppUsageForDate(usageStatsManager, pkg, sysCal.timeInMillis)
                            }
                            if (dayUsage != null) sysTotal += dayUsage
                            sysCal.add(Calendar.DAY_OF_YEAR, 1)
                        }
                        if (sysTotal > 0) weekTotal = sysTotal
                    }

                    val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                    if (success) pastStreak++ else { foundDefiniteFailure = true; break }
                }
            } else {
                val c = Calendar.getInstance()
                for (i in 1..shieldStreakLimit) {
                    c.timeInMillis = todayStart; c.add(Calendar.DAY_OF_YEAR, -i)
                    if (shield.lastStreakUpdateTimestamp == 0L && shield.currentStreak == 0) break
                    val dStr = dateFormat.format(c.time)
                    if (timeAddedDateStr != null && dStr.compareTo(timeAddedDateStr) < 0) break
                    var usage = history.find { it.date == dStr }?.usageTimeMillis

                    if (usage == null) {
                        if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                            if (shield.type == FocusType.SHIELD) usage = 0L else break
                        } else if (i <= 14) {
                            usage = withContext(Dispatchers.IO) {
                                fetchSystemAppUsageForDate(usageStatsManager, pkg, c.timeInMillis)
                            }
                        }
                    }

                    if (usage != null) {
                        val success = if (shield.type == FocusType.GOAL) usage >= limitMillis else usage <= limitMillis
                        if (success) pastStreak++ else { foundDefiniteFailure = true; break }
                    } else break
                }
            }

            val isSuccessToday = if (timeAddedDateStr == todayStr && todayUsage == 0L) {
                false
            } else if (shield.type == FocusType.GOAL) todayUsage >= limitMillis else todayUsage <= limitMillis

            if (isWeekly) {
                val thisMonday = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val lastUpdateMonday = Calendar.getInstance().apply {
                    timeInMillis = shield.lastStreakUpdateTimestamp
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val isLastUpdateThisWeek = lastUpdateMonday == thisMonday
                val isLastUpdateLastWeek = lastUpdateMonday == thisMonday - 7L * 24 * 60 * 60 * 1000L

                val currentStreak = if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) {
                        if (isLastUpdateLastWeek || isLastUpdateThisWeek) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateLastWeek) 1 else 0))
                        } else pastStreak + 1
                    } else pastStreak
                } else {
                    if (isSuccessToday) {
                        if (isLastUpdateLastWeek || isLastUpdateThisWeek) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateLastWeek) 1 else 0))
                        } else pastStreak + 1
                    } else 0
                }

                var bestStreak = shield.bestStreak
                var tempStreak = 0
                try {
                    val startDateStr = if (timeAddedDateStr != null) timeAddedDateStr else (oldestHistoryDate ?: todayStr)
                    val startD = dateFormat.parse(startDateStr) ?: Date()
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    val bestWeekCal = Calendar.getInstance().apply {
                        time = startD
                        firstDayOfWeek = Calendar.MONDAY
                        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    }

                    while (!bestWeekCal.time.after(todayDate)) {
                        val weekStartStr = dateFormat.format(bestWeekCal.time)
                        val weekEndBest = Calendar.getInstance().apply { timeInMillis = bestWeekCal.timeInMillis; add(Calendar.DAY_OF_YEAR, 6) }
                        val weekEndStr = dateFormat.format(weekEndBest.time)

                        val weekTotal = if (weekStartStr == dateFormat.format(thisMonday)) {
                            todayUsage
                        } else {
                            history.filter { it.date >= weekStartStr && it.date <= weekEndStr }.sumOf { it.usageTimeMillis }
                        }

                        val success = if (shield.type == FocusType.GOAL) weekTotal >= limitMillis else weekTotal <= limitMillis
                        if (success) {
                            tempStreak++
                            bestStreak = maxOf(bestStreak, tempStreak)
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
                    lastStreakUpdateTimestamp = if (isSuccessToday && (shield.type == FocusType.GOAL || todayUsage > 0)) now else shield.lastStreakUpdateTimestamp
                ))
            } else {
                val lastUpdateDayStart = DateTimeUtils.getDayStartTime(shield.lastStreakUpdateTimestamp, prefs.dayStartHour, prefs.dayStartMinute)
                val yesterdayStart = DateTimeUtils.getDayStartTime(todayStart - 1, prefs.dayStartHour, prefs.dayStartMinute)
                val isLastUpdateYesterday = lastUpdateDayStart == yesterdayStart
                val isLastUpdateToday = lastUpdateDayStart == todayStart

                val currentStreak = if (shield.type == FocusType.GOAL) {
                    if (isSuccessToday) {
                        if (isLastUpdateYesterday || isLastUpdateToday) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateYesterday) 1 else 0))
                        } else pastStreak + 1
                    } else pastStreak
                } else {
                    if (isSuccessToday) {
                        if (isLastUpdateYesterday || isLastUpdateToday) {
                            maxOf(pastStreak + 1, shield.currentStreak + (if (isLastUpdateYesterday) 1 else 0))
                        } else pastStreak + 1
                    } else 0
                }

                var bestStreak = shield.bestStreak
                var tempStreak = 0
                val calendarForBest = Calendar.getInstance()
                try {
                    val startDateStr = if (timeAddedDateStr != null) timeAddedDateStr else (oldestHistoryDate ?: todayStr)
                    val startD = dateFormat.parse(startDateStr) ?: Date()
                    calendarForBest.time = startD
                    val todayDate = dateFormat.parse(todayStr) ?: Date()

                    while (!calendarForBest.time.after(todayDate)) {
                        val dStr = dateFormat.format(calendarForBest.time)
                        val usage = if (dStr == todayStr) todayUsage else history.find { it.date == dStr }?.usageTimeMillis

                        val effectiveUsage = if (usage == null && shield.type == FocusType.SHIELD) 0L else usage
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
                    lastStreakUpdateTimestamp = if (isSuccessToday && (shield.type == FocusType.GOAL || todayUsage > 0)) now else shield.lastStreakUpdateTimestamp
                ))
            }
        }
    }

    suspend fun refreshAllAppStreaks(shieldRepository: ShieldRepository) {
        refreshAppStreaks(shieldRepository)
        refreshWebStreaks(shieldRepository)
    }

    suspend fun runManualStreakRecovery(shieldRepository: ShieldRepository) {
        val prefs = userPreferencesFlow.first()
        val targetMillis = prefs.screenTimeTargetMinutes * 60 * 1000L
        val dbUsage = shieldRepository.getAllUsage().first()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStart = DateTimeUtils.getDayStartTime(System.currentTimeMillis(), prefs.dayStartHour, prefs.dayStartMinute)
        if (targetMillis > 0) {
            val (launcherApps, launcherPackage) = withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
                    val lPkg = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                    apps to lPkg
                } catch (_: Exception) { emptySet<String>() to null }
            }
            val excludePackages = setOfNotNull(context.packageName, launcherPackage)

            val globalHistory = dbUsage.filter { it.packageName == "TOTAL" }
            val oldestHistoryDate = globalHistory.map { it.date }.minOrNull()
            var pastStreak = 0
            val recoveryGlobalLimit = (prefs.globalCurrentStreak + 30).coerceAtMost(90)
            for (i in 1..recoveryGlobalLimit) {
                val c = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -i) }
                val dStr = dateFormat.format(c.time)
                var usage = globalHistory.find { it.date == dStr }?.usageTimeMillis
                if (usage == null) {
                    if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                        usage = 0L
                    } else if (i <= 14) {
                        usage = withContext(Dispatchers.IO) {
                            fetchSystemTotalUsageForDate(usageStatsManager, c.timeInMillis, launcherApps, excludePackages)
                        }
                    }
                }

                if (usage != null) {
                    if (usage <= targetMillis) pastStreak++ else break
                } else break
            }

            val (totalToday, stats) = withContext(Dispatchers.IO) {
                val stats = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = prefs.dayStartHour, dayStartMinute = prefs.dayStartMinute).appUsageMap
                var total = 0L
                stats.forEach { (pkg, time) -> if (pkg !in excludePackages && pkg in launcherApps) total += time }
                total to stats
            }

            val isSuccessToday = totalToday <= targetMillis
            if (isSuccessToday && (pastStreak + 1) < prefs.globalBestStreak) {
                var provenDays = 1
                for (j in 1..3) {
                    val cal = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -j) }
                    val u = globalHistory.find { it.date == dateFormat.format(cal.time) }?.usageTimeMillis
                        ?: withContext(Dispatchers.IO) {
                            fetchSystemTotalUsageForDate(usageStatsManager, cal.timeInMillis, launcherApps, excludePackages)
                        }
                    if (u <= targetMillis) provenDays++ else break
                }
                if (provenDays >= 3) {
                    updateGlobalStreak(maxOf(pastStreak + 1, prefs.globalBestStreak), prefs.globalBestStreak, System.currentTimeMillis())
                }
            }
        }
        val shields = shieldRepository.allShields.first()
        val allUsage = dbUsage.groupBy { it.packageName }
        val todayUsageMap = withContext(Dispatchers.IO) {
            com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager, dayStartHour = prefs.dayStartHour, dayStartMinute = prefs.dayStartMinute).appUsageMap
        }

        shields.forEach { shield ->
            val pkg = shield.packageName
            val history = allUsage[pkg] ?: emptyList()
            val oldestHistoryDate = history.map { it.date }.minOrNull()
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            if (limitMillis <= 0 && shield.type == FocusType.SHIELD) return@forEach

            var pastStreak = 0
            val recoveryShieldLimit = (shield.currentStreak + 30).coerceAtMost(90)
            for (i in 1..recoveryShieldLimit) {
                val c = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -i) }
                if (shield.lastStreakUpdateTimestamp == 0L && shield.currentStreak == 0) break
                val dStr = dateFormat.format(c.time)
                var usage = history.find { it.date == dStr }?.usageTimeMillis
                if (usage == null) {
                    if (oldestHistoryDate != null && dStr >= oldestHistoryDate) {
                        if (shield.type == FocusType.SHIELD) usage = 0L else break
                    } else if (i <= 14) {
                        usage = withContext(Dispatchers.IO) {
                            fetchSystemAppUsageForDate(usageStatsManager, pkg, c.timeInMillis)
                        }
                    }
                }

                if (usage != null) {
                    val success = if (shield.type == FocusType.GOAL) usage >= limitMillis else usage <= limitMillis
                    if (success) pastStreak++ else break
                } else break
            }

            val todayUsage = todayUsageMap[pkg] ?: 0L
            val isSuccessToday = if (shield.type == FocusType.GOAL) todayUsage >= limitMillis else todayUsage <= limitMillis
            val currentStreak = if (shield.type == FocusType.GOAL) (if (isSuccessToday) pastStreak + 1 else pastStreak) else (if (isSuccessToday) pastStreak + 1 else 0)

            if (isSuccessToday && currentStreak < shield.bestStreak) {
                var provenDays = 1
                for (j in 1..3) {
                    val cal = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -j) }
                    val u = history.find { it.date == dateFormat.format(cal.time) }?.usageTimeMillis
                        ?: withContext(Dispatchers.IO) {
                            fetchSystemAppUsageForDate(usageStatsManager, pkg, cal.timeInMillis)
                        }
                    val success = if (shield.type == FocusType.GOAL) u >= limitMillis else u <= limitMillis
                    if (success) provenDays++ else break
                }
                if (provenDays >= 3) {
                    shieldRepository.updateShield(shield.copy(currentStreak = maxOf(currentStreak, shield.bestStreak)))
                }
            }
        }
    }
}
