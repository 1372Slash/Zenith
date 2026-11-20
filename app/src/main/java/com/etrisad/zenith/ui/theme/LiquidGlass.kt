package com.etrisad.zenith.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Global toggle for optional Liquid Glass surface styling across Zenith.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

private const val LIQUID_REFRACTION_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float cornerRadius;
    uniform float strength;

    float roundedBoxSDF(float2 centerPos, float2 halfSize, float radius) {
        float2 q = abs(centerPos) - halfSize + radius;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = size * 0.5;
        float2 centerPos = fragCoord - halfSize;
        float dist = roundedBoxSDF(centerPos, halfSize, cornerRadius);

        // Subtle rim refraction near container edges
        float edgeBand = smoothstep(-28.0, 0.0, dist) * (1.0 - smoothstep(0.0, 2.0, dist));
        float2 normal = length(centerPos) > 0.001 ? normalize(centerPos) : float2(0.0, 0.0);
        float2 refractedCoord = clamp(fragCoord - normal * (edgeBand * strength), float2(0.0), size);

        half4 baseColor = content.eval(refractedCoord);
        float specular = pow(clamp(1.0 - abs(dist) / 14.0, 0.0, 1.0), 3.0) * 0.14;
        return half4(baseColor.rgb + half3(specular), baseColor.a);
    }
"""

/**
 * Applies an adaptive Liquid Glass surface effect (Android 13+ AGSL refraction + specular rim,
 * with a clean translucent Material 3 fallback on Android 12 and below).
 */
fun Modifier.liquidGlass(
    enabled: Boolean = LocalLiquidGlassEnabled.current,
    shape: Shape = RoundedCornerShape(24.dp),
    tintAlpha: Float = 0.22f,
    borderWidth: Dp = 1.dp,
    refractionStrength: Float = 6.5f
): Modifier = composed {
    val active = enabled
    if (!active) return@composed this

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = Color.White.copy(alpha = 0.34f)
    val shadowEdgeColor = Color.White.copy(alpha = 0.08f)

    val glassBrush = remember(surfaceColor, tintAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                surfaceColor.copy(alpha = (tintAlpha + 0.12f).coerceAtMost(0.85f)),
                surfaceColor.copy(alpha = tintAlpha.coerceIn(0.08f, 0.75f))
            )
        )
    }

    val borderBrush = remember(highlightColor, shadowEdgeColor) {
        Brush.verticalGradient(
            colors = listOf(highlightColor, shadowEdgeColor)
        )
    }

    val shaderModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val runtimeShader = remember { RuntimeShader(LIQUID_REFRACTION_AGSL) }
        Modifier.graphicsLayer {
            val w = size.width
            val h = size.height
            if (w > 0f && h > 0f) {
                runtimeShader.setFloatUniform("size", w, h)
                runtimeShader.setFloatUniform("cornerRadius", 24.dp.toPx())
                runtimeShader.setFloatUniform("strength", refractionStrength)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(runtimeShader, "content")
                    .asComposeRenderEffect()
            }
        }
    } else {
        Modifier
    }

    this
        .clip(shape)
        .then(shaderModifier)
        .background(brush = glassBrush, shape = shape)
        .border(BorderStroke(borderWidth, borderBrush), shape = shape)
}

@Composable
fun ProvideLiquidGlass(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLiquidGlassEnabled provides enabled) {
        content()
    }
}
