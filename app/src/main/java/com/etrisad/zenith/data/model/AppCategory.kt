package com.etrisad.zenith.data.model

import androidx.annotation.Keep

@Keep
data class AppCategory(
    val id: String,
    val label: String,
    val description: String,
    val iconKey: String,
    val colorArgb: Int? = null,
    val isCustom: Boolean = false
) {
    companion object {
        val PRODUCTIVE = AppCategory(
            id = "PRODUCTIVE",
            label = "Productive",
            description = "Work, study, and tools that move you forward",
            iconKey = "productive"
        )
        val SOCIAL = AppCategory(
            id = "SOCIAL",
            label = "Social",
            description = "Messaging, social feeds, and communities",
            iconKey = "social"
        )
        val LEISURE = AppCategory(
            id = "LEISURE",
            label = "Leisure",
            description = "Games, video, and time-wasting scrolling",
            iconKey = "leisure"
        )
        val UTILITIES = AppCategory(
            id = "UTILITIES",
            label = "Utilities",
            description = "System tools and everyday essentials",
            iconKey = "utilities"
        )
        val UNCATEGORIZED = AppCategory(
            id = "UNCATEGORIZED",
            label = "Uncategorized",
            description = "No category assigned yet",
            iconKey = "uncategorized"
        )

        val assignable: List<AppCategory> = listOf(PRODUCTIVE, SOCIAL, LEISURE, UTILITIES)
        val builtIns: List<AppCategory> = assignable + UNCATEGORIZED

        fun fromStorage(raw: String?): AppCategory =
            builtIns.firstOrNull { it.id == raw } ?: UNCATEGORIZED
    }
}
