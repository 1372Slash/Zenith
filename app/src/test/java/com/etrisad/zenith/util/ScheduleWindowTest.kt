package com.etrisad.zenith.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the schedule-window math used by the extended goal lock:
 * goal-linked schedules keep blocking after their window ends for the rest
 * of the day while the linked goal is incomplete.
 */
class ScheduleWindowTest {

    @Test
    fun normalWindowIsNotPastEndBeforeEnd() {
        // 09:00 - 17:00
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 0))
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 539))
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 540))
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 1019))
    }

    @Test
    fun normalWindowIsPastEndAfterEnd() {
        // 09:00 - 17:00
        assertTrue(DateTimeUtils.isPastScheduleEnd(540, 1020, 1020))
        assertTrue(DateTimeUtils.isPastScheduleEnd(540, 1020, 1200))
        assertTrue(DateTimeUtils.isPastScheduleEnd(540, 1020, 1439))
    }

    @Test
    fun overnightWindowIsNotPastEndDuringItsHours() {
        // 22:00 - 06:00
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 1320))
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 1439))
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 0))
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 359))
    }

    @Test
    fun overnightWindowIsPastEndAfterMorningEnd() {
        // 22:00 - 06:00
        assertTrue(DateTimeUtils.isPastScheduleEnd(1320, 360, 360))
        assertTrue(DateTimeUtils.isPastScheduleEnd(1320, 360, 720))
        assertTrue(DateTimeUtils.isPastScheduleEnd(1320, 360, 1319))
    }

    @Test
    fun degenerateWindowIsNeverPastEnd() {
        assertFalse(DateTimeUtils.isPastScheduleEnd(600, 600, 0))
        assertFalse(DateTimeUtils.isPastScheduleEnd(600, 600, 599))
        assertFalse(DateTimeUtils.isPastScheduleEnd(600, 600, 600))
        assertFalse(DateTimeUtils.isPastScheduleEnd(600, 600, 1439))
    }
}
