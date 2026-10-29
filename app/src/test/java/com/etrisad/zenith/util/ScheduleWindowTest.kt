package com.etrisad.zenith.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class ScheduleWindowTest {

    @Test
    fun normalWindowIsNotPastEndBeforeEnd() {
        
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 0))
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 539))
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 540))
        assertFalse(DateTimeUtils.isPastScheduleEnd(540, 1020, 1019))
    }

    @Test
    fun normalWindowIsPastEndAfterEnd() {
        
        assertTrue(DateTimeUtils.isPastScheduleEnd(540, 1020, 1020))
        assertTrue(DateTimeUtils.isPastScheduleEnd(540, 1020, 1200))
        assertTrue(DateTimeUtils.isPastScheduleEnd(540, 1020, 1439))
    }

    @Test
    fun overnightWindowIsNotPastEndDuringItsHours() {
        
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 1320))
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 1439))
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 0))
        assertFalse(DateTimeUtils.isPastScheduleEnd(1320, 360, 359))
    }

    @Test
    fun overnightWindowIsPastEndAfterMorningEnd() {
        
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
