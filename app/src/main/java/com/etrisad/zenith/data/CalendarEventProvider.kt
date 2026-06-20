package com.etrisad.zenith.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CurrentCalendarEvent(
    val title: String,
    val description: String?,
    val eventStartMillis: Long,
    val eventEndMillis: Long
)

object CalendarEventProvider {

    fun fetchCurrentEvent(context: Context): CurrentCalendarEvent? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val now = System.currentTimeMillis()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END
        )
        val selection = "${CalendarContract.Instances.BEGIN} <= ? AND ${CalendarContract.Instances.END} >= ?"
        val selectionArgs = arrayOf(now.toString(), now.toString())

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(now.toString())
            .build()

        var bestEvent: CurrentCalendarEvent? = null

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.END} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE))
                    val description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION))
                    val begin = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
                    val end = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END))

                    bestEvent = CurrentCalendarEvent(
                        title = title ?: "(No Title)",
                        description = description,
                        eventStartMillis = begin,
                        eventEndMillis = end
                    )
                    break
                }
            }
        } catch (_: Exception) {
            return null
        }

        return bestEvent
    }
}
