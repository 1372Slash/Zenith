package com.etrisad.zenith.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun toList(data: String): List<String> {
        return if (data.isEmpty()) emptyList() else data.split(",")
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
