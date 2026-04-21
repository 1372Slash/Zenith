package com.etrisad.zenith.service

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InterceptOverlayContent(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    delayDurationSeconds: Int = 0,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            // Downsample bitmap secara drastis ke 120px untuk menghemat RAM.
            // Dari ~1MB menjadi ~60KB per ikon.
            drawable.toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    var showContent by remember { mutableStateOf(false) }
    var isEmergencyUnlocked by remember { mutableStateOf(false) }
    
    // Delay App State
    val isDelayEnabled = shield?.isDelayAppEnabled == true && shield.type == FocusType.SHIELD
    
    val initialProgress = remember(shield, delayDurationSeconds) {
        if (isDelayEnabled && shield.lastDelayStartTimestamp > 0 && delayDurationSeconds > 0) {
            val elapsed = System.currentTimeMillis() - shield.lastDelayStartTimestamp
            (elapsed.toFloat() / (delayDurationSeconds * 1000f)).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val delayProgressAnimatable = remember { Animatable(initialProgress) }
    var isDelaying by remember { mutableStateOf(isDelayEnabled && shield.lastDelayStartTimestamp != 0L && initialProgress < 1f) }

    val motivationalMessages = remember {
        listOf(
            "Time for a quick stretch! 🧘",
            "Have you had enough water today? 💧",
            "Take 3 deep breaths... 💨",
            "Ready to crush your goals? 🚀",
            "Productivity is a marathon, not a sprint. 🏃",
            "Check your to-do list for a quick win! ✅",
            "A small step today is a big leap tomorrow. ✨",
            "Stay focused, stay mindful. 🧠",
            "Remember your homework or tasks! 📚",
            "Do one small productive thing now. ⚡"
        )
    }
    val randomMessage = remember(isDelaying) {
        if (isDelaying) motivationalMessages.random() else ""
    }
    
    val scope = rememberCoroutineScope()

    val backgroundAlphaState = animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    // Delay Timer Effect
    LaunchedEffect(isDelaying) {
        if (isDelaying && delayDurationSeconds > 0) {
            val remainingProgress = 1f - delayProgressAnimatable.value
            val remainingDuration = (remainingProgress * delayDurationSeconds * 1000).toInt()
            
            if (remainingDuration > 0) {
                delayProgressAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainingDuration,
                        easing = LinearEasing
                    )
                )
            }
            isDelaying = false
        } else {
            isDelaying = false
        }
    }

    val isPeriodExpired = remember(shield) {
        shield != null && (System.currentTimeMillis() - shield.lastPeriodResetTimestamp > shield.refreshPeriodMinutes * 60 * 1000L)
    }
    
    val currentUses = remember(shield, isPeriodExpired) {
        if (isPeriodExpired) 0 else (shield?.currentPeriodUses ?: 0)
    }
    val maxUses = shield?.maxUsesPerPeriod ?: 5
    val isUsesExceeded = remember(currentUses, maxUses) { currentUses >= maxUses }
    val isTimeLimitReached = remember(shield, totalUsageToday) {
        shield != null && totalUsageToday >= (shield.timeLimitMinutes * 60 * 1000L)
    }
    
    val refreshTimeLeftMillis = if (shield != null) {
        val nextRefresh = shield.lastPeriodResetTimestamp + (shield.refreshPeriodMinutes * 60 * 1000L)
        (nextRefresh - System.currentTimeMillis()).coerceAtLeast(0L)
    } else 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. Scrim background (Semi-transparan agar aplikasi di bawah terlihat)
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
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .imePadding(), // Navigation bar padding dipindah ke dalam agar background kartu meluas ke bawah
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Uses & Emergency Indicators (Top of Bottom Sheet) - Only for SHIELD
                    if (shield != null && shield.type == FocusType.SHIELD) {
                        // Left: Uses
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 28.dp, start = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Timer,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$currentUses/$maxUses uses",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Right: Emergency
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
                                text = "Emergency: ${shield.emergencyUseCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding(), // Memastikan konten tetap aman di atas navigation bar
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle simulation
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // App Icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    if (shield?.type == FocusType.GOAL) Icons.Outlined.Flag else Icons.Outlined.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (shield?.type == FocusType.GOAL) "Goal Pursuit" else "Mindful Pause",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (shield?.type == FocusType.GOAL) 
                                "You're working towards your usage goal." 
                                else "Zenith Shield is active for this app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (shield != null && shield.type == FocusType.GOAL) {
                            // GOAL UI
                            val targetLimitMillis = shield.timeLimitMinutes * 60 * 1000L
                            val progress = if (targetLimitMillis > 0) totalUsageToday.toFloat() / targetLimitMillis else 0f
                            val remainingMillis = (targetLimitMillis - totalUsageToday).coerceAtLeast(0L)
                            
                            val estimateTime = remember(remainingMillis) {
                                val finishTime = System.currentTimeMillis() + remainingMillis
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(finishTime))
                            }

                            val motivationText = remember(progress) {
                                when {
                                    progress < 0.3f -> "Great start! Keep going."
                                    progress < 0.6f -> "You're halfway there! Stay focused."
                                    progress < 0.9f -> "Almost finished! You can do it."
                                    else -> "Just a little more to go!"
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${(progress * 100).toInt()}% Done",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Target: ${shield.timeLimitMinutes}m",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val animatedProgressState = animateFloatAsState(
                                    targetValue = progress.coerceIn(0f, 1f),
                                    animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
                                    label = "goalProgress"
                                )
                                LinearWavyProgressIndicator(
                                    progress = { animatedProgressState.value },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    wavelength = 40.dp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Estimated finish: $estimateTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = motivationText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        showContent = false
                                        delay(400)
                                        onGoalDismiss()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Got it, let's continue", fontWeight = FontWeight.Bold)
                                }
                            }

                        } else {
                            // SHIELD UI (Original)
                            // Usage Progress
                            if (shield != null) {
                                Spacer(modifier = Modifier.height(24.dp))
                                val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
                                val remainingMillis = (totalLimitMillis - totalUsageToday).coerceAtLeast(0L)
                                val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatMillis(totalUsageToday),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${formatMillis(remainingMillis)} left today",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val animatedProgressState = animateFloatAsState(
                                        targetValue = progress.coerceIn(0f, 1f),
                                        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
                                        label = "progress"
                                    )
                                    LinearWavyProgressIndicator(
                                        progress = { animatedProgressState.value },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp),
                                        color = if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        wavelength = 40.dp
                                    )
                                }
                            }

                            if ((isUsesExceeded || isTimeLimitReached) && !isEmergencyUnlocked) {
                                Spacer(modifier = Modifier.height(32.dp))
                                
                                if (isUsesExceeded && !isTimeLimitReached) {
                                    var countdownText by remember { mutableStateOf(formatCountdown(refreshTimeLeftMillis)) }
                                    LaunchedEffect(refreshTimeLeftMillis) {
                                        var current = refreshTimeLeftMillis
                                        while (current > 0) {
                                            delay(1000)
                                            current -= 1000
                                            countdownText = formatCountdown(current)
                                        }
                                    }

                                    Text(
                                        text = "Uses limit reached. Refresh in $countdownText",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = "Daily limit reached. Come back tomorrow.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (shield != null && shield.emergencyUseCount > 0) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    EmergencyButton(onEmergencyUse = { isEmergencyUnlocked = true })
                                }
                            } else {
                                Spacer(modifier = Modifier.height(32.dp))

                                AnimatedContent(
                                    targetState = isDelaying,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(500)) togetherWith
                                                fadeOut(animationSpec = tween(500))
                                    },
                                    label = "delayContent"
                                ) { delaying ->
                                    if (delaying) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = randomMessage,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(modifier = Modifier.height(32.dp))
                                            
                                            Box(contentAlignment = Alignment.Center) {
                                                CircularWavyProgressIndicator(
                                                    progress = { delayProgressAnimatable.value },
                                                    modifier = Modifier.size(120.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    amplitude = { 1f }, // Menggunakan nilai konstan atau lambda yang benar
                                                    wavelength = 36.dp, // Menambah wavelength agar gelombang lebih jarang
                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                                )
                                                val secondsLeft = kotlin.math.ceil((1f - delayProgressAnimatable.value) * delayDurationSeconds).toInt()
                                                Text(
                                                    text = "${secondsLeft}s",
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Text(
                                                text = "Mindfulness in progress...",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = if (isEmergencyUnlocked) "Emergency Use: Select Duration" else "How long do you want to use it?",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            DurationButtonsGrid { minutes ->
                                                scope.launch {
                                                    showContent = false
                                                    delay(400)
                                                    onAllowUse(minutes, isEmergencyUnlocked)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            TextButton(
                                onClick = {
                                    scope.launch {
                                        showContent = false
                                        delay(400)
                                        onCloseApp()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Close App", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScheduleOverlayContent(
    packageName: String,
    appName: String,
    schedule: ScheduleEntity,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }
    var isEmergencyUnlocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            // Downsample bitmap secara drastis ke 120px untuk menghemat RAM.
            // Dari ~1MB menjadi ~60KB per ikon.
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
            delay(30000) // Update setiap 30 detik saja, jadwal tidak butuh presisi detik
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim background with fade animation
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
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .imePadding(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Emergency Indicator
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                contentScale = ContentScale.Fit
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

                    Spacer(modifier = Modifier.height(32.dp))

                    if (schedule.mode == ScheduleMode.ALLOW) {
                        // Progress indicator for Allow mode
                        CircularWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(120.dp),
                            color = MaterialTheme.colorScheme.primary,
                            amplitude = { 1f },
                            wavelength = 36.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (!isEmergencyUnlocked) {
                        if (schedule.emergencyUseCount > 0) {
                            EmergencyButton(onEmergencyUse = { isEmergencyUnlocked = true })
                            Spacer(modifier = Modifier.height(24.dp))
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

                            Spacer(modifier = Modifier.height(16.dp))

                            DurationButtonsGrid { minutes ->
                                scope.launch {
                                    showContent = false
                                    delay(400)
                                    onAllowUse(minutes, true)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                onCloseApp()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Close App", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h ${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmergencyButton(onEmergencyUse: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgressTarget by remember { mutableFloatStateOf(0f) }

    val animatedProgressState = animateFloatAsState(
        targetValue = holdProgressTarget,
        animationSpec = if (isHolding) tween(5000, easing = LinearEasing) else tween(300),
        label = "holdProgress"
    )

    LaunchedEffect(isHolding) {
        if (isHolding) {
            holdProgressTarget = 1f
            delay(5000)
            if (isHolding) {
                onEmergencyUse()
                isHolding = false
                holdProgressTarget = 0f
            }
        } else {
            holdProgressTarget = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        try {
                            awaitRelease()
                        } finally {
                            isHolding = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Optimized progress background fill
        val progressColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        color = progressColor,
                        size = size.copy(width = size.width * animatedProgressState.value)
                    )
                }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isHolding) "Hold for ${5 - (animatedProgressState.value * 5).toInt()}s..." else "Hold for 5s to use Emergency",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DurationButtonsGrid(onAllowUse: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DurationButton(minutes = 2, delaySeconds = 0, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
            DurationButton(minutes = 5, delaySeconds = 3, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DurationButton(minutes = 10, delaySeconds = 6, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
            DurationButton(minutes = 20, delaySeconds = 10, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DurationButton(
    minutes: Int,
    delaySeconds: Int,
    onAllowUse: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    // Smooth progress animation
    val progressState = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = if (delaySeconds > 0) {
            tween(durationMillis = delaySeconds * 1000, easing = LinearEasing)
        } else {
            snap()
        },
        label = "buttonProgress"
    )

    val isEnabled = progressState.value >= 1f

    // Spring bounce scale animation when becoming enabled
    val scaleState = animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.95f,
        animationSpec = if (isEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            tween(300)
        },
        label = "buttonScale"
    )

    FilledTonalButton(
        onClick = { if (isEnabled) onAllowUse(minutes) },
        enabled = isEnabled,
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
                alpha = if (isEnabled) 1f else 0.8f
            },
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AnimatedContent(
            targetState = isEnabled,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + 
                 scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "buttonContent"
        ) { enabled ->
            if (!enabled) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { progressState.value },
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        stroke = Stroke(width = 6.dp.value),
                        trackStroke = Stroke(width = 6.dp.value),
                        wavelength = 12.dp
                    )
                    // Calculate countdown from progress for smooth transition
                    val secondsLeft = if (delaySeconds > 0) {
                        kotlin.math.ceil(delaySeconds * (1f - progressState.value)).toInt().coerceAtLeast(1)
                    } else 0
                    
                    if (secondsLeft > 0) {
                        Text(
                            text = secondsLeft.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = "$minutes mins",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
