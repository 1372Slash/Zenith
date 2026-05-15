package com.etrisad.zenith.ui.components.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPickerBottomSheet(
    uiState: FocusUiState,
    title: String? = null,
    onDismiss: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onSearchQueryChange: (String) -> Unit
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.isLoadingApps) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), contentAlignment = Alignment.Center) {
                        ZenithContainedLoadingIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
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
        }
    }
}
