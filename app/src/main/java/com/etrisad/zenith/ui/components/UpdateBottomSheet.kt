package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.Brush
import androidx.palette.graphics.Palette
import coil.request.ImageRequest
import com.etrisad.zenith.data.remote.model.GitHubRelease
import com.etrisad.zenith.ui.theme.ZenithTheme
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    release: GitHubRelease,
    useExpressiveColors: Boolean,
    isDark: Boolean = isSystemInDarkTheme(),
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var seedColor by remember { mutableStateOf<Color?>(null) }
    
    val firstImageUrl = remember(release.body) {
        val lines = release.body.trim().lines()
        lines.firstOrNull { it.trim().startsWith("![") || it.trim().startsWith("<img", ignoreCase = true) }?.let { line ->
            if (line.trim().startsWith("![")) {
                line.substringAfter("(").substringBefore(")")
            } else {
                line.substringAfter("src=\"", "").substringBefore("\"")
            }
        }
    }

    LaunchedEffect(firstImageUrl) {
        if (firstImageUrl != null) {
            val loader = coil.Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(firstImageUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is coil.request.SuccessResult) {
                val bitmap = result.drawable.toBitmap()
                Palette.from(bitmap).generate { palette ->
                    seedColor = palette?.vibrantSwatch?.rgb?.let { Color(it) }
                        ?: palette?.dominantSwatch?.rgb?.let { Color(it) }
                }
            }
        }
    }

    val currentScheme = MaterialTheme.colorScheme
    
    val dynamicColorScheme = remember(seedColor, isDark) {
        seedColor?.let {
            generateDynamicColorScheme(it, isDark, currentScheme)
        } ?: currentScheme
    }

    val hideAndDismiss = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    val content = @Composable {
        val containerColor by animateColorAsState(
            targetValue = if (useExpressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "containerColor"
        )

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            UpdateBottomSheetContent(
                release = release,
                containerColor = containerColor,
                screenHeight = screenHeight,
                onDismiss = { hideAndDismiss() },
                onUpdate = {
                    onUpdate()
                    hideAndDismiss()
                }
            )
        }
    }

    MaterialTheme(colorScheme = dynamicColorScheme) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogBottomSheet(
    releases: List<GitHubRelease>,
    useExpressiveColors: Boolean,
    isDark: Boolean = isSystemInDarkTheme(),
    title: String = "Changelog History",
    subtitle: String = "Updates & Improvements",
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var displayCount by remember { mutableStateOf(minOf(5, releases.size)) }
    val displayedReleases = remember(releases, displayCount) { releases.take(displayCount) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 48.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(displayedReleases) { release ->
                            ChangelogVersionCard(
                                release = release,
                                useExpressiveColors = useExpressiveColors,
                                isDark = isDark
                            )
                        }

                        if (displayCount < releases.size) {
                            item {
                                LaunchedEffect(Unit) {
                                    displayCount = minOf(releases.size, displayCount + 5)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ZenithLoadingIndicator()
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ChangelogVersionCard(
    release: GitHubRelease,
    useExpressiveColors: Boolean,
    isDark: Boolean
) {
    val context = LocalContext.current
    var seedColor by remember { mutableStateOf<Color?>(null) }
    
    val firstImageUrl = remember(release.body) {
        val lines = release.body.trim().lines()
        lines.firstOrNull { it.trim().startsWith("![") || it.trim().startsWith("<img", ignoreCase = true) }?.let { line ->
            if (line.trim().startsWith("![")) {
                line.substringAfter("(").substringBefore(")")
            } else {
                line.substringAfter("src=\"", "").substringBefore("\"")
            }
        }
    }

    LaunchedEffect(firstImageUrl) {
        if (firstImageUrl != null) {
            val loader = coil.Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(firstImageUrl)
                .allowHardware(false)
                .build()
            val result = try { loader.execute(request) } catch (e: Exception) { null }
            if (result is coil.request.SuccessResult) {
                val bitmap = result.drawable.toBitmap()
                Palette.from(bitmap).generate { palette ->
                    seedColor = palette?.vibrantSwatch?.rgb?.let { Color(it) }
                        ?: palette?.dominantSwatch?.rgb?.let { Color(it) }
                }
            }
        }
    }

    val currentScheme = MaterialTheme.colorScheme
    
    val dynamicColorScheme = remember(seedColor, isDark) {
        seedColor?.let {
            generateDynamicColorScheme(it, isDark, currentScheme)
        } ?: currentScheme
    }

    MaterialTheme(colorScheme = dynamicColorScheme) {
        val targetCardColor = MaterialTheme.colorScheme.surfaceContainerLow
        val animatedCardColor by animateColorAsState(
            targetValue = targetCardColor,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "cardColor"
        )
        
        val targetMarkdownColor = if (useExpressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh
        
        val animatedMarkdownColor by animateColorAsState(
            targetValue = targetMarkdownColor,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "markdownColor"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = animatedCardColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = release.tagName,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val badgeType = remember(release.body, release.name) {
                        extractUpdateBadge(release.body, release.name)
                    }
                    if (badgeType != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = getBadgeColor(badgeType),
                            shape = CircleShape
                        ) {
                            Text(
                                text = badgeType,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = getBadgeTextColor(badgeType),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatReleaseDate(release.publishedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                MarkdownBody(
                    content = cleanMarkdownBody(release.body),
                    containerColor = animatedMarkdownColor
                )
            }
        }
    }
}

private fun formatReleaseDate(dateString: String?): String {
    if (dateString == null) return ""
    return try {
        val instant = java.time.Instant.parse(dateString)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
            .withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        dateString.substringBefore("T")
    }
}

private fun generateDynamicColorScheme(seed: Color, isDark: Boolean, currentScheme: ColorScheme): ColorScheme {
    val hsl = FloatArray(3)
    val argb = seed.toArgb()
    android.graphics.Color.RGBToHSV(
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb),
        hsl
    )
    
    val primaryV = if (isDark) 0.9f else 0.4f
    val primary = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], hsl[1] * 0.8f, primaryV)))
    
    val secondaryV = if (isDark) 0.9f else 0.35f
    val secondary = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], hsl[1] * 0.4f, secondaryV)))
    
    val tertiaryV = if (isDark) 0.9f else 0.45f
    val tertiary = Color(android.graphics.Color.HSVToColor(floatArrayOf((hsl[0] + 60f) % 360f, hsl[1] * 0.6f, tertiaryV)))

    val surfaceV = if (isDark) 0.1f else 0.98f
    val surface = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], hsl[1] * 0.1f, surfaceV)))
    val surfaceContainerLowest = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], hsl[1] * 0.05f, if (isDark) 0.04f else 1.0f)))
    val surfaceContainerLow = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], hsl[1] * 0.1f, if (isDark) 0.12f else 0.96f)))
    val surfaceContainer = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], hsl[1] * 0.15f, if (isDark) 0.15f else 0.94f)))
    val surfaceContainerHigh = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], hsl[1] * 0.2f, if (isDark) 0.2f else 0.9f)))

    return currentScheme.copy(
        primary = primary,
        onPrimary = if (isDark) Color.Black else Color.White,
        primaryContainer = primary.copy(alpha = if (isDark) 0.3f else 0.15f),
        onPrimaryContainer = primary,
        
        secondary = secondary,
        onSecondary = if (isDark) Color.Black else Color.White,
        secondaryContainer = secondary.copy(alpha = if (isDark) 0.25f else 0.2f),
        onSecondaryContainer = secondary,
        
        tertiary = tertiary,
        onTertiary = if (isDark) Color.Black else Color.White,
        tertiaryContainer = tertiary.copy(alpha = if (isDark) 0.25f else 0.14f),
        onTertiaryContainer = tertiary,

        surface = surface,
        onSurface = if (isDark) Color.White else Color.Black,
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = if (isDark) Color.LightGray else Color.DarkGray,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHigh.copy(alpha = 0.9f)
    )
}

@Composable
fun UpdateBottomSheetContent(
    release: GitHubRelease,
    containerColor: Color,
    screenHeight: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }

    val iconScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = screenHeight * 0.9f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    UpdateHeaderSection(
                        release = release,
                        iconScale = iconScale
                    )

                    MarkdownBody(
                        content = cleanMarkdownBody(release.body),
                        containerColor = containerColor
                    )
                    
                    Spacer(modifier = Modifier.height(64.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                        startY = 0f,
                        endY = 40f
                    )
                )
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            ZenithGroupedButton(
                size = ZenithButtonSize.ExtraLarge
            ) {
                ZenithButtonWeighted(
                    onClick = onDismiss,
                    weight = 1f,
                    text = "Later",
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.ExtraLarge,
                    isFirst = true,
                    isLast = false
                )

                ZenithButtonWeighted(
                    onClick = onUpdate,
                    weight = 1.5f,
                    text = "Update",
                    icon = Icons.Outlined.Download,
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.ExtraLarge,
                    isFirst = false,
                    isLast = true
                )
            }
        }
    }
}


@Composable
private fun UpdateHeaderSection(
    release: GitHubRelease,
    iconScale: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "New Version Available",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiary,
                shape = CircleShape
            ) {
                Text(
                    text = release.tagName,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold
                )
            }

            val badgeType = remember(release.body, release.name) { extractUpdateBadge(release.body, release.name) }
            if (badgeType != null) {
                Surface(
                    color = getBadgeColor(badgeType),
                    shape = CircleShape
                ) {
                    Text(
                        text = badgeType,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = getBadgeTextColor(badgeType),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val timeAgo = formatTimeAgo(release.publishedAt)
            if (timeAgo.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Text(
                        text = timeAgo,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatTimeAgo(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(dateString)
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(instant, now)
        when {
            duration.toDays() > 365 -> "${duration.toDays() / 365}y ago"
            duration.toDays() > 30 -> "${duration.toDays() / 30}mo ago"
            duration.toDays() > 0 -> "${duration.toDays()}d ago"
            duration.toHours() > 0 -> "${duration.toHours()}h ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
            else -> "Just now"
        }
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun MarkdownBody(
    content: String,
    containerColor: Color
) {
    val sections = remember(content) { parseMarkdownIntoSections(content) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        sections.forEach { section ->
            when (section) {
                is MarkdownSection.Header1 -> {
                    Text(
                        text = parseInlineMarkdown(section.text),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                is MarkdownSection.GroupedCard -> {
                    MarkdownGroupedCard(
                        elements = section.elements,
                        containerColor = containerColor
                    )
                }
                is MarkdownSection.Image -> {
                    MarkdownImageItem(url = section.url)
                }
            }
        }
    }
}

private data class Header3Subsection(
    val header: MarkdownElement.Header3,
    val children: List<MarkdownElement>
)

private data class ParsedGroup(
    val topElements: List<MarkdownElement>,
    val subsections: List<Header3Subsection>
)

private fun parseGroup(elements: List<MarkdownElement>): ParsedGroup {
    val top = mutableListOf<MarkdownElement>()
    val subs = mutableListOf<Header3Subsection>()
    var currentHeader3: MarkdownElement.Header3? = null
    var currentChildren = mutableListOf<MarkdownElement>()

    elements.forEach { element ->
        when (element) {
            is MarkdownElement.Header3 -> {
                if (currentHeader3 != null) {
                    subs.add(Header3Subsection(currentHeader3, currentChildren.toList()))
                    currentChildren = mutableListOf()
                }
                currentHeader3 = element
            }
            else -> {
                if (currentHeader3 != null) {
                    currentChildren.add(element)
                } else {
                    top.add(element)
                }
            }
        }
    }
    if (currentHeader3 != null) {
        subs.add(Header3Subsection(currentHeader3, currentChildren.toList()))
    }
    return ParsedGroup(top, subs)
}

@Composable
fun MarkdownGroupedCard(
    elements: List<MarkdownElement>,
    containerColor: Color
) {
    val elementGroups = remember(elements) {
        val groups = mutableListOf<List<MarkdownElement>>()
        var currentSubGroup = mutableListOf<MarkdownElement>()
        
        elements.forEach { element ->
            if (element is MarkdownElement.Header2 && currentSubGroup.isNotEmpty()) {
                groups.add(currentSubGroup.toList())
                currentSubGroup = mutableListOf()
            }
            currentSubGroup.add(element)
        }
        if (currentSubGroup.isNotEmpty()) groups.add(currentSubGroup)
        groups
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        elementGroups.forEach { group ->
            val parsed = remember(group) { parseGroup(group) }
            val hasTop = parsed.topElements.isNotEmpty()
            
            val allItems = mutableListOf<Any>()
            if (hasTop) allItems.add(parsed.topElements)
            parsed.subsections.forEach { allItems.add(it) }

            val totalItems = allItems.size

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                allItems.forEachIndexed { itemIndex, item ->
                    val isFirst = itemIndex == 0
                    val isLast = itemIndex == totalItems - 1
                    val shape = when {
                        isFirst && isLast -> RoundedCornerShape(24.dp)
                        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        isLast -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(8.dp)
                    }

                    if (item is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        val topElems = item as List<MarkdownElement>
                        Card(
                            modifier = Modifier.fillMaxWidth().clip(shape),
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            shape = shape
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                topElems.forEach { element ->
                                    RenderMarkdownElement(element)
                                }
                            }
                        }
                    } else if (item is Header3Subsection) {
                        val sub = item
                        var expanded by remember { mutableStateOf(false) }
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 90f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "chevronRotation"
                        )

                        Card(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth().clip(shape),
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            shape = shape
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = parseInlineMarkdown(sub.header.text),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer { rotationZ = rotation }
                                    )
                                }

                                AnimatedVisibility(
                                    visible = expanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        sub.children.forEach { child ->
                                            RenderMarkdownElement(child)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenderMarkdownElement(element: MarkdownElement) {
    when (element) {
        is MarkdownElement.Header2 -> {
            MarkdownHeaderItem(
                text = element.text,
                icon = Icons.AutoMirrored.Outlined.ListAlt,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.titleLarge
            )
        }
        is MarkdownElement.Header3 -> {}
        is MarkdownElement.ListItem -> {
            MarkdownListItemRow(text = element.text)
        }
        is MarkdownElement.Text -> {
            Text(
                text = parseInlineMarkdown(element.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        is MarkdownElement.Quote -> {
            MarkdownQuoteItem(text = element.text)
        }
    }
}


@Composable
fun MarkdownHeaderItem(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = parseInlineMarkdown(text),
            style = style,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MarkdownListItemRow(text: String) {
    Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 4.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MarkdownImageItem(url: String) {
    coil.compose.SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .heightIn(max = 400.dp)
            .clip(RoundedCornerShape(28.dp)),
        contentScale = ContentScale.FillWidth,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                ZenithLoadingIndicator()
            }
        }
    )
}

@Composable
fun MarkdownQuoteItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(IntrinsicSize.Min)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    var i = 0
    val colorScheme = MaterialTheme.colorScheme
    val onSurface = colorScheme.onSurface

    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = onSurface))
                    builder.append(text.substring(i + 2, end))
                    builder.pop()
                    i = end + 2
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    builder.append(text.substring(i + 1, end))
                    builder.pop()
                    i = end + 1
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            text.startsWith("![", i) -> {
                val endAlt = text.indexOf("]", i)
                if (endAlt != -1 && text.startsWith("(", endAlt + 1)) {
                    val endUrl = text.indexOf(")", endAlt + 1)
                    if (endUrl != -1) {
                        i = endUrl + 1
                    } else {
                        builder.append(text[i])
                        i++
                    }
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            text.startsWith("[", i) -> {
                val endText = text.indexOf("]", i)
                if (endText != -1 && text.startsWith("(", endText + 1)) {
                    val endUrl = text.indexOf(")", endText + 1)
                    if (endUrl != -1) {
                        val linkText = text.substring(i + 1, endText)
                        val url = text.substring(endText + 2, endUrl)
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(
                            color = colorScheme.primary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        ))
                        builder.append(linkText)
                        builder.addStringAnnotation("URL", url, builder.length - linkText.length, builder.length)
                        builder.pop()
                        i = endUrl + 1
                    } else {
                        builder.append(text[i])
                        i++
                    }
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            text.startsWith("<img", i) -> {
                val end = text.indexOf(">", i)
                if (end != -1) {
                    i = end + 1
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            text[i] == '#' && i + 1 < text.length && text[i+1].isDigit() -> {
                var j = i + 1
                while (j < text.length && text[j].isDigit()) {
                    j++
                }
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(
                    color = colorScheme.secondary,
                    fontWeight = FontWeight.ExtraBold
                ))
                builder.append(text.substring(i, j))
                builder.pop()
                i = j
            }
            else -> {
                builder.append(text[i])
                i++
            }
        }
    }
    return builder.toAnnotatedString()
}

sealed class MarkdownSection {
    data class Header1(val text: String) : MarkdownSection()
    data class Image(val url: String) : MarkdownSection()
    data class GroupedCard(val elements: List<MarkdownElement>) : MarkdownSection()
}

sealed class MarkdownElement {
    data class Header2(val text: String) : MarkdownElement()
    data class Header3(val text: String) : MarkdownElement()
    data class Text(val text: String) : MarkdownElement()
    data class ListItem(val text: String) : MarkdownElement()
    data class Quote(val text: String) : MarkdownElement()
}

private fun parseMarkdownIntoSections(content: String): List<MarkdownSection> {
    val lines = content.trim().lines()
    val result = mutableListOf<MarkdownSection>()
    var currentGroup = mutableListOf<MarkdownElement>()
    
    fun flushGroup() {
        if (currentGroup.isNotEmpty()) {
            result.add(MarkdownSection.GroupedCard(currentGroup.toList()))
            currentGroup = mutableListOf()
        }
    }

    lines.forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("# ") -> {
                flushGroup()
                result.add(MarkdownSection.Header1(trimmed.removePrefix("# ")))
            }
            trimmed.startsWith("![") -> {
                flushGroup()
                val url = trimmed.substringAfter("(").substringBefore(")")
                if (url.isNotEmpty()) {
                    result.add(MarkdownSection.Image(url))
                }
            }
            trimmed.startsWith("<img", ignoreCase = true) -> {
                flushGroup()
                val src = trimmed.substringAfter("src=\"", "").substringBefore("\"")
                if (src.isNotEmpty()) {
                    result.add(MarkdownSection.Image(src))
                }
            }
            trimmed.startsWith("## ") -> {
                currentGroup.add(MarkdownElement.Header2(trimmed.removePrefix("## ")))
            }
            trimmed.startsWith("### ") -> {
                currentGroup.add(MarkdownElement.Header3(trimmed.removePrefix("### ")))
            }
            trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                currentGroup.add(MarkdownElement.ListItem(trimmed.substring(2).trim()))
            }
            trimmed.startsWith("> ") -> {
                currentGroup.add(MarkdownElement.Quote(trimmed.removePrefix("> ").trim()))
            }
            trimmed.isNotBlank() -> {
                currentGroup.add(MarkdownElement.Text(line))
            }
        }
    }
    flushGroup()
    return result
}

private fun extractUpdateBadge(body: String?, name: String?): String? {
    val explicitRegex = Regex("\\[(?:badge|type|tag)?\\s*:?\\s*([^]]+)]", RegexOption.IGNORE_CASE)
    val textToSearch = "${name ?: ""} \n ${body ?: ""}"
    
    val match = explicitRegex.find(textToSearch)?.groupValues?.get(1)?.trim()
    if (!match.isNullOrBlank()) {
        return normalizeCategory(match)
    }
    return null
}

private fun normalizeCategory(input: String): String {
    val lower = input.lowercase().trim()
    return when {
        lower.contains("critical") || lower.contains("hotfix") -> "Critical Fix"
        lower.contains("bug") || lower.contains("fix") -> "Bug Fixes"
        lower.contains("feature") || lower.contains("new") -> "Features"
        lower.contains("perf") -> "Performances"
        lower.contains("cosmetic") || lower.contains("ui") -> "Cosmetics"
        lower.contains("qol") || lower.contains("quality") -> "QOL"
        else -> input.replaceFirstChar { it.uppercase() }
    }
}

private fun cleanMarkdownBody(body: String?): String {
    if (body.isNullOrBlank()) return ""
    return body.replace(Regex("\\[(?:badge|type|tag)?\\s*:?\\s*[^]]+]", RegexOption.IGNORE_CASE), "").trim()
}

@Composable
private fun getBadgeColor(type: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (type.lowercase()) {
        "critical fix" -> MaterialTheme.colorScheme.errorContainer
        "bug fixes" -> MaterialTheme.colorScheme.tertiaryContainer
        "features" -> MaterialTheme.colorScheme.primaryContainer
        "performances" -> Color(0xFF2196F3).copy(alpha = if (isDark) 0.25f else 0.15f)
        "cosmetics" -> Color(0xFF9C27B0).copy(alpha = if (isDark) 0.25f else 0.15f)
        "qol" -> Color(0xFFFFC107).copy(alpha = if (isDark) 0.25f else 0.2f)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
}

@Composable
private fun getBadgeTextColor(type: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (type.lowercase()) {
        "critical fix" -> MaterialTheme.colorScheme.onErrorContainer
        "bug fixes" -> MaterialTheme.colorScheme.onTertiaryContainer
        "features" -> MaterialTheme.colorScheme.onPrimaryContainer
        "performances" -> if (isDark) Color(0xFF90CAF9) else Color(0xFF1976D2)
        "cosmetics" -> if (isDark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
        "qol" -> if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
}

@Preview(showBackground = true)
@Composable
fun UpdateBottomSheetPreview() {
    ZenithTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            UpdateBottomSheetContent(
                release = GitHubRelease(
                    tagName = "v1.6.0",
                    name = "Zenith v1.6.0",
                    body = """
                        # Zenith v1.6.0
                        This is a major update with many new features and improvements.
                        
                        > This update is critical for database safety.
                        
                        ## New Features
                        * ✨ **Material 3 Expressive**: New floating tab bar.
                        * 🚀 **GitHub Update Checker**: Fast updates.
                        ![Preview Image](https://raw.githubusercontent.com/1372Slash/Zenith/master/art/preview.png)
                        
                        <img src="https://raw.githubusercontent.com/1372Slash/Zenith/master/art/banner.png" alt="Banner" />

                        ## Improvements
                        ### Performance
                        * ⚡ Optimized usage history loading.
                        ### UI/UX
                        * 📱 Better support for foldable devices.
                        
                        This version is the result of many weeks of work.
                    """.trimIndent(),
                    htmlUrl = "https://github.com/1372Slash/Zenith",
                    assets = emptyList()
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                screenHeight = 800.dp,
                onDismiss = {},
                onUpdate = {}
            )
        }
    }
}
