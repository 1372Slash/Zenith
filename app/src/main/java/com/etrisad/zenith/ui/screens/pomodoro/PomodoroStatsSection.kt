package com.etrisad.zenith.ui.screens.pomodoro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.components.LongTermSection
import com.etrisad.zenith.ui.viewmodel.DailyUsage
import com.etrisad.zenith.ui.viewmodel.PomodoroViewModel
import com.etrisad.zenith.ui.viewmodel.StatsRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Session-history stats reusing the long-term summary: monthly/yearly toggle,
 * calendar heatmap of focus time, week detail, and per-day session counts.
 */
@Composable
fun PomodoroStatsSection(viewModel: PomodoroViewModel) {
    val selectedRange by viewModel.pomodoroStatsRange.collectAsState()
    val offset by viewModel.pomodoroPeriodOffset.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val periodDays = remember(selectedRange, offset) {
        viewModel.getPomodoroPeriodDayMillis(selectedRange, offset)
    }
    val dailyStats by remember(selectedRange, offset) {
        viewModel.getPomodoroDailyStats(selectedRange, offset)
    }.collectAsState(initial = emptyList())
    val totalForPeriod by remember(selectedRange, offset) {
        viewModel.getPomodoroPeriodTotal(selectedRange, offset)
    }.collectAsState(initial = 0L)
    val prevTotal by remember(selectedRange, offset) {
        viewModel.getPomodoroPeriodTotal(selectedRange, offset + 1)
    }.collectAsState(initial = 0L)
    val rangeLabel = remember(selectedRange, offset) {
        viewModel.getPomodoroPeriodRangeLabel(selectedRange, offset)
    }

    val keyFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val dayCountByMillis = remember(dailyStats) { dailyStats.associate { it.dateMillis to it.sessions } }
    val statByKey = remember(dailyStats) {
        dailyStats.associate { keyFmt.format(Date(it.dateMillis)) to it }
    }
    val dailyHistory = remember(dailyStats) {
        dailyStats.map { DailyUsage(date = it.dateMillis, totalTime = it.focusMillis) }
    }

    // Today + this-week accumulation (only meaningful on the current period).
    val countsLine = remember(statByKey, offset, prefs) {
        if (offset != 0) {
            val sessions = dailyStats.sumOf { it.sessions }
            "This period: $sessions ${if (sessions == 1) "session" else "sessions"}"
        } else {
            val todayKey = com.etrisad.zenith.util.DateTimeUtils.getDayStartDateString(
                System.currentTimeMillis(), prefs.dayStartHour, prefs.dayStartMinute
            )
            val weekKeys = mondayWeekKeys(keyFmt)
            val today = statByKey[todayKey]
            val weekStats = weekKeys.mapNotNull { statByKey[it] }
            val todaySessions = today?.sessions ?: 0
            val weekSessions = weekStats.sumOf { it.sessions }
            val weekMillis = weekStats.sumOf { it.focusMillis }
            "Today: $todaySessions ${if (todaySessions == 1) "session" else "sessions"} • " +
                "This week: $weekSessions ${if (weekSessions == 1) "session" else "sessions"} • " +
                formatPomodoroDuration(weekMillis)
        }
    }

    Column {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            LongTermSection(
                title = "Session History",
                accentColor = MaterialTheme.colorScheme.tertiary,
                highlightColor = MaterialTheme.colorScheme.primary,
                selectedRange = selectedRange,
                onRangeSelected = viewModel::selectPomodoroStatsRange,
                rangeLabel = rangeLabel,
                canGoNewer = offset > 0,
                onPreviousPeriod = { viewModel.prevPomodoroPeriod() },
                onNextPeriod = { viewModel.nextPomodoroPeriod() },
                totalMillis = totalForPeriod,
                prevTotal = prevTotal,
                dailyHistory = dailyHistory,
                heatmapEmptyText = "No sessions yet - complete a focus session",
                formatDuration = ::formatPomodoroDuration,
                periodDays = periodDays,
                detailExtraLine = { dayMillis ->
                    val count = dayCountByMillis[dayMillis] ?: 0
                    Text(
                        text = if (count == 1) "1 session" else "$count sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            )
            Text(
                text = countsLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/** Monday-first yyyy-MM-dd keys of the current week. */
private fun mondayWeekKeys(keyFmt: SimpleDateFormat): Set<String> {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return buildSet {
        repeat(7) {
            add(keyFmt.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun formatPomodoroDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val mins = millis / 60000L
    val hrs = mins / 60
    return if (hrs > 0) "${hrs}h ${mins % 60}m" else "${mins}m"
}
