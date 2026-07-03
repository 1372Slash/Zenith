package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.PickerTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPickerBottomSheet(
    uiState: FocusUiState,
    title: String? = null,
    onDismiss: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTabChange: ((PickerTab) -> Unit)? = null,
    onWebsiteSearchChange: ((String) -> Unit)? = null,
    onWebsiteSelected: ((String) -> Unit)? = null
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
                    val defaultTitle = if (uiState.selectedFocusType == FocusType.GOAL) "Select Productive App" else "Select App to Shield"
                    Text(
                        text = title ?: defaultTitle,
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
                        label = "PickerSearchBar"
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
                                shape = SearchBarDefaults.inputFieldShape,
                                colors = SearchBarDefaults.colors(),
                                tonalElevation = SearchBarDefaults.TonalElevation,
                                shadowElevation = 0.dp,
                                windowInsets = SearchBarDefaults.windowInsets,
                                content = {}
                            )
                        } else {
                            SearchBar(
                                inputField = {
                                    SearchBarDefaults.InputField(
                                        query = uiState.websiteSearchQuery,
                                        onQueryChange = { onWebsiteSearchChange?.invoke(it) },
                                        onSearch = { onWebsiteSelected?.invoke(uiState.websiteSearchQuery) },
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
                                shape = SearchBarDefaults.inputFieldShape,
                                colors = SearchBarDefaults.colors(),
                                tonalElevation = SearchBarDefaults.TonalElevation,
                                shadowElevation = 0.dp,
                                windowInsets = SearchBarDefaults.windowInsets,
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
                        label = "PickerContent"
                    ) { tab ->
                        if (tab == PickerTab.APPS) {
                            AppsContent(uiState = uiState, sheetState = sheetState, scope = scope, onAppSelected = onAppSelected, onSearchQueryChange = onSearchQueryChange)
                        } else {
                            WebsitesContent(uiState = uiState, sheetState = sheetState, scope = scope, onWebsiteSelected = onWebsiteSelected)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppsContent(
    uiState: FocusUiState,
    sheetState: androidx.compose.material3.SheetState,
    scope: kotlinx.coroutines.CoroutineScope,
    onAppSelected: (AppInfo) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    if (uiState.isLoadingApps) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), contentAlignment = Alignment.Center) {
            ZenithContainedLoadingIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
            )
        ) {
            if (uiState.topApps.isNotEmpty() && uiState.searchQuery.isEmpty()) {
                item {
                    PickerSectionHeader(title = "Top Used Apps")
                    Spacer(modifier = Modifier.height(16.dp))
                }

                itemsIndexed(
                    items = uiState.topApps,
                    key = { _, app -> "top_${app.packageName}" }
                ) { index, app ->
                    val shape = when {
                        uiState.topApps.size == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        index == uiState.topApps.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(8.dp)
                    }
                    Column(modifier = Modifier.animateItem()) {
                        AppPickerItem(
                            app = app,
                            shape = shape,
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onAppSelected(app)
                                }
                            },
                            isTopApp = true
                        )
                        if (index < uiState.topApps.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item {
                PickerSectionHeader(
                    title = if (uiState.searchQuery.isEmpty()) "All Apps" else "Search Results"
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.installedApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateItem(),
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
                                text = "No apps found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = uiState.installedApps,
                    key = { _, app -> "all_${app.packageName}" }
                ) { index, app ->
                    val shape = when {
                        uiState.installedApps.size == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        index == uiState.installedApps.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(8.dp)
                    }
                    Column(modifier = Modifier.animateItem()) {
                        AppPickerItem(
                            app = app,
                            shape = shape,
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onAppSelected(app)
                                }
                            },
                            isTopApp = false
                        )
                        if (index < uiState.installedApps.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WebsitesContent(
    uiState: FocusUiState,
    sheetState: androidx.compose.material3.SheetState,
    scope: kotlinx.coroutines.CoroutineScope,
    onWebsiteSelected: ((String) -> Unit)? = null
) {
    if (uiState.websiteSuggestionsLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), contentAlignment = Alignment.Center) {
            ZenithContainedLoadingIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
            )
        ) {
            if (uiState.websiteSuggestions.isNotEmpty()) {
            item {
                PickerSectionHeader(title = "Suggestions")
                Spacer(modifier = Modifier.height(16.dp))
            }

            itemsIndexed(
                items = uiState.websiteSuggestions,
                key = { _, domain -> "web_$domain" }
            ) { index, domain ->
                val fullUrl = com.etrisad.zenith.data.website.WebsiteRepository.normalizeUrl(domain)
                val domainOnly = com.etrisad.zenith.data.website.WebsiteRepository.extractDomain(domain)
                val displayName = com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domainOnly, fullUrl)

                val shape = when {
                    uiState.websiteSuggestions.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    index == uiState.websiteSuggestions.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                Card(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onWebsiteSelected?.invoke(domain)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
            } else if (uiState.websiteSearchQuery.isNotEmpty()) {
                item {
                val normalized = com.etrisad.zenith.data.website.WebsiteRepository.validateUrl(uiState.websiteSearchQuery)
                if (normalized != null) {
                    val domainOnly = com.etrisad.zenith.data.website.WebsiteRepository.extractDomain(normalized)
                    val displayName = com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domainOnly, normalized)
                    Card(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onWebsiteSelected?.invoke(normalized)
                            }
                        },
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
                                    text = normalized,
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
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Open",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                } else {
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
                                text = "Type a website domain or URL",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "e.g. youtube, twitter, notion",
                                style = MaterialTheme.typography.bodySmall,
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
