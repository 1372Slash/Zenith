package com.etrisad.zenith.crash

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashScreen(
    record: CrashRecord?,
    exporting: Boolean,
    onRetry: () -> Unit,
    onSafeMode: () -> Unit,
    onExit: () -> Unit,
    onExportZip: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showDetails by remember { mutableStateOf(false) }
    val shareText = record?.toReportText().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Zenith stopped unexpectedly",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        val dateText = record?.let {
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(it.timestamp))
        }.orEmpty()
        Text(
            text = listOfNotNull(
                record?.versionName?.takeIf { it.isNotBlank() }?.let { "v$it" },
                dateText.takeIf { it.isNotBlank() }
            ).joinToString(" - "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        if (record == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No crash details were saved, but the app can be restarted or " +
                        "booted in Safe Mode from here.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = record.exceptionClass.substringAfterLast('.'),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (record.message.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = record.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 6
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide technical details" else "Show technical details")
            }

            if (showDetails) {
                DetailBlock(title = "Stack trace", content = record.stackTrace)
                Spacer(Modifier.height(12.dp))
                DetailBlock(title = "App device: ${record.device}", content = record.logTail)
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }

        Spacer(Modifier.height(10.dp))

        FilledTonalButton(
            onClick = onSafeMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enter Safe Mode")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Starts Zenith without loading your data. Recommended when the " +
                "crash keeps coming back; your saved data is not modified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onExportZip,
            enabled = !exporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (exporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Preparing report...")
            } else {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Export diagnostic report (ZIP)")
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (shareText.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Zenith crash report")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share crash report"))
                    }
                },
                enabled = shareText.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share log", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = {
                    if (shareText.isNotBlank()) {
                        clipboard.setText(AnnotatedString(shareText))
                    }
                },
                enabled = shareText.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Copy log", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(20.dp))

        TextButton(onClick = onExit) {
            Icon(
                Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Exit Zenith", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nothing is sent automatically. Reports are only shared when you tap " +
                "Export or Share above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DetailBlock(title: String, content: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.medium
                )
                .padding(10.dp)
        ) {
            SelectionContainer {
                Text(
                    text = content.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
