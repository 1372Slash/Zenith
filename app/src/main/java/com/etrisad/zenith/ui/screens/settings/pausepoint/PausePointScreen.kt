package com.etrisad.zenith.ui.screens.settings.pausepoint

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskType
import com.etrisad.zenith.ui.screens.settings.PreferenceCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PausePointScreen(
    preferences: UserPreferences,
    innerPadding: PaddingValues,
    preferencesRepository: UserPreferencesRepository,
    onTaskTypeClick: (PausePointTaskType) -> Unit = {},
    onBack: () -> Unit = {},
    backInterceptor: MutableState<() -> Boolean> = remember { mutableStateOf({ false }) }
) {
    val coroutineScope = rememberCoroutineScope()
    val isSingleType = preferences.pausePointTaskTypes.size == 1
    var showExitWarning by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun hideWarningSheet(afterHide: () -> Unit = {}) {
        coroutineScope.launch {
            sheetState.hide()
            showExitWarning = false
            afterHide()
        }
    }

    val tryInterceptBack: () -> Boolean = {
        if (isSingleType && !showExitWarning) {
            showExitWarning = true
            true
        } else {
            false
        }
    }
    val currentTryInterceptBack by rememberUpdatedState(tryInterceptBack)
    LaunchedEffect(Unit) {
        backInterceptor.value = { currentTryInterceptBack() }
    }
    DisposableEffect(Unit) {
        onDispose { backInterceptor.value = { false } }
    }

    BackHandler(enabled = isSingleType && !showExitWarning) {
        showExitWarning = true
    }

    if (showExitWarning) {
        val onlyTypeName = preferences.pausePointTaskTypes.firstOrNull()?.displayName ?: "Unknown"
        ModalBottomSheet(
            onDismissRequest = { hideWarningSheet() },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Only one task type?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "\u201C$onlyTypeName\u201D is your only enabled type - " +
                        "that can make Pause Point too hard if something unexpected happens, " +
                        "like a lost tag, a missing code, or a failing sensor. " +
                        "Add one more type as a backup plan before leaving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                ZenithButton(
                    onClick = { hideWarningSheet() },
                    text = "Add another type",
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.ExtraLarge,
                    fillMaxWidth = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                ZenithButton(
                    onClick = { hideWarningSheet { onBack() } },
                    text = "Leave anyway",
                    type = ZenithButtonType.Text,
                    contentColor = MaterialTheme.colorScheme.error,
                    size = ZenithButtonSize.ExtraLarge,
                    fillMaxWidth = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            )
    ) {
        PreferenceCategory(title = "Task Type")

        PausePointTaskType.entries.forEachIndexed { index, taskType ->
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                PausePointTaskType.entries.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(8.dp)
            }
            val checked = taskType in preferences.pausePointTaskTypes
            val onCheckedChange: (Boolean) -> Unit = { isNowChecked ->
                val updated = if (isNowChecked) {
                    preferences.pausePointTaskTypes + taskType
                } else {
                    preferences.pausePointTaskTypes - taskType
                }
                coroutineScope.launch { preferencesRepository.setPausePointTaskTypes(updated) }
            }

            SettingsToggleWithNavigation(
                title = taskType.displayName,
                description = taskType.description,
                summary = pausePointSummary(taskType, preferences),
                checked = checked,
                onCheckedChange = onCheckedChange,
                icon = taskType.icon,
                shape = shape,
                onClick = { onTaskTypeClick(taskType) }
            )
            if (index != PausePointTaskType.entries.lastIndex) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun pausePointSummary(taskType: PausePointTaskType, preferences: UserPreferences): String = when (taskType) {
    PausePointTaskType.WAITING -> "${preferences.pausePointWaitingVariants.size} sub task${if (preferences.pausePointWaitingVariants.size == 1) "" else "s"}"
    PausePointTaskType.BREATHING -> "${preferences.pausePointBreathingVariants.size} sub task${if (preferences.pausePointBreathingVariants.size == 1) "" else "s"}"
    PausePointTaskType.WALK -> "${preferences.pausePointWalkVariants.size} sub task${if (preferences.pausePointWalkVariants.size == 1) "" else "s"}"
    PausePointTaskType.QR_SCAN -> "${preferences.pausePointQrCodes.size} saved code${if (preferences.pausePointQrCodes.size == 1) "" else "s"}"
    PausePointTaskType.NFC_SCAN -> "${preferences.pausePointNfcTagIds.size} saved tag${if (preferences.pausePointNfcTagIds.size == 1) "" else "s"}"
    PausePointTaskType.NUMBER_SLIDE -> "${preferences.pausePointNumberSlideVariants.size} sub task${if (preferences.pausePointNumberSlideVariants.size == 1) "" else "s"}"
    PausePointTaskType.SWITCH -> "${preferences.pausePointSwitchVariants.size} sub task${if (preferences.pausePointSwitchVariants.size == 1) "" else "s"}"
    PausePointTaskType.MATH -> "${preferences.pausePointMathVariants.size} sub task${if (preferences.pausePointMathVariants.size == 1) "" else "s"}"
    PausePointTaskType.COUNTING -> "${preferences.pausePointCountingVariants.size} sub task${if (preferences.pausePointCountingVariants.size == 1) "" else "s"}"
    PausePointTaskType.TYPING -> "${preferences.pausePointTypingVariants.size} text${if (preferences.pausePointTypingVariants.size == 1) "" else "s"}"
    PausePointTaskType.CHOOSE_APP -> "Uses goal apps automatically"
}

@Composable
private fun SettingsToggleWithNavigation(
    title: String,
    description: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            VerticalDivider(
                modifier = Modifier.height(40.dp),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    val thumbSize by animateDpAsState(
                        targetValue = if (checked) 28.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "thumb_size"
                    )

                    val iconColor by animateColorAsState(
                        targetValue = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "switch_icon_color"
                    )

                    Box(
                        modifier = Modifier.size(thumbSize),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = checked,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                            },
                            label = "switch_icon_anim"
                        ) { isChecked ->
                            Icon(
                                imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(if (isChecked) 18.dp else 16.dp),
                                tint = iconColor
                            )
                        }
                    }
                }
            )
        }
    }
}