package com.etrisad.zenith.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlarmItemDismissMissionsTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter<List<AlarmItem>>(
        Types.newParameterizedType(List::class.java, AlarmItem::class.java)
    )

    @Test
    fun dismissTaskTypeNamesRoundTripThroughJson() {
        val alarm = AlarmItem.createNew().copy(
            dismissTaskTypeNames = setOf("MATH", "WAITING", "QR_SCAN")
        )

        val parsed = adapter.fromJson(adapter.toJson(listOf(alarm)))

        assertEquals(setOf("MATH", "WAITING", "QR_SCAN"), parsed!!.single().dismissTaskTypeNames)
    }

    @Test
    fun legacyJsonWithoutNewFieldDefaultsToEmpty() {
        val parsed = adapter.fromJson(
            """[{"id":1,"hour":7,"minute":0,"name":"Alarm","mathChallengeEnabled":true}]"""
        )

        assertEquals(emptySet<String>(), parsed!!.single().dismissTaskTypeNames)
    }

    @Test
    fun defaultAlarmHasNoDismissMissions() {
        val alarm = AlarmItem.createNew()

        assertEquals(emptySet<String>(), alarm.dismissTaskTypeNames)
        assertFalse(alarm.mathChallengeEnabled)
    }

    @Test
    fun fullDraftSetupSurvivesSerializeRoundTrip() {
        val draft = AlarmItem(
            id = 12345L,
            hour = 6,
            minute = 30,
            name = "Kerja",
            enabled = true,
            days = setOf(2, 3, 4, 5, 6),
            soundUri = "content:
            soundEnabled = true,
            autoRepeatEnabled = false,
            vibrateEnabled = false,
            snoozeDurationMinutes = 10,
            snoozeMaxCount = 5,
            gradualVolumeEnabled = true,
            gradualVolumeDurationSeconds = 60,
            mathChallengeEnabled = false,
            dismissTaskTypeNames = setOf("MATH", "QR_SCAN"),
            alarmVolume = 0.5f,
            ttsEnabled = true,
            ttsCustomPhrase = "Bangun, jam {time}",
            ttsLanguage = "in_ID",
            ttsTalkAfterSeconds = 5,
            ttsRepeatCount = 3,
            ttsIntervalSeconds = 8,
            preventVolumeDrop = true,
            wakeUpAppPackageNames = listOf("com.example.a", "com.example.b"),
            wakeUpAppDurationSeconds = 180
        )

        val parsed = adapter.fromJson(adapter.toJson(listOf(draft)))!!.single()

        assertEquals(draft, parsed)
    }
}
