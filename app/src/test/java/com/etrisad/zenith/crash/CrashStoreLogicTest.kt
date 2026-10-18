package com.etrisad.zenith.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashStoreLogicTest {

    @Test
    fun noLoopBelowThreshold() {
        val now = 1_000_000L
        val times = listOf(now - 10_000, now - 20_000)
        assertFalse(CrashStore.isRapidCrashLoop(times, now))
    }

    @Test
    fun loopWhenThreeCrashesInsideWindow() {
        val now = 1_000_000L
        val times = listOf(now - 5_000, now - 15_000, now - 30_000)
        assertTrue(CrashStore.isRapidCrashLoop(times, now))
    }

    @Test
    fun oldCrashesDoNotCount() {
        val now = 1_000_000L
        val times = listOf(now - 5_000, now - 120_000, now - 200_000)
        assertFalse(CrashStore.isRapidCrashLoop(times, now))
    }

    @Test
    fun crashExactlyAtWindowBoundaryCounts() {
        val now = 1_000_000L
        val times = listOf(now - CrashStore.RAPID_WINDOW_MS, now - 10, now - 20)
        assertTrue(CrashStore.isRapidCrashLoop(times, now))
    }

    @Test
    fun futureTimestampsAreIgnored() {
        val now = 1_000_000L
        val times = listOf(now + 60_000, now + 61_000, now - 5_000)
        assertFalse(CrashStore.isRapidCrashLoop(times, now))
    }

    @Test
    fun reportTextContainsKeyFields() {
        val record = CrashRecord(
            timestamp = 0L,
            versionName = "1.6.2",
            versionCode = 15,
            flavor = "full",
            threadName = "main",
            exceptionClass = "java.lang.IllegalStateException",
            message = "boom",
            stackTrace = "java.lang.IllegalStateException: boom\n\tat Test.kt:1",
            logTail = "12:00:00.000 D Test: hello",
            device = "Google Pixel 9a (Android 15, API 35)"
        )

        val text = record.toReportText()

        assertTrue(text.contains("1.6.2 (15) [full]"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("main"))
        assertTrue(text.contains("Google Pixel 9a"))
        assertTrue(text.contains("12:00:00.000 D Test: hello"))
        assertTrue(text.contains("--- Stack trace ---"))
    }
}
