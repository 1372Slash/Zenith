package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.etrisad.zenith.ui.components.focus.appIconShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.PickerTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MultiAppPickerBottomSheet(
    uiState: FocusUiState,
    onDismiss: () -> Unit,
    onAppToggled: (String) -> Unit,
    onConfirm: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTabChange: ((PickerTab) -> Unit)? = null,
    onWebsiteSearchChange: ((String) -> Unit)? = null,
    onWebsiteToggled: ((String) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        text = "Select Apps for Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    TabRow(
                        selectedTabIndex = if (uiState.pickerTab == PickerTab.APPS) 0 else 1,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp))
                    ) {
                        Tab(
                            selected = uiState.pickerTab == PickerTab.APPS,
                            onClick = { onTabChange?.invoke(PickerTab.APPS) },
                            text = { Text("Apps", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = uiState.pickerTab == PickerTab.WEBSITES,
                            onClick = { onTabChange?.invoke(PickerTab.WEBSITES) },
                            text = { Text("Websites", fontWeight = FontWeight.Bold) }
                        )
                    }

                    AnimatedContent(
                        targetState = uiState.pickerTab,
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn(animationSpec = tween(300)) + slideInHorizontally { direction * it / 4 })
                                .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -direction * it / 4 })
                        },
                        label = "MultiPickerSearchBar"
                    ) { tab ->
                        if (tab == PickerTab.APPS) {
                            SearchBar(
                                inputField = {
                                    SearchBarDefaults.InputField(
                                        query = uiState.searchQuery,
                                        onQueryChange = onSearchQueryChange,
                                        onSearch = { },
                                        expanded = false,
                                        onExpandedChange = {},
                                        placeholder = { Text("Search apps...") },
                                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                        trailingIcon = {
                                            if (uiState.searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { onSearchQueryChange("") }) {
                                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
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
                        } else {
                            SearchBar(
                                inputField = {
                                    SearchBarDefaults.InputField(
                                        query = uiState.websiteSearchQuery,
                                        onQueryChange = { onWebsiteSearchChange?.invoke(it) },
                                        onSearch = { },
                                        expanded = false,
                                        onExpandedChange = {},
                                        placeholder = { Text("Enter website URL...") },
                                        leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                                        trailingIcon = {
                                            if (uiState.websiteSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { onWebsiteSearchChange?.invoke("") }) {
                                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = uiState.pickerTab,
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn(animationSpec = tween(300)) + slideInHorizontally { direction * it / 4 })
                                .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -direction * it / 4 })
                        },
                        label = "MultiPickerContent"
                    ) { tab ->
                        if (tab == PickerTab.APPS) {
                            MultiAppsContent(
                                uiState = uiState,
                                onAppToggled = onAppToggled
                            )
                        } else {
                            MultiWebsitesContent(
                                uiState = uiState,
                                onWebsiteToggled = onWebsiteToggled
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onConfirm()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MultiAppsContent(
    uiState: FocusUiState,
    onAppToggled: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val allApps = (uiState.topApps + uiState.installedApps).distinctBy { it.packageName }
        itemsIndexed(
            allApps,
            key = { _, app -> app.packageName }
        ) { index, app ->
            val isSelected = app.packageName in uiState.selectedAppsForSchedule
            val itemScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.98f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "itemScale"
            )

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "containerColor"
            )

            val shape = when {
                allApps.size == 1 -> RoundedCornerShape(24.dp)
                index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                index == allApps.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(8.dp)
            }

            Box(
                modifier = Modifier.animateItem(
                    fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                    fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                    placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                )
            ) {
                AppPickerItem(
                    app = app,
                    shape = shape,
                    onClick = { onAppToggled(app.packageName) },
                    isSelected = isSelected,
                    itemScale = itemScale,
                    containerColor = containerColor,
                    showCheckbox = true
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MultiWebsitesContent(
    uiState: FocusUiState,
    onWebsiteToggled: ((String) -> Unit)? = null
) {
    val selectedWebsites = uiState.selectedAppsForSchedule.filter { WebsiteRepository.isWebsitePackageName(it) }

    if (uiState.websiteSuggestionsLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), contentAlignment = Alignment.Center) {
            ZenithContainedLoadingIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (uiState.websiteSuggestions.isNotEmpty()) {
            item {
                Text(
                    text = "Suggestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            itemsIndexed(
                items = uiState.websiteSuggestions,
                key = { _, domain -> "web_$domain" }
            ) { index, domain ->
                val fullUrl = WebsiteRepository.normalizeUrl(domain)
                val domainOnly = WebsiteRepository.extractDomain(domain)
                val displayName = WebsiteRepository.getDisplayName(domainOnly, fullUrl)
                val packageName = WebsiteRepository.createPackageName(domainOnly)
                val isSelected = packageName in uiState.selectedAppsForSchedule

                val itemScale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.98f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "itemScale"
                )

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "containerColor"
                )

                val shape = when {
                    uiState.websiteSuggestions.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    index == uiState.websiteSuggestions.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                Card(
                    onClick = { onWebsiteToggled?.invoke(domain) },
                    modifier = Modifier.fillMaxWidth().scale(itemScale),
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            val wsShape = MaterialShapes.Square.toShape()
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("app-icon://${com.etrisad.zenith.data.website.WebsiteRepository.createPackageName(domainOnly)}")
                                    .crossfade(400)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, wsShape)
                                    .clip(wsShape),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(wsShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Language, contentDescription = null)
                                    }
                                }
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onWebsiteToggled?.invoke(domain) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
                }
            } else if (selectedWebsites.isNotEmpty()) {
            item {
                Text(
                    text = "Selected Websites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            itemsIndexed(
                items = selectedWebsites.toList(),
                key = { _, pkg -> pkg }
            ) { index, pkg ->
                val domain = WebsiteRepository.extractDomainFromPackageName(pkg)
                val displayName = WebsiteRepository.getDisplayName(domain, "https://$domain")

                Card(
                    onClick = { onWebsiteToggled?.invoke(domain) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            val wsShape = MaterialShapes.Square.toShape()
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("app-icon://${com.etrisad.zenith.data.website.WebsiteRepository.createPackageName(domain)}")
                                    .crossfade(400)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, wsShape)
                                    .clip(wsShape),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(wsShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Language, contentDescription = null)
                                    }
                                }
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }

            if (uiState.websiteSearchQuery.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Type a website name to add more",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        } else if (uiState.websiteSearchQuery.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No suggestions found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Type a website name to get suggestions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
        }
    }
}


