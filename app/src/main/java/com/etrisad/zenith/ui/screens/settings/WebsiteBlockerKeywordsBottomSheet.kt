package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.components.focus.CardGroup
import kotlinx.coroutines.launch

data class KeywordPresetCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val keywords: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteBlockerKeywordsBottomSheet(
    initialKeywords: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var keywords by remember { mutableStateOf(initialKeywords.toMutableList()) }
    var customInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    val middleShape = RoundedCornerShape(8.dp)
    val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    val singleShape = RoundedCornerShape(24.dp)

    val presetCategories = remember {
        listOf(
            KeywordPresetCategory(
                title = "Shorts & Reels",
                description = "Block short-form video feeds",
                icon = Icons.Outlined.Videocam,
                keywords = listOf("shorts", "reels", "clips")
            ),
            KeywordPresetCategory(
                title = "Gambling & Casino",
                description = "Block online gambling and betting",
                icon = Icons.Outlined.Casino,
                keywords = listOf("casino", "bet", "poker", "slot", "gambling", "betting")
            ),
            KeywordPresetCategory(
                title = "Adult & Dating",
                description = "Block explicit adult content and dating",
                icon = Icons.Outlined.Security,
                keywords = listOf("adult", "porn", "nsfw", "dating", "xxx")
            ),
            KeywordPresetCategory(
                title = "Live Streams",
                description = "Block live streaming platforms",
                icon = Icons.Outlined.LocalFireDepartment,
                keywords = listOf("live", "stream", "twitch", "kick")
            )
        )
    }

    fun addKeyword(raw: String) {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isEmpty()) return
        if (keywords.contains(trimmed)) {
            errorMessage = "Keyword already added."
            return
        }
        keywords.add(trimmed)
        customInput = ""
        errorMessage = null
        onSave(keywords)
    }

    fun removeKeyword(keyword: String) {
        keywords.remove(keyword)
        onSave(keywords)
    }

    fun togglePreset(preset: KeywordPresetCategory, enable: Boolean) {
        if (enable) {
            for (kw in preset.keywords) {
                if (!keywords.contains(kw)) {
                    keywords.add(kw)
                }
            }
        } else {
            keywords.removeAll(preset.keywords.toSet())
        }
        onSave(keywords)
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
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
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Block,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Website Blocker",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rules & Keywords",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Toggle quick categories or add custom plain-text words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        PreferenceCategory(title = "Quick Categories")
                        Spacer(modifier = Modifier.height(8.dp))

                        presetCategories.forEachIndexed { index, preset ->
                            val isEnabled = preset.keywords.all { keywords.contains(it) }
                            val shape = when (index) {
                                0 -> topShape
                                presetCategories.lastIndex -> bottomShape
                                else -> middleShape
                            }

                            if (index > 0) Spacer(modifier = Modifier.height(4.dp))

                            SettingsToggle(
                                title = preset.title,
                                description = preset.description,
                                checked = isEnabled,
                                onCheckedChange = { checked -> togglePreset(preset, checked) },
                                icon = preset.icon,
                                shape = shape
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        PreferenceCategory(title = "Custom Keywords")
                        Spacer(modifier = Modifier.height(8.dp))

                        CardGroup(shape = singleShape, containerColor = containerColor) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = customInput,
                                            onValueChange = {
                                                customInput = it
                                                errorMessage = null
                                            },
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { addKeyword(customInput) }),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (customInput.isEmpty()) {
                                                        Text(
                                                            text = "Type word (e.g. distraction)",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilledIconButton(
                                            onClick = { addKeyword(customInput) },
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Add keyword",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (keywords.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Active Keywords (${keywords.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        keywords.forEach { keyword ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Outlined.Notes,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = keyword,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { removeKeyword(keyword) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Delete,
                                                        contentDescription = "Remove",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(120.dp))
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
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithGroupedButton {
                    ZenithButtonWeighted(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onSave(keywords)
                                onDismiss()
                            }
                        },
                        text = "Done",
                        type = ZenithButtonType.Filled,
                        weight = 1f,
                        isFirst = true,
                        isLast = true
                    )
                }
            }
        }
    }
}
