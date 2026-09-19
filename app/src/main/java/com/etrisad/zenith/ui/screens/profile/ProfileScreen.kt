package com.etrisad.zenith.ui.screens.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.LongTermSection
import com.etrisad.zenith.ui.components.StreakCard
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.focus.SettingsToggle
import com.etrisad.zenith.ui.components.focus.appIconShape
import com.etrisad.zenith.ui.screens.home.GroupedCard
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_PROFILE_NAME_LENGTH = 20
private const val MAX_PROFILE_BIO_LENGTH = 120

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel,
    homeViewModel: HomeViewModel,
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues,
    onAppClick: (String) -> Unit
) {
    val state by profileViewModel.uiState.collectAsState()
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences()
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showNameDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var selectedAch by remember { mutableStateOf<AchievementState?>(null) }

    // Recompute on every entry so freshly met tiers raise a notification.
    LaunchedEffect(Unit) { profileViewModel.refresh() }

    val pending by profileViewModel.pendingUnlocks.collectAsState()
    val currentUnlock = pending.firstOrNull()
    val currentUnlockState = remember(currentUnlock, state.achievements) {
        currentUnlock?.let { u -> state.achievements.find { it.def.id == u.defId } }
    }
    LaunchedEffect(currentUnlock) {
        if (currentUnlock != null) {
            delay(4500)
            profileViewModel.consumeUnlock(currentUnlock.defId, currentUnlock.tierValue)
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            scope.launch { preferencesRepository.setUserAvatarUri(uri.toString()) }
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            scope.launch { preferencesRepository.setUserBannerUri(uri.toString()) }
        }
    }

    val explorer = remember(state.achievements) {
        state.achievements.filter { it.def.category == AchievementCategory.EXPLORER }
    }
    val accumulation = remember(state.achievements) {
        state.achievements.filter { it.def.category == AchievementCategory.ACCUMULATION }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "identity") {
            ProfileIdentityCard(
                userName = preferences.userName,
                userBio = preferences.userBio,
                avatarUri = preferences.userAvatarUri,
                bannerUri = preferences.userBannerUri,
                level = state.level,
                isEditing = isEditing,
                onEditName = { showNameDialog = true },
                onEditBio = { showBioDialog = true },
                onAvatarClick = { avatarPicker.launch("image/*") },
                onBannerClick = { bannerPicker.launch("image/*") }
            )
        }

        item(key = "banner_setting") {
            val bannerShape = RoundedCornerShape(24.dp)
            Card(
                shape = bannerShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsToggle(
                    title = "Show banner on Home",
                    description = "Use your profile banner in the Daily Screen Time card",
                    checked = preferences.profileBannerOnHome,
                    onCheckedChange = {
                        scope.launch { preferencesRepository.setProfileBannerOnHome(it) }
                    },
                    icon = Icons.Outlined.Image,
                    shape = bannerShape
                )
            }
        }

        item(key = "top_apps") {
            LifetimeTopAppsCard(
                topApps = state.topApps,
                lifetimeTotal = state.lifetimeTotal,
                lifetimeAppCount = state.lifetimeAppCount,
                isLoading = state.isLoading,
                formatDuration = homeViewModel::formatLongDuration,
                onAppClick = onAppClick
            )
        }

        item(key = "heatmap") {
            ProfileHeatmapCard(
                homeViewModel = homeViewModel,
                onAppClick = onAppClick
            )
        }

        item(key = "streak") {
            StreakCard(
                currentStreak = state.streakCurrent,
                bestStreak = state.streakBest,
                expressiveColors = preferences.expressiveColors,
                shape = RoundedCornerShape(24.dp)
            )
        }

        item(key = "xp") {
            val unlockedBadges = remember(state.achievements) {
                state.achievements.count { it.earnedTier != null }
            }
            XpCard(
                level = state.level,
                levelProgress = state.levelProgress,
                xpTotal = state.xpTotal,
                xpToday = state.xpToday,
                totalSavedMillis = state.totalSavedMillis,
                unlockedBadges = unlockedBadges,
                totalBadges = state.achievements.size,
                formatDuration = homeViewModel::formatLongDuration
            )
        }

        item(key = "achievements_title") {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        item(key = "ach_explorer_label") {
            Text(
                text = "Explorer - try Zenith features",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        item(key = "ach_explorer_row") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(explorer, key = { _, s -> "ach-${s.def.id}" }) { index, ach ->
                        AchievementSquareCard(
                            achievement = ach,
                            index = index,
                            total = explorer.size,
                            onClick = { selectedAch = ach }
                        )
                    }
                }
            }
        }

        item(key = "ach_acc_label") {
            Text(
                text = "Accumulation - grow over time",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        item(key = "ach_acc_row") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(accumulation, key = { _, s -> "ach-${s.def.id}" }) { index, ach ->
                        AchievementSquareCard(
                            achievement = ach,
                            index = index,
                            total = accumulation.size,
                            onClick = { selectedAch = ach }
                        )
                    }
                }
            }
        }

        item(key = "share") {
            ZenithButton(
                onClick = {
                    scope.launch {
                        isSharing = true
                        val ok = withContext(Dispatchers.IO) {
                            renderAndShareProfile(
                                context = context,
                                userName = preferences.userName.ifBlank { "User" },
                                level = state.level,
                                xpTotal = state.xpTotal,
                                streakBest = state.streakBest,
                                topApps = state.topApps,
                                achievements = state.achievements
                            )
                        }
                        isSharing = false
                        if (!ok) {
                            Toast.makeText(context, "Could not share profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                text = if (isSharing) "Rendering card..." else "Share Your Profile",
                icon = Icons.Outlined.Share,
                size = ZenithButtonSize.Large,
                fillMaxWidth = true,
                enabled = !isSharing
            )
            if (isSharing) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    FloatingActionButton(
        onClick = { isEditing = !isEditing },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = innerPadding.calculateBottomPadding() + 16.dp),
        containerColor = if (isEditing) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary
    ) {
        Icon(
            imageVector = if (isEditing) Icons.Outlined.Close else Icons.Outlined.Edit,
            contentDescription = if (isEditing) "Done editing" else "Edit profile"
        )
    }

    AnimatedVisibility(
        visible = currentUnlock != null,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(
                top = innerPadding.calculateTopPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp
            ),
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeOut(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )
    ) {
        val unlock = currentUnlock
        if (unlock != null) {
            AchievementUnlockBanner(
                unlock = unlock,
                state = currentUnlockState,
                onOpen = {
                    currentUnlockState?.let { selectedAch = it }
                    profileViewModel.consumeUnlock(unlock.defId, unlock.tierValue)
                },
                onDismiss = {
                    profileViewModel.consumeUnlock(unlock.defId, unlock.tierValue)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    }

    if (showNameDialog) {
        var draft by remember(preferences.userName) {
            mutableStateOf(preferences.userName.take(MAX_PROFILE_NAME_LENGTH))
        }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Display name") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MAX_PROFILE_NAME_LENGTH) },
                    singleLine = true,
                    placeholder = { Text("How should we call you?") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { preferencesRepository.setUserName(draft.trim().ifBlank { "User" }) }
                    showNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBioDialog) {
        var draft by remember(preferences.userBio) {
            mutableStateOf(preferences.userBio.take(MAX_PROFILE_BIO_LENGTH))
        }
        AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text("Bio") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MAX_PROFILE_BIO_LENGTH) },
                    placeholder = { Text("A line about you (optional)") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { preferencesRepository.setUserBio(draft.trim()) }
                    showBioDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showBioDialog = false }) { Text("Cancel") }
            }
        )
    }

    selectedAch?.let { ach ->
        AchievementDetailSheet(
            achievement = ach,
            onDismiss = { selectedAch = null }
        )
    }
}

@Composable
private fun AchievementUnlockBanner(
    unlock: PendingUnlock,
    state: AchievementState?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = state?.def?.icon ?: unlock.fallbackIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Achievement unlocked",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = state?.def?.title ?: "New badge",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                TierSymbolsRow(
                    level = unlock.tierLevel,
                    iconSize = 12.dp,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun PendingUnlock.fallbackIcon() = Icons.Outlined.EmojiEvents

@Composable
private fun EditRevealButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(
                initialScale = 0.6f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleOut(
                targetScale = 0.6f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
    ) {
        IconButton(onClick = onClick, modifier = buttonModifier) {
            content()
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    userName: String,
    userBio: String,
    avatarUri: String,
    bannerUri: String,
    level: Int,
    isEditing: Boolean,
    onEditName: () -> Unit,
    onEditBio: () -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Banner: default mirrors the Home Daily Screen Time card tint,
                // customizable later from the user gallery.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    if (bannerUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(bannerUri).crossfade(300).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    EditRevealButton(
                        visible = isEditing,
                        onClick = onBannerClick,
                        modifier = Modifier.align(Alignment.TopEnd),
                        buttonModifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Change banner",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(52.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userName.ifBlank { "User" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        EditRevealButton(
                            visible = isEditing,
                            onClick = onEditName,
                            buttonModifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit name",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Level $level",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userBio.ifBlank { "Add a bio..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (userBio.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        EditRevealButton(
                            visible = isEditing,
                            onClick = onEditBio,
                            buttonModifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit bio",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            // Avatar: default tertiary tint with a user glyph,
            // customizable later from the user gallery.
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, top = 106.dp)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUri).crossfade(300).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            EditRevealButton(
                visible = isEditing,
                onClick = onAvatarClick,
                modifier = Modifier.padding(start = 88.dp, top = 158.dp),
                buttonModifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Change photo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LifetimeTopAppsCard(
    topApps: List<LifetimeApp>,
    lifetimeTotal: Long,
    lifetimeAppCount: Int,
    isLoading: Boolean,
    formatDuration: (Long) -> String,
    onAppClick: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Most Used - Lifetime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lifetimeAppCount > 0) {
                    "$lifetimeAppCount apps - ${formatDuration(lifetimeTotal)} total"
                } else {
                    "Cumulative totals build up as you use your phone"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (topApps.isEmpty() && !isLoading) {
                Text(
                    text = "No lifetime data yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LifetimePodium(
                    topApps = topApps,
                    formatDuration = formatDuration,
                    onAppClick = onAppClick
                )
            }
        }
    }
}

@Composable
private fun LifetimePodium(
    topApps: List<LifetimeApp>,
    formatDuration: (Long) -> String,
    onAppClick: (String) -> Unit
) {
    // Visual order: 2nd left, 1st center tallest, 3rd right.
    val slots = remember(topApps) {
        when {
            topApps.size >= 3 -> listOf(
                2 to topApps[1], 1 to topApps[0], 3 to topApps[2]
            )
            topApps.size == 2 -> listOf(2 to topApps[1], 1 to topApps[0])
            else -> listOf(1 to topApps[0])
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.forEach { (rank, app) ->
            PodiumColumn(
                rank = rank,
                app = app,
                timeLabel = formatDuration(app.totalMillis),
                onClick = { onAppClick(app.packageName) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PodiumColumn(
    rank: Int,
    app: LifetimeApp,
    timeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = when (rank) {
        1 -> 96.dp
        2 -> 64.dp
        else -> 48.dp
    }
    val barColor = when (rank) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val onBarColor = when (rank) {
        1 -> MaterialTheme.colorScheme.onPrimary
        2 -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onTertiary
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = app.appName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(40.dp)
        )
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        val isWebsite = app.packageName.startsWith("zenith-web:")
        val iconShape = appIconShape(isWebsite)
        Surface(
            onClick = onClick,
            shape = iconShape,
            color = Color.Transparent,
            modifier = Modifier.size(if (rank == 1) 56.dp else 48.dp)
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("app-icon://${app.packageName}").crossfade(300).build(),
                contentDescription = app.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(iconShape),
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(iconShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isWebsite) Icons.Outlined.Language else Icons.Outlined.Android,
                            null, modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomStart = 4.dp, bottomEnd = 4.dp
                    )
                )
                .background(barColor),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = onBarColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ProfileHeatmapCard(
    homeViewModel: HomeViewModel,
    onAppClick: (String) -> Unit
) {
    val selectedRange by homeViewModel.selectedStatsRange.collectAsState()
    val offset by homeViewModel.selectedPeriodOffset.collectAsState()
    val longTermUsage by remember(selectedRange, offset) {
        homeViewModel.getLongTermAppUsage(selectedRange, offset)
    }.collectAsState(initial = emptyList())
    val totalPeriod = remember(longTermUsage) { longTermUsage.sumOf { it.totalTimeVisible } }
    val prevTotal by remember(selectedRange, offset) {
        homeViewModel.getLongTermTotal(selectedRange, offset + 1)
    }.collectAsState(initial = 0L)
    val periodLabel = remember(selectedRange, offset) {
        homeViewModel.getPeriodRangeLabel(selectedRange, offset)
    }
    val periodDays = remember(selectedRange, offset) {
        homeViewModel.getPeriodDayMillis(selectedRange, offset)
    }
    val earliestTick by homeViewModel.earliestDataDate.collectAsState()
    val dataNote = remember(earliestTick, periodDays) {
        homeViewModel.dataStartNoteFor(periodDays)
    }
    val dailyHistory by remember(selectedRange, offset) {
        homeViewModel.getLongTermDailyHistory(selectedRange, offset)
    }.collectAsState(initial = emptyList())

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        LongTermSection(
            title = "Activity Heatmap",
            accentColor = MaterialTheme.colorScheme.primary,
            highlightColor = MaterialTheme.colorScheme.tertiary,
            selectedRange = selectedRange,
            onRangeSelected = homeViewModel::selectStatsRange,
            rangeLabel = periodLabel,
            canGoNewer = offset > 0,
            onPreviousPeriod = { homeViewModel.prevPeriod() },
            onNextPeriod = { homeViewModel.nextPeriod() },
            totalMillis = totalPeriod,
            prevTotal = prevTotal,
            dailyHistory = dailyHistory,
            heatmapEmptyText = "No daily data",
            formatDuration = homeViewModel::formatLongDuration,
            periodDays = periodDays,
            dayAppLoader = homeViewModel::getDayAppBreakdown,
            onAppClick = onAppClick,
            dataNote = dataNote
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun XpCard(
    level: Int,
    levelProgress: Float,
    xpTotal: Long,
    xpToday: Int,
    totalSavedMillis: Long,
    unlockedBadges: Int,
    totalBadges: Int,
    formatDuration: (Long) -> String
) {
    val xpToNext = PROFILE_XP_PER_LEVEL - (xpTotal % PROFILE_XP_PER_LEVEL)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$level",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "LEVEL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$xpTotal XP",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "$unlockedBadges/$totalBadges badges",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "+$xpToday XP today",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Shields earn for staying under the limit (less usage = more XP). " +
                    "Goals earn for reaching or passing the target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearWavyProgressIndicator(
                progress = { levelProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                stroke = Stroke(
                    width = with(LocalDensity.current) { 4.dp.toPx() },
                    cap = StrokeCap.Round
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "$xpToNext XP to Level ${level + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Saved: ${formatDuration(totalSavedMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AchievementSquareCard(
    achievement: AchievementState,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    val earned = achievement.earnedTier != null
    // Achieved cards stay fully rounded even in the middle of the group.
    val shape = when {
        earned || total == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp
        )
        index == total - 1 -> RoundedCornerShape(
            topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(8.dp)
    }
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (earned) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
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
                        if (earned) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = achievement.def.icon,
                    contentDescription = null,
                    tint = if (earned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = achievement.def.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (earned) {
                Box(
                    modifier = Modifier.height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TierSymbolsRow(
                        level = achievement.earnedLevel,
                        iconSize = 14.dp,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            } else {
                Text(
                    text = "Locked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { achievement.progressFraction },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = achievementProgressLabel(achievement),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AchievementDetailSheet(
    achievement: AchievementState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = achievement.def.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = achievement.def.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = achievement.def.desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = achievementProgressLabel(achievement),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(achievement.progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearWavyProgressIndicator(
                progress = { achievement.progressFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                stroke = Stroke(
                    width = with(LocalDensity.current) { 4.dp.toPx() },
                    cap = StrokeCap.Round
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Tiers",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            val thresholds = achievement.def.thresholds
            val level = achievement.earnedLevel
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TierMiniCard(
                    label = "Before",
                    threshold = thresholds.getOrNull(level - 2),
                    tierLevel = level - 1,
                    date = thresholds.getOrNull(level - 2)?.let {
                        achievement.unlockedDates[it.tier.value]
                    },
                    highlighted = false,
                    index = 0,
                    total = 3,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                TierMiniCard(
                    label = "Current",
                    threshold = thresholds.getOrNull(level - 1),
                    tierLevel = level,
                    date = thresholds.getOrNull(level - 1)?.let {
                        achievement.unlockedDates[it.tier.value]
                    },
                    highlighted = true,
                    index = 1,
                    total = 3,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                TierMiniCard(
                    label = "Next",
                    threshold = thresholds.getOrNull(level),
                    tierLevel = level + 1,
                    date = null,
                    highlighted = false,
                    index = 2,
                    total = 3,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Unlock history",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            val history = achievement.unlockedDates.entries.sortedBy { it.key }
            if (history.isEmpty()) {
                Text(
                    text = "No tier unlocked yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    history.forEach { (tierValue, date) ->
                        val threshold = thresholds.find { it.tier.value == tierValue }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.width(64.dp)) {
                                TierSymbolsRow(
                                    level = thresholds.indexOfFirst { it.tier.value == tierValue } + 1,
                                    iconSize = 14.dp,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Text(
                                text = threshold?.requireLabel ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = prettyProfileDate(date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierSymbolsRow(
    level: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val icons = remember(level) { tierIconsForLevel(level) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEach { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun TierMiniCard(
    label: String,
    threshold: TierThreshold?,
    tierLevel: Int,
    date: String?,
    highlighted: Boolean,
    index: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val shape = when {
        total == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp
        )
        index == total - 1 -> RoundedCornerShape(
            topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(8.dp)
    }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                threshold == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                date != null -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                highlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (threshold == null) Arrangement.Center
            else Arrangement.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (threshold == null) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                Icon(
                    imageVector = threshold.tier.icon,
                    contentDescription = null,
                    tint = if (date != null) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                TierSymbolsRow(
                    level = tierLevel.coerceAtLeast(1),
                    iconSize = 12.dp,
                    tint = if (date != null) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = threshold.requireLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = date?.let { prettyProfileDate(it) }
                        ?: if (highlighted) "In progress" else "Locked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

private fun prettyProfileDate(raw: String): String {
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).parse(raw)
            ?: return raw
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(parsed)
    } catch (_: Exception) {
        raw
    }
}

private fun formatShareDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val mins = millis / 60_000L
    val h = mins / 60
    return if (h > 0) "${h}h ${mins % 60}m" else "${mins}m"
}

/**
 * Offline share: renders a profile summary card to a PNG in cache and
 * shares it with a plain ACTION_SEND intent. No network involved.
 */
private fun renderAndShareProfile(
    context: android.content.Context,
    userName: String,
    level: Int,
    xpTotal: Long,
    streakBest: Int,
    topApps: List<LifetimeApp>,
    achievements: List<AchievementState>
): Boolean {
    return try {
        val width = 1080
        val rowH = 90
        val height = 980 + topApps.size * rowH
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = Paint().apply { color = android.graphics.Color.parseColor("#141318") }
        val banner = Paint().apply { color = android.graphics.Color.parseColor("#4F378B") }
        val accent = Paint().apply { color = android.graphics.Color.parseColor("#D0BCFF") }
        val title = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sub = Paint().apply {
            color = android.graphics.Color.parseColor("#CAC4D0")
            textSize = 38f
        }
        val section = Paint().apply {
            color = android.graphics.Color.parseColor("#D0BCFF")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
        }
        val faint = Paint().apply {
            color = android.graphics.Color.parseColor("#938F99")
            textSize = 34f
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        canvas.drawRect(0f, 0f, width.toFloat(), 300f, banner)
        var y = 140f
        canvas.drawText("ZENITH PROFILE", 64f, y, accent)
        y += 100f
        canvas.drawText(userName.take(24), 64f, y, title)
        y += 70f
        canvas.drawText("Level $level  -  $xpTotal XP  -  Best streak $streakBest days", 64f, y, sub)

        y += 120f
        canvas.drawText("TOP APPS (LIFETIME)", 64f, y, section)
        y += 70f
        if (topApps.isEmpty()) {
            canvas.drawText("No data yet", 64f, y, faint)
            y += rowH
        } else {
            topApps.forEachIndexed { i, app ->
                canvas.drawText(
                    "${i + 1}. ${app.appName.take(28)} - ${formatShareDuration(app.totalMillis)}",
                    64f, y, body
                )
                y += rowH.toFloat()
            }
        }
        y += 40f
        val unlocked = achievements.count { it.earnedTier != null }
        canvas.drawText("ACHIEVEMENTS  $unlocked/${achievements.size}", 64f, y, section)
        y += 70f
        achievements.filter { it.earnedTier != null }.take(6).forEach { ach ->
            canvas.drawText(
                "${ach.def.title} [${toCustomRoman(ach.earnedLevel)}]",
                64f, y, body
            )
            y += rowH.toFloat()
        }
        canvas.drawText("Tracked 100% offline with Zenith", 64f, height - 64f, faint)

        val file = File(context.cacheDir, "zenith-profile.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "$userName - Zenith Level $level ($xpTotal XP), best streak $streakBest days"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Your Profile").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: Exception) {
        false
    }
}
