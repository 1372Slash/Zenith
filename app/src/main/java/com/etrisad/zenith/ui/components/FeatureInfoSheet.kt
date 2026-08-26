package com.etrisad.zenith.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class FeatureInfoSection(
    val title: String,
    val bullets: List<String>,
    val icon: ImageVector? = null
)

data class FeatureInfo(
    val title: String,
    val summary: String,
    val sections: List<FeatureInfoSection>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureInfoSheet(
    info: FeatureInfo?,
    onDismissRequest: () -> Unit
) {
    if (info == null) return

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = info.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            info.sections.forEach { section ->
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    section.icon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                section.bullets.forEach { bullet ->
                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

object FeatureInfoSections {
    val WhatItDoes = Icons.Outlined.AutoAwesome
    val WhatYoullSee = Icons.Outlined.Visibility
    val Tips = Icons.Outlined.TipsAndUpdates

    fun whatItDoes(vararg bullets: String) = FeatureInfoSection("What it does", bullets.toList(), WhatItDoes)
    fun whatYoullSee(vararg bullets: String) = FeatureInfoSection("What you'll see", bullets.toList(), WhatYoullSee)
    fun tips(vararg bullets: String) = FeatureInfoSection("Tips", bullets.toList(), Tips)
}
