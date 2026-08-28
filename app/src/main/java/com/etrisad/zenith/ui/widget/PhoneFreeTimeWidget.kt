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
import com.etrisad.zenith.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneFreeTimeWidget : GlanceAppWidget() {
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
            val dayStartHour = prefs?.dayStartHour ?: 0
            val dayStartMinute = prefs?.dayStartMinute ?: 0
            val now = System.currentTimeMillis()
            val startOfDay = DateTimeUtils.getDayStartTime(now, dayStartHour, dayStartMinute)
            val elapsedToday = (now - startOfDay).coerceAtLeast(0L)
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
            val idleMillis = (elapsedToday - totalMillis).coerceAtLeast(0L)
            val idleHours = idleMillis / 3600000
            val idleMinutes = (idleMillis % 3600000) / 60000

            val uiMode = context.resources.configuration.uiMode
            val sunnyBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Sunny) }
            val cookieBitmap = remember(uiMode) { createShapeBitmap(context, 120, MaterialShapes.Cookie9Sided) }

            GlanceTheme {
                val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                val action = actionStartActivity(intent)
                Box(
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(32.dp).clickable(action),
                    contentAlignment = Alignment.Center
                ) {
                    PhoneFreeContent(
                        idleHours = idleHours,
                        idleMinutes = idleMinutes,
                        idleMillis = idleMillis,
                        sunnyBitmap = sunnyBitmap,
                        cookieBitmap = cookieBitmap
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun PhoneFreeContent(
        idleHours: Long,
        idleMinutes: Long,
        idleMillis: Long,
        sunnyBitmap: Bitmap,
        cookieBitmap: Bitmap
    ) {
        val size = LocalSize.current
        val squareSize = minOf(size.width, size.height)
        val scale = squareSize.value / 100f

        val timeText = if (idleHours > 0) "${idleHours}h ${idleMinutes}m" else "${idleMinutes}m"
        val subText = "phone-free today"

        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(modifier = GlanceModifier.size(squareSize), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(cookieBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer)
                )
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding((12 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size((40 * scale).dp)) {
                        Image(
                            provider = ImageProvider(sunnyBitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size((40 * scale).dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.secondary)
                        )
                        Image(
                            provider = ImageProvider(R.drawable.widget_preview_sunny),
                            contentDescription = null,
                            modifier = GlanceModifier.size((20 * scale).dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondary)
                        )
                    }
                    Text(
                        text = timeText,
                        style = TextStyle(
                            fontSize = (18 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        ),
                        modifier = GlanceModifier.padding(top = (4 * scale).dp)
                    )
                    Text(
                        text = subText,
                        style = TextStyle(fontSize = (9 * scale).sp, color = GlanceTheme.colors.onSecondaryContainer, textAlign = TextAlign.Center)
                    )
                    Text(
                        text = "resets at midnight",
                        style = TextStyle(fontSize = (7 * scale).sp, color = GlanceTheme.colors.onSecondaryContainer)
                    )
                }
            }
        }
    }

    private fun createShapeBitmap(context: Context, sizeDp: Int, shape: RoundedPolygon): Bitmap {
        val uiMode = context.resources.configuration.uiMode
        val key = "free_${sizeDp}_${shape.hashCode()}_$uiMode"
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
