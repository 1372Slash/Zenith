package com.etrisad.zenith.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.local.database.OverlayLogBuffer
import com.etrisad.zenith.data.local.database.OverlayLogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayLogBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(OverlayLogBuffer.getAll()) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var autoRefresh by remember { mutableStateOf(true) }

    if (autoRefresh) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                entries = if (selectedFilter != null) {
                    OverlayLogBuffer.getFiltered(tag = selectedFilter)
                } else {
                    OverlayLogBuffer.getAll()
                }
            }
        }
    }

    val tags = remember(entries) { entries.map { it.tag }.distinct().sorted() }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Overlay Log Viewer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${entries.size} entries${if (selectedFilter != null) " (filtered: $selectedFilter)" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilterChip(
                    selected = autoRefresh,
                    onClick = { autoRefresh = !autoRefresh },
                    label = { Text("Live", fontSize = 11.sp) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (autoRefresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "All",
                    onClick = { copyToClipboard(context, OverlayLogBuffer.copyAll()) },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    icon = Icons.Outlined.Refresh,
                    label = "Refresh",
                    onClick = {
                        entries = if (selectedFilter != null) {
                            OverlayLogBuffer.getFiltered(tag = selectedFilter)
                        } else {
                            OverlayLogBuffer.getAll()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    icon = Icons.Outlined.Delete,
                    label = "Clear",
                    onClick = {
                        OverlayLogBuffer.clear()
                        entries = emptyList()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            if (tags.size > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = {
                            selectedFilter = null
                            entries = OverlayLogBuffer.getAll()
                        },
                        label = { Text("All tags", fontSize = 11.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = selectedFilter == tag,
                            onClick = {
                                selectedFilter = if (selectedFilter == tag) null else tag
                                entries = if (selectedFilter != null) {
                                    OverlayLogBuffer.getFiltered(tag = selectedFilter)
                                } else {
                                    OverlayLogBuffer.getAll()
                                }
                            },
                            label = { Text(tag, fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No log entries yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                ) {
                    items(
                        count = entries.size,
                        key = { index -> "${entries[index].timestamp}_$index" }
                    ) { index ->
                        val entry = entries[index]
                        val isFirst = index == 0
                        val isLast = index == entries.lastIndex

                        OverlayLogEntryRow(
                            entry = entry,
                            context = context,
                            topRound = isFirst,
                            bottomRound = isLast
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayLogEntryRow(
    entry: OverlayLogEntry,
    context: Context,
    topRound: Boolean = false,
    bottomRound: Boolean = false
) {
    val isWarning = entry.level == OverlayLogEntry.Level.W
    val isError = entry.level == OverlayLogEntry.Level.E
    val msg = entry.message.lowercase()
    val isSpecial = entry.level == OverlayLogEntry.Level.D && (
        msg.contains("show") || msg.contains("hide") || msg.contains("bypass") ||
        msg.contains("block") || msg.contains("close") || msg.contains("kick")
    )

    val bgColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        isSpecial -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isError -> MaterialTheme.colorScheme.error
        isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        isSpecial -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    val levelBadgeColor = when {
        isError -> MaterialTheme.colorScheme.error
        isWarning -> Color(0xFFFF9800)
        isSpecial -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    val cornerRadius = 24.dp
    val shape = when {
        topRound && bottomRound -> RoundedCornerShape(cornerRadius)
        topRound -> RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomStart = 4.dp, bottomEnd = 4.dp)
        bottomRound -> RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius, topStart = 4.dp, topEnd = 4.dp)
        else -> RoundedCornerShape(4.dp)
    }

    Surface(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("log", entry.format()))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        },
        shape = shape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = entry.level.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    lineHeight = 10.sp
                ),
                color = levelBadgeColor,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(10.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = entry.message,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                ),
                color = textColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("zenith_overlay_logs", text))
    Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
}
