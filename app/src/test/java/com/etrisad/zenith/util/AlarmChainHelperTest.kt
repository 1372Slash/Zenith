package com.etrisad.zenith.util

import com.etrisad.zenith.data.model.AlarmItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmChainHelperTest {

    private fun alarm(id: Long, hour: Int, minute: Int) =
        AlarmItem.createNew(hour, minute, "Alarm").copy(id = id)

    @Test
    fun alarmKeyPrefersIdOverTime() {
        assertEquals("id:11", AlarmChainHelper.alarmKey(11L, "05:00"))
        assertEquals("id:22", AlarmChainHelper.alarmKey(22L, "05:00"))
    }

    @Test
    fun alarmKeyFallsBackToTimeForLegacyZeroId() {
        assertEquals("time:05:00", AlarmChainHelper.alarmKey(0L, "05:00"))
    }

    @Test
    fun sameTimeDifferentIdsAreDifferentKeys() {
        val a = AlarmChainHelper.alarmKey(11L, "05:00")
        val b = AlarmChainHelper.alarmKey(22L, "05:00")
        assertFalse(a == b)
    }

    @Test
    fun findAlarmMatchesByIdWhenTimesCollide() {
        val alarms = listOf(alarm(11L, 5, 0), alarm(22L, 5, 0))

        assertEquals(22L, AlarmChainHelper.findAlarm(alarms, 22L, "05:00")?.id)
        assertEquals(11L, AlarmChainHelper.findAlarm(alarms, 11L, "05:00")?.id)
    }

    @Test
    fun findAlarmFallsBackToTimeForZeroId() {
        val alarms = listOf(alarm(11L, 5, 0))

        assertEquals(11L, AlarmChainHelper.findAlarm(alarms, 0L, "05:00")?.id)
    }

    @Test
    fun findAlarmReturnsNullForUnknownIdInsteadOfWrongTimeMatch() {
        val alarms = listOf(alarm(11L, 5, 0))

        assertNull(AlarmChainHelper.findAlarm(alarms, 99L, "05:00"))
    }

    @Test
    fun findAlarmReturnsNullWhenEmpty() {
        assertNull(AlarmChainHelper.findAlarm(emptyList(), 11L, "05:00"))
        assertNull(AlarmChainHelper.findAlarm(emptyList(), 0L, "05:00"))
    }

    @Test
    fun usageCheckKeepsWaitingWhenSameAlarmStillShowing() {
        var providerCalls = 0
        val outcome = AlarmChainHelper.decideUsageCheck(
            isShowing = true,
            showingKey = "id:11",
            checkKey = "id:11"
        ) {
            providerCalls++
            true
        }

        assertEquals(AlarmChainHelper.UsageCheckOutcome.KEEP_WAITING_SAME_ALARM, outcome)
        assertEquals(0, providerCalls)
    }

    @Test
    fun usageCheckEvaluatesUsageWhenDifferentAlarmShowing() {
        val awake = AlarmChainHelper.decideUsageCheck(
            isShowing = true, showingKey = "id:22", checkKey = "id:11"
        ) { true }
        val asleep = AlarmChainHelper.decideUsageCheck(
            isShowing = true, showingKey = "id:22", checkKey = "id:11"
        ) { false }

        assertEquals(AlarmChainHelper.UsageCheckOutcome.COMPLETE, awake)
        assertEquals(AlarmChainHelper.UsageCheckOutcome.RE_TRIGGER, asleep)
    }

    @Test
    fun usageCheckEvaluatesUsageWhenNothingShowing() {
        val awake = AlarmChainHelper.decideUsageCheck(
            isShowing = false, showingKey = null, checkKey = "id:11"
        ) { true }
        val asleep = AlarmChainHelper.decideUsageCheck(
            isShowing = false, showingKey = null, checkKey = "id:11"
        ) { false }

        assertEquals(AlarmChainHelper.UsageCheckOutcome.COMPLETE, awake)
        assertEquals(AlarmChainHelper.UsageCheckOutcome.RE_TRIGGER, asleep)
    }

    @Test
    fun usageCheckFallsBackToAwakeWhenShowingKeyUnknown() {
        val outcome = AlarmChainHelper.decideUsageCheck(
            isShowing = true, showingKey = null, checkKey = "id:11"
        ) { false }

        assertEquals(AlarmChainHelper.UsageCheckOutcome.RE_TRIGGER, outcome)
    }

    @Test
    fun wakeUpOffsetMergeNeverDoubleCountsOrGoesNegative() {
        assertEquals(135_000L, AlarmChainHelper.mergeWakeUpOffset(90_000L, 45_000L))
        assertEquals(90_000L, AlarmChainHelper.mergeWakeUpOffset(90_000L, -5_000L))
    }

    @Test
    fun wakeUpCompleteAtThreshold() {
        assertTrue(AlarmChainHelper.isWakeUpComplete(120_000L, 120))
        assertFalse(AlarmChainHelper.isWakeUpComplete(119_999L, 120))
        assertFalse(AlarmChainHelper.isWakeUpComplete(999_999L, 0))
    }

    @Test
    fun preemptedAlarmProgressIsPreservedAcrossSwitch() {
        val preservedOffsetMs = 90_000L
        val windowMs = 45_000L
        val merged = AlarmChainHelper.mergeWakeUpOffset(preservedOffsetMs, windowMs)

        assertEquals(135_000L, merged)
        assertEquals(135, AlarmChainHelper.wakeUpTotalSeconds(preservedOffsetMs, windowMs))
        assertTrue(AlarmChainHelper.isWakeUpComplete(merged, 120))
    }
}
