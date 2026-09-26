package com.etrisad.zenith.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.focus.PreferenceCategory
import kotlinx.coroutines.launch
private fun rewardGroupShape(index: Int, total: Int, highlighted: Boolean) = when {
    highlighted || total == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(
        topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp
    )
    index == total - 1 -> RoundedCornerShape(
        topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp
    )
    else -> RoundedCornerShape(8.dp)
}

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
                                .avatarRing(
                                    AVATAR_BORDERS.find { it.id == equippedBorderId }, 3.dp,
                                    SolidColor(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(LEVEL_TITLES, key = { _, t -> "title-${t.id}" }) { index, title ->
                            val unlocked = level >= title.requiredLevel
                            val equipped = equippedTitleId == title.id && unlocked
                            Card(
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setUserTitle(
                                            if (equipped) "" else title.id
                                        )
                                    }
                                },
                                enabled = unlocked,
                                shape = rewardGroupShape(index, LEVEL_TITLES.size, unlocked),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (unlocked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.width(124.dp).aspectRatio(0.78f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (unlocked) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.WorkspacePremium,
                                            contentDescription = null,
                                            tint = if (unlocked) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = title.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (unlocked) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Lv ${title.requiredLevel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (unlocked) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Box(
                                        modifier = Modifier.height(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (equipped) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircleOutline,
                                                contentDescription = "Equipped",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else if (!unlocked) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = "Locked",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "borders") {
            Column {
                PreferenceCategory(title = "Avatar Borders")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(AVATAR_BORDERS, key = { _, b -> "border-${b.id}" }) { index, border ->
                            val unlocked = level >= border.requiredLevel
                            val equipped = equippedBorderId == border.id && unlocked
                            Card(
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setUserAvatarBorder(
                                            if (equipped) "" else border.id
                                        )
                                    }
                                },
                                enabled = unlocked,
                                shape = rewardGroupShape(index, AVATAR_BORDERS.size, unlocked),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (unlocked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.width(124.dp).aspectRatio(0.78f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .then(
                                                if (unlocked) Modifier.avatarRing(
                                                    border, 4.dp,
                                                    SolidColor(
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                                )
                                                else Modifier.border(
                                                    4.dp,
                                                    SolidColor(
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    ),
                                                    CircleShape
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!unlocked) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = "Locked",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = border.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (unlocked) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Lv ${border.requiredLevel} - ${border.colors.size} color" +
                                            if (border.colors.size > 1) "s" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (unlocked) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        modifier = Modifier.height(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (equipped) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircleOutline,
                                                contentDescription = "Equipped",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
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
