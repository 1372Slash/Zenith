package com.etrisad.zenith.ui.components.pausepoint

/**
 * Bridges alarm dismissal with Pause Point tasks.
 *
 * Each alarm stores its chosen dismiss missions as [com.etrisad.zenith.data.model.AlarmItem.dismissTaskTypeNames]
 * (a set of [PausePointTaskType.name] values). When the alarm fires, one random
 * task is generated from those types via [PausePointEngine.generateTask] using
 * the global Pause Point config (variants, saved QR codes / NFC tags), and the
 * alarm can only be dismissed by completing it.
 */
object AlarmDismissTasks {

    /**
     * Task types offered as alarm dismiss missions.
     *
     * CHOOSE_APP is excluded: it needs goal apps to suggest, which an alarm
     * firing from the lock screen has no context for. App-based wake proof is
     * already covered by the alarm's own "use certain app" verification.
     */
    val availableTypes: List<PausePointTaskType> =
        PausePointTaskType.entries.filter { it != PausePointTaskType.CHOOSE_APP }

    /** Lenient parse: unknown or unavailable names are dropped, never crash. */
    fun parseNames(names: Set<String>): Set<PausePointTaskType> =
        names.mapNotNull { runCatching { PausePointTaskType.valueOf(it) }.getOrNull() }
            .filter { it in availableTypes }
            .toSet()

    fun toNames(types: Set<PausePointTaskType>): Set<String> =
        types.filter { it in availableTypes }.map { it.name }.toSet()

    /**
     * Effective dismiss missions for an alarm.
     *
     * New selections win; when nothing was ever selected, the legacy
     * math-challenge toggle maps to a single MATH mission so old alarms keep
     * requiring a task exactly as before.
     */
    fun effectiveTypes(
        dismissTaskTypeNames: Set<String>,
        legacyMathChallengeEnabled: Boolean
    ): Set<PausePointTaskType> {
        val parsed = parseNames(dismissTaskTypeNames)
        if (parsed.isNotEmpty()) return parsed
        return if (legacyMathChallengeEnabled) setOf(PausePointTaskType.MATH) else emptySet()
    }
}
