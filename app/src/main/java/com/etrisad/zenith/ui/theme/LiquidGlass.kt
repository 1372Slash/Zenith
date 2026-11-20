package com.etrisad.zenith.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }
val LocalLiquidGlassStrength = staticCompositionLocalOf { 8.5f }

private const val LIQUID_GLASS_IOS26_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float cornerRadius;
    uniform float strength;
    uniform float isDark;

    float roundedBoxSDF(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    float2 sdfNormal(float2 p, float2 b, float r) {
        const float eps = 1.0;
        float dx = roundedBoxSDF(p + float2(eps, 0.0), b, r) - roundedBoxSDF(p - float2(eps, 0.0), b, r);
        float dy = roundedBoxSDF(p + float2(0.0, eps), b, r) - roundedBoxSDF(p - float2(0.0, eps), b, r);
        float len = length(float2(dx, dy));
        return len > 0.0001 ? float2(dx, dy) / len : float2(0.0, -1.0);
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = size * 0.5;
        float r = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 p = fragCoord - halfSize;
        float dist = roundedBoxSDF(p, halfSize, r);

        float bevelWidth = clamp(min(size.x, size.y) * 0.28, 14.0, 30.0);
        float bevel = smoothstep(-bevelWidth, 0.0, dist) * (1.0 - smoothstep(0.0, 1.5, dist));
        float lensCurve = pow(bevel, 2.15);

        float2 n = sdfNormal(p, halfSize, r);
        float2 shift = n * (lensCurve * strength);

        float2 uvR = clamp(fragCoord - shift * 1.18, float2(0.5), size - 0.5);
        float2 uvG = clamp(fragCoord - shift * 1.00, float2(0.5), size - 0.5);
        float2 uvB = clamp(fragCoord - shift * 0.82, float2(0.5), size - 0.5);

        half4 cR = content.eval(uvR);
        half4 cG = content.eval(uvG);
        half4 cB = content.eval(uvB);
        half4 color = half4(cR.r, cG.g, cB.b, cG.a);

        float2 lightPrimary = normalize(float2(-0.68, -0.73));
        float2 lightBounce = normalize(float2(0.65, 0.76));
        float rimMask = exp(-abs(dist) * 0.24);
        float fresnel = pow(bevel, 2.8);

        float specTopLeft = pow(max(dot(n, lightPrimary), 0.0), 3.2) * rimMask;
        float specBottomRight = pow(max(dot(n, lightBounce), 0.0), 4.0) * rimMask * 0.45;
        float innerSheen = fresnel * (isDark > 0.5 ? 0.07 : 0.11);

        float specularIntensity = (specTopLeft * (isDark > 0.5 ? 0.34 : 0.48)) +
            (specBottomRight * (isDark > 0.5 ? 0.18 : 0.24)) + innerSheen;

        half3 finalRgb = clamp(color.rgb + half3(specularIntensity), half3(0.0), half3(1.0));
        return half4(finalRgb, color.a);
    }
"""

fun Modifier.liquidGlass(
    enabled: Boolean = LocalLiquidGlassEnabled.current,
    shape: Shape = RoundedCornerShape(24.dp),
    tintAlpha: Float = 0.20f,
    borderWidth: Dp = 1.dp,
    refractionStrength: Float = LocalLiquidGlassStrength.current
): Modifier = composed {
    if (!enabled) return@composed this

    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryTint = MaterialTheme.colorScheme.primary
    val isDarkTheme = Color(surfaceColor.toArgb()).let {
        (0.299f * it.red + 0.587f * it.green + 0.114f * it.blue) < 0.5f
    }

    val glassFillBrush = remember(surfaceColor, primaryTint, tintAlpha, isDarkTheme) {
        val topAlpha = (if (isDarkTheme) tintAlpha + 0.16f else tintAlpha + 0.28f).coerceIn(0.12f, 0.82f)
        val bottomAlpha = (if (isDarkTheme) tintAlpha + 0.04f else tintAlpha + 0.14f).coerceIn(0.08f, 0.72f)
        Brush.linearGradient(
            colors = listOf(
                surfaceColor.copy(alpha = topAlpha),
                primaryTint.copy(alpha = 0.05f),
                surfaceColor.copy(alpha = bottomAlpha)
            )
        )
    }

    val specularBorderBrush = remember(isDarkTheme, primaryTint) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDarkTheme) 0.42f else 0.68f),
                primaryTint.copy(alpha = if (isDarkTheme) 0.16f else 0.22f),
                Color.White.copy(alpha = if (isDarkTheme) 0.06f else 0.14f),
                Color.White.copy(alpha = if (isDarkTheme) 0.24f else 0.38f)
            )
        )
    }

    val shaderModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val runtimeShader = remember { RuntimeShader(LIQUID_GLASS_IOS26_AGSL) }
        Modifier.graphicsLayer {
            val w = size.width
            val h = size.height
            if (w > 0f && h > 0f) {
                val resolvedCornerPx = if (shape is CornerBasedShape) {
                    shape.topStart.toPx(Size(w, h), density)
                } else {
                    24.dp.toPx()
                }
                runtimeShader.setFloatUniform("size", w, h)
                runtimeShader.setFloatUniform("cornerRadius", resolvedCornerPx)
                runtimeShader.setFloatUniform("strength", refractionStrength)
                runtimeShader.setFloatUniform("isDark", if (isDarkTheme) 1f else 0f)
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
        .background(brush = glassFillBrush, shape = shape)
        .border(BorderStroke(borderWidth, specularBorderBrush), shape = shape)
}

@Composable
fun ProvideLiquidGlass(
    enabled: Boolean,
    strength: Float = 8.5f,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides enabled,
        LocalLiquidGlassStrength provides strength
    ) {
        content()
    }
}
