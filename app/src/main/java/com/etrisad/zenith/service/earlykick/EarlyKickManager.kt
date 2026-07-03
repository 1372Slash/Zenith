package com.etrisad.zenith.service.earlykick

class EarlyKickManager {
    private val kickedApps = mutableSetOf<String>()
    private var lastResetDay = -1L

    fun shouldKick(packageName: String, remainingTimeMillis: Long, isEnabled: Boolean): Boolean {
        if (!isEnabled) return false
        
        checkDayChange()

        if (remainingTimeMillis in 1..300000L && !kickedApps.contains(packageName)) {
            kickedApps.add(packageName)
            return true
        }
        
        return false
    }

    fun wasKicked(packageName: String): Boolean {
        checkDayChange()
        return kickedApps.contains(packageName)
    }

    fun markKicked(packageName: String) {
        checkDayChange()
        kickedApps.add(packageName)
    }

    private fun checkDayChange() {
        val today = java.time.LocalDate.now()
        val dayValue = today.toEpochDay()
        if (lastResetDay != dayValue) {
            kickedApps.clear()
            lastResetDay = dayValue
        }
    }

    fun reset() {
        // kicked state persists until day change
    }
}
