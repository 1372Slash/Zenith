package com.etrisad.zenith.ui.screens.profile

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LevelRing(
    level: Int,
    progress: Float,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    badgeSize: Dp = 64.dp,
    ringSize: Dp = 88.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "LevelProgressRing"
    )
    val strokeWidth = with(LocalDensity.current) { 5.dp.toPx() }

    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = containerColor,
            trackColor = containerColor.copy(alpha = 0.15f),
            stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            trackStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            amplitude = { 1f },
            wavelength = 48.dp
        )
        Surface(
            color = containerColor,
            shape = shape,
            modifier = Modifier.size(badgeSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$level",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = contentColor
                    )
                    Text(
                        text = "LEVEL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
