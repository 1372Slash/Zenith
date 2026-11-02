package com.etrisad.zenith.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.R
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.focus.CardGroup
import com.etrisad.zenith.ui.components.focus.PreferenceCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedPathHubBottomSheet(
    controller: OnboardingController,
    onDismiss: () -> Unit
) {
    val completedIds = controller.completedChapterIds()
    val totalChapters = guideChapters.size

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    val middleShape = RoundedCornerShape(8.dp)
    val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.onboarding_open_path),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.onboarding_path_card_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(
                                R.string.onboarding_hub_progress,
                                completedIds.size,
                                totalChapters
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        PreferenceCategory(title = stringResource(R.string.onboarding_hub_chapters))

                        guideChapters.forEachIndexed { index, chapter ->
                            if (index > 0) Spacer(modifier = Modifier.height(4.dp))

                            val isCompleted = chapter.id in completedIds
                            val badgeName = stringResource(chapter.badgeNameRes)
                            val title = stringResource(chapter.titleRes)
                            val subtitle = stringResource(chapter.subtitleRes)
                            val shape = when (index) {
                                0 -> topShape
                                guideChapters.lastIndex -> bottomShape
                                else -> middleShape
                            }
                            val iconContainer = when (index % 3) {
                                0 -> MaterialTheme.colorScheme.primaryContainer
                                1 -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                            val iconTint = when (index % 3) {
                                0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                1 -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onTertiaryContainer
                            }

                            CardGroup(shape = shape, containerColor = containerColor) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            controller.replayChapter(chapter.id)
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(iconContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = chapter.tier.icon,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(
                                                R.string.onboarding_hub_chapter_title,
                                                chapter.order,
                                                title
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.onboarding_hub_chapter_summary,
                                                subtitle,
                                                badgeName
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (isCompleted) {
                                            Icons.Outlined.CheckCircle
                                        } else {
                                            Icons.Outlined.PlayArrow
                                        },
                                        contentDescription = stringResource(
                                            if (isCompleted) R.string.onboarding_hub_status_completed
                                            else R.string.onboarding_hub_status_start
                                        ),
                                        tint = if (isCompleted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                        onDismiss()
                        controller.replayAll()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.onboarding_path_start)
                )
            }
        }
    }
}