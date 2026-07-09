package com.etrisad.zenith.data.model

data class AlarmItem(
    val id: Long = System.currentTimeMillis(),
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
    val mathChallengeEnabled: Boolean = false,
    val ttsEnabled: Boolean = false,
    val ttsCustomPhrase: String? = null,
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
