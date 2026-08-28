package com.etrisad.zenith.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
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
import androidx.glance.background
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
import androidx.glance.unit.ColorProvider
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.MainActivity
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TotalScreenTimeWidget : GlanceAppWidget() {
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
            val prefs by prefsRepo.userPreferencesFlow.collectAsState(initial = null)
            val dayStartHour = prefs?.dayStartHour ?: 0
            val dayStartMinute = prefs?.dayStartMinute ?: 0

            val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
            val totalMillis = remember(todayStr, prefs) {
                try {
                    kotlinx.coroutines.runBlocking {
                        val daily = repo.getDailyUsagesForDateSync(todayStr)
                        val total = daily.find { it.packageName == "TOTAL" }?.usageTimeMillis
                        if (total != null && total > 0) total else daily.filter { it.packageName !in setOf("SHIELD_TOTAL","GOAL_TOTAL","OTHER_TOTAL") }.sumOf { it.usageTimeMillis }
                    }
                } catch (_: Exception) { 0L }
            }

            val uiMode = context.resources.configuration.uiMode
            val pillBitmap = remember(uiMode) { createShapeBitmap(context, 120, MaterialShapes.Pill) }
            val cookieBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Cookie12Sided) }

            GlanceTheme {
                val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                val action = actionStartActivity(intent)
                Box(
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).clickable(action),
                    contentAlignment = Alignment.Center
                ) {
                    TotalScreenTimeContent(
                        totalMillis = totalMillis,
                        dayStartHour = dayStartHour,
                        pillBitmap = pillBitmap,
                        cookieBitmap = cookieBitmap
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun TotalScreenTimeContent(
        totalMillis: Long,
        dayStartHour: Int,
        pillBitmap: Bitmap,
        cookieBitmap: Bitmap
    ) {
        val size = LocalSize.current
        val squareSize = minOf(size.width, size.height)
        val scale = squareSize.value / 100f

        val hours = totalMillis / 3600000
        val minutes = (totalMillis % 3600000) / 60000
        val timeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        val secondaryText = if (hours > 0) "today" else "screen time"

        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(modifier = GlanceModifier.size(squareSize), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(pillBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground)
                )
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding((12 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size((44 * scale).dp)) {
                        Image(
                            provider = ImageProvider(cookieBitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size((44 * scale).dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                        )
                        Image(
                            provider = ImageProvider(R.drawable.ic_fire_department_outlined),
                            contentDescription = null,
                            modifier = GlanceModifier.size((18 * scale).dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
                        )
                    }
                    Text(
                        text = timeText,
                        style = TextStyle(
                            fontSize = (18 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        modifier = GlanceModifier.padding(top = (6 * scale).dp)
                    )
                    Text(
                        text = secondaryText,
                        style = TextStyle(
                            fontSize = (10 * scale).sp,
                            color = GlanceTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }

    private fun createShapeBitmap(context: Context, sizeDp: Int, shape: RoundedPolygon): Bitmap {
        val uiMode = context.resources.configuration.uiMode
        val key = "total_${sizeDp}_${shape.hashCode()}_$uiMode"
        bitmapCache[key]?.let { if (!it.isRecycled) return it }
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = shape.toPath()
        val matrix = Matrix().apply { setScale(sizePx.toFloat(), sizePx.toFloat()) }
        path.transform(matrix)
        val paint = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawPath(path, paint)
        bitmapCache[key] = bitmap
        return bitmap
    }
}
