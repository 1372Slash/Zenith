package com.etrisad.zenith.data.model

import com.squareup.moshi.JsonClass
import java.util.concurrent.atomic.AtomicLong

@JsonClass(generateAdapter = true)
data class AlarmItem(
    val id: Long,
    val hour: Int = 7,
    val minute: Int = 0,
    val name: String = "Alarm",
    val enabled: Boolean = true,
    val days: Set<Int> = emptySet(),
    val soundUri: String? = null,
    val soundEnabled: Boolean = true,
    val autoRepeatEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val snoozeDurationMinutes: Int = 5,
    val snoozeMaxCount: Int = 3,
    val gradualVolumeEnabled: Boolean = false,
    val gradualVolumeDurationSeconds: Int = 30,
    val mathChallengeEnabled: Boolean = false,
    val alarmVolume: Float = 1.0f,
    val ttsEnabled: Boolean = false,
    val ttsCustomPhrase: String? = null,
    val ttsLanguage: String? = null,
    val ttsTalkAfterSeconds: Int = 0,
    val ttsRepeatCount: Int = 0,
    val ttsIntervalSeconds: Int = 3,
    val preventVolumeDrop: Boolean = false,
    val wakeUpAppPackageNames: List<String> = emptyList(),
    val wakeUpAppDurationSeconds: Int = 120
) {
    val timeString: String get() = String.format("%02d:%02d", hour, minute)

    val daysLabel: String
        get() {
            if (days.isEmpty()) return "Once"
            val names = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
            if (days.size == 7) return "Every day"
            return days.sorted().mapNotNull { names[it] }.joinToString(" ")
        }

    companion object {
        private val idCounter = AtomicLong(System.currentTimeMillis())

        fun createNew(hour: Int = 7, minute: Int = 0, name: String = "Alarm") =
            AlarmItem(id = idCounter.getAndIncrement(), hour = hour, minute = minute, name = name)

        fun nextName(existingAlarms: List<AlarmItem>): String {
            val takenNumbers = existingAlarms.mapNotNull { alarm ->
                val match = Regex("""^Alarm #(\d+)$""").find(alarm.name)
                match?.groupValues?.get(1)?.toIntOrNull()
            }.toSet()
            var num = 1
            while (num in takenNumbers) num++
            return "Alarm #$num"
        }
    }
}
