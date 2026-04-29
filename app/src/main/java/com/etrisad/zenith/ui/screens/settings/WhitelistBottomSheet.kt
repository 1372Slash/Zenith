package com.etrisad.zenith.ui.screens.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WhitelistAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhitelistBottomSheet(
    initialWhitelisted: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    var apps by remember { mutableStateOf<List<WhitelistAppInfo>>(emptyList()) }
    var selectedApps by remember { mutableStateOf(initialWhitelisted) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val mappedApps = installedApps.map {
                val isSystem = (it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                WhitelistAppInfo(
                    packageName = it.packageName,
                    appName = pm.getApplicationLabel(it).toString(),
                    icon = pm.getApplicationIcon(it),
                    isSystemApp = isSystem
                )
            }.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
            
            apps = mappedApps

            if (initialWhitelisted.isEmpty()) {
                selectedApps = mappedApps.filter { it.isSystemApp }.map { it.packageName }.toSet()
            }
            isLoading = false
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    val userApps = remember(filteredApps) { filteredApps.filter { !it.isSystemApp } }
    val systemApps = remember(filteredApps) { filteredApps.filter { it.isSystemApp } }
    var isSystemAppsExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Whitelist Apps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Whitelisted apps will bypass all Zenith restrictions (Schedules, Shields, and Goals).",
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

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(bottom = 100.dp),
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
                                    shape = getAdaptiveShape(index, filteredApps.size),
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
                            // User Apps Section
                            itemsIndexed(
                                userApps,
                                key = { _, app -> app.packageName }
                            ) { index, app ->
                                WhitelistAppItem(
                                    app = app,
                                    isSelected = app.packageName in selectedApps,
                                    shape = getAdaptiveShape(index, userApps.size),
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
                                            shape = getAdaptiveShape(index, systemApps.size),
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
                onClick = { onSave(selectedApps) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Save selection")
            }
        }
    }
}

@Composable
private fun getAdaptiveShape(index: Int, size: Int): RoundedCornerShape {
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

@Composable
fun WhitelistAppItem(
    app: WhitelistAppInfo,
    isSelected: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.98f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    Card(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        ListItem(
            headlineContent = { Text(app.appName, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Column {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    if (app.isSystemApp) {
                        Text(
                            text = "System App",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            leadingContent = {
                app.icon?.let {
                    Image(
                        painter = BitmapPainter(it.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            },
            trailingContent = {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
