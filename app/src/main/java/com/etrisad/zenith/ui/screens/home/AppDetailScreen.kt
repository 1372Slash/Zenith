package com.etrisad.zenith.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.TodoEntity
import com.etrisad.zenith.ui.screens.focus.FocusSettingsBottomSheet
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.DailyUsage
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: HomeViewModel,
    focusViewModel: FocusViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.appDetailUiState.collectAsState()
    val focusUiState by focusViewModel.uiState.collectAsState()

    LaunchedEffect(packageName) {
        viewModel.loadAppDetail(packageName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("App Details", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { focusViewModel.openSettingsForPackage(uiState.packageName) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        val targetMillis = uiState.shieldEntity?.timeLimitMinutes?.let { it * 60 * 1000L } ?: 0L
        val isFocusActive = uiState.shieldEntity != null

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 32.dp
                )
            ) {
                item {
                    AppHeader(
                        appName = uiState.appName,
                        icon = uiState.icon,
                        focusType = uiState.type,
                        isActive = isFocusActive
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (uiState.type == FocusType.GOAL) {
                    item {
                        TodoGroupCard(
                            todos = uiState.todos,
                            onAddTodo = { content -> viewModel.addTodo(uiState.packageName, content) },
                            onToggleTodo = { viewModel.toggleTodo(it) },
                            onDeleteTodo = { viewModel.deleteTodo(it) },
                            onReorder = { viewModel.reorderTodos(uiState.packageName, it) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                item {
                    UsageCard(
                        title = "Today's Usage",
                        time = viewModel.formatDuration(uiState.todayUsage),
                        targetMillis = targetMillis,
                        currentUsage = uiState.todayUsage,
                        focusType = uiState.type,
                        formatDuration = { viewModel.formatDuration(it) },
                        isActive = isFocusActive,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    UsageTrendsRow(
                        yesterdayTime = viewModel.formatDuration(uiState.yesterdayUsage),
                        percentageChange = uiState.percentageChange,
                        focusType = uiState.type
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    if (uiState.usageHistory.isNotEmpty()) {
                        UsageHistoryCard(
                            history = uiState.usageHistory,
                            targetMillis = targetMillis,
                            focusType = uiState.type,
                            formatDuration = { viewModel.formatDuration(it) },
                            onDaySelected = { /* No-op */ },
                            shape = RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 8.dp,
                                bottomStart = if (uiState.shieldEntity == null) 24.dp else 8.dp,
                                bottomEnd = if (uiState.shieldEntity == null) 24.dp else 8.dp
                            )
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(250.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    AnimatedVisibility(
                        visible = uiState.shieldEntity != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        DeleteShieldCard(
                            onDelete = {
                                viewModel.deleteShieldFromDetail()
                            },
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        )
                    }
                }
            }

            if (focusUiState.isSettingsSheetOpen && focusUiState.selectedAppForFocus != null) {
                FocusSettingsBottomSheet(
                    appInfo = focusUiState.selectedAppForFocus!!,
                    focusType = focusUiState.selectedFocusType,
                    existingShield = (focusUiState.activeShields + focusUiState.activeGoals).find { it.packageName == focusUiState.selectedAppForFocus!!.packageName },
                    onDismiss = { focusViewModel.closeSettingsSheet() },
                    onSave = { limit, emergency, reminders, strict, autoQuit, maxUses, refresh, goalReminder, delayApp ->
                        focusViewModel.saveFocus(limit, emergency, reminders, strict, autoQuit, maxUses, refresh, goalReminder, delayApp)
                        viewModel.loadAppDetail(packageName) // Refresh UI after save
                    }
                )
            }
        }
    }
}

@Composable
fun AppHeader(
    appName: String,
    icon: android.graphics.drawable.Drawable?,
    focusType: FocusType?,
    isActive: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Image(
                painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Android, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(
            visible = isActive && focusType != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (focusType != null) {
                val typeColor = if (focusType == FocusType.SHIELD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                Surface(
                    color = typeColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = focusType.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UsageCard(
    title: String,
    time: String,
    targetMillis: Long,
    currentUsage: Long,
    focusType: FocusType?,
    formatDuration: (Long) -> String,
    isActive: Boolean,
    shape: androidx.compose.ui.graphics.Shape
) {
    val isTargetSet = targetMillis > 0
    val isExceeded = isTargetSet && focusType == FocusType.SHIELD && currentUsage > targetMillis

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = time,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = isActive && isTargetSet && focusType != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (focusType != null) {
                    val isGoal = focusType == FocusType.GOAL
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isGoal) {
                                if (currentUsage >= targetMillis) "Goal achieved! Keep it up."
                                else "Goal: ${formatDuration(targetMillis)} (${formatDuration(targetMillis - currentUsage)} more to go)"
                            } else {
                                if (isExceeded) "Limit exceeded!"
                                else "Limit: ${formatDuration(targetMillis)} (${formatDuration(targetMillis - currentUsage)} left)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val progress = if (isGoal) {
                            (currentUsage.toFloat() / targetMillis).coerceIn(0f, 1f)
                        } else {
                            if (isExceeded) 0f
                            else ((targetMillis - currentUsage).toFloat() / targetMillis).coerceIn(0f, 1f)
                        }

                        val dashboardProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 50f),
                            label = "AppUsageProgress"
                        )

                        val progressColor = when {
                            isGoal -> MaterialTheme.colorScheme.tertiary
                            isExceeded -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }

                        LinearWavyProgressIndicator(
                            progress = { dashboardProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            color = progressColor,
                            trackColor = progressColor.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageTrendsRow(
    yesterdayTime: String,
    percentageChange: Float,
    focusType: FocusType?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Yesterday",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    yesterdayTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Trend",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isUp = percentageChange >= 0
                    val isPositiveTrend = if (focusType == FocusType.GOAL) isUp else !isUp
                    val trendColor = if (isPositiveTrend) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

                    Icon(
                        imageVector = if (isUp) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = trendColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${kotlin.math.abs(percentageChange).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
            }
        }
    }
}

@Composable
fun UsageHistoryCard(
    history: List<DailyUsage>,
    targetMillis: Long,
    focusType: FocusType?,
    formatDuration: (Long) -> String,
    onDaySelected: (DailyUsage?) -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    var selectedUsage by remember { mutableStateOf<DailyUsage?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val todayDate = remember { dateFormat.format(System.currentTimeMillis()) }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History (21 Days)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedContent(
                    targetState = selectedUsage,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                slideInVertically { it / 2 })
                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                    slideOutVertically { -it / 2 })
                    },
                    label = "SelectedUsageAnim"
                ) { usage ->
                    if (usage != null && dateFormat.format(usage.date) != todayDate) {
                        Text(
                            text = formatDuration(usage.totalTime),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            UsageGraph(
                history = history,
                targetMillis = targetMillis,
                focusType = focusType,
                onDaySelected = { usage ->
                    selectedUsage = usage
                    onDaySelected(usage)
                }
            )
        }
    }
}

@Composable
fun DeleteShieldCard(
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Remove from Zenith",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Stop tracking limits for this app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TodoGroupCard(
    todos: List<TodoEntity>,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (TodoEntity) -> Unit,
    onDeleteTodo: (TodoEntity) -> Unit,
    @Suppress("UNUSED_PARAMETER") onReorder: (List<TodoEntity>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var todoText by remember { mutableStateOf("") }
    var isDoneExpanded by remember { mutableStateOf(false) }

    val activeTodos = todos.filter { !it.isDone }
    val doneTodos = todos.filter { it.isDone }
    
    val totalVisibleItems = activeTodos.size + (if (doneTodos.isNotEmpty()) 1 else 0)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "To-do List",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = { showAddDialog = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add Todo", modifier = Modifier.size(20.dp))
            }
        }

        if (activeTodos.isEmpty() && doneTodos.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Text(
                    text = "No tasks yet. Add some to stay productive!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Individual cards for active tasks with grouped shapes
            activeTodos.forEachIndexed { index, todo ->
                val shape = getGroupedShape(index, totalVisibleItems)
                TodoCard(
                    todo = todo,
                    onToggle = { onToggleTodo(todo) },
                    onDelete = { onDeleteTodo(todo) },
                    shape = shape
                )
            }

            // Group card for done tasks
            if (doneTodos.isNotEmpty()) {
                val shape = getGroupedShape(totalVisibleItems - 1, totalVisibleItems)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDoneExpanded = !isDoneExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDoneExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Done (${doneTodos.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = isDoneExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                doneTodos.forEach { todo ->
                                    TodoItem(
                                        todo = todo,
                                        onToggle = { onToggleTodo(todo) },
                                        onDelete = { onDeleteTodo(todo) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                todoText = ""
            },
            title = { Text("Add Task") },
            text = {
                OutlinedTextField(
                    value = todoText,
                    onValueChange = { todoText = it },
                    placeholder = { Text("Enter task...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (todoText.isNotBlank()) {
                            onAddTodo(todoText)
                            todoText = ""
                            showAddDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false 
                    todoText = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun getGroupedShape(index: Int, total: Int): RoundedCornerShape {
    return when {
        total <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }
}

@Composable
fun TodoCard(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    shape: RoundedCornerShape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        TodoItem(
            todo = todo,
            onToggle = onToggle,
            onDelete = onDelete,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TodoItem(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = todo.isDone,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = todo.content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
            color = if (todo.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


