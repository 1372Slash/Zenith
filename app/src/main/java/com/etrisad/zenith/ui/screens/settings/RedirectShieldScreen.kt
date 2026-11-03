package com.etrisad.zenith.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.focus.rememberAccessibilityServiceEnabled
import com.etrisad.zenith.ui.screens.settings.redirectshield.RedirectShieldAppPickerSheet
import com.etrisad.zenith.ui.screens.settings.redirectshield.RedirectShieldAppRole
import kotlinx.coroutines.launch

@Composable
fun RedirectShieldScreen(
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues,
) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences()
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val accessibilityEnabled = rememberAccessibilityServiceEnabled()
    var pickerRole by remember { mutableStateOf<RedirectShieldAppRole?>(null) }

    pickerRole?.let { role ->
        RedirectShieldAppPickerSheet(
            role = role,
            preferencesRepository = preferencesRepository,
            onDismiss = { pickerRole = null }
        )
    }

    val shieldedCount = preferences.redirectShieldApps.size
    val whitelistCount = preferences.redirectShieldWhitelistedApps.size
    val triggerCount = preferences.redirectShieldTriggerApps.size
    val isConfigured = shieldedCount > 0 && triggerCount > 0

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !preferences.redirectShieldEnabled,
            enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                expandVertically(
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                shrinkVertically(
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = innerPadding.calculateTopPadding() + 16.dp)
            ) {
                RedirectShieldToggleCard(
                    enabled = preferences.redirectShieldEnabled,
                    onToggle = { enabled ->
                        coroutineScope.launch {
                            preferencesRepository.setRedirectShieldEnabled(enabled)
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = preferences.redirectShieldEnabled,
            enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { height -> height / 12 },
            exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                slideOutVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { height -> height / 16 },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                )
            ) {
                if (!accessibilityEnabled) {
                    item(key = "redirect_accessibility") {
                        SettingsActionItem(
                            title = "Accessibility Service Off",
                            summary = "Turn on Zenith's accessibility service so app switches can be detected",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            icon = Icons.Outlined.AccessibilityNew,
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }

                item(key = "redirect_lists_header") {
                    SectionHeader(title = "App lists")
                }

                item(key = "redirect_shielded") {
                    SettingsActionItem(
                        title = "Shielded Apps",
                        summary = if (shieldedCount == 0) {
                            "No apps shielded yet. Tap to choose the apps to block."
                        } else if (shieldedCount == 1) {
                            "1 app sends you back to your previous app"
                        } else {
                            "$shieldedCount apps send you back to your previous app"
                        },
                        onClick = { pickerRole = RedirectShieldAppRole.SHIELDED },
                        icon = Icons.Outlined.Security,
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        )
                    )
                }

                item(key = "redirect_whitelist") {
                    SettingsActionItem(
                        title = "Whitelisted Apps",
                        summary = if (whitelistCount == 0) {
                            "No exceptions. Every app is redirected while armed."
                        } else if (whitelistCount == 1) {
                            "1 app may open shielded apps"
                        } else {
                            "$whitelistCount apps may open shielded apps"
                        },
                        onClick = { pickerRole = RedirectShieldAppRole.WHITELISTED },
                        icon = Icons.Outlined.Apps,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                item(key = "redirect_trigger") {
                    SettingsActionItem(
                        title = "Trigger Apps",
                        summary = if (triggerCount == 0) {
                            "No trigger apps yet. Tap to choose what activates the shield."
                        } else if (triggerCount == 1) {
                            "1 app arms the shield"
                        } else {
                            "$triggerCount apps arm the shield"
                        },
                        onClick = { pickerRole = RedirectShieldAppRole.TRIGGER },
                        icon = Icons.Outlined.Shield,
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        )
                    )
                }

                if (!isConfigured) {
                    item(key = "redirect_setup_warning") {
                        Spacer(modifier = Modifier.height(8.dp))
                        RedirectInfoCard(
                            icon = Icons.Outlined.Shield,
                            title = "Setup incomplete",
                            body = if (triggerCount == 0 && shieldedCount == 0) {
                                "Pick at least one trigger app and one shielded app before the shield can protect you."
                            } else if (triggerCount == 0) {
                                "Pick at least one trigger app so the shield knows when to arm."
                            } else {
                                "Pick at least one shielded app so the shield has something to block."
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                item(key = "redirect_how_header") {
                    SectionHeader(title = "How it works")
                }

                item(key = "redirect_how") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RedirectStep(
                                number = 1,
                                title = "Trigger apps arm the shield",
                                body = "While a trigger app is in the foreground the shield stays armed."
                            )
                            RedirectStep(
                                number = 2,
                                title = "Shielded apps bounce you back",
                                body = "Opening a shielded app in that window returns you to the app you came from."
                            )
                            RedirectStep(
                                number = 3,
                                title = "Whitelisted apps are the exception",
                                body = "Whitelisted apps can open shielded apps, and being in one disarms the shield."
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedirectShieldToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "redirectToggleCardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Redirect Shield",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (enabled) {
                        "Sends shielded apps back to your previous app"
                    } else {
                        "Currently disabled"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                thumbContent = {
                    val thumbSize by animateDpAsState(
                        targetValue = if (enabled) 28.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "redirect_thumb_size"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "redirect_switch_icon_color"
                    )
                    Box(
                        modifier = Modifier.size(thumbSize),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = enabled,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                    scaleIn(
                                        initialScale = 0.5f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioHighBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )).togetherWith(
                                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleOut(
                                            targetScale = 0.5f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                )
                            },
                            label = "redirect_switch_icon_anim"
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun RedirectInfoCard(
    icon: ImageVector,
    title: String,
    body: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun RedirectStep(
    number: Int,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
