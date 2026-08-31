package com.etrisad.zenith.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.MainActivity
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemainingTargetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        private val bitmapCache = mutableMapOf<String, Bitmap>()
        fun clearCache() {
            bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            bitmapCache.clear()
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ZenithApplication
        val repo = app.shieldRepository
        val prefsRepo = app.userPreferencesRepository

        provideContent {
            val uiMode = context.resources.configuration.uiMode
            val sunnyBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.VerySunny) }
            val backgroundBitmap = remember(uiMode) { createShapeBitmap(context, 120, MaterialShapes.Gem) }

            val prefs by prefsRepo.userPreferencesFlow.collectAsState(initial = null)
            val targetMinutes = prefs?.screenTimeTargetMinutes ?: 0
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val totalMillis = remember(todayStr) {
                try {
                    kotlinx.coroutines.runBlocking {
                        val daily = repo.getDailyUsagesForDateSync(todayStr)
                        daily.find { it.packageName == "TOTAL" }?.usageTimeMillis
                            ?: daily.filter { it.packageName !in setOf("SHIELD_TOTAL","GOAL_TOTAL","OTHER_TOTAL") }.sumOf { it.usageTimeMillis }
                    }
                } catch (_: Exception) { 0L }
            }
            val remainingMillis = if (targetMinutes > 0) (targetMinutes * 60_000L - totalMillis).coerceAtLeast(0L) else -1L
            val isOver = targetMinutes > 0 && totalMillis > targetMinutes * 60_000L
            val overMillis = if (isOver) totalMillis - targetMinutes * 60_000L else 0L

            GlanceTheme {
                val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                val action = actionStartActivity(intent)
                Box(
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(100.dp).clickable(action),
                    contentAlignment = Alignment.Center
                ) {
                    RemainingTargetContent(
                        targetMinutes = targetMinutes,
                        remainingMillis = remainingMillis,
                        overMillis = overMillis,
                        isOver = isOver,
                        sunnyBitmap = sunnyBitmap,
                        backgroundBitmap = backgroundBitmap
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("RestrictedApi")
    @Composable
    private fun RemainingTargetContent(
        targetMinutes: Int,
        remainingMillis: Long,
        overMillis: Long,
        isOver: Boolean,
        sunnyBitmap: Bitmap,
        backgroundBitmap: Bitmap
    ) {
        val size = LocalSize.current
        val squareSize = minOf(size.width, size.height)
        val scaleFactor = squareSize.value / 100f

        val contentPadding = (8 * scaleFactor).dp
        val containerSize = (40 * scaleFactor).dp
        val iconSize = (20 * scaleFactor).dp

        val timeText = when {
            targetMinutes == 0 -> "--"
            isOver -> {
                val h = overMillis / 3600000
                val m = (overMillis % 3600000) / 60000
                if (h > 0) "${h}h ${m}m" else "${m}m"
            }
            else -> {
                val h = remainingMillis / 3600000
                val m = (remainingMillis % 3600000) / 60000
                if (h > 0) "${h}h ${m}m" else "${m}m"
            }
        }
        val badgeLabel = when {
            targetMinutes == 0 -> "NO TARGET"
            isOver -> "OVER"
            else -> "LEFT"
        }
        val sunnyIcon = if (isOver) R.drawable.ic_warning else R.drawable.ic_flag
        val sunnyTint = if (isOver) GlanceTheme.colors.error else GlanceTheme.colors.primary
        val sunnyContentTint = if (isOver) GlanceTheme.colors.onError else GlanceTheme.colors.primaryContainer
        // tertiary accent for label — over switches to error as accent variant
        val labelColor = if (isOver) GlanceTheme.colors.error else GlanceTheme.colors.tertiary

        val mainFontSize = when {
            timeText == "--" -> (30 * scaleFactor).sp
            timeText.length >= 7 -> (16 * scaleFactor).sp
            timeText.length == 6 -> (18 * scaleFactor).sp
            timeText.length == 5 -> (22 * scaleFactor).sp
            timeText.length == 4 -> (26 * scaleFactor).sp
            else -> (30 * scaleFactor).sp
        }

        val labelFontSize = (10 * scaleFactor).sp

        val backgroundColor = GlanceTheme.colors.widgetBackground

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier.size(squareSize),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(backgroundBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(backgroundColor)
                )

                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                provider = ImageProvider(sunnyBitmap),
                                contentDescription = null,
                                modifier = GlanceModifier.size(containerSize),
                                colorFilter = ColorFilter.tint(sunnyTint)
                            )
                            Image(
                                provider = ImageProvider(sunnyIcon),
                                contentDescription = null,
                                modifier = GlanceModifier.size(iconSize),
                                colorFilter = ColorFilter.tint(sunnyContentTint)
                            )
                        }
                        Text(
                            text = timeText,
                            style = TextStyle(
                                fontSize = mainFontSize,
                                fontWeight = FontWeight.Medium,
                                color = sunnyTint
                            )
                        )
                        Text(
                            text = badgeLabel,
                            style = TextStyle(
                                fontSize = labelFontSize,
                                fontWeight = FontWeight.Medium,
                                color = labelColor
                            )
                        )
                    }
                }
            }
        }
    }

    private fun createShapeBitmap(context: Context, sizeDp: Int, shape: RoundedPolygon, alpha: Int = 255): Bitmap {
        val uiMode = context.resources.configuration.uiMode
        val key = "remaining_${sizeDp}_${shape.hashCode()}_$uiMode"
        bitmapCache[key]?.let { if (!it.isRecycled) return it }
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = shape.toPath()
        val matrix = Matrix().apply { setScale(sizePx.toFloat(), sizePx.toFloat()) }
        path.transform(matrix)
        val paint = Paint().apply { color = Color.WHITE; this.alpha = alpha; isAntiAlias = true; isFilterBitmap = true; style = Paint.Style.FILL }
        canvas.drawPath(path, paint)
        bitmapCache[key] = bitmap
        return bitmap
    }
}
