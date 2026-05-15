package com.etrisad.zenith.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication
import androidx.glance.Image
import androidx.glance.ColorFilter
import androidx.glance.unit.ColorProvider
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import android.graphics.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import com.etrisad.zenith.MainActivity
import com.etrisad.zenith.data.local.entity.FocusType
import java.util.Calendar
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppStreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val SELECTED_PACKAGE_KEY = stringPreferencesKey("selected_package")
        val EXTRA_WIDGET_ID_KEY = ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)
        val PACKAGE_NAME_KEY = ActionParameters.Key<String>("package_name")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ZenithApplication

        provideContent {
            val prefs = currentState<Preferences>()
            val selectedPackage = prefs[SELECTED_PACKAGE_KEY]

            val shields by remember(app) {
                app.shieldRepository.allShields
                    .distinctUntilChanged { old, new ->
                        if (old.size != new.size) return@distinctUntilChanged false
                        old.zip(new).all { (o, n) ->
                            o.packageName == n.packageName &&
                            o.currentStreak == n.currentStreak &&
                            o.bestStreak == n.bestStreak &&
                            o.lastStreakUpdateTimestamp == n.lastStreakUpdateTimestamp &&
                            (o.remainingTimeMillis <= 0) == (n.remainingTimeMillis <= 0)
                        }
                    }
            }.collectAsState(initial = emptyList())

            val packageToDisplay = remember(selectedPackage, shields) {
                if (!selectedPackage.isNullOrEmpty()) {
                    selectedPackage
                } else {
                    shields.filter { it.currentStreak > 0 }.maxByOrNull { it.currentStreak }?.packageName
                }
            }
            
            val targetShield = remember(packageToDisplay, shields) {
                shields.find { it.packageName == packageToDisplay }
            }
            
            val iconBitmap = remember(packageToDisplay, context.resources.configuration.uiMode) {
                packageToDisplay?.let {
                    try {
                        val original = context.packageManager.getApplicationIcon(it).toBitmap()
                        createShapeBitmap(context, 120, MaterialShapes.Cookie12Sided, sourceBitmap = original)
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            val uiMode = context.resources.configuration.uiMode
            val sunnyBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Sunny) }
            val cookieBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Cookie12Sided) }
            val pillBitmap = remember(uiMode) { createShapeBitmap(context, 120, MaterialShapes.Pill) }
            val circleBitmap = remember(uiMode) { createShapeBitmap(context, 80, MaterialShapes.Circle) }

            GlanceTheme {
                val appWidgetId = remember { GlanceAppWidgetManager(context).getAppWidgetId(id) }

                val isStreakAchieved = remember(targetShield) {
                    if (targetShield?.type == FocusType.GOAL) {
                        targetShield.remainingTimeMillis <= 0 && targetShield.timeLimitMinutes > 0
                    } else {
                        isToday(targetShield?.lastStreakUpdateTimestamp ?: 0L)
                    }
                }

                val mainAction = if (!packageToDisplay.isNullOrEmpty()) {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("package_name", packageToDisplay)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    actionStartActivity(intent)
                } else {
                    val intent = Intent(context, AppStreakWidgetConfigurationActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    actionStartActivity(intent)
                }
                
                val appLaunchIntent = packageToDisplay?.let {
                    context.packageManager.getLaunchIntentForPackage(it)
                }
                val appAction = appLaunchIntent?.let { actionStartActivity(it) }

                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .cornerRadius(100.dp)
                        .clickable(mainAction)
                ) {
                    if (!packageToDisplay.isNullOrEmpty()) {
                        AppStreakContent(
                            icon = iconBitmap,
                            currentStreak = targetShield?.currentStreak ?: 0,
                            bestStreak = targetShield?.bestStreak ?: 0,
                            isStreakAchieved = isStreakAchieved,
                            sunnyBitmap = sunnyBitmap,
                            cookieBitmap = cookieBitmap,
                            pillBitmap = pillBitmap,
                            circleBitmap = circleBitmap,
                            onAppIconClick = appAction
                        )
                    } else {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(pillBitmap),
                                contentDescription = null,
                                modifier = GlanceModifier.fillMaxSize(),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.surface)
                            )
                            Text(
                                text = "Tap to select app",
                                style = TextStyle(color = GlanceTheme.colors.secondary)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        if (timestamp == 0L) return false
        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_YEAR)
        val todayYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = timestamp
        return cal.get(Calendar.DAY_OF_YEAR) == todayDay && cal.get(Calendar.YEAR) == todayYear
    }

    private fun createShapeBitmap(
        context: Context,
        sizeDp: Int,
        shape: RoundedPolygon,
        alpha: Int = 255,
        sourceBitmap: Bitmap? = null
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

        sourceBitmap?.let {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(it, null, Rect(0, 0, sizePx, sizePx), paint)
        }

        return bitmap
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("RestrictedApi")
    @Composable
    private fun AppStreakContent(
        icon: Bitmap?,
        currentStreak: Int,
        bestStreak: Int,
        isStreakAchieved: Boolean,
        sunnyBitmap: Bitmap,
        cookieBitmap: Bitmap,
        pillBitmap: Bitmap,
        circleBitmap: Bitmap,
        onAppIconClick: androidx.glance.action.Action?
    ) {
        val size = LocalSize.current
        val squareSize = minOf(size.width, size.height)

        val scaleFactor = squareSize.value / 100f
        val containerSize = (44 * scaleFactor).dp
        val iconSize = (44 * scaleFactor).dp
        
        val currentStreakStr = currentStreak.toString()
        val mainFontSize = when {
            currentStreakStr.length >= 4 -> (8 * scaleFactor).sp
            currentStreakStr.length == 3 -> (11 * scaleFactor).sp
            currentStreakStr.length == 2 -> (14 * scaleFactor).sp
            else -> (18 * scaleFactor).sp
        }

        val fireSize = (12 * scaleFactor).dp
        val contentPadding = (12 * scaleFactor).dp

        val bestGemSize = (28 * scaleFactor).dp
        val bestStreakStr = bestStreak.toString()
        val bestPillFontSize = when {
            bestStreakStr.length >= 4 -> (7 * scaleFactor).sp
            bestStreakStr.length == 3 -> (8 * scaleFactor).sp
            else -> (10 * scaleFactor).sp
        }
        val bestIconSize = (10 * scaleFactor).dp

        val pillColor = GlanceTheme.colors.widgetBackground

        val sunnyContainerColor = if (isStreakAchieved) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary
        val sunnyContentColor = if (isStreakAchieved) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.primaryContainer
        val fireIcon = if (isStreakAchieved) R.drawable.ic_check else R.drawable.ic_fire_department_outlined

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier.size(squareSize),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(pillBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(pillColor)
                )

                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Box(
                        modifier = if (onAppIconClick != null) GlanceModifier.clickable(onAppIconClick) else GlanceModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(cookieBitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size(containerSize),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground)
                        )
                        if (icon != null) {
                            Image(
                                provider = ImageProvider(icon),
                                contentDescription = null,
                                modifier = GlanceModifier.size(iconSize)
                            )
                        }
                    }
                }

                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            provider = ImageProvider(sunnyBitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size(containerSize),
                            colorFilter = ColorFilter.tint(sunnyContainerColor)
                        )
                        Column(
                            modifier = GlanceModifier.size(containerSize).padding(top = (4 * scaleFactor).dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                provider = ImageProvider(fireIcon),
                                contentDescription = null,
                                modifier = GlanceModifier.size(fireSize),
                                colorFilter = ColorFilter.tint(sunnyContentColor)
                            )
                            Text(
                                text = currentStreak.toString(),
                                style = TextStyle(
                                    fontSize = mainFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = sunnyContentColor,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = GlanceModifier.padding(top = (-2 * scaleFactor).dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .padding(bottom = contentPadding - (6 * scaleFactor).dp, end = (2 * scaleFactor).dp),
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_crown),
                                contentDescription = null,
                                modifier = GlanceModifier.size(bestIconSize),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiary)
                            )
                            Text(
                                text = bestStreak.toString(),
                                style = TextStyle(
                                    fontSize = bestPillFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = GlanceTheme.colors.onTertiary,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = GlanceModifier.padding(top = (-2 * scaleFactor).dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
