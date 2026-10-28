package com.etrisad.zenith.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Launcher icon rendered exactly like the Usage Stats app list: a circular
 * Coil image with the platform Android glyph as the error fallback. Keeping one
 * implementation means Reels Shield no longer grows its own icon style.
 */
@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val request = remember(packageName) {
        ImageRequest.Builder(context)
            .data("app-icon://$packageName")
            .crossfade(300)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = ContentScale.Crop,
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Android,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    )
}
