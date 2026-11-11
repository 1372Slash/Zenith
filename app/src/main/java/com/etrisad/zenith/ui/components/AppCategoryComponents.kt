package com.etrisad.zenith.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsMotorsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.model.AppCategory
import com.etrisad.zenith.data.model.CategoryCatalog
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import kotlin.math.roundToInt

@Composable
fun appCategoryColor(category: AppCategory): Color {
    category.colorArgb?.let { return Color(it) }
    return when (category.id) {
        AppCategory.PRODUCTIVE.id -> MaterialTheme.colorScheme.primary
        AppCategory.SOCIAL.id -> MaterialTheme.colorScheme.tertiary
        AppCategory.LEISURE.id -> MaterialTheme.colorScheme.secondary
        AppCategory.UTILITIES.id -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outline
    }
}

val customCategoryIcons: List<Pair<String, ImageVector>> = listOf(
    "work" to Icons.Outlined.Work,
    "business" to Icons.Outlined.BusinessCenter,
    "engineering" to Icons.Outlined.Engineering,
    "school" to Icons.Outlined.School,
    "science" to Icons.Outlined.Science,
    "book" to Icons.Outlined.Book,
    "reading" to Icons.AutoMirrored.Outlined.MenuBook,
    "library" to Icons.Outlined.LocalLibrary,
    "docs" to Icons.Outlined.Description,
    "article" to Icons.AutoMirrored.Outlined.Article,
    "news" to Icons.Outlined.Newspaper,
    "notes" to Icons.AutoMirrored.Outlined.StickyNote2,
    "tasks" to Icons.Outlined.TaskAlt,
    "calendar" to Icons.Outlined.CalendarMonth,
    "code" to Icons.Outlined.Code,
    "idea" to Icons.Outlined.Lightbulb,
    "translate" to Icons.Outlined.Translate,
    "chart" to Icons.Outlined.BarChart,
    "trending" to Icons.AutoMirrored.Outlined.TrendingUp,
    "apps" to Icons.Outlined.Apps,
    "star" to Icons.Outlined.Star,
    "favorite" to Icons.Outlined.Favorite,
    "chat" to Icons.Outlined.ChatBubbleOutline,
    "groups" to Icons.Outlined.Groups,
    "call" to Icons.Outlined.Call,
    "email" to Icons.Outlined.Email,
    "celebration" to Icons.Outlined.Celebration,
    "volunteer" to Icons.Outlined.VolunteerActivism,
    "child" to Icons.Outlined.ChildCare,
    "movie" to Icons.Outlined.Movie,
    "video" to Icons.Outlined.Videocam,
    "tv" to Icons.Outlined.Tv,
    "music" to Icons.Outlined.MusicNote,
    "headphones" to Icons.Outlined.Headphones,
    "podcast" to Icons.Outlined.Mic,
    "radio" to Icons.Outlined.Radio,
    "games" to Icons.Outlined.SportsEsports,
    "museum" to Icons.Outlined.Museum,
    "theater" to Icons.Outlined.TheaterComedy,
    "camera" to Icons.Outlined.CameraAlt,
    "photo" to Icons.Outlined.PhotoCamera,
    "photos" to Icons.Outlined.PhotoLibrary,
    "fitness" to Icons.Outlined.FitnessCenter,
    "health" to Icons.Outlined.HealthAndSafety,
    "medical" to Icons.Outlined.MedicalServices,
    "medication" to Icons.Outlined.Medication,
    "mindfulness" to Icons.Outlined.SelfImprovement,
    "spa" to Icons.Outlined.Spa,
    "water" to Icons.Outlined.WaterDrop,
    "sleep" to Icons.Outlined.Hotel,
    "sports" to Icons.Outlined.SportsSoccer,
    "basketball" to Icons.Outlined.SportsBasketball,
    "tennis" to Icons.Outlined.SportsTennis,
    "bike" to Icons.AutoMirrored.Outlined.DirectionsBike,
    "motorsport" to Icons.Outlined.SportsMotorsports,
    "hiking" to Icons.Outlined.Hiking,
    "pool" to Icons.Outlined.Pool,
    "coffee" to Icons.Outlined.Coffee,
    "restaurant" to Icons.Outlined.Restaurant,
    "fastfood" to Icons.Outlined.Fastfood,
    "grocery" to Icons.Outlined.LocalGroceryStore,
    "bar" to Icons.Outlined.LocalBar,
    "bakery" to Icons.Outlined.BakeryDining,
    "shopping" to Icons.Outlined.ShoppingCart,
    "finance" to Icons.Outlined.Paid,
    "wallet" to Icons.Outlined.AccountBalanceWallet,
    "savings" to Icons.Outlined.Savings,
    "card" to Icons.Outlined.CreditCard,
    "receipt" to Icons.AutoMirrored.Outlined.ReceiptLong,
    "security" to Icons.Outlined.Security,
    "shield" to Icons.Outlined.Shield,
    "folder" to Icons.Outlined.Folder,
    "cloud" to Icons.Outlined.Cloud,
    "web" to Icons.Outlined.Language,
    "home" to Icons.Outlined.Home,
    "pets" to Icons.Outlined.Pets,
    "nature" to Icons.Outlined.Park,
    "forest" to Icons.Outlined.Forest,
    "beach" to Icons.Outlined.BeachAccess,
    "map" to Icons.Outlined.Map,
    "place" to Icons.Outlined.Place,
    "explore" to Icons.Outlined.TravelExplore,
    "flight" to Icons.Outlined.Flight,
    "travel" to Icons.Outlined.Luggage,
    "car" to Icons.Outlined.DirectionsCar,
    "train" to Icons.Outlined.Train,
    "palette" to Icons.Outlined.Palette,
    "brush" to Icons.Outlined.Brush,
    "design" to Icons.Outlined.Draw,
    "build" to Icons.Outlined.Build,
    "robot" to Icons.Outlined.SmartToy,
    "psychology" to Icons.Outlined.Psychology
)

val customCategoryColors: List<Int> = listOf(
    0xFFEF5350, 0xFFF44336, 0xFFE53935, 0xFFE91E63, 0xFFEC407A, 0xFFF06292,
    0xFFAB47BC, 0xFF9C27B0, 0xFF7E57C2, 0xFF673AB7, 0xFF4A148C, 0xFF880E4F,
    0xFF5C6BC0, 0xFF3F51B5, 0xFF1A237E, 0xFF42A5F5, 0xFF2196F3, 0xFF1565C0,
    0xFF29B6F6, 0xFF03A9F4, 0xFF0D47A1, 0xFF26C6DA, 0xFF00BCD4, 0xFF00838F,
    0xFF26A69A, 0xFF009688, 0xFF004D40, 0xFF66BB6A, 0xFF4CAF50, 0xFF2E7D32,
    0xFF9CCC65, 0xFF8BC34A, 0xFF33691E, 0xFFD4E157, 0xFFCDDC39, 0xFF827717,
    0xFFFFCA28, 0xFFFFC107, 0xFFFF8F00, 0xFFFFA726, 0xFFFF9800, 0xFFE65100,
    0xFFFF7043, 0xFFFF5722, 0xFFBF360C, 0xFF8D6E63, 0xFF795548, 0xFF5D4037,
    0xFF78909C, 0xFF607D8B, 0xFF455A64, 0xFF9E9E9E, 0xFF616161, 0xFF212121
).map { it.toInt() }

fun customCategoryIcon(iconKey: String): ImageVector =
    customCategoryIcons.firstOrNull { it.first == iconKey }?.second
        ?: Icons.AutoMirrored.Outlined.Label

fun appCategoryIcon(category: AppCategory): ImageVector = when (category.id) {
    AppCategory.PRODUCTIVE.id -> Icons.Outlined.School
    AppCategory.SOCIAL.id -> Icons.Outlined.Groups
    AppCategory.LEISURE.id -> Icons.Outlined.SportsEsports
    AppCategory.UTILITIES.id -> Icons.Outlined.Apps
    AppCategory.UNCATEGORIZED.id -> Icons.AutoMirrored.Outlined.Label
    else -> customCategoryIcon(category.iconKey)
}

@Composable
fun CategoryChip(category: AppCategory, modifier: Modifier = Modifier) {
    val color = appCategoryColor(category)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = appCategoryIcon(category),
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCategoryCard(
    category: AppCategory,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    isSuggested: Boolean = false,
    onDetailsClick: (() -> Unit)? = null
) {
    val color = appCategoryColor(category)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSuggested) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Suggested",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (onDetailsClick != null) {
                IconButton(onClick = onDetailsClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "Category details",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCategoryPickerSheet(
    appName: String,
    packageName: String,
    selected: AppCategory,
    onDismiss: () -> Unit,
    onSelect: (AppCategory) -> Unit,
    categories: List<AppCategory> = AppCategory.builtIns,
    onCreateNew: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Categorize App",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            if (packageName.isNotEmpty()) {
                val website = com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(packageName)
                Text(
                    text = if (website) {
                        "Website · ${com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(packageName)}"
                    } else {
                        packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Zenith suggests a category for well-known apps and sites. Pick one to override it, or choose Uncategorized to clear your choice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            categories.forEachIndexed { index, category ->
                if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                val isSelected = category == selected
                val color = appCategoryColor(category)
                val shape = when {
                    categories.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(
                        topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp
                    )
                    index == categories.lastIndex -> RoundedCornerShape(
                        topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp
                    )
                    else -> RoundedCornerShape(8.dp)
                }
                Surface(
                    onClick = { onSelect(category) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f)),
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = category.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (onCreateNew != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "New category",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    slices: List<Pair<AppCategory, Long>>,
    total: Long,
    centerLabel: String,
    centerCaption: String = "screen time",
    modifier: Modifier = Modifier
) {
    val visible = remember(slices) { slices.filter { it.second > 0 } }
    val progress = remember(visible) { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val sliceColors = visible.map { it.first to appCategoryColor(it.first) }
    val sliceValues = visible.map { it.first to it.second }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Box(modifier = modifier.size(152.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 20.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            if (total <= 0L) return@Canvas
            var startAngle = -90f
            val gap = if (sliceColors.size > 1) 4f else 0f
            sliceColors.forEach { (category, color) ->
                val millis = sliceValues.firstOrNull { it.first == category }?.second ?: 0L
                val sweep = (millis.toFloat() / total.toFloat()) * 360f * progress.value
                if (sweep > 1.2f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = centerCaption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryBreakdownCard(
    apps: List<AppUsageInfo>,
    catalog: CategoryCatalog,
    formatDuration: (Long) -> String,
    onCategoryClick: (AppCategory) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    val grouped = remember(apps, catalog) {
        apps.filter { it.totalTimeVisible > 0 }
            .groupBy { catalog.resolve(it.packageName, it.appName) }
            .mapValues { (_, list) -> list.sortedByDescending { it.totalTimeVisible } }
    }
    val total = remember(grouped) {
        grouped.values.sumOf { list -> list.sumOf { it.totalTimeVisible } }
    }
    if (total <= 0L) return

    val ordered = remember(grouped, catalog) {
        catalog.categories
            .filter { grouped.containsKey(it) }
            .sortedByDescending { category ->
                grouped[category]?.sumOf { it.totalTimeVisible } ?: 0L
            }
    }
    val visibleAppCount = remember(apps) { apps.count { it.totalTimeVisible > 0 } }
    val categorizedCount = remember(apps, catalog) {
        apps.count {
            it.totalTimeVisible > 0 &&
                catalog.resolve(it.packageName, it.appName) != AppCategory.UNCATEGORIZED
        }
    }
    val slices = remember(ordered, grouped) {
        ordered.map { category ->
            category to (grouped[category]?.sumOf { it.totalTimeVisible } ?: 0L)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Label,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$categorizedCount of $visibleAppCount apps categorized · Tap a category for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            CategoryDonutChart(
                slices = slices,
                total = total,
                centerLabel = formatDuration(total),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            ordered.forEach { category ->
                val millis = grouped[category]?.sumOf { it.totalTimeVisible } ?: 0L
                CategoryStatRow(
                    category = category,
                    millis = millis,
                    appCount = grouped[category]?.size ?: 0,
                    total = total,
                    formatDuration = formatDuration,
                    onClick = { onCategoryClick(category) }
                )
            }
        }
    }
}

@Composable
private fun CategoryStatRow(
    category: AppCategory,
    millis: Long,
    appCount: Int,
    total: Long,
    formatDuration: (Long) -> String,
    onClick: () -> Unit
) {
    val share = if (total > 0L) (millis.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animatedShare by animateFloatAsState(targetValue = share, label = "CategoryShare")
    val color = appCategoryColor(category)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = appCategoryIcon(category),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$appCount ${if (appCount == 1) "app" else "apps"} · ${(share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatDuration(millis),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedShare)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconKey: String, colorArgb: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf(customCategoryIcons.first().first) }
    var colorArgb by remember { mutableStateOf(customCategoryColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                customCategoryIcons.chunked(6).forEach { iconRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        iconRow.forEach { (key, icon) ->
                            val selectedIcon = key == iconKey
                            Surface(
                                onClick = { iconKey = key },
                                shape = CircleShape,
                                color = if (selectedIcon) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = key,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (selectedIcon) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                customCategoryColors.chunked(6).forEach { colorRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        colorRow.forEach { option ->
                            val selectedColor = option == colorArgb
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(option))
                                    .clickable { colorArgb = option },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Selected color",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), iconKey, colorArgb) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryHeader(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = color
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun CategoryHeader(
    category: AppCategory,
    color: Color = Color(category.colorArgb ?: MaterialTheme.colorScheme.primary.toArgb()),
    modifier: Modifier = Modifier
) {
    CategoryHeader(
        title = category.label,
        description = category.description,
        icon = appCategoryIcon(category),
        color = color,
        modifier = modifier
    )
}

