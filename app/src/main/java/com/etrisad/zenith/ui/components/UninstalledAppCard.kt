package com.etrisad.zenith.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UninstalledAppCard(
    appName: String,
    onDelete: () -> Unit,
    onDismissToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "\"$appName\" is no longer installed on your device",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            ZenithGroupedButton(size = ZenithButtonSize.Small) {
                ZenithButtonWeighted(
                    onClick = onDismissToday,
                    text = "Remind tomorrow",
                    icon = Icons.Outlined.Snooze,
                    size = ZenithButtonSize.Small,
                    type = ZenithButtonType.Tonal,
                    isFirst = true,
                    isLast = false
                )
                ZenithButtonWeighted(
                    onClick = onDelete,
                    text = "Remove from Zenith",
                    icon = Icons.Outlined.Delete,
                    size = ZenithButtonSize.Small,
                    type = ZenithButtonType.Tonal,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    isFirst = false,
                    isLast = true
                )
            }
        }
    }
}
