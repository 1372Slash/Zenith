package com.etrisad.zenith.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.focus.PreferenceCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LevelScreen(
    profileViewModel: ProfileViewModel,
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues
) {
    val state by profileViewModel.uiState.collectAsState()
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.etrisad.zenith.data.preferences.UserPreferences()
    )
    val scope = rememberCoroutineScope()
    val level = state.level

    val equippedTitleId = remember(preferences.userTitle, level) {
        LEVEL_TITLES.find { it.id == preferences.userTitle }
            ?.takeIf { it.requiredLevel <= level }?.id ?: ""
    }
    val equippedBorderId = remember(preferences.userAvatarBorder, level) {
        AVATAR_BORDERS.find { it.id == preferences.userAvatarBorder }
            ?.takeIf { it.requiredLevel <= level }?.id ?: ""
    }
    val nextReward = remember(level) { nextLevelReward(level) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "overview") {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .border(
                                    3.dp,
                                    AVATAR_BORDERS.find { it.id == equippedBorderId }
                                        ?.let { borderBrushFor(it) }
                                        ?: SolidColor(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        ),
                                    CircleShape
                                )
                                .padding(3.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$level",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${state.xpTotal} XP",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = nextReward?.let { (req, name) ->
                                    "Next reward: $name at Level $req"
                                } ?: "All level rewards unlocked",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearWavyProgressIndicator(
                        progress = { state.levelProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        stroke = Stroke(
                            width = with(LocalDensity.current) { 4.dp.toPx() },
                            cap = StrokeCap.Round
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${xpToNextLevel(state.xpTotal)} XP to Level ${level + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item(key = "titles") {
            Column {
                PreferenceCategory(title = "Titles")
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        LEVEL_TITLES.forEach { title ->
                            val unlocked = level >= title.requiredLevel
                            val equipped = equippedTitleId == title.id && unlocked
                            RewardRow(
                                name = title.name,
                                requirement = "Level ${title.requiredLevel}",
                                unlocked = unlocked,
                                equipped = equipped,
                                leading = {
                                    Icon(
                                        imageVector = Icons.Outlined.WorkspacePremium,
                                        contentDescription = null,
                                        tint = if (unlocked) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setUserTitle(
                                            if (equipped) "" else title.id
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        item(key = "borders") {
            Column {
                PreferenceCategory(title = "Avatar Borders")
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        AVATAR_BORDERS.forEach { border ->
                            val unlocked = level >= border.requiredLevel
                            val equipped = equippedBorderId == border.id && unlocked
                            RewardRow(
                                name = "${border.name} - ${border.colors.size} color" +
                                    if (border.colors.size > 1) "s" else "",
                                requirement = "Level ${border.requiredLevel}",
                                unlocked = unlocked,
                                equipped = equipped,
                                leading = {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .border(
                                                3.dp,
                                                if (unlocked) borderBrushFor(border)
                                                else SolidColor(
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                ),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!unlocked) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setUserAvatarBorder(
                                            if (equipped) "" else border.id
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        item(key = "history") {
            Column {
                PreferenceCategory(title = "Daily XP")
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Saved ${formatCompactDuration(state.totalSavedMillis)} in total",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (state.xpHistory.isEmpty()) {
                            Text(
                                text = "No XP recorded yet - check back tomorrow",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.xpHistory.forEach { day ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = prettyProfileDate(day.date),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "+${day.xp} XP",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Shields earn XP for staying under the limit " +
                                "(less usage = more XP, over the limit = 0). Goals earn " +
                                "XP for reaching the target, plus a bonus for going over. " +
                                "XP is awarded once per day. Each level needs more XP " +
                                "than the last.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
