package com.etrisad.zenith.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.R
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.InterceptedNotificationEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import kotlinx.coroutines.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ZenithNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: ZenithDatabase
    private var cachedActiveSchedules: List<com.etrisad.zenith.data.local.entity.ScheduleEntity>? = null
    private var lastScheduleCacheTime = 0L

    override fun onCreate() {
        super.onCreate()
        database = ZenithDatabase.getDatabase(this)
    }

    private suspend fun getActiveSchedulesCached(): List<com.etrisad.zenith.data.local.entity.ScheduleEntity> {
        val now = System.currentTimeMillis()
        val cached = cachedActiveSchedules
        if (cached != null && now - lastScheduleCacheTime < 60000) {
            return cached
        }
        val schedules = database.scheduleDao().getActiveSchedules()
        cachedActiveSchedules = schedules
        lastScheduleCacheTime = now
        return schedules
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return
        if (!AppStateHolder.isScreenOn.value) {
            android.util.Log.d("Zenith_SCREEN", "NotificationListener SKIPPED: screen OFF")
            return
        }

        serviceScope.launch {
            val activeSchedules = getActiveSchedulesCached()
            val currentTime = LocalTime.now()
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            for (schedule in activeSchedules) {
                if (!schedule.interceptNotifications || schedule.mode != ScheduleMode.BLOCK) continue

                val startTime = try { LocalTime.parse(schedule.startTime, timeFormatter) } catch (e: Exception) { null } ?: continue
                val endTime = try { LocalTime.parse(schedule.endTime, timeFormatter) } catch (e: Exception) { null } ?: continue

                val isCurrentlyActive = if (startTime.isBefore(endTime)) {
                    currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
                } else {
                    currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
                }

                if (isCurrentlyActive && schedule.packageNames.contains(sbn.packageName)) {
                    cancelNotification(sbn.key)

                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

                    database.interceptedNotificationDao().insert(
                        InterceptedNotificationEntity(
                            packageName = sbn.packageName,
                            title = title,
                            text = text,
                            timestamp = System.currentTimeMillis(),
                            scheduleId = schedule.id
                        )
                    )
                    return@launch
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }

    override fun onDestroy() {
        super.onDestroy()
        cachedActiveSchedules = null
        serviceScope.cancel()
    }

    companion object {
        fun restoreNotifications(context: Context, scheduleId: Long) {
            val database = ZenithDatabase.getDatabase(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            CoroutineScope(Dispatchers.IO).launch {
                val notifications = database.interceptedNotificationDao().getNotificationsByScheduleId(scheduleId)
                
                val channelId = "zenith_restored_notifications"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Restored Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(channel)
                }

                notifications.forEach { intercepted ->
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(intercepted.packageName)
                    val pendingIntent = if (launchIntent != null) {
                        PendingIntent.getActivity(context, intercepted.id.toInt(), launchIntent, PendingIntent.FLAG_IMMUTABLE)
                    } else null

                    val appName = try {
                        val ai = context.packageManager.getApplicationInfo(intercepted.packageName, 0)
                        context.packageManager.getApplicationLabel(ai).toString()
                    } catch (e: Exception) {
                        intercepted.packageName
                    }

                    val appIcon = try {
                        val drawable = context.packageManager.getApplicationIcon(intercepted.packageName)
                        if (drawable is BitmapDrawable) {
                            drawable.bitmap
                        } else {
                            val bitmap = Bitmap.createBitmap(
                                drawable.intrinsicWidth.coerceAtLeast(1),
                                drawable.intrinsicHeight.coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bitmap
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val builder = NotificationCompat.Builder(context, channelId)
                        .setContentTitle(intercepted.title ?: appName)
                        .setContentText(intercepted.text)
                        .setSubText(appName)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setLargeIcon(appIcon)
                        .setAutoCancel(true)
                        .setWhen(intercepted.timestamp)
                        .setShowWhen(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setGroup("zenith_restored")
                    
                    pendingIntent?.let { builder.setContentIntent(it) }

                    notificationManager.notify(intercepted.id.toInt(), builder.build())
                }
                database.interceptedNotificationDao().deleteByScheduleId(scheduleId)
            }
        }
    }
}
