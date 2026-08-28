package com.etrisad.zenith.ui.widget

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
import androidx.glance.text.TextAlign
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
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ZenithApplication
        val repo = app.shieldRepository
        val prefsRepo = app.userPreferencesRepository

        provideContent {
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

            val uiMode = context.resources.configuration.uiMode
            val circleBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Circle) }
            val pillBitmap = remember(uiMode) { createShapeBitmap(context, 120, MaterialShapes.Pill) }

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
                        circleBitmap = circleBitmap,
                        pillBitmap = pillBitmap
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun RemainingTargetContent(
        targetMinutes: Int,
        remainingMillis: Long,
        overMillis: Long,
        isOver: Boolean,
        circleBitmap: Bitmap,
        pillBitmap: Bitmap
    ) {
        val size = LocalSize.current
        val squareSize = minOf(size.width, size.height)
        val scale = squareSize.value / 100f

        val (timeText, label) = when {
            targetMinutes == 0 -> "No target" to "set in settings"
            isOver -> {
                val h = overMillis / 3600000
                val m = (overMillis % 3600000) / 60000
                "${h}h ${m}m over" to "target exceeded"
            }
            else -> {
                val h = remainingMillis / 3600000
                val m = (remainingMillis % 3600000) / 60000
                if (h > 0) "${h}h ${m}m left" to "remaining" else "${m}m left" to "remaining"
            }
        }

        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(modifier = GlanceModifier.size(squareSize), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(pillBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground)
                )
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding((10 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size((36 * scale).dp)) {
                        Image(
                            provider = ImageProvider(circleBitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size((36 * scale).dp),
                            colorFilter = ColorFilter.tint(if (isOver) GlanceTheme.colors.error else GlanceTheme.colors.tertiary)
                        )
                        Image(
                            provider = ImageProvider(if (isOver) R.drawable.ic_crown else R.drawable.ic_fire_department_outlined),
                            contentDescription = null,
                            modifier = GlanceModifier.size((16 * scale).dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiary)
                        )
                    }
                    Text(
                        text = timeText,
                        style = TextStyle(
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOver) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        modifier = GlanceModifier.padding(top = (4 * scale).dp)
                    )
                    Text(
                        text = label,
                        style = TextStyle(fontSize = (9 * scale).sp, color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    )
                    if (targetMinutes > 0) {
                        Text(
                            text = "target ${targetMinutes / 60}h ${targetMinutes % 60}m",
                            style = TextStyle(fontSize = (8 * scale).sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    }

    private fun createShapeBitmap(context: Context, sizeDp: Int, shape: RoundedPolygon): Bitmap {
        val uiMode = context.resources.configuration.uiMode
        val key = "remaining_${sizeDp}_${shape.hashCode()}_$uiMode"
        val cache = Companion.bitmapCache[key]
        if (cache != null && !cache.isRecycled) return cache
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = shape.toPath()
        val matrix = Matrix().apply { setScale(sizePx.toFloat(), sizePx.toFloat()) }
        path.transform(matrix)
        val paint = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawPath(path, paint)
        Companion.bitmapCache[key] = bitmap
        return bitmap
    }
}
