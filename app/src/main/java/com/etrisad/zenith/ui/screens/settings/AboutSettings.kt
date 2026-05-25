package com.etrisad.zenith.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.BuildConfig
import com.etrisad.zenith.R

@Composable
fun AboutSettings(
    developerModeEnabled: Boolean,
    onDeveloperModeChange: (Boolean) -> Unit,
    checkUpdateOnStart: Boolean,
    onCheckUpdateOnStartChange: (Boolean) -> Unit,
    isCheckingForUpdate: Boolean,
    onCheckForUpdate: () -> Unit,
    onViewChangelog: () -> Unit
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME

    Column {
        PreferenceCategory(title = "About")

        AppInfoCard(
            versionName = versionName,
            developerModeEnabled = developerModeEnabled,
            onDeveloperModeChange = onDeveloperModeChange,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        if (BuildConfig.SHOW_UPDATES) {
            Spacer(modifier = Modifier.height(4.dp))
            AboutActionCard(
                title = if (isCheckingForUpdate) "Checking for update..." else "Check for Update",
                icon = Icons.Outlined.Update,
                shape = RoundedCornerShape(8.dp),
                onClick = onCheckForUpdate
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsToggle(
                title = "Check update on start",
                description = "Automatically check for updates when the app starts",
                checked = checkUpdateOnStart,
                onCheckedChange = onCheckUpdateOnStartChange,
                icon = Icons.Outlined.SystemUpdate,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            AboutActionCard(
                title = "View Changelog",
                icon = Icons.Outlined.History,
                shape = RoundedCornerShape(8.dp),
                onClick = onViewChangelog
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        DeveloperCard(
            name = "1372Slash",
            onGithubClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/1372Slash"))
                context.startActivity(intent)
            },
            onWebsiteClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://1372slash.vercel.app"))
                context.startActivity(intent)
            },
            onWhatsAppClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://whatsapp.com/channel/0029VbAKkhlAojYyegxvV83V"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        AboutActionCard(
            title = "View Repository",
            icon = Icons.Outlined.Code,
            shape = RoundedCornerShape(8.dp),
            onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/1372Slash/Zenith"))
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(4.dp))
        AboutActionCard(
            title = "GNU General Public Licence v3.0",
            icon = Icons.Outlined.Description,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/1372Slash/Zenith/blob/master/LICENSE"))
                context.startActivity(intent)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppInfoCard(
    versionName: String,
    developerModeEnabled: Boolean,
    onDeveloperModeChange: (Boolean) -> Unit,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var clickCount by remember { mutableIntStateOf(0) }
    
    val logoShape = remember {
        GenericShape { size, _ ->
            val materialPath = MaterialShapes.Sunny.toPath()
            val composePath = materialPath.asComposePath()
            val matrix = Matrix()
            matrix.scale(size.width, size.height)
            composePath.transform(matrix)
            addPath(composePath)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                clickCount++
                if (!developerModeEnabled) {
                    if (clickCount >= 3) {
                        onDeveloperModeChange(true)
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        Toast.makeText(context, "Developer mode enabled!", Toast.LENGTH_SHORT).show()
                        clickCount = 0
                    } else {
                        val remaining = 3 - clickCount
                        Toast.makeText(context, "Tap $remaining more times for Developer Mode", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = logoShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zenith",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape
                ) {
                    Text(
                        text = "v$versionName",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperCard(
    name: String,
    onGithubClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    shape: Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
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
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "Created by $name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickLinkButton(
                    icon = Icons.Outlined.Code,
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                    onClick = onGithubClick
                )
                QuickLinkButton(
                    icon = Icons.Outlined.Language,
                    shape = RoundedCornerShape(6.dp),
                    onClick = onWebsiteClick
                )
                QuickLinkButton(
                    icon = Icons.Outlined.Chat,
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 6.dp, bottomStart = 6.dp),
                    onClick = onWhatsAppClick
                )
            }
        }
    }
}

@Composable
private fun QuickLinkButton(
    icon: ImageVector,
    shape: Shape,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "quickLinkScale"
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(width = 44.dp, height = 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
