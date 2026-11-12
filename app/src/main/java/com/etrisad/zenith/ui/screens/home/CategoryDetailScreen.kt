package com.etrisad.zenith.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.model.AppCategory
import com.etrisad.zenith.data.model.CategoryCatalog
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.CategoryHeader
import com.etrisad.zenith.ui.components.CategorySettingsBottomSheet
import com.etrisad.zenith.ui.components.appCategoryColor
import com.etrisad.zenith.ui.components.appCategoryIcon
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryDetailScreen(
    categoryId: String,
    viewModel: HomeViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues,
    onAppClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val detailUiState by viewModel.categoryDetailUiState.collectAsState()
    val preferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences()
    )
    val categoryCatalog = remember(preferences) { CategoryCatalog(preferences) }
    val category = remember(categoryId, categoryCatalog) { categoryCatalog.get(categoryId) }
    val color = appCategoryColor(category)
    val scope = rememberCoroutineScope()

    if (detailUiState.isEditSheetOpen) {
        CategorySettingsBottomSheet(
            existingCategory = category,
            onDismiss = { viewModel.closeCategoryEditSheet() },
            onSave = { name, description, iconKey, colorArgb ->
                viewModel.closeCategoryEditSheet()
                scope.launch {
                    if (category.isCustom) {
                        userPreferencesRepository.updateCustomCategory(category.id, name, description, iconKey, colorArgb)
                    } else if (category.id != AppCategory.UNCATEGORIZED.id) {
                        userPreferencesRepository.updateDefaultCategory(category.id, name, description, iconKey, colorArgb)
                    }
                }
            },
            onDelete = when {
                category.isCustom -> {
                    {
                        viewModel.closeCategoryEditSheet()
                        scope.launch {
                            userPreferencesRepository.deleteCustomCategory(category.id)
                        }
                    }
                }
                category.id != AppCategory.UNCATEGORIZED.id -> {
                    {
                        viewModel.closeCategoryEditSheet()
                        scope.launch {
                            userPreferencesRepository.resetDefaultCategory(category.id)
                        }
                    }
                }
                else -> null
            }
        )
    }

    val allUsage = remember(uiState.allAppsUsage, uiState.websiteUsage) {
        uiState.allAppsUsage + uiState.websiteUsage
    }
    val categoryApps = remember(allUsage, categoryCatalog, category) {
        allUsage.filter {
            it.totalTimeVisible > 0 &&
                categoryCatalog.resolve(it.packageName, it.appName) == category
        }.sortedByDescending { it.totalTimeVisible }
    }
    val categoryTotal = categoryApps.sumOf { it.totalTimeVisible }
    val dayTotal = allUsage.sumOf { it.totalTimeVisible }
    val share = if (dayTotal > 0L) (categoryTotal.toFloat() / dayTotal).coerceIn(0f, 1f) else 0f
    val animatedShare by animateFloatAsState(targetValue = share, label = "CategoryDetailShare")

    val hourly = remember(uiState.hourlyUsage, categoryCatalog, category) {
        val totals = LongArray(24)
        uiState.hourlyUsage.forEach { hourInfo ->
            hourInfo.apps.forEach { app ->
                if (categoryCatalog.resolve(app.packageName, app.appName) == category) {
                    if (hourInfo.hour in 0..23) {
                        totals[hourInfo.hour] = totals[hourInfo.hour] + app.totalTimeVisible
                    }
                }
            }
        }
        totals.toList()
    }
    val peakHour = remember(hourly) {
        hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0L }
    }
    val dayLabel = remember(uiState.selectedDateMillis) {
        if (uiState.selectedDateMillis == 0L) {
            "Today"
        } else {
            java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                .format(java.util.Date(uiState.selectedDateMillis))
        }
    }

    if (detailUiState.isInfoDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeCategoryInfoDialog() },
            title = { Text(category.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(category.description.ifEmpty { "Categories help you organize and track your screen time by app purpose." })
                    HorizontalDivider()
                    Text("• Total Usage: ${viewModel.formatDuration(categoryTotal)} (${(share * 100).roundToInt()}% of screen time)")
                    Text("• Apps Used: ${categoryApps.size}")
                    Text("• Average per App: ${viewModel.formatDuration(if (categoryApps.isNotEmpty()) categoryTotal / categoryApps.size else 0L)}")
                    Text("• Peak Hour: ${peakHour?.let { String.format(Locale.getDefault(), "%02d:00", it) } ?: "—"}")
                    Text("• Top App: ${categoryApps.firstOrNull()?.let { "${it.appName} (${viewModel.formatDuration(it.totalTimeVisible)})" } ?: "None"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closeCategoryInfoDialog() }) {
                    Text("Got it")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 80.dp
        )
    ) {
        item(key = "category_detail_header") {
            CategoryHeader(
                category = category,
                color = color,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item(key = "category_detail_usage_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = color.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Today's Usage",
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = viewModel.formatDuration(categoryTotal),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$dayLabel · ${(share * 100).roundToInt()}% of your screen time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearWavyProgressIndicator(
                        progress = { animatedShare },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = color,
                        trackColor = color.copy(alpha = 0.1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        item(key = "category_detail_stats_row1") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CategoryMiniStat(
                    title = "Apps used",
                    value = "${categoryApps.size}",
                    icon = Icons.Outlined.Apps,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                CategoryMiniStat(
                    title = "Average / app",
                    value = viewModel.formatDuration(if (categoryApps.isNotEmpty()) categoryTotal / categoryApps.size else 0L),
                    icon = Icons.Outlined.BarChart,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        item(key = "category_detail_stats_row2") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CategoryMiniStat(
                    title = "Peak hour",
                    value = peakHour?.let { String.format(Locale.getDefault(), "%02d:00", it) } ?: "—",
                    icon = Icons.Outlined.Schedule,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                CategoryMiniStat(
                    title = "Top app",
                    value = categoryApps.firstOrNull()?.appName ?: "—",
                    icon = Icons.Outlined.Star,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        item(key = "category_detail_hourly") {
            Card(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CategoryHourlyChart(hourly = hourly, peakHour = peakHour, color = color, formatDuration = viewModel::formatDuration)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        item(key = "category_detail_apps_header") {
            Text(
                text = "Apps in this category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (categoryApps.isEmpty()) {
            item(key = "category_detail_empty") {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No apps in this category have usage on $dayLabel. Use Usage Stats to tag apps or pick another day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = categoryApps,
                key = { _, app -> app.packageName }
            ) { index, app ->
                CategoryAppRow(
                    app = app,
                    share = if (categoryTotal > 0L) app.totalTimeVisible.toFloat() / categoryTotal else 0f,
                    formatDuration = viewModel::formatDuration,
                    shape = categoryAppShape(index, categoryApps.size),
                    onClick = { onAppClick(app.packageName) }
                )
                if (index < categoryApps.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        item(key = "category_detail_note") {
            Text(
                text = "Categories include built-in suggestions for common apps. Open an app to change its category at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryMiniStat(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = color
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryHourlyChart(
    hourly: List<Long>,
    peakHour: Int?,
    color: Color,
    formatDuration: (Long) -> String
) {
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    val maxUsage = remember(hourly) { hourly.maxOrNull()?.coerceAtLeast(1L) ?: 1L }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hourly Usage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedContent(
                targetState = selectedHour,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                     slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it / 2 })
                        .togetherWith(
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                            slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it / 2 }
                        )
                },
                label = "SelectedHourText"
            ) { hour ->
                if (hour != null) {
                    val usage = hourly.getOrNull(hour) ?: 0L
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format("%02d:00 - %02d:00", hour, (hour + 1) % 24),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatDuration(usage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = color
                        )
                    }
                } else {
                    Text(
                        text = "Tap a bar for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            
            val paddedHourly = remember(hourly) {
                val list = mutableListOf<Long>()
                for (i in 0..23) {
                    list.add(hourly.getOrNull(i) ?: 0L)
                }
                list
            }

            paddedHourly.forEachIndexed { hour, usage ->
                val isSelected = selectedHour == hour
                val isCurrentHour = hour == currentHour
                val isPeak = hour == peakHour
                
                val barHeight = (usage.toFloat() / maxUsage).coerceIn(0.05f, 1f)
                val animatedHeight by animateFloatAsState(
                    targetValue = barHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "CategoryHourlyBarHeight_$hour"
                )

                val barColor = when {
                    isSelected -> color
                    isPeak -> color.copy(alpha = 0.85f)
                    isCurrentHour -> color.copy(alpha = 0.6f)
                    else -> color.copy(alpha = 0.3f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.5.dp)
                        .fillMaxHeight(animatedHeight)
                        .clip(CircleShape)
                        .background(barColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedHour = if (selectedHour == hour) null else hour }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("23:59", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryAppRow(
    app: AppUsageInfo,
    share: Float,
    formatDuration: (Long) -> String,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    val context = LocalContext.current
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
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(share.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDuration(app.totalTimeVisible),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun categoryAppShape(index: Int, size: Int): RoundedCornerShape = when {
    size == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    index == size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(8.dp)
}
