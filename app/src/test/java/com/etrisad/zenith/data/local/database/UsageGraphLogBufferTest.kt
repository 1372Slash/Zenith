package com.etrisad.zenith.data.local.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UsageGraphLogBufferTest {

    @Before
    fun setUp() = UsageGraphLogBuffer.clear()

    @After
    fun tearDown() = UsageGraphLogBuffer.clear()

    @Test
    fun copyAllContainsHeaderLegendAndEntries() {
        UsageGraphLogBuffer.d("GRAPH_HOME", "BARS[1]: days=[]")
        UsageGraphLogBuffer.w("GRAPH_HOME", "BARS_ZERO_WITH_SYS[1]: 1/21")

        val dump = UsageGraphLogBuffer.copyAll()
        assertTrue(dump.contains("Zenith Usage Graph Log Dump"))
        assertTrue(dump.contains("Entries: 2"))
        assertTrue(dump.contains("Legend:"))
        assertTrue(dump.contains("D GRAPH_HOME: BARS[1]: days=[]"))
        assertTrue(dump.contains("W GRAPH_HOME: BARS_ZERO_WITH_SYS[1]: 1/21"))
    }

    @Test
    fun getFilteredReturnsOnlyMatchingTagAndLevel() {
        UsageGraphLogBuffer.d("GRAPH_HOME", "a")
        UsageGraphLogBuffer.d("GRAPH_TAP", "b")
        UsageGraphLogBuffer.e("GRAPH_HOME", "c")

        assertEquals(2, UsageGraphLogBuffer.getFiltered(tag = "GRAPH_HOME").size)
        assertEquals(2, UsageGraphLogBuffer.getFiltered(level = UsageGraphLogEntry.Level.D).size)
        assertEquals(
            1,
            UsageGraphLogBuffer.getFiltered(tag = "GRAPH_TAP", level = UsageGraphLogEntry.Level.D).size
        )
    }

    @Test
    fun ringBufferKeepsOnlyTheNewestEntries() {
        val total = 600
        for (i in 0 until total) UsageGraphLogBuffer.d("GRAPH_HOME", "entry-$i")

        val all = UsageGraphLogBuffer.getAll()
        assertEquals(500, all.size)
        assertFalse(all.any { it.message == "entry-0" })
        assertTrue(all.any { it.message == "entry-${total - 1}" })
    }
}
