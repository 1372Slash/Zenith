package com.etrisad.zenith.ui.components.overlay

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    
    val allSchedules by shieldRepository.allSchedules.collectAsState(initial = emptyList())
    val currentSchedule = remember(allSchedules, schedule.id) {
        allSchedules.find { it.id == schedule.id } ?: schedule
    }

    val databaseUsage by shieldRepository.getAllUsage().collectAsState(initial = emptyList())
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val dbGlobalUsage = remember(databaseUsage, todayDate) {
        databaseUsage.find { it.date == todayDate && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
    }

    val currentTotalGlobalUsageToday = remember(totalGlobalUsageToday, dbGlobalUsage) { maxOf(totalGlobalUsageToday, dbGlobalUsage) }

    val userPrefsRepo = remember { UserPreferencesRepository(context) }
    val userPrefs by userPrefsRepo.userPreferencesFlow.collectAsState(initial = UserPreferences())

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
                autoKickProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
                )
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

    val progress by produceState(initialValue = 0f) {
        val calendar = java.util.Calendar.getInstance()

        while (true) {
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

            value = elapsed.toFloat() / total.toFloat()
            delay(30000)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlphaState.value }
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
        )

        AnimatedVisibility(
            visible = showContent,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                modifier = Modifier
                    .let {
                        if (isLandscape) it.widthIn(max = 640.dp).wrapContentHeight()
                        else it.fillMaxWidth().wrapContentHeight()
                    }
                    .align(Alignment.BottomCenter)
                    .imePadding(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                if (isLandscape) {
                    LandscapeScheduleLayout(
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
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 20.dp),
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

        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            val modeText = if (schedule.mode == ScheduleMode.BLOCK)
                "This app is blocked by your schedule."
                else "Only selected apps are allowed during this schedule."

            Text(
                text = modeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(20.dp))

            if (schedule.mode == ScheduleMode.ALLOW) {
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
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (!isEmergencyUnlocked) {
                if (schedule.emergencyUseCount > 0) {
                    EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Emergency Use: Select Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DurationButtonsGrid(null, onAllowUse)
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            CloseAppTextButton(onCloseApp, autoKickProgress)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LandscapeScheduleLayout(
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp, top = 12.dp),
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

                CloseAppTextButton(onCloseApp, autoKickProgress)
            }
        }
    }
}
