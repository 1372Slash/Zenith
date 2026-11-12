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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
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
    val unlockedBadges = remember(state.achievements) {
        state.achievements.count { it.earnedTier != null }
    }
    val totalBadges = state.achievements.size

    val equippedTitleId = remember(preferences.userTitle, level) {
        LEVEL_TITLES.find { it.id == preferences.userTitle }
            ?.takeIf { it.requiredLevel <= level }?.id ?: ""
    }
    val equippedBorderId = remember(preferences.userAvatarBorder, level) {
        AVATAR_BORDERS.find { it.id == preferences.userAvatarBorder }
            ?.takeIf { it.requiredLevel <= level }?.id ?: ""
    }
    val equippedShapeDef = remember(preferences.userProfileShape, level) {
        PROFILE_SHAPES.find { it.id == preferences.userProfileShape }
            ?.takeIf { it.requiredLevel <= level } ?: PROFILE_SHAPES.first()
    }
    val equippedShape = equippedShapeDef.shapeResolver()
    val nextReward = remember(level) { nextLevelReward(level) }
    var selectedBorder by remember { mutableStateOf<AvatarBorder?>(null) }

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
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.98f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "scale"
            )
            val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val shape = RoundedCornerShape(24.dp)

            Card(
                onClick = {},
                interactionSource = interactionSource,
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .clip(shape)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val currentShapeDef = remember(level) {
                                PROFILE_SHAPES.lastOrNull { level >= it.requiredLevel } ?: PROFILE_SHAPES.first()
                            }
                            val progressiveShape = currentShapeDef.shapeResolver()
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = progressiveShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$level",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onTertiary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "level",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Level Progress",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${formatXp(state.xpTotal)} XP",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearWavyProgressIndicator(
                                progress = { state.levelProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${formatXp(xpToNextLevel(state.xpTotal))} XP to Lvl ${level + 1} • ${nextReward?.let { (_, name) -> "Next: $name" } ?: "Max level"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                                    selectedBorder = border
                                },
                                enabled = true,
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
                                                    ),
                                                    animate = false,
                                                    shape = equippedShape
                                                )
                                                else Modifier.border(
                                                    4.dp,
                                                    SolidColor(
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    ),
                                                    equippedShape
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

        item(key = "shapes") {
            Column {
                PreferenceCategory(title = "Profile Shapes")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(PROFILE_SHAPES, key = { _, s -> "shape-${s.id}" }) { index, pShapeDef ->
                            val unlocked = level >= pShapeDef.requiredLevel
                            val equipped = equippedShapeDef.id == pShapeDef.id && unlocked
                            val pShape = pShapeDef.shapeResolver()
                            Card(
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setUserProfileShape(
                                            if (equipped) "" else pShapeDef.id
                                        )
                                    }
                                },
                                enabled = unlocked,
                                shape = rewardGroupShape(index, PROFILE_SHAPES.size, unlocked),
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
                                            .clip(pShape)
                                            .background(
                                                if (unlocked) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(pShape)
                                                    .background(MaterialTheme.colorScheme.onPrimary)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pShapeDef.name,
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
                                        text = "Lv ${pShapeDef.requiredLevel}",
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
                                            text = "+${formatXp(day.xp.toLong())} XP",
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

    selectedBorder?.let { border ->
        val equipped = equippedBorderId == border.id && (level >= border.requiredLevel)
        BorderDetailSheet(
            border = border,
            level = level,
            xpTotal = state.xpTotal,
            avatarUri = preferences.userAvatarUri,
            equipped = equipped,
            shape = equippedShape,
            onDismiss = { selectedBorder = null },
            onEquipToggle = {
                scope.launch {
                    preferencesRepository.setUserAvatarBorder(
                        if (equipped) "" else border.id
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BorderDetailSheet(
    border: AvatarBorder,
    level: Int,
    xpTotal: Long,
    avatarUri: String,
    equipped: Boolean,
    shape: Shape,
    onDismiss: () -> Unit,
    onEquipToggle: () -> Unit
) {
    val unlocked = level >= border.requiredLevel
    val requiredXp = xpForLevel(border.requiredLevel)
    val remainingXp = (requiredXp - xpTotal).coerceAtLeast(0L)
    val progressFraction = if (requiredXp <= 0L) 1f else (xpTotal.toFloat() / requiredXp.toFloat()).coerceIn(0f, 1f)

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "borderSheetContainerColor"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .avatarRing(
                            border, 6.dp,
                            SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                            animate = true,
                            shape = shape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    var imgError by remember(avatarUri) { mutableStateOf(false) }
                    if (avatarUri.isEmpty() || imgError) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUri).crossfade(300).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { imgError = true },
                            modifier = Modifier.fillMaxSize().clip(shape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = border.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Border",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (unlocked) "Unlocked • Level ${border.requiredLevel}" else "Locked • Requires Level ${border.requiredLevel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (unlocked) "Unlocked" else "Progress to Unlock",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearWavyProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            stroke = Stroke(
                                width = with(LocalDensity.current) { 4.dp.toPx() },
                                cap = StrokeCap.Round
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (unlocked) "You have unlocked this avatar border. Equip it to display on your profile avatar."
                            else "${formatXp(remainingXp)} XP needed (Target: ${formatXp(requiredXp)} XP) to reach Level ${border.requiredLevel}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ZenithButton(
                    onClick = {
                        onEquipToggle()
                        onDismiss()
                    },
                    enabled = unlocked,
                    fillMaxWidth = true,
                    size = ZenithButtonSize.ExtraLarge,
                    type = if (equipped) ZenithButtonType.Outlined else ZenithButtonType.Filled,
                    icon = if (equipped) Icons.Outlined.CheckCircleOutline else Icons.Outlined.WorkspacePremium,
                    text = if (equipped) "Equipped (Tap to Unequip)"
                    else if (unlocked) "Equip Border"
                    else "Locked (Reach Level ${border.requiredLevel})"
                )
            }
        }
    }
}
