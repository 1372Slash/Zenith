package com.etrisad.zenith.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.R
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton

enum class PathCardMode { INTRO, RESUME }

@Composable
fun OnboardingPathCard(
    mode: PathCardMode,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (mode == PathCardMode.RESUME) {
        stringResource(R.string.onboarding_resume_title)
    } else {
        stringResource(R.string.onboarding_path_card_title)
    }
    val body = if (mode == PathCardMode.RESUME) {
        stringResource(R.string.onboarding_resume_body)
    } else {
        stringResource(R.string.onboarding_path_card_body)
    }
    val primaryLabel = if (mode == PathCardMode.RESUME) {
        stringResource(R.string.onboarding_continue)
    } else {
        stringResource(R.string.onboarding_path_start)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.onboarding_path_explore),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp, end = 8.dp)
            )
            ZenithGroupedButton(
                size = ZenithButtonSize.Medium,
                modifier = Modifier.padding(start = 34.dp, end = 8.dp, top = 8.dp)
            ) {
                if (mode == PathCardMode.INTRO) {
                    ZenithButtonWeighted(
                        onClick = onDismiss,
                        text = stringResource(R.string.onboarding_path_explore),
                        type = ZenithButtonType.Tonal,
                        size = ZenithButtonSize.Medium,
                        isFirst = true,
                        isLast = false
                    )
                }
                ZenithButtonWeighted(
                    onClick = onPrimary,
                    text = primaryLabel,
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.Medium,
                    isFirst = mode != PathCardMode.INTRO,
                    isLast = true
                )
            }
        }
    }
}
