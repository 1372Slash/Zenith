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
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ZenithNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: ZenithDatabase
    private var cachedActiveSchedules: List<com.etrisad.zenith.data.local.entity.ScheduleEntity>? = null
    private var lastScheduleCacheTime = 0L
    private var preferencesJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        database = ZenithDatabase.getDatabase(this)
        // Keep privacy settings available even when the accessibility service is not running.
        preferencesJob = serviceScope.launch {
            UserPreferencesRepository(applicationContext).userPreferencesFlow.collectLatest { prefs ->
                SharedMonitoringState.privatePackages = prefs.privatePackages
                SharedMonitoringState.notificationInterceptionEnabled = prefs.notificationInterceptionEnabled
                SharedMonitoringState.notificationExcludedPackages = prefs.notificationExcludedPackages
                SharedMonitoringState.storeInterceptedNotificationContent = prefs.storeInterceptedNotificationContent
            }
        }
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
        if (!SharedMonitoringState.notificationInterceptionEnabled) return
        // Private and explicitly excluded apps are never inspected or stored.
        if (SharedMonitoringState.isNotificationExcluded(sbn.packageName)) return
        if (!AppStateHolder.isScreenOn.value) {
            return
        }

        serviceScope.launch {
            if (SharedMonitoringState.isPomodoroActive && !SharedMonitoringState.isPomodoroPaused) {
                val pkg = sbn.packageName
                val isAllowed = pkg in SharedMonitoringState.pomodoroAllowedPackages ||
                    pkg in SharedMonitoringState.CRITICAL_SYSTEM_PACKAGES ||
                    pkg in SharedMonitoringState.whitelistedPackages
                if (!isAllowed) {
                    cancelNotification(sbn.key)
                    // Only read notification content when the user allows it to be stored.
                    val storeContent = SharedMonitoringState.storeInterceptedNotificationContent
                    val extras = if (storeContent) sbn.notification.extras else null
                    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    database.interceptedNotificationDao().insert(
                        InterceptedNotificationEntity(
                            packageName = pkg,
                            title = title,
                            text = text,
                            timestamp = System.currentTimeMillis(),
                            scheduleId = -1L
                        )
                    )
                    return@launch
                }
            }

            val activeSchedules = getActiveSchedulesCached()
            val currentTime = LocalTime.now()
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            for (schedule in activeSchedules) {
                if (!schedule.interceptNotifications || schedule.mode != ScheduleMode.BLOCK) continue
                val todayCalDay = try {
                    java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                } catch (_: Exception) { -1 }
                if (todayCalDay != -1 && todayCalDay !in schedule.activeDays) continue

                val startTime = try { LocalTime.parse(schedule.startTime, timeFormatter) } catch (e: Exception) { null } ?: continue
                val endTime = try { LocalTime.parse(schedule.endTime, timeFormatter) } catch (e: Exception) { null } ?: continue

                val isCurrentlyActive = if (startTime.isBefore(endTime)) {
                    !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)
                } else {
                    !currentTime.isBefore(startTime) || currentTime.isBefore(endTime)
                }

                if (isCurrentlyActive && schedule.packageNames.contains(sbn.packageName)) {
                    cancelNotification(sbn.key)
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
        preferencesJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        fun restorePomodoroNotifications(context: Context) {
            val database = ZenithDatabase.getDatabase(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            CoroutineScope(Dispatchers.IO).launch {
                val prefs = UserPreferencesRepository(context).userPreferencesFlow.first()
                val excluded = prefs.privatePackages + prefs.notificationExcludedPackages
                if (excluded.isNotEmpty()) {
                    database.interceptedNotificationDao().deleteByPackages(excluded.toList())
                }
                val notifications = database.interceptedNotificationDao()
                    .getNotificationsByScheduleId(-1L)
                    .filterNot { it.packageName in excluded }
                if (notifications.isEmpty()) {
                    database.interceptedNotificationDao().deleteByScheduleId(-1L)
                    return@launch
                }

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
                database.interceptedNotificationDao().deleteByScheduleId(-1L)
            }
        }

        fun restoreNotifications(context: Context, scheduleId: Long) {
            val database = ZenithDatabase.getDatabase(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            CoroutineScope(Dispatchers.IO).launch {
                val prefs = UserPreferencesRepository(context).userPreferencesFlow.first()
                val excluded = prefs.privatePackages + prefs.notificationExcludedPackages
                if (excluded.isNotEmpty()) {
                    database.interceptedNotificationDao().deleteByPackages(excluded.toList())
                }
                val notifications = database.interceptedNotificationDao()
                    .getNotificationsByScheduleId(scheduleId)
                    .filterNot { it.packageName in excluded }

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
