package com.etrisad.zenith.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.AppUsageMonitorService
import com.etrisad.zenith.service.SharedMonitoringState
import com.etrisad.zenith.util.canScheduleExactAlarms
import com.etrisad.zenith.util.hasCalendarPermission
import com.etrisad.zenith.util.hasNotificationPermission
import com.etrisad.zenith.util.hasUsageStatsPermission
import com.etrisad.zenith.util.isAccessibilityServiceEnabled
import com.etrisad.zenith.util.isAndroidGo
import com.etrisad.zenith.util.isIgnoringBatteryOptimizations
import com.etrisad.zenith.util.isNotificationListenerEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GroupPosition {
    Top, Middle, Bottom, Single
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionBottomSheet(
    preferencesRepository: UserPreferencesRepository,
    onDismissRequest: () -> Unit,
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences()
    )
    
    var hasUsageStats by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasNotifications by remember { mutableStateOf(hasNotificationPermission(context)) }
    var hasNotificationPolicy by remember { mutableStateOf((context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted) }
    var hasNotificationListener by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var hasCalendar by remember { mutableStateOf(hasCalendarPermission(context)) }
    var isBatteryOptimized by remember { mutableStateOf(!isIgnoringBatteryOptimizations(context)) }
    var canExactAlarm by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    
    var stabilityExpanded by remember { mutableStateOf(false) }
    var optionalExpanded by remember { mutableStateOf(false) }
    var showBankingWarning by remember { mutableStateOf(false) }

    val installedBankingApps = remember {
        val allInstalled = context.packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .toSet()
        SharedMonitoringState.FINANCIAL_APPS.filter { it in allInstalled }
    }

    val optionalAllGranted = if (preferences.accessibilityRequired) hasNotificationListener else (hasAccessibility && hasNotificationListener)
    val stabilityAllGranted = !isBatteryOptimized && canExactAlarm

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotifications = isGranted
            if (isGranted) {
                context.startService(Intent(context, AppUsageMonitorService::class.java).apply {
                    action = "SEND_TEST_NOTIFICATION"
                })
            }
        }
    )

    fun openAccessibilitySettings(ctx: android.content.Context) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        scope.launch {
            while (true) {
                delay(500)
                if (isAccessibilityServiceEnabled(ctx)) {
                    delay(300)
                    val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(launchIntent)
                    }
                    break
                }
            }
        }
    }

    val allGranted = hasUsageStats && hasOverlay && hasNotifications && hasNotificationPolicy && (hasAccessibility || !preferences.accessibilityRequired)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStats = hasUsageStatsPermission(context)
                hasOverlay = Settings.canDrawOverlays(context)
                hasAccessibility = isAccessibilityServiceEnabled(context)
                hasNotifications = hasNotificationPermission(context)
                hasNotificationPolicy = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
                hasNotificationListener = isNotificationListenerEnabled(context)
                hasCalendar = hasCalendarPermission(context)
                isBatteryOptimized = !isIgnoringBatteryOptimizations(context)
                canExactAlarm = canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Zenith needs these permissions to function properly and help you stay focused.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            PermissionItemRow(
                title = "Notifications",
                description = "For Goal Reminders and status",
                isGranted = hasNotifications,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                icon = Icons.Outlined.Notifications,
                position = GroupPosition.Top
            )

            Spacer(modifier = Modifier.height(4.dp))

            PermissionItemRow(
                title = "Usage Stats",
                description = "To track app usage time",
                isGranted = hasUsageStats,
                onClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                icon = Icons.Outlined.BarChart,
                position = GroupPosition.Middle
            )

            Spacer(modifier = Modifier.height(4.dp))

            PermissionItemRow(
                title = "Notification Policy",
                description = "Required for Bedtime DND",
                isGranted = hasNotificationPolicy,
                onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                icon = Icons.Outlined.DoNotDisturbOn,
                position = GroupPosition.Middle
            )

            Spacer(modifier = Modifier.height(4.dp))

            PermissionItemRow(
                title = "System Overlay",
                description = if (isAndroidGo(context)) "Required to show shields. May require ADB on some Go devices." else "To show the shield over apps",
                isGranted = hasOverlay,
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                icon = Icons.Outlined.Layers,
                position = if (preferences.accessibilityRequired) GroupPosition.Middle else GroupPosition.Bottom
            )

            if (preferences.accessibilityRequired) {
                Spacer(modifier = Modifier.height(4.dp))
                PermissionItemRow(
                    title = "Accessibility Service",
                    description = "To detect app launches instantly",
                    isGranted = hasAccessibility,
                    onClick = {
                        if (!preferences.bankingWarningDismissed) {
                            showBankingWarning = true
                        } else {
                            openAccessibilitySettings(context)
                        }
                    },
                    onInfoClick = { showBankingWarning = true },
                    icon = Icons.Outlined.AccessibilityNew,
                    position = GroupPosition.Bottom
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CollapsibleSection(
                title = "Optional Service",
                icon = Icons.Outlined.AccessibilityNew,
                expanded = optionalExpanded,
                onToggle = { optionalExpanded = !optionalExpanded },
                isAllGranted = optionalAllGranted
            ) {
                Column {
                    if (!preferences.accessibilityRequired) {
                        PermissionItemRow(
                            title = "Accessibility Service",
                            description = "To detect app launches instantly",
                            isGranted = hasAccessibility,
                            onClick = {
                                if (!preferences.bankingWarningDismissed) {
                                    showBankingWarning = true
                                } else {
                                    openAccessibilitySettings(context)
                                }
                            },
                            onInfoClick = { showBankingWarning = true },
                            icon = Icons.Outlined.AccessibilityNew,
                            position = GroupPosition.Top,
                            isInsideCollapse = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    PermissionItemRow(
                        title = "Intercept Service",
                        description = "Required to block schedule notifications",
                        isGranted = hasNotificationListener,
                        onClick = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        icon = Icons.Outlined.PhonelinkErase,
                        position = if (preferences.accessibilityRequired) GroupPosition.Top else GroupPosition.Middle,
                        isInsideCollapse = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PermissionItemRow(
                        title = "Calendar Access",
                        description = "Show current calendar event during app delay",
                        isGranted = hasCalendar,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        icon = Icons.Outlined.CalendarMonth,
                        position = GroupPosition.Bottom,
                        isInsideCollapse = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            CollapsibleSection(
                title = "App Stability",
                icon = Icons.Outlined.AutoGraph,
                expanded = stabilityExpanded,
                onToggle = { stabilityExpanded = !stabilityExpanded },
                isAllGranted = stabilityAllGranted
            ) {
                Column {
                    PermissionItemRow(
                        title = "Battery Optimization",
                        description = "Prevent system from killing Zenith",
                        isGranted = !isBatteryOptimized,
                        icon = Icons.Outlined.BatteryStd,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        position = GroupPosition.Top,
                        isInsideCollapse = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PermissionItemRow(
                        title = "Exact Alarms",
                        description = "Required for precise reset tasks",
                        isGranted = canExactAlarm,
                        icon = Icons.Outlined.Alarm,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        position = GroupPosition.Bottom,
                        isInsideCollapse = true
                    )
                }
            }
            
            AnimatedVisibility(
                visible = allGranted,
                enter = fadeIn(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ),
                exit = fadeOut(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
            ) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    ZenithButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onAllPermissionsGranted()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        text = "Everything is Ready!"
                    )
                }
            }
        }
    }

    if (showBankingWarning) {
        BankingWarningBottomSheet(
            onDismiss = { showBankingWarning = false },
            onProceed = {
                showBankingWarning = false
                openAccessibilitySettings(context)
            },
            onDontShowAgain = {
                scope.launch {
                    preferencesRepository.setBankingWarningDismissed(true)
                }
            }
        )
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    isAllGranted: Boolean = false,
    content: @Composable () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isAllGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                      else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "sectionColor"
    )

    val contentColor = MaterialTheme.colorScheme.onSurface

    val iconPillColor = if (isAllGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.secondaryContainer

    val iconTint = if (isAllGranted) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.secondary

    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(48.dp)
                            .background(iconPillColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(48.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(modifier = Modifier.padding(6.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    position: GroupPosition = GroupPosition.Middle,
    isInsideCollapse: Boolean = false,
    onInfoClick: (() -> Unit)? = null
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isGranted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            isInsideCollapse -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bgColor"
    )

    val shape = when (position) {
        GroupPosition.Top -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        GroupPosition.Middle -> RoundedCornerShape(8.dp)
        GroupPosition.Bottom -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        GroupPosition.Single -> RoundedCornerShape(24.dp)
    }

    Surface(
        onClick = if (!isGranted) onClick else ({}),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.CenterVertically)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGranted) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    if (onInfoClick != null) {
                        IconButton(
                            onClick = onInfoClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Grant",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

