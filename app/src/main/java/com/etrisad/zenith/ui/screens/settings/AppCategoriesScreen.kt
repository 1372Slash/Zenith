package com.etrisad.zenith.ui.screens.settings

import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.model.AppCategory
import com.etrisad.zenith.data.model.CategoryCatalog
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.ui.components.AppCategoryPickerSheet
import com.etrisad.zenith.ui.components.CategoryChip
import com.etrisad.zenith.ui.components.CategoryHeader
import com.etrisad.zenith.ui.components.CategorySettingsBottomSheet
import com.etrisad.zenith.ui.components.CategorySetupWizardSheet
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.customCategoryColors
import com.etrisad.zenith.ui.components.customCategoryIcons
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.components.appCategoryColor
import com.etrisad.zenith.ui.components.appCategoryIcon
import com.etrisad.zenith.ui.viewmodel.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class CategorisedApp(
    val packageName: String,
    val appName: String,
    val isWebsite: Boolean = false
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppCategoriesScreen(
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    val application = context.applicationContext as ZenithApplication
    val shieldRepository = remember(application) { application.shieldRepository }
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val categoryCatalog = remember(preferences) { CategoryCatalog(preferences) }
    val defaultCategories = remember(preferences.defaultCategoryOverrides) {
        AppCategory.assignable.map { preferences.defaultCategoryOverrides[it.id] ?: it }
    }
    val coroutineScope = rememberCoroutineScope()
    var sortMode by remember { mutableStateOf(CategoryBrowseSort.APP_NAME) }
    var recentWebsites by remember { mutableStateOf<List<String>>(emptyList()) }
    var apps by remember { mutableStateOf<List<CategorisedApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var editingApp by remember { mutableStateOf<CategorisedApp?>(null) }
    var pendingAssignment by remember { mutableStateOf<CategorisedApp?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var pendingNewApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wizardQueue by remember { mutableStateOf<List<CategorisedApp>>(emptyList()) }
    var wizardIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        recentWebsites = withContext(Dispatchers.IO) {
            runCatching { shieldRepository.getWebsiteDomains() }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }
            } catch (_: Exception) {
                emptyList()
            }

            val websiteItems = runCatching {
                shieldRepository.getWebsiteDomains().map { domain ->
                    CategorisedApp(
                        packageName = WebsiteRepository.createPackageName(domain),
                        appName = domain.removePrefix("www."),
                        isWebsite = true
                    )
                }
            }.getOrDefault(emptyList())

            apps = (
                installed
                    .asSequence()
                    .filter { it.packageName != context.packageName }
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { CategorisedApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                    .toList() + websiteItems
                ).sortedBy { it.appName.lowercase() }
            isLoading = false
        }
    }

    val allItems = remember(apps, preferences.appCategories) {
        val known = apps.map { it.packageName }.toSet()
        val taggedWebsites = preferences.appCategories.keys
            .filter { WebsiteRepository.isWebsitePackageName(it) && it !in known }
            .map { packageName ->
                val domain = WebsiteRepository.extractDomainFromPackageName(packageName)
                CategorisedApp(packageName, domain.removePrefix("www."), isWebsite = true)
            }
        apps + taggedWebsites
    }
    val filtered = remember(searchQuery, allItems) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val websiteItems = remember(filtered, categoryCatalog, sortMode) {
        filtered.filter { it.isWebsite }.sortedWith(browseComparator(categoryCatalog, sortMode))
    }
    val appItems = remember(filtered, categoryCatalog, sortMode) {
        filtered.filterNot { it.isWebsite }.sortedWith(browseComparator(categoryCatalog, sortMode))
    }
    val taggedCount = remember(allItems, categoryCatalog) {
        allItems.count {
            categoryCatalog.resolve(it.packageName, it.appName) != AppCategory.UNCATEGORIZED
        }
    }
    val totalCount = allItems.size
    val uncategorizedCount = (totalCount - taggedCount).coerceAtLeast(0)
    val progressFraction = if (totalCount > 0) taggedCount.toFloat() / totalCount else 0f
    val installCount = remember(allItems) { allItems.count { !it.isWebsite } }
    val websiteCount = remember(allItems) { allItems.count { it.isWebsite } }
    val appOptions = remember(allItems) {
        allItems.map { AppInfo(it.packageName, it.appName) }
    }

    fun assignedPackages(category: AppCategory): Set<String> =
        preferences.appCategories.filterValues { it == category.id }.keys

    fun advanceWizard() {
        if (wizardIndex + 1 >= wizardQueue.size) {
            wizardQueue = emptyList()
            wizardIndex = 0
        } else {
            wizardIndex += 1
        }
    }

    fun startCategoryWizard() {
        val pending = allItems.filter {
            categoryCatalog.resolve(it.packageName, it.appName) == AppCategory.UNCATEGORIZED
        }
        if (pending.isEmpty()) return
        wizardQueue = pending
        wizardIndex = 0
    }

    fun assignWizardCategory(category: AppCategory) {
        val app = wizardQueue.getOrNull(wizardIndex) ?: return
        coroutineScope.launch {
            preferencesRepository.setAppCategory(app.packageName, category)
        }
        advanceWizard()
    }

    fun createWizardCategory(name: String) {
        val app = wizardQueue.getOrNull(wizardIndex) ?: return
        val label = name.trim()
        if (label.isEmpty()) return
        val iconKey = customCategoryIcons.random().first
        val colorArgb = customCategoryColors.random()
        coroutineScope.launch {
            val category = preferencesRepository.addCustomCategory(
                label = label,
                description = "",
                iconKey = iconKey,
                colorArgb = colorArgb
            )
            preferencesRepository.setAppCategory(app.packageName, category)
        }
        advanceWizard()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "category_status_progress") {
                Box(modifier = Modifier.animateItem()) {
                    CategoryStatusProgress(
                        taggedCount = taggedCount,
                        totalCount = totalCount,
                        progressFraction = progressFraction,
                        installCount = installCount,
                        websiteCount = websiteCount,
                        customCount = preferences.customCategories.size,
                        isLoading = isLoading
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "category_search") {
                Box(modifier = Modifier.animateItem()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        placeholder = { Text("Search apps and websites...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            if (!isLoading && uncategorizedCount > 0) {
                item(key = "category_wizard_cta") {
                    Box(modifier = Modifier.animateItem()) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            ZenithButton(
                                onClick = { startCategoryWizard() },
                                text = "Start Setup Wizard",
                                icon = Icons.Outlined.AutoAwesome,
                                type = ZenithButtonType.Tonal,
                                size = ZenithButtonSize.Medium,
                                fillMaxWidth = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$uncategorizedCount apps still need a category",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            if (!isLoading) {
                item(key = "default_categories_header") {
                    Box(modifier = Modifier.animateItem()) {
                        CategorySectionHeader(title = "Default Categories", count = defaultCategories.size)
                    }
                }
                itemsIndexed(
                    items = defaultCategories,
                    key = { _, category -> category.id }
                ) { index, category ->
                    val appCount = allItems.count { preferences.appCategories[it.packageName] == category.id }
                    Box(modifier = Modifier.animateItem()) {
                        CategoryListRow(
                            category = category,
                            appCount = appCount,
                            shape = categoryRowShape(index, defaultCategories.size),
                            onEdit = {
                                editingCategory = category
                                showCategorySheet = true
                            }
                        )
                    }
                }

                if (preferences.customCategories.isNotEmpty()) {
                    item(key = "your_categories_header") {
                        Box(modifier = Modifier.animateItem()) {
                            CategorySectionHeader(title = "Your Categories", count = preferences.customCategories.size)
                        }
                    }
                    itemsIndexed(
                        items = preferences.customCategories,
                        key = { _, category -> category.id }
                    ) { index, category ->
                        val appCount = allItems.count { preferences.appCategories[it.packageName] == category.id }
                        Box(modifier = Modifier.animateItem()) {
                            CategoryListRow(
                                category = category,
                                appCount = appCount,
                                shape = categoryRowShape(index, preferences.customCategories.size),
                                onEdit = {
                                    editingCategory = category
                                    showCategorySheet = true
                                }
                            )
                        }
                    }
                }

                item(key = "category_sort_header") {
                    Box(modifier = Modifier.animateItem()) {
                        CategorySortHeader(
                            title = "Apps & Websites",
                            sortMode = sortMode,
                            onSortModeChange = { sortMode = it },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            when {
                isLoading -> item(key = "category_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                            .animateItem(),
                        contentAlignment = Alignment.Center
                    ) {
                        ZenithContainedLoadingIndicator()
                    }
                }

                filtered.isEmpty() -> item(key = "category_empty") {
                    Text(
                        text = if (searchQuery.isBlank()) "No apps or websites found" else "Nothing matches \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(24.dp)
                            .animateItem()
                    )
                }

                else -> {
                    if (websiteItems.isNotEmpty()) {
                        item(key = "category_websites_header") {
                            Box(modifier = Modifier.animateItem()) {
                                CategorySectionHeader(title = "Websites", count = websiteItems.size)
                            }
                        }
                        itemsIndexed(
                            items = websiteItems,
                            key = { _, app -> app.packageName }
                        ) { index, app ->
                            val category = categoryCatalog.resolve(app.packageName, app.appName)
                            Box(modifier = Modifier.animateItem()) {
                                AppCategoryRow(
                                    app = app,
                                    category = category,
                                    isSuggested = categoryCatalog.isSuggested(app.packageName, app.appName),
                                    shape = categoryRowShape(index, websiteItems.size),
                                    onClick = { editingApp = app }
                                )
                            }
                        }
                    }
                    if (appItems.isNotEmpty()) {
                        item(key = "category_apps_header") {
                            Box(modifier = Modifier.animateItem()) {
                                CategorySectionHeader(title = "Apps", count = appItems.size)
                            }
                        }
                        itemsIndexed(
                            items = appItems,
                            key = { _, app -> app.packageName }
                        ) { index, app ->
                            val category = categoryCatalog.resolve(app.packageName, app.appName)
                            Box(modifier = Modifier.animateItem()) {
                                AppCategoryRow(
                                    app = app,
                                    category = category,
                                    isSuggested = categoryCatalog.isSuggested(app.packageName, app.appName),
                                    shape = categoryRowShape(index, appItems.size),
                                    onClick = { editingApp = app }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp)
        ) {
            MediumFloatingActionButton(
                onClick = {
                    pendingAssignment = null
                    pendingNewApps = emptySet()
                    editingCategory = null
                    showCategorySheet = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(28.dp),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Create Custom Category",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    editingApp?.let { app ->
        AppCategoryPickerSheet(
            appName = app.appName,
            packageName = app.packageName,
            selected = categoryCatalog.resolve(app.packageName, app.appName),
            categories = categoryCatalog.categories,
            onDismiss = { editingApp = null },
            onCreateNew = {
                pendingAssignment = app
                pendingNewApps = emptySet()
                editingApp = null
                editingCategory = null
                showCategorySheet = true
            },
            onSelect = { category ->
                coroutineScope.launch { preferencesRepository.setAppCategory(app.packageName, category) }
                editingApp = null
            }
        )
    }

    if (showCategorySheet) {
        val sheetCategory = editingCategory
        CategorySettingsBottomSheet(
            existingCategory = sheetCategory,
            apps = appOptions,
            assignedApps = sheetCategory?.let { assignedPackages(it) } ?: emptySet(),
            recentWebsites = recentWebsites,
            onAppsChange = { selected ->
                if (sheetCategory != null) {
                    val previous = assignedPackages(sheetCategory)
                    coroutineScope.launch {
                        (selected - previous).forEach { packageName ->
                            preferencesRepository.setAppCategory(packageName, sheetCategory)
                        }
                        (previous - selected).forEach { packageName ->
                            preferencesRepository.setAppCategory(packageName, AppCategory.UNCATEGORIZED)
                        }
                    }
                } else {
                    pendingNewApps = selected
                }
            },
            onDismiss = {
                showCategorySheet = false
                editingCategory = null
                pendingAssignment = null
                pendingNewApps = emptySet()
            },
            onSave = { name, description, iconKey, colorArgb ->
                showCategorySheet = false
                val existing = editingCategory
                editingCategory = null
                if (existing != null) {
                    coroutineScope.launch {
                        if (existing.isCustom) {
                            preferencesRepository.updateCustomCategory(existing.id, name, description, iconKey, colorArgb)
                        } else {
                            preferencesRepository.updateDefaultCategory(existing.id, name, description, iconKey, colorArgb)
                        }
                    }
                } else {
                    coroutineScope.launch {
                        val newCategory = preferencesRepository.addCustomCategory(name, description, iconKey, colorArgb)
                        pendingAssignment?.let {
                            preferencesRepository.setAppCategory(it.packageName, newCategory)
                        }
                        pendingNewApps.forEach { packageName ->
                            preferencesRepository.setAppCategory(packageName, newCategory)
                        }
                        pendingAssignment = null
                        pendingNewApps = emptySet()
                    }
                }
            },
            onDelete = {
                showCategorySheet = false
                val cat = editingCategory
                editingCategory = null
                if (cat != null) {
                    coroutineScope.launch {
                        if (cat.isCustom) {
                            preferencesRepository.deleteCustomCategory(cat.id)
                        } else {
                            preferencesRepository.resetDefaultCategory(cat.id)
                        }
                    }
                }
            }
        )
    }

    val wizardApp = wizardQueue.getOrNull(wizardIndex)
    if (wizardApp != null) {
        CategorySetupWizardSheet(
            appName = wizardApp.appName,
            packageName = wizardApp.packageName,
            isWebsite = wizardApp.isWebsite,
            categories = categoryCatalog.categories.filter { it != AppCategory.UNCATEGORIZED },
            remaining = wizardQueue.size - wizardIndex,
            total = wizardQueue.size,
            onSelectCategory = { assignWizardCategory(it) },
            onCreateCategory = { createWizardCategory(it) },
            onDismiss = {
                wizardQueue = emptyList()
                wizardIndex = 0
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryStatusProgress(
    taggedCount: Int,
    totalCount: Int,
    progressFraction: Float,
    installCount: Int,
    websiteCount: Int,
    customCount: Int,
    isLoading: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "CategoryProgress"
    )

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 4.dp.toPx() } }

    val infiniteTransition = rememberInfiniteTransition(label = "wavy")
    val waveAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .padding(16.dp)
        ) {
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                stroke = Stroke(width = strokeWidthPx),
                trackStroke = Stroke(width = strokeWidthPx),
                amplitude = { waveAmplitude },
                wavelength = 48.dp
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(progressFraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isLoading) "Loading..." else "$taggedCount/$totalCount categorized",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryMiniPill(icon = Icons.Outlined.Apps, label = "$installCount apps")
            CategoryMiniPill(icon = Icons.Outlined.Language, label = "$websiteCount sites")
            CategoryMiniPill(icon = Icons.Outlined.Add, label = "$customCount custom")
        }
    }
}

@Composable
private fun CategoryMiniPill(icon: ImageVector, label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppCategoryRow(
    app: CategorisedApp,
    category: AppCategory,
    isSuggested: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    val fallbackIcon = if (app.isWebsite) Icons.Outlined.Language else appCategoryIcon(category)
    val context = LocalContext.current
    val iconShape = if (app.isWebsite) MaterialShapes.Square.toShape() else CircleShape
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data("app-icon://${app.packageName}")
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (app.isWebsite) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        iconShape
                    )
                    .clip(iconShape),
                contentScale = ContentScale.Crop,
                error = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(iconShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            tint = if (app.isWebsite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (app.isWebsite) "Website" else app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (isSuggested) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Suggested",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                CategoryChip(category = category)
            }
        }
    }
}

private enum class CategoryBrowseSort { APP_NAME, CATEGORY_NAME }

private fun browseComparator(
    catalog: CategoryCatalog,
    sort: CategoryBrowseSort
): Comparator<CategorisedApp> = when (sort) {
    CategoryBrowseSort.APP_NAME -> compareBy { it.appName.lowercase() }
    CategoryBrowseSort.CATEGORY_NAME -> compareBy<CategorisedApp>(
        { catalog.resolve(it.packageName, it.appName).label.lowercase() },
        { it.appName.lowercase() }
    )
}

@Composable
private fun CategorySortHeader(
    title: String,
    sortMode: CategoryBrowseSort,
    onSortModeChange: (CategoryBrowseSort) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ZenithToggleButtonGroup(
            modifier = Modifier.width(140.dp),
            size = ZenithButtonSize.Medium,
            options = listOf(
                ZenithToggleOption(
                    icon = if (sortMode == CategoryBrowseSort.APP_NAME) {
                        Icons.Filled.SortByAlpha
                    } else {
                        Icons.Outlined.SortByAlpha
                    }
                ),
                ZenithToggleOption(
                    icon = if (sortMode == CategoryBrowseSort.CATEGORY_NAME) {
                        Icons.Filled.Category
                    } else {
                        Icons.Outlined.Category
                    }
                )
            ),
            selectedIndices = setOf(if (sortMode == CategoryBrowseSort.APP_NAME) 0 else 1),
            onToggle = { index ->
                onSortModeChange(
                    if (index == 0) CategoryBrowseSort.APP_NAME else CategoryBrowseSort.CATEGORY_NAME
                )
            }
        )
    }
}

@Composable
private fun CategorySectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

private fun categoryRowShape(index: Int, size: Int): RoundedCornerShape = when {
    size == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    index == size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(8.dp)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoryListRow(
    category: AppCategory,
    appCount: Int,
    shape: RoundedCornerShape,
    onEdit: () -> Unit
) {
    val color = appCategoryColor(category)
    Card(
        onClick = onEdit,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = appCategoryIcon(category),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("$appCount ${if (appCount == 1) "app" else "apps"}")
                        if (category.description.isNotBlank()) {
                            append(" · ")
                            append(category.description)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
