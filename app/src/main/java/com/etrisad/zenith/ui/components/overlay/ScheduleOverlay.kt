package com.etrisad.zenith.ui.components.overlay

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.util.DateTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScheduleOverlay(
    packageName: String,
    appName: String,
    schedule: ScheduleEntity,
    totalGlobalUsageToday: Long,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }
    var isEmergencyUnlocked by remember { mutableStateOf(false) }
    val autoKickProgress = remember(packageName) { Animatable(0f) }
    var isEmergencyHolding by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as com.etrisad.zenith.ZenithApplication
    val shieldRepository = app.shieldRepository
    
    val currentSchedule by produceState(initialValue = schedule) {
        value = shieldRepository.allSchedules.first().find { it.id == schedule.id } ?: schedule
    }

    val currentTotalGlobalUsageTodayState = produceState(
        initialValue = totalGlobalUsageToday,
        totalGlobalUsageToday
    ) {
        val usm = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val dayStartHour = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartHour
        val dayStartMinute = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartMinute
        var lastOfDay = 0L
        var cachedOfDay = 0L
        while (true) {
            val now = System.currentTimeMillis()
            if (now - lastOfDay > 60000) {
                cachedOfDay = DateTimeUtils.getDayStartTime(now, dayStartHour, dayStartMinute)
                lastOfDay = now
            }
            val timeSinceMidnight = (now - cachedOfDay).coerceAtLeast(0L)

            val detailedUsage = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm, dayStartHour = dayStartHour, dayStartMinute = dayStartMinute)
            }
            
            var totalToday = 0L
            detailedUsage.appUsageMap.entries.forEach { entry ->
                if (entry.key != context.packageName) {
                    totalToday += entry.value
                }
            }

            value = totalToday.coerceAtMost(timeSinceMidnight)
            delay(30000)
        }
    }
    val currentTotalGlobalUsageToday = currentTotalGlobalUsageTodayState.value

    val userPrefsRepo = remember { UserPreferencesRepository(context) }
    val userPrefs by produceState(initialValue = UserPreferences()) {
        value = userPrefsRepo.userPreferencesFlow.first()
    }

    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    val backgroundAlphaState = animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    LaunchedEffect(packageName, isEmergencyUnlocked, isEmergencyHolding) {
        if (!isEmergencyUnlocked) {
            if (!isEmergencyHolding) {
                delay(2000)
                if (isEmergencyUnlocked || isEmergencyHolding) return@LaunchedEffect

                autoKickProgress.snapTo(0f)
                val startTime = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val p = (elapsed.toFloat() / 4000f).coerceIn(0f, 1f)
                    autoKickProgress.snapTo(p)
                    if (p >= 1f) break
                    delay(16)
                }
                if (showContent) {
                    showContent = false
                    delay(300)
                }
                onCloseApp()
            } else {
                autoKickProgress.stop()
            }
        } else {
            autoKickProgress.snapTo(0f)
        }
    }

    val progress = remember(schedule) {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        val nowH = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val nowM = calendar.get(java.util.Calendar.MINUTE)
        val nowTotalMin = nowH * 60 + nowM

        fun toMinutes(timeStr: String): Int {
            return try {
                val parts = timeStr.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (_: Exception) { 0 }
        }

        val startMin = toMinutes(schedule.startTime)
        var endMin = toMinutes(schedule.endTime)
        var currentMin = nowTotalMin

        if (endMin <= startMin) {
            endMin += 24 * 60
            if (currentMin < startMin) currentMin += 24 * 60
        }

        val total = (endMin - startMin).coerceAtLeast(1)
        val elapsed = (currentMin - startMin).coerceIn(0, total)

        elapsed.toFloat() / total.toFloat()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    InterceptBottomSheet(
        visible = showContent,
        backgroundAlpha = backgroundAlphaState.value,
        isLandscape = isLandscape,
        showBedtimePill = true,
        userPreferences = userPrefs,
        dragHandleEmergencyCount = currentSchedule.emergencyUseCount
    ) { _ ->
        if (isLandscape) {
            LandscapeScheduleLayout(
                modifier = Modifier.displayCutoutPadding(),
                appName = appName,
                appIcon = appIcon,
                schedule = currentSchedule,
                progress = progress,
                totalGlobalUsageToday = currentTotalGlobalUsageToday,
                userPrefs = userPrefs,
                isEmergencyUnlocked = isEmergencyUnlocked,
                autoKickProgress = { autoKickProgress.value },
                onEmergencyHoldingChange = { isEmergencyHolding = it },
                onEmergencyClick = { isEmergencyUnlocked = true },
                onAllowUse = { minutes ->
                    scope.launch {
                        showContent = false
                        delay(400)
                        onAllowUse(minutes, isEmergencyUnlocked)
                    }
                },
                onCloseApp = {
                    scope.launch {
                        showContent = false
                        delay(400)
                        onCloseApp()
                    }
                }
            )
        } else {
            PortraitScheduleLayout(
                appName = appName,
                appIcon = appIcon,
                schedule = currentSchedule,
                progress = progress,
                totalGlobalUsageToday = currentTotalGlobalUsageToday,
                userPrefs = userPrefs,
                isEmergencyUnlocked = isEmergencyUnlocked,
                autoKickProgress = { autoKickProgress.value },
                onEmergencyHoldingChange = { isEmergencyHolding = it },
                onEmergencyClick = { isEmergencyUnlocked = true },
                onAllowUse = { minutes ->
                    scope.launch {
                        showContent = false
                        delay(400)
                        onAllowUse(minutes, isEmergencyUnlocked)
                    }
                },
                onCloseApp = {
                    scope.launch {
                        showContent = false
                        delay(400)
                        onCloseApp()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PortraitScheduleLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    schedule: ScheduleEntity,
    progress: Float,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    isEmergencyUnlocked: Boolean,
    autoKickProgress: () -> Float = { 0f },
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onEmergencyClick: () -> Unit,
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
            .then(
                if (userPrefs.overlayFullScreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().wrapContentHeight()
            )
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            modifier = if (userPrefs.overlayFullScreen) Modifier.weight(1f) else Modifier.wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Schedule Active: ${schedule.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = appName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                val modeText = if (schedule.mode == ScheduleMode.BLOCK)
                    "This app is blocked by your schedule."
                else "Only selected apps are allowed during this schedule."

                Text(
                    text = modeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${schedule.startTime} - ${schedule.endTime}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (schedule.mode == ScheduleMode.ALLOW) {
                if (userPrefs.overlayFullScreen) {
                    ScheduleFullScreenProgress(
                        progress = progress,
                        totalGlobalUsageToday = totalGlobalUsageToday,
                        schedule = schedule,
                        userPrefs = userPrefs
                    )
                } else {
                    TotalUsagePill(totalGlobalUsageToday, userPrefs)
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(120.dp),
                        color = MaterialTheme.colorScheme.primary,
                        amplitude = { 1f },
                        wavelength = 36.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isEmergencyUnlocked) {
                if (schedule.emergencyUseCount > 0) {
                    EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else {
                Text(
                    text = "Emergency Use: Select Duration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                DurationButtonsGrid(null, onAllowUse)
                Spacer(modifier = Modifier.height(20.dp))
            }

            CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.ExtraLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LandscapeScheduleLayout(
    modifier: Modifier = Modifier,
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    schedule: ScheduleEntity,
    progress: Float,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    isEmergencyUnlocked: Boolean,
    autoKickProgress: () -> Float = { 0f },
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onEmergencyClick: () -> Unit,
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = modifier.then(
            if (userPrefs.overlayFullScreen) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().wrapContentHeight()
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (userPrefs.overlayFullScreen) Arrangement.Center else Arrangement.Top
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Emergency: ${schedule.emergencyUseCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Active: ${schedule.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                val modeText = if (schedule.mode == ScheduleMode.BLOCK)
                    "This app is blocked by your schedule."
                else "Only selected apps are allowed during this schedule."

                Text(
                    text = modeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${schedule.startTime} - ${schedule.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (schedule.mode == ScheduleMode.ALLOW) {
                    if (userPrefs.overlayFullScreen) {
                        ScheduleFullScreenProgressMini(
                            progress = progress,
                            totalGlobalUsageToday = totalGlobalUsageToday,
                            schedule = schedule,
                            userPrefs = userPrefs
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        TotalUsagePill(totalGlobalUsageToday, userPrefs)
                        CircularWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(80.dp),
                            color = MaterialTheme.colorScheme.primary,
                            wavelength = 24.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (!isEmergencyUnlocked) {
                    Text(
                        text = if (schedule.mode == ScheduleMode.BLOCK) "Blocked by Schedule" else "Not on Allow-list",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (schedule.emergencyUseCount > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
                    }
                } else {
                    Text(
                        text = "Emergency Use: Select Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DurationButtonsGrid(null, onAllowUse)
                }
                Spacer(modifier = Modifier.weight(1f))

                CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.Large)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleFullScreenProgress(
    progress: Float,
    totalGlobalUsageToday: Long,
    schedule: ScheduleEntity,
    userPrefs: UserPreferences
) {
    val animatedProgress = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "scheduleFullScreenProgress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier.size(220.dp),
                color = MaterialTheme.colorScheme.primary,
                amplitude = { 1f },
                wavelength = 58.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "used today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMillis(totalGlobalUsageToday),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "${schedule.startTime} - ${schedule.endTime}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleFullScreenProgressMini(
    progress: Float,
    totalGlobalUsageToday: Long,
    schedule: ScheduleEntity,
    userPrefs: UserPreferences
) {
    val animatedProgress = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "scheduleFullScreenProgressMini"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier.size(140.dp),
                color = MaterialTheme.colorScheme.primary,
                amplitude = { 1f },
                wavelength = 36.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "used today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMillis(totalGlobalUsageToday),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "${schedule.startTime} - ${schedule.endTime}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}
