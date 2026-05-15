package com.etrisad.zenith.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.MainActivity
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication

class GlobalStreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ZenithApplication
        val repository = app.userPreferencesRepository

        provideContent {
            val uiMode = context.resources.configuration.uiMode
            val sunnyBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Sunny) }
            val backgroundBitmap = remember(uiMode) { createShapeBitmap(context, 120, MaterialShapes.Cookie12Sided) }
            val circleBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Circle) }

            val prefs by repository.userPreferencesFlow.collectAsState(initial = null)

            val currentStreak = prefs?.globalCurrentStreak ?: 0
            val bestStreak = prefs?.globalBestStreak ?: 0

            GlanceTheme {
                val mainAction = actionStartActivity(Intent(context, MainActivity::class.java))
                if (prefs != null) {
                    Box(
                        modifier = GlanceModifier.fillMaxSize()
                            .cornerRadius(100.dp)
                            .clickable(mainAction)
                    ) {
                        GlobalStreakContent(
                            currentStreak = currentStreak,
                            bestStreak = bestStreak,
                            sunnyBitmap = sunnyBitmap,
                            backgroundBitmap = backgroundBitmap,
                            circleBitmap = circleBitmap
                        )
                    }
                } else {
                    Box(modifier = GlanceModifier.fillMaxSize()) {}
                }
            }
        }
    }

    private fun createShapeBitmap(
        context: Context,
        sizeDp: Int,
        shape: RoundedPolygon,
        alpha: Int = 255
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = shape.toPath()
        val matrix = Matrix()
        matrix.setScale(sizePx.toFloat(), sizePx.toFloat())
        path.transform(matrix)

        val paint = Paint().apply {
            color = Color.WHITE
            this.alpha = alpha
            isAntiAlias = true
            isFilterBitmap = true
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("RestrictedApi")
    @Composable
    private fun GlobalStreakContent(
        currentStreak: Int,
        bestStreak: Int,
        sunnyBitmap: Bitmap,
        backgroundBitmap: Bitmap,
        circleBitmap: Bitmap
    ) {
        val size = LocalSize.current
        val squareSize = minOf(size.width, size.height)
        val scaleFactor = squareSize.value / 100f
        
        val contentPadding = (8 * scaleFactor).dp
        val containerSize = (40 * scaleFactor).dp
        val fireSize = (20 * scaleFactor).dp
        
        val currentStreakStr = currentStreak.toString()
        val mainFontSize = when {
            currentStreakStr.length >= 5 -> (16 * scaleFactor).sp
            currentStreakStr.length == 4 -> (20 * scaleFactor).sp
            currentStreakStr.length == 3 -> (26 * scaleFactor).sp
            else -> (32 * scaleFactor).sp
        }
        
        val bestGemSize = (28 * scaleFactor).dp
        val bestStreakStr = bestStreak.toString()
        val bestPillFontSize = when {
            bestStreakStr.length >= 4 -> (7 * scaleFactor).sp
            bestStreakStr.length == 3 -> (8 * scaleFactor).sp
            else -> (10 * scaleFactor).sp
        }
        val bestIconSize = (10 * scaleFactor).dp

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
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                            )
                            Image(
                                provider = ImageProvider(R.drawable.ic_fire_department_outlined),
                                contentDescription = null,
                                modifier = GlanceModifier.size(fireSize),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer)
                            )
                        }
                        
                        Text(
                            text = currentStreak.toString(),
                            style = TextStyle(
                                fontSize = mainFontSize,
                                fontWeight = FontWeight.Medium,
                                color = GlanceTheme.colors.primary
                            )
                        )
                    }
                }

                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .padding(bottom = contentPadding, end = (2 * scaleFactor).dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            provider = ImageProvider(circleBitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size(bestGemSize),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiary)
                        )
                        Column(
                            modifier = GlanceModifier.size(bestGemSize).padding(top = (2 * scaleFactor).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_crown),
                                contentDescription = null,
                                modifier = GlanceModifier.size(bestIconSize),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiary)
                            )
                            Text(
                                text = bestStreak.toString(),
                                modifier = GlanceModifier.padding(top = (-2 * scaleFactor).dp),
                                style = TextStyle(
                                    fontSize = bestPillFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = GlanceTheme.colors.onTertiary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
