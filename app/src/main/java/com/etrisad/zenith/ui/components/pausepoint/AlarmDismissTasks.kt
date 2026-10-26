package com.etrisad.zenith.ui.components.pausepoint
object AlarmDismissTasks {
    val availableTypes: List<PausePointTaskType> =
        PausePointTaskType.entries.filter { it != PausePointTaskType.CHOOSE_APP }
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
