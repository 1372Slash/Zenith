package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.focus.CardGroup
import kotlinx.coroutines.launch

private data class PrivacySection(
    val title: String,
    val icon: ImageVector,
    val summary: String,
    val details: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyInfoBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val sections = remember {
        listOf(
            PrivacySection(
                title = "Accessibility Service",
                icon = Icons.Outlined.AccessibilityNew,
                summary = "Detects app launches in real time. Standard mode reads window titles and active browser URLs; Limited mode requests app-switch events only.",
                details = listOf(
                    "Why it's needed: to detect app launches instantly so Zenith can show mindful messages and shields.",
                    "Standard mode reads window titles and active browser URLs for website monitoring. It never stores what you type or the content of your screen.",
                    "Limited mode requests ONLY app-switch events. Content-change events, text-change events, interactive window trees, and key-event filtering are not requested. Browser URL tracking and the back-key overlay shortcut stop working; mindful messages and app shields keep working.",
                    "No keylogging: Zenith never listens to or records keystrokes in either mode.",
                    "If access is revoked, instant app-launch detection, browser tracking, and the back-key shortcut stop. With Usage Stats access, Zenith falls back to polling, so shields may arrive with a delay.",
                    "Limited mode reduces what Android sends to Zenith. The Accessibility permission itself stays granted until you revoke it in Android Settings."
                )
            ),
            PrivacySection(
                title = "Notification Interception",
                icon = Icons.Outlined.NotificationsActive,
                summary = "Optional. Pauses distracting notifications during focus sessions or schedules and can restore them afterwards.",
                details = listOf(
                    "Why it's needed: to pause distracting notifications during active schedules or Pomodoro sessions and optionally restore them afterwards.",
                    "Turn Notification Interception off in Privacy settings and Zenith ignores notifications even if Android notification access is still granted.",
                    "If disabled, notification pausing and post-focus restoration stop. Mindful messages and app shields are unaffected.",
                    "Content control: with Store Notification Contents off, titles and body text are never saved. Turning it off also scrubs title/body text already stored locally.",
                    "Private apps and Excluded Notification Apps are never inspected for content, silenced, or stored. Adding an app to either list deletes its stored intercepted notifications.",
                    "Notification data lives in Zenith's local app database. It is not encrypted at rest, but the Android app sandbox stops other apps from reading it, and Zenith never uploads it.",
                    "Disabling interception does not revoke Android notification access. Revoke it in Android Settings if you want the system permission removed entirely."
                )
            ),
            PrivacySection(
                title = "Local Storage & Cloud Backup",
                icon = Icons.Outlined.Storage,
                summary = "Usage history and notification logs stay on this device. Android cloud backup is blocked; local backups you create include the full database.",
                details = listOf(
                    "Local database: your usage history and notification logs live only in Zenith's local database on this device.",
                    "Android Cloud Backup: the system backup rules allow only two local preference files, so zenith_database is never included in Android Auto Backup or device-to-device transfers.",
                    "What Android may back up: Zenith preferences (theme, targets, private and hidden apps) and lightweight app state such as streaks, timers, and the last known daily total. Usage history and notification logs are not included.",
                    "Local backups: Manual Backup and Auto Backup in Data Management write a ZIP with the full local database (including usage history) and the settings preference file. Store those files somewhere private or disable local auto backup.",
                    "Private apps: marking an app as private deletes its stored usage rows, intercepted notifications, and shield configuration from Zenith.",
                    "Hidden exclusions remove an app from screen-time totals, history, and streaks without deleting data or stopping monitoring.",
                    "Data control: you can clear history or reset database logs anytime from Data Management settings."
                )
            ),
            PrivacySection(
                title = "Private & Hidden vs Bypass Restrictions",
                icon = Icons.Outlined.Security,
                summary = "Private and Hidden control what Zenith sees or counts. Bypass Restrictions only skips blocking.",
                details = listOf(
                    "Private: fully ignored; stored data is purged.",
                    "Hidden: still monitored, but excluded from usage totals, history, and streaks.",
                    "Bypass Restrictions: still tracked, but skips schedules, shields, and goals.",
                    "In short: Private & Hidden Apps control privacy and stats; Bypass Restrictions controls blocking."
                )
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Privacy & Data Guarantees",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Everything stays on your device. Here is what Zenith can access and where your data lives.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        sections.forEachIndexed { index, section ->
                            PrivacySectionCard(
                                section = section,
                                shape = when {
                                    sections.size == 1 -> RoundedCornerShape(24.dp)
                                    index == 0 -> RoundedCornerShape(
                                        topStart = 24.dp,
                                        topEnd = 24.dp,
                                        bottomStart = 8.dp,
                                        bottomEnd = 8.dp
                                    )
                                    index == sections.lastIndex -> RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = 24.dp,
                                        bottomEnd = 24.dp
                                    )
                                    else -> RoundedCornerShape(8.dp)
                                }
                            )
                            if (index != sections.lastIndex) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(120.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            startY = 0f,
                            endY = 40f
                        )
                    )
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Understood",
                    size = ZenithButtonSize.ExtraLarge,
                    fillMaxWidth = true
                )
            }
        }
    }
}

@Composable
private fun PrivacySectionCard(
    section: PrivacySection,
    shape: Shape
) {
    var expanded by remember { mutableStateOf(false) }

    CardGroup(
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = section.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            ZenithButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.Start),
                text = if (expanded) "Hide details" else "Show details",
                icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                type = ZenithButtonType.Text,
                size = ZenithButtonSize.Small
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 220)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 180))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    section.details.forEach { detail ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(14.dp)
                            )
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
