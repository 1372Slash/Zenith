package com.etrisad.zenith.ui.screens.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExcludeFromTrackingBottomSheet(
    initialExcluded: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    var apps by remember { mutableStateOf<List<WhitelistAppInfo>>(emptyList()) }
    var selectedApps by remember { mutableStateOf(initialExcluded) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val installedApps = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }
            } catch (e: Exception) {
                android.util.Log.e("ExcludeFromTrackingBottomSheet", "Failed to get installed applications", e)
                emptyList()
            }

            val mappedApps = installedApps.map {
                val isSystemFlag = (it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val hasLauncher = pm.getLaunchIntentForPackage(it.packageName) != null
                val pkg = it.packageName

                val isCoreComponent = pkg == "android" ||
                        pkg.startsWith("com.android.settings") ||
                        pkg.startsWith("com.android.systemui") ||
                        pkg.startsWith("com.android.shell") ||
                        pkg.startsWith("com.android.phone") ||
                        pkg.startsWith("com.android.angle") ||
                        pkg.startsWith("com.android.providers") ||
                        pkg.startsWith("com.google.android.angle") ||
                        pkg.startsWith("com.google.android.setupwizard") ||
                        pkg.contains("restore") ||
                        pkg.contains("overlay") ||
                        pkg.contains("documentsui")

                WhitelistAppInfo(
                    packageName = pkg,
                    appName = pm.getApplicationLabel(it).toString(),
                    isSystemApp = isSystemFlag && (!hasLauncher || isCoreComponent),
                    isPreinstalledApp = isSystemFlag && hasLauncher && !isCoreComponent
                )
            }.sortedWith(compareBy({ it.isPreinstalledApp || it.isSystemApp }, { it.isSystemApp }, { it.appName.lowercase() }))

            apps = mappedApps
            isLoading = false
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    val userApps = remember(filteredApps) { filteredApps.filter { !it.isSystemApp && !it.isPreinstalledApp } }
    val preinstalledApps = remember(filteredApps) { filteredApps.filter { it.isPreinstalledApp } }
    val systemApps = remember(filteredApps) { filteredApps.filter { it.isSystemApp } }
    var isSystemAppsExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                ) {
                    Text(
                        text = "Exclude from Tracking",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Excluded apps' screen time will not be tracked or shown in your usage data. Useful for media players and other apps that don't depend on screen time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = { Text("Search apps...") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                        }
                                    }
                                }
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        content = {}
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ZenithContainedLoadingIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            itemsIndexed(
                                filteredApps,
                                key = { _, app -> app.packageName }
                            ) { index, app ->
                                WhitelistAppItem(
                                    app = app,
                                    isSelected = app.packageName in selectedApps,
                                    shape = getAdaptiveShapeExclude(index, filteredApps.size),
                                    modifier = Modifier.animateItem(),
                                    onToggle = {
                                        selectedApps = if (app.packageName in selectedApps) {
                                            selectedApps - app.packageName
                                        } else {
                                            selectedApps + app.packageName
                                        }
                                    }
                                )
                            }
                        } else {
                            if (userApps.isNotEmpty()) {
                                item(key = "user_apps_header") {
                                    Text(
                                        "User Apps",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    userApps,
                                    key = { _, app -> app.packageName }
                                ) { index, app ->
                                    WhitelistAppItem(
                                        app = app,
                                        isSelected = app.packageName in selectedApps,
                                        shape = getAdaptiveShapeExclude(index, userApps.size),
                                        modifier = Modifier.animateItem(),
                                        onToggle = {
                                            selectedApps = if (app.packageName in selectedApps) {
                                                selectedApps - app.packageName
                                            } else {
                                                selectedApps + app.packageName
                                            }
                                        }
                                    )
                                }
                            }

                            if (preinstalledApps.isNotEmpty()) {
                                item(key = "preinstalled_apps_header") {
                                    Text(
                                        "Preinstalled Apps",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    preinstalledApps,
                                    key = { _, app -> app.packageName }
                                ) { index, app ->
                                    WhitelistAppItem(
                                        app = app,
                                        isSelected = app.packageName in selectedApps,
                                        shape = getAdaptiveShapeExclude(index, preinstalledApps.size),
                                        modifier = Modifier.animateItem(),
                                        onToggle = {
                                            selectedApps = if (app.packageName in selectedApps) {
                                                selectedApps - app.packageName
                                            } else {
                                                selectedApps + app.packageName
                                            }
                                        }
                                    )
                                }
                            }

                            if (systemApps.isNotEmpty()) {
                                item(key = "system_apps_header") {
                                    Surface(
                                        onClick = { isSystemAppsExpanded = !isSystemAppsExpanded },
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    "System Apps",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            supportingContent = {
                                                Text("${systemApps.size} apps hidden by default")
                                            },
                                            trailingContent = {
                                                Icon(
                                                    imageVector = if (isSystemAppsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                    contentDescription = null
                                                )
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }

                                if (isSystemAppsExpanded) {
                                    itemsIndexed(
                                        systemApps,
                                        key = { _, app -> app.packageName }
                                    ) { index, app ->
                                        WhitelistAppItem(
                                            app = app,
                                            isSelected = app.packageName in selectedApps,
                                            shape = getAdaptiveShapeExclude(index, systemApps.size),
                                            modifier = Modifier.animateItem(),
                                            onToggle = {
                                                selectedApps = if (app.packageName in selectedApps) {
                                                    selectedApps - app.packageName
                                                } else {
                                                    selectedApps + app.packageName
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onSave(selectedApps)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Save selection")
            }
        }
    }
}

@Composable
private fun getAdaptiveShapeExclude(index: Int, size: Int): RoundedCornerShape {
    return when {
        size == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 8.dp,
            bottomEnd = 8.dp
        )
        index == size - 1 -> RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(8.dp)
    }
}
