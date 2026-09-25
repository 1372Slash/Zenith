package com.etrisad.zenith.ui.screens.settings.pausepoint

import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.BuildConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.InterceptOverlayManager
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.nfc.EnableNfcReaderMode
import com.etrisad.zenith.ui.components.nfc.isNfcEnabled
import com.etrisad.zenith.ui.components.nfc.isNfcSupported
import com.etrisad.zenith.ui.components.nfc.normalizeNfcTagId
import com.etrisad.zenith.ui.components.nfc.openNfcSettings
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskType
import com.etrisad.zenith.ui.screens.settings.PreferenceCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PausePointNfcSettingsScreen(
    preferences: UserPreferences,
    innerPadding: PaddingValues,
    preferencesRepository: UserPreferencesRepository
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var tags by remember(preferences.pausePointNfcTagIds) {
        mutableStateOf(preferences.pausePointNfcTagIds.map { normalizeNfcTagId(it) }.filter { it.isNotEmpty() }.distinct())
    }
    var justAdded by remember { mutableStateOf<String?>(null) }
    var lastScanned by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scanningEnabled by remember { mutableStateOf(true) }
    var manualInput by remember { mutableStateOf("") }

    val supported = remember { isNfcSupported(context) }
    var nfcOn by remember { mutableStateOf(isNfcEnabled(context)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            nfcOn = isNfcEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }
    val enabled = nfcOn

    if (supported && enabled && scanningEnabled) {
        EnableNfcReaderMode(enabled = true) { tagId ->
            val normalized = normalizeNfcTagId(tagId)
            if (normalized.isEmpty()) return@EnableNfcReaderMode
            lastScanned = normalized
            if (normalized in tags) {
                scanError = "Tag already registered"
            } else {
                scanError = null
                tags = tags + normalized
                coroutineScope.launch { preferencesRepository.setPausePointNfcTagIds(tags) }
                justAdded = normalized
            }
        }
    }

    val addManual: () -> Unit = {
        val normalized = normalizeNfcTagId(manualInput)
        if (normalized.isNotEmpty() && normalized !in tags) {
            tags = tags + normalized
            coroutineScope.launch { preferencesRepository.setPausePointNfcTagIds(tags) }
            justAdded = normalized
            scanError = null
            manualInput = ""
        } else if (normalized in tags) {
            scanError = "Tag already registered"
        }
    }

    val removeTag: (String) -> Unit = { tag ->
        tags = tags - tag
        coroutineScope.launch { preferencesRepository.setPausePointNfcTagIds(tags) }
        if (justAdded == tag) justAdded = null
    }

    val launchTest: () -> Unit = {
        if (Settings.canDrawOverlays(context)) {
            val manager = InterceptOverlayManager(context.applicationContext, preferencesRepository)
            manager.showOverlay(
                packageName = context.packageName,
                appName = "Zenith",
                shield = null,
                totalUsageToday = 0,
                totalGlobalUsageToday = 0,
                delayDurationSeconds = 0,
                forcedTaskType = PausePointTaskType.NFC_SCAN,
                onAllowUse = { _, _ -> },
                onCloseApp = {},
                onGoalDismiss = {}
            )
        } else {
            Toast.makeText(context, "Allow display over other apps to test", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            NfcTypeHeader(tagCount = tags.size)
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(16.dp))
                PausePointTestButton(onClick = launchTest)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            NfcStatusCard(
                supported = supported,
                enabled = enabled,
                scanningEnabled = scanningEnabled,
                onScanningChange = { scanningEnabled = it },
                lastScanned = lastScanned,
                scanError = scanError,
                onOpenSettings = { openNfcSettings(context) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Add tag manually",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Paste a tag ID (e.g. 04A13B8C5D80) if you already know it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tag ID") },
                        placeholder = { Text("e.g. 04A13B8C5D80") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ZenithButton(
                        onClick = addManual,
                        text = "Add Tag",
                        icon = Icons.Outlined.TouchApp,
                        type = ZenithButtonType.Filled,
                        size = ZenithButtonSize.Large,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = normalizeNfcTagId(manualInput).isNotEmpty()
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (justAdded != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tag saved!",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            PreferenceCategory(title = "Saved Tags (${tags.size})")
        }

        if (tags.isEmpty()) {
            item {
                EmptyNfcCard()
            }
        } else {
            itemsIndexed(tags, key = { _, it -> it }) { index, tag ->
                NfcTagRow(
                    index = index,
                    total = tags.size,
                    tag = tag,
                    onRemove = { removeTag(tag) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    )
                )
            }
        }
    }
}

@Composable
private fun NfcTypeHeader(tagCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NFC Scan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Register your NFC tags here. Tap one of them to pass the Pause Point.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = CircleShape,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = when {
                    tagCount == 0 -> "No tags saved"
                    tagCount == 1 -> "1 tag saved"
                    else -> "$tagCount tags saved"
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NfcStatusCard(
    supported: Boolean,
    enabled: Boolean,
    scanningEnabled: Boolean,
    onScanningChange: (Boolean) -> Unit,
    lastScanned: String?,
    scanError: String?,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (supported && enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Nfc,
                        contentDescription = null,
                        tint = if (supported && enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !supported -> "NFC not supported"
                            !enabled -> "NFC is turned off"
                            else -> "Ready to scan"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            !supported -> "This device has no NFC hardware. You can still add tag IDs manually."
                            !enabled -> "Turn on NFC to scan tags directly."
                            scanningEnabled -> "Hold an NFC tag near your phone to register it."
                            else -> "Scanning paused. Turn it on to register tags."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!supported || !enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(if (!supported) "Open Wireless Settings" else "Turn On NFC")
                }
            }
            if (supported && enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scan to register",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Listen for NFC tags on this screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = scanningEnabled, onCheckedChange = onScanningChange)
                }
            }
            if (lastScanned != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Last read: $lastScanned",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (scanError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = scanError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun NfcTagRow(
    index: Int,
    total: Int,
    tag: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val shape = when {
        total == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = tag,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(tag))
            }) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyNfcCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No tags saved yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hold a tag near your phone or add its ID manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
