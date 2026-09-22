package com.etrisad.zenith.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun toList(data: String): List<String> {
        if (data.isBlank()) return emptyList()
        return data.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromIntSet(set: Set<Int>): String {
        return set.joinToString(",")
    }

    @TypeConverter
    fun toIntSet(data: String): Set<Int> {
        if (data.isBlank()) return setOf(1, 2, 3, 4, 5, 6, 7)
        val parsed = data.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        // Never return empty for a corrupt row: a corrupt activeDays must not
        // wipe the Flow (which would freeze shields/schedules UI). Fall back
        // to all-days so the row stays visible and editable.
        return parsed.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
    }

    @TypeConverter
    fun fromFocusType(type: com.etrisad.zenith.data.local.entity.FocusType): String {
        return type.name
    }

    @TypeConverter
    fun toFocusType(value: String): com.etrisad.zenith.data.local.entity.FocusType {
        return try {
            com.etrisad.zenith.data.local.entity.FocusType.valueOf(value)
        } catch (e: Exception) {
            com.etrisad.zenith.data.local.entity.FocusType.SHIELD
        }
    }

    @TypeConverter
    fun fromLimitPeriod(period: com.etrisad.zenith.data.local.entity.LimitPeriod): String {
        return period.name
    }

    @TypeConverter
    fun toLimitPeriod(value: String): com.etrisad.zenith.data.local.entity.LimitPeriod {
        return try {
            com.etrisad.zenith.data.local.entity.LimitPeriod.valueOf(value)
        } catch (e: Exception) {
            com.etrisad.zenith.data.local.entity.LimitPeriod.DAILY
        }
    }
}
