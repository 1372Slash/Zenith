package com.etrisad.zenith.ui.screens.focus

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.ui.components.ShieldSortHeader
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.ShieldSortType

@Composable
fun FocusScreen(viewModel: FocusViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var isAppPickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAppPickerOpen = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .offset(y = 12.dp) // Offset downwards to break the default Scaffold padding
                    .size(80.dp)
            ) {
                Icon(
                    Icons.Outlined.Add, 
                    contentDescription = "Add Shield",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    ) { innerPadding ->
        FocusScreenContent(
            uiState = uiState,
            innerPadding = innerPadding,
            onEditShield = { viewModel.editShield(it) },
            onDeleteShield = { viewModel.deleteShield(it) },
            onSortTypeChange = { viewModel.onSortTypeChange(it) }
        )

        if (isAppPickerOpen) {
            AppPickerBottomSheet(
                uiState = uiState,
                onDismiss = { isAppPickerOpen = false },
                onAppSelected = {
                    viewModel.selectAppForShield(it)
                    isAppPickerOpen = false
                },
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) }
            )
        }

        if (uiState.isSettingsSheetOpen && uiState.selectedAppForShield != null) {
            ShieldSettingsBottomSheet(
                appInfo = uiState.selectedAppForShield!!,
                existingShield = uiState.shieldedApps.find { it.packageName == uiState.selectedAppForShield!!.packageName },
                onDismiss = { viewModel.closeSettingsSheet() },
                onSave = { limit, emergency, reminders, strict, autoQuit, maxUses, refresh ->
                    viewModel.saveShield(limit, emergency, reminders, strict, autoQuit, maxUses, refresh)
                }
            )
        }
    }
}

@Composable
fun FocusScreenContent(
    uiState: FocusUiState,
    innerPadding: PaddingValues,
    onEditShield: (ShieldEntity) -> Unit,
    onDeleteShield: (ShieldEntity) -> Unit,
    onSortTypeChange: (ShieldSortType) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = 100.dp
        )
    ) {
        item {
            FocusHeader()
        }
        item {
            ShieldSortHeader(
                title = "My Shields",
                currentSortType = uiState.sortType,
                onSortTypeChange = onSortTypeChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.shieldedApps.isEmpty()) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f)) + fadeIn()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Add a Zenith Shield to stay focused",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.shieldedApps,
                key = { _, shield -> shield.packageName }
            ) { index, shield ->
                val shape = when {
                    uiState.shieldedApps.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    index == uiState.shieldedApps.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(8.dp)
                }
                
                Column(modifier = Modifier.animateItem()) {
                    ShieldConfigItem(
                        shield = shield,
                        shape = shape,
                        onEdit = { onEditShield(shield) },
                        onDelete = { onDeleteShield(shield) }
                    )
                    if (index < uiState.shieldedApps.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FocusHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Manage your focus barriers",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Zenith Shields",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldConfigItem(
    shield: ShieldEntity,
    shape: RoundedCornerShape,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(shield.packageName) {
        try {
            context.packageManager.getApplicationIcon(shield.packageName)
        } catch (e: Exception) {
            null
        }
    }

    val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val remainingMillis = shield.remainingTimeMillis.coerceIn(0L, totalLimitMillis)
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading Icon
                if (appIcon != null) {
                    Image(
                        painter = BitmapPainter(appIcon.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Android, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shield.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val hours = shield.timeLimitMinutes / 60
                    val mins = shield.timeLimitMinutes % 60
                    val limitText = if (hours > 0) "${hours}h ${mins}m limit" else "${mins}m limit"
                    
                    Text(
                        text = "$limitText • ${if (shield.isStrictModeEnabled) "Strict" else "Normal"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "${formatRemainingTime(remainingMillis)} Left",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Percentage Text (Now Centered Vertically)
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Trailing Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
                label = "Progress"
            )

            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

fun formatRemainingTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPickerBottomSheet(
    uiState: FocusUiState,
    onDismiss: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Select App to Shield",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onSearch = { /* Handle search if needed */ },
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search apps...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth(),
                shape = SearchBarDefaults.inputFieldShape,
                colors = SearchBarDefaults.colors(),
                tonalElevation = SearchBarDefaults.Elevation,
                shadowElevation = 0.dp,
                windowInsets = SearchBarDefaults.windowInsets,
                content = {}
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoadingApps) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(uiState.installedApps) { index, app ->
                        val shape = when {
                            uiState.installedApps.size == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            index == uiState.installedApps.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(8.dp)
                        }
                        AppListItem(app = app, shape = shape, onClick = { onAppSelected(app) })
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, shape: RoundedCornerShape, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                if (app.icon != null) {
                    Image(
                        painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Android, contentDescription = null)
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSettingsBottomSheet(
    appInfo: AppInfo,
    existingShield: ShieldEntity?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Boolean, Boolean, Boolean, Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = (existingShield?.timeLimitMinutes ?: 30) / 60,
        initialMinute = (existingShield?.timeLimitMinutes ?: 30) % 60,
        is24Hour = true
    )
    var emergencyUses by remember { mutableStateOf(existingShield?.emergencyUseCount?.toString() ?: "3") }
    var remindersEnabled by remember { mutableStateOf(existingShield?.isRemindersEnabled ?: true) }
    var strictModeEnabled by remember { mutableStateOf(existingShield?.isStrictModeEnabled ?: false) }
    var autoQuitEnabled by remember { mutableStateOf(existingShield?.isAutoQuitEnabled ?: false) }
    var maxUses by remember { mutableStateOf(existingShield?.maxUsesPerPeriod?.toString() ?: "5") }
    var refreshPeriodMinutes by remember { mutableIntStateOf(existingShield?.refreshPeriodMinutes ?: 60) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val refreshOptions = listOf(
        "Per 30 Menit" to 30,
        "Per 1 Jam" to 60,
        "Per 2 Jam" to 120,
        "Per 6 Jam" to 360,
        "Per 12 Jam" to 720,
        "Per 24 Jam" to 1440
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appInfo.icon != null) {
                    Image(
                        painter = BitmapPainter(appInfo.icon.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = appInfo.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(text = "Shield Settings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Daily Time Limit (HH:MM)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimeInput(
                    state = timePickerState,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = maxUses,
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxUses = it },
                    label = { Text("Times of Uses") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) }
                )

                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = it },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = refreshOptions.find { it.second == refreshPeriodMinutes }?.first ?: "Custom",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Refresh Period") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        refreshOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.first) },
                                onClick = {
                                    refreshPeriodMinutes = option.second
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = emergencyUses,
                onValueChange = { if (it.all { char -> char.isDigit() }) emergencyUses = it },
                label = { Text("Emergency Use Count") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsToggle(
                title = "Show Reminders",
                description = "Get notified before limit is reached",
                checked = remindersEnabled,
                onCheckedChange = { remindersEnabled = it },
                icon = Icons.Outlined.NotificationsActive
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsToggle(
                title = "Strict Mode",
                description = "No extensions allowed after limit",
                checked = strictModeEnabled,
                onCheckedChange = { strictModeEnabled = it },
                icon = Icons.Outlined.GppGood
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsToggle(
                title = "Auto Quit",
                description = "Exit app automatically when session ends",
                checked = autoQuitEnabled,
                onCheckedChange = { autoQuitEnabled = it },
                icon = Icons.Outlined.ExitToApp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onSave(
                        timePickerState.hour * 60 + timePickerState.minute,
                        emergencyUses.toIntOrNull() ?: 3,
                        remindersEnabled,
                        strictModeEnabled,
                        autoQuitEnabled,
                        maxUses.toIntOrNull() ?: 5,
                        refreshPeriodMinutes
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Save Shield", modifier = Modifier.padding(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true)
@Composable
fun FocusScreenPreview() {
    ZenithTheme {
        FocusScreenContent(
            uiState = FocusUiState(
                shieldedApps = listOf(
                    ShieldEntity("com.instagram", "Instagram", 60),
                    ShieldEntity("com.twitter", "X", 30, isStrictModeEnabled = true)
                )
            ),
            innerPadding = PaddingValues(0.dp),
            onEditShield = {},
            onDeleteShield = {},
            onSortTypeChange = {}
        )
    }
}
