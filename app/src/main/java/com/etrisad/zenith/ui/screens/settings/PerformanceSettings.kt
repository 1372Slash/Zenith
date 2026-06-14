package com.etrisad.zenith.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.etrisad.zenith.data.preferences.PerformanceConfig
import com.etrisad.zenith.data.preferences.PerformanceLevel
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.detectPreset
import com.etrisad.zenith.data.preferences.isPreset
import com.etrisad.zenith.data.preferences.toConfig
import com.etrisad.zenith.ui.components.focus.PreferenceCategory
import com.etrisad.zenith.util.isAccessibilityServiceEnabled
import kotlinx.coroutines.launch

@Composable
fun PerformanceSettings(
    preferences: UserPreferences,
    selectedLevel: PerformanceLevel,
    onSelectLevel: (PerformanceLevel) -> Unit,
) {
    val allLevels = PerformanceLevel.values()

    PreferenceCategory(title = "Performance Profile")

    allLevels.forEachIndexed { index, level ->
        val isSelected = preferences.performanceLevel == level
        val isChecked = selectedLevel == level
        val isCustom = level == PerformanceLevel.CUSTOM

        val shape = when {
            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isCustom && index == allLevels.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }

        val bgColor by animateColorAsState(
            targetValue = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else if (isCustom) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
            label = "preset_bg_${level.name}"
        )

        Surface(
            onClick = { onSelectLevel(level) },
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = bgColor,
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val circleBg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "circleBg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                    label = "borderColor"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "iconTint"
                )
                val iconState = when {
                    isSelected -> 0
                    isChecked -> 1
                    isCustom -> 2
                    else -> 3
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.5.dp, borderColor, CircleShape)
                        .clip(CircleShape)
                        .background(circleBg),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = iconState, animationSpec = spring(0.8f, 400f), label = "profileIcon") { state ->
                        when (state) {
                            0 -> Icon(Icons.Filled.Check, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                            1 -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                            2 -> Icon(Icons.Outlined.Tune, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = level.labelRes,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    if (isCustom && isSelected) {
                        Text(text = activeConfigSummary(preferences.buildPerformanceConfig()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(text = level.descriptionRes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (index < allLevels.lastIndex) {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Custom profile lets you tune every parameter manually",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun PerformanceTuningPanel(
    preferences: UserPreferences,
    selectedLevel: PerformanceLevel,
    onSelectedLevelChange: (PerformanceLevel) -> Unit,
    onApplyCustomSettings: (PerformanceConfig) -> Unit,
    onSetPerformanceLevel: ((PerformanceLevel) -> Unit)? = null,
    onRegisterApplyAction: ((() -> Unit) -> Unit)? = null,
    onResetPerfMonDelays: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var tuningExpanded by remember { mutableStateOf(selectedLevel == PerformanceLevel.CUSTOM) }
    var delayExpanded by remember { mutableStateOf(selectedLevel == PerformanceLevel.CUSTOM) }

    var a11yActive by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().a11yActiveDelay
            else preferences.perfA11yActiveDelay
        )
    }
    var a11yInactive by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().a11yInactiveDelay
            else preferences.perfA11yInactiveDelay
        )
    }
    var screenOff by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().screenOffDelay
            else preferences.perfScreenOffDelay
        )
    }
    var powerSave by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().powerSaveDelay
            else preferences.perfPowerSaveDelay
        )
    }
    var usageCache by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().usageStatsCacheMs
            else preferences.perfUsageStatsCacheMs
        )
    }
    var dbWrite by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().shieldDbWriteMs
            else preferences.perfShieldDbWriteMs
        )
    }
    var dbWriteNear by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().shieldDbWriteNearMs
            else preferences.perfShieldDbWriteNearMs
        )
    }
    var launcherCache by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().launcherCacheMs
            else preferences.perfLauncherCacheMs
        )
    }

    var delayPowerSave by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monPowerSave
            else preferences.perfMonPowerSave
        )
    }
    var delayOverlayShowing by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monOverlayShowing
            else preferences.perfMonOverlayShowing
        )
    }
    var delayGoalNear by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monGoalNear
            else preferences.perfMonGoalNear
        )
    }
    var delayGoalMid by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monGoalMid
            else preferences.perfMonGoalMid
        )
    }
    var delayGoalFar by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monGoalFar
            else preferences.perfMonGoalFar
        )
    }
    var delayShieldNear by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monShieldNear
            else preferences.perfMonShieldNear
        )
    }
    var delayShieldMid by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monShieldMid
            else preferences.perfMonShieldMid
        )
    }
    var delayShieldFar by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monShieldFar
            else preferences.perfMonShieldFar
        )
    }
    var delayShieldVeryFar by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monShieldVeryFar
            else preferences.perfMonShieldVeryFar
        )
    }
    var delayDefault by remember {
        mutableStateOf(
            if (selectedLevel.isPreset()) selectedLevel.toConfig().monDefault
            else preferences.perfMonDefault
        )
    }

    LaunchedEffect(selectedLevel) {
        if (selectedLevel == PerformanceLevel.CUSTOM) {
            tuningExpanded = true
            delayExpanded = true
        }
        if (selectedLevel.isPreset()) {
            val cfg = selectedLevel.toConfig()
            a11yActive = cfg.a11yActiveDelay
            a11yInactive = cfg.a11yInactiveDelay
            screenOff = cfg.screenOffDelay
            powerSave = cfg.powerSaveDelay
            usageCache = cfg.usageStatsCacheMs
            dbWrite = cfg.shieldDbWriteMs
            dbWriteNear = cfg.shieldDbWriteNearMs
            launcherCache = cfg.launcherCacheMs
            delayPowerSave = cfg.monPowerSave
            delayOverlayShowing = cfg.monOverlayShowing
            delayGoalNear = cfg.monGoalNear
            delayGoalMid = cfg.monGoalMid
            delayGoalFar = cfg.monGoalFar
            delayShieldNear = cfg.monShieldNear
            delayShieldMid = cfg.monShieldMid
            delayShieldFar = cfg.monShieldFar
            delayShieldVeryFar = cfg.monShieldVeryFar
            delayDefault = cfg.monDefault
        }
    }

    val activeCfg = preferences.buildPerformanceConfig()

    val autoSwitch = {
        if (selectedLevel.isPreset()) {
            onSelectedLevelChange(PerformanceLevel.CUSTOM)
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAccessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        onRegisterApplyAction?.invoke {
            scope.launch {
                val config = PerformanceConfig(
                    a11yActiveDelay = a11yActive,
                    a11yInactiveDelay = a11yInactive,
                    screenOffDelay = screenOff,
                    powerSaveDelay = powerSave,
                    usageStatsCacheMs = usageCache,
                    shieldDbWriteMs = dbWrite,
                    shieldDbWriteNearMs = dbWriteNear,
                    launcherCacheMs = launcherCache,
                    monPowerSave = delayPowerSave,
                    monOverlayShowing = delayOverlayShowing,
                    monGoalNear = delayGoalNear,
                    monGoalMid = delayGoalMid,
                    monGoalFar = delayGoalFar,
                    monShieldNear = delayShieldNear,
                    monShieldMid = delayShieldMid,
                    monShieldFar = delayShieldFar,
                    monShieldVeryFar = delayShieldVeryFar,
                    monDefault = delayDefault,
                )
                val detectedLevel = config.detectPreset()
                onApplyCustomSettings(config)
                onSelectedLevelChange(detectedLevel)
                onSetPerformanceLevel?.invoke(detectedLevel)
            }
        }
    }

    PreferenceCategory(title = "Instant Detection")
    TuningPermissionItemRow(
        title = "Accessibility Service",
        description = if (hasAccessibility) "Service is active and detecting launches instantly" 
                      else "Detect app launches instantly for peak performance",
        isGranted = hasAccessibility,
        onClick = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        },
        icon = Icons.Outlined.AccessibilityNew,
        position = TuningGroupPosition.Single
    )
    Spacer(modifier = Modifier.height(16.dp))

    PreferenceCategory(title = "Custom Tuning")

    val tuningChevronRotation by animateFloatAsState(
        targetValue = if (tuningExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f), label = "tuningChevronRotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { tuningExpanded = !tuningExpanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Tune, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Parameter Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (tuningExpanded) "Drag any slider then tap Apply to save"
                    else "Tap to expand custom tuning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (tuningExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(tuningChevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    AnimatedVisibility(
        visible = tuningExpanded,
        enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)),
        exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeOut(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))
    ) {
        Column {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Check Interval", Icons.Outlined.Timer)
                    Spacer(modifier = Modifier.height(8.dp))
                    SliderItem("Overlay Active", "How often to check when overlay is visible",
                        a11yActive / 1000f, 30f..600f, "s", Icons.Outlined.Visibility,
                        onChange = { a11yActive = (it * 1000).toLong(); autoSwitch() })
                    SliderItem("Overlay Hidden", "How often to check when overlay is hidden",
                        a11yInactive / 1000f, 1f..10f, "s", Icons.Outlined.VisibilityOff, 9,
                        onChange = { a11yInactive = (it * 1000).toLong(); autoSwitch() })
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Power Saving", Icons.Outlined.BatteryChargingFull)
                    Spacer(modifier = Modifier.height(8.dp))
                    SliderItem("Screen Off", "How often to check when screen is off",
                        screenOff / 60000f, 1f..30f, "min", Icons.Outlined.ScreenLockPortrait, 29,
                        onChange = { screenOff = (it * 60000).toLong(); autoSwitch() })
                    SliderItem("Battery Saver", "How often to check during power saving",
                        powerSave / 60000f, 1f..30f, "min", Icons.Outlined.BatteryStd, 29,
                        onChange = { powerSave = (it * 60000).toLong(); autoSwitch() })
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Data Refresh", Icons.Outlined.Storage)
                    Spacer(modifier = Modifier.height(8.dp))
                    SliderItem("Usage Stats", "Refresh app usage from system",
                        usageCache / 60000f, 0.17f..60f, "min", Icons.Outlined.Refresh,
                        onChange = { usageCache = (it * 60000).toLong(); autoSwitch() })
                    SliderItem("App List", "Refresh list of installed apps",
                        launcherCache / 60000f, 5f..240f, "min", Icons.Outlined.Apps,
                        onChange = { launcherCache = (it * 60000).toLong(); autoSwitch() })
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Storage", Icons.Outlined.Save)
                    Spacer(modifier = Modifier.height(8.dp))
                    SliderItem("Save Data", "Write usage data to storage",
                        dbWrite / 60000f, 1f..30f, "min", Icons.Outlined.Storage, 29,
                        onChange = { dbWrite = (it * 60000).toLong(); autoSwitch() })
                    SliderItem("Save Data (Near)", "More frequent saves near daily limit",
                        dbWriteNear / 60000f, 0.5f..10f, "min", Icons.Outlined.TimerOff,
                        onChange = { dbWriteNear = (it * 60000).toLong(); autoSwitch() })
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    val delayBottomCorner by animateDpAsState(
        targetValue = if (delayExpanded) 8.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f), label = "delayBottomCorner"
    )
    val delayChevronRotation by animateFloatAsState(
        targetValue = if (delayExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f), label = "delayChevronRotation"
    )
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = delayBottomCorner, bottomEnd = delayBottomCorner)).clickable { delayExpanded = !delayExpanded },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = delayBottomCorner, bottomEnd = delayBottomCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Scan Timing", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text("Fine-tune monitoring frequency for each scenario",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2)
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (delayExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(delayChevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    AnimatedVisibility(
        visible = delayExpanded,
        enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)),
        exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeOut(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))
    ) {
        Column {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Power & Overlay", Icons.Outlined.BatteryChargingFull)
                    Spacer(modifier = Modifier.height(8.dp))
                    DelaySliderItem(label = "Power Save Mode", description = "Checks less often when your battery is low", icon = Icons.Outlined.BatteryChargingFull, value = delayPowerSave, onValueChange = { v -> delayPowerSave = v; autoSwitch() }, onReset = { delayPowerSave = 5000L; autoSwitch() }, range = 500f..15000f)
                    DelaySliderItem(label = "Overlay Showing", description = "How often it checks while the overlay is visible", icon = Icons.Outlined.Visibility, value = delayOverlayShowing, onValueChange = { v -> delayOverlayShowing = v; autoSwitch() }, onReset = { delayOverlayShowing = 8000L; autoSwitch() }, range = 500f..15000f)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Goal Shield", Icons.Outlined.Flag)
                    Spacer(modifier = Modifier.height(8.dp))
                    DelaySliderItem(label = "Near (<1m)", description = "Time is almost up, check more often", icon = Icons.Outlined.Flag, value = delayGoalNear, onValueChange = { v -> delayGoalNear = v; autoSwitch() }, onReset = { delayGoalNear = 600L; autoSwitch() }, range = 100f..10000f)
                    DelaySliderItem(label = "Mid (<5m)", description = "A few minutes left, moderate checking", icon = Icons.Outlined.Flag, value = delayGoalMid, onValueChange = { v -> delayGoalMid = v; autoSwitch() }, onReset = { delayGoalMid = 1200L; autoSwitch() }, range = 100f..10000f)
                    DelaySliderItem(label = "Far (>5m)", description = "Plenty of time left, can take it easy", icon = Icons.Outlined.Flag, value = delayGoalFar, onValueChange = { v -> delayGoalFar = v; autoSwitch() }, onReset = { delayGoalFar = 1800L; autoSwitch() }, range = 100f..10000f)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Regular Shield", Icons.Outlined.Security)
                    Spacer(modifier = Modifier.height(8.dp))
                    DelaySliderItem(label = "Near (<1m)", description = "Almost done, keep a close eye", icon = Icons.Outlined.Security, value = delayShieldNear, onValueChange = { v -> delayShieldNear = v; autoSwitch() }, onReset = { delayShieldNear = 600L; autoSwitch() }, range = 100f..10000f)
                    DelaySliderItem(label = "Mid (>1m)", description = "Still going, check at a normal pace", icon = Icons.Outlined.Security, value = delayShieldMid, onValueChange = { v -> delayShieldMid = v; autoSwitch() }, onReset = { delayShieldMid = 1500L; autoSwitch() }, range = 100f..10000f)
                    DelaySliderItem(label = "Far (>10m)", description = "Long session ahead, no rush to check", icon = Icons.Outlined.Security, value = delayShieldFar, onValueChange = { v -> delayShieldFar = v; autoSwitch() }, onReset = { delayShieldFar = 3000L; autoSwitch() }, range = 100f..15000f)
                    DelaySliderItem(label = "Very Far (>1h)", description = "Hours left, barely needs checking", icon = Icons.Outlined.Security, value = delayShieldVeryFar, onValueChange = { v -> delayShieldVeryFar = v; autoSwitch() }, onReset = { delayShieldVeryFar = 5000L; autoSwitch() }, range = 100f..15000f)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupHeader("Default", Icons.Outlined.TouchApp)
                    Spacer(modifier = Modifier.height(8.dp))
                    DelaySliderItem(label = "Default Interval", description = "Used when nothing else is active", icon = Icons.Outlined.TouchApp, value = delayDefault, onValueChange = { v -> delayDefault = v; autoSwitch() }, onReset = { delayDefault = 1200L; autoSwitch() }, range = 100f..10000f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                delayPowerSave = 5000L; delayOverlayShowing = 8000L
                                delayGoalNear = 600L; delayGoalMid = 1200L; delayGoalFar = 1800L
                                delayShieldNear = 600L; delayShieldMid = 1500L; delayShieldFar = 3000L; delayShieldVeryFar = 5000L
                                delayDefault = 1200L
                                onResetPerfMonDelays?.invoke()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset All", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DelaySliderItem(
    label: String,
    description: String,
    icon: ImageVector,
    value: Long,
    onValueChange: (Long) -> Unit,
    onReset: () -> Unit,
    range: ClosedFloatingPointRange<Float> = 100f..5000f
) {
    val scope = rememberCoroutineScope()
    var localValue by remember { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    val animatable = remember { Animatable(value.toFloat()) }

    LaunchedEffect(value) {
        if (!isDragging) {
            try {
                animatable.animateTo(value.toFloat(), spring(0.8f, 400f))
            } catch (_: Exception) { }
        }
    }

    val displayValue = if (isDragging) localValue else animatable.value

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(text = description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = "${displayValue.toInt()}ms", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(32.dp))
            Slider(
                value = displayValue.coerceIn(range),
                onValueChange = {
                    isDragging = true
                    localValue = (Math.round(it / 10.0) * 10).toFloat()
                },
                onValueChangeFinished = {
                    scope.launch {
                        animatable.snapTo(localValue)
                        isDragging = false
                        onValueChange(localValue.toLong())
                    }
                },
                valueRange = range,
                modifier = Modifier.weight(1f).height(24.dp)
            )
        }
    }
}

@Composable
private fun GroupHeader(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

private fun fmtDur(ms: Long): String = when {
    ms >= 60000 -> {
        val m = ms / 60000
        val s = (ms % 60000) / 1000
        if (s > 0) "${m}m ${s}s" else "${m}min"
    }
    ms >= 1000 -> "${ms / 1000}s"
    else -> "${ms}ms"
}

@Composable
private fun SliderItem(
    label: String,
    desc: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    icon: ImageVector,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    val animatable = remember { Animatable(value) }

    LaunchedEffect(value) {
        if (!isDragging) {
            try {
                animatable.animateTo(value, spring(0.8f, 400f))
            } catch (_: Exception) { }
        }
    }

    val displayValue = if (isDragging) dragValue else animatable.value

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = fmtVal(displayValue, unit), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(32.dp))
            Slider(
                value = displayValue.coerceIn(range),
                onValueChange = {
                    isDragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    scope.launch {
                        animatable.snapTo(dragValue)
                        isDragging = false
                        onChange(dragValue)
                    }
                },
                valueRange = range,
                steps = steps,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun fmtVal(value: Float, unit: String): String = when {
    unit == "min" && value >= 60 -> "${(value / 60).toInt()}h"
    unit == "min" && value >= 1 -> "${value.toInt()}m"
    unit == "min" && value < 1 -> "${(value * 60).toInt()}s"
    else -> "${value.toInt()}s"
}

private fun activeConfigSummary(cfg: PerformanceConfig): String {
    val parts = mutableListOf<String>()
    parts.add("Active: ${fmtDur(cfg.a11yActiveDelay)}")
    parts.add("Hidden: ${fmtDur(cfg.a11yInactiveDelay)}")
    parts.add("Save: ${fmtDur(cfg.shieldDbWriteMs)}")
    return parts.joinToString(" · ")
}

private enum class TuningGroupPosition {
    Top, Middle, Bottom, Single
}

@Composable
private fun TuningPermissionItemRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    position: TuningGroupPosition = TuningGroupPosition.Middle
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bgColor"
    )

    val shape = when (position) {
        TuningGroupPosition.Top -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        TuningGroupPosition.Middle -> RoundedCornerShape(8.dp)
        TuningGroupPosition.Bottom -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        TuningGroupPosition.Single -> RoundedCornerShape(24.dp)
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
                modifier = Modifier.weight(1f),
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

            if (isGranted) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
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
