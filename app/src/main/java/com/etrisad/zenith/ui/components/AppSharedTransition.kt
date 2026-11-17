@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.etrisad.zenith.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.etrisad.zenith.BuildConfig
import android.util.Log

private const val TAG = "AppSharedTransition"

data class AppClickTarget(
    val packageName: String,
    val appName: String,
    val sharedOrigin: String? = null,
    val streak: Int = 0
)

data class AppSharedKey(
    val packageName: String,
    val origin: String,
    val type: Type
) {
    enum class Type {
        Icon,
        Title,
        Container
    }
}

object AppSharedTransition {
    const val ENABLED = true

    const val ANIMATE_PLACEHOLDER_SIZE = false

    const val ORIGIN_HOME = "home"
    const val ORIGIN_FOCUS = "focus"
    const val ORIGIN_USAGE_STATS = "usage_stats"
    const val ORIGIN_TOP_APPS = "home_top_apps"
    const val ORIGIN_USAGE_STATS_HOURLY = "usage_stats_hourly"
    const val ORIGIN_CATEGORY = "category_detail"
    const val ORIGIN_PROFILE = "profile"
    const val ORIGIN_HEATMAP = "heatmap"

    const val DURATION_MILLIS = 480

    const val Z_CONTAINER = 0.1f
    const val Z_ICON = 1f
    const val Z_TITLE = 2f

    val BoundsSpring = spring<Rect>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    val ContentFade = tween<Float>(
        durationMillis = DURATION_MILLIS,
        easing = FastOutSlowInEasing
    )

    internal val boundsTransform = BoundsTransform { _, _ -> BoundsSpring }

    internal val containerEnter: EnterTransition =
        fadeIn(animationSpec = ContentFade)
    internal val containerExit: ExitTransition =
        fadeOut(animationSpec = ContentFade)
}

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalSharedTransitionAnimationScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

fun appIconSharedKey(packageName: String, origin: String?): AppSharedKey? =
    origin?.takeIf { it.isNotBlank() }?.let {
        AppSharedKey(packageName = packageName, origin = it, type = AppSharedKey.Type.Icon)
    }

fun appTitleSharedKey(packageName: String, origin: String?): AppSharedKey? =
    origin?.takeIf { it.isNotBlank() }?.let {
        AppSharedKey(packageName = packageName, origin = it, type = AppSharedKey.Type.Title)
    }

fun appContainerSharedKey(packageName: String, origin: String?): AppSharedKey? =
    origin?.takeIf { it.isNotBlank() }?.let {
        AppSharedKey(packageName = packageName, origin = it, type = AppSharedKey.Type.Container)
    }

@Composable
fun Modifier.appSharedElement(
    key: AppSharedKey?,
    zIndexInOverlay: Float = 0f,
    animatePlaceholderSize: Boolean = AppSharedTransition.ANIMATE_PLACEHOLDER_SIZE
): Modifier {
    if (!AppSharedTransition.ENABLED || key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalSharedTransitionAnimationScope.current ?: return this
    val base = this

    return with(sharedScope) {
        val csState = rememberSharedContentState(key = key)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "sharedElement created key=$key, contentState=$csState")
        }
        base.sharedElement(
            sharedContentState = csState,
            animatedVisibilityScope = animatedScope,
            boundsTransform = AppSharedTransition.boundsTransform,
            placeholderSize = placeholderSize(animatePlaceholderSize),
            zIndexInOverlay = zIndexInOverlay
        )
    }
}

@Composable
fun Modifier.appSharedBounds(
    key: AppSharedKey?,
    scaleToBounds: Boolean = false,
    zIndexInOverlay: Float = 0f,
    animatePlaceholderSize: Boolean = AppSharedTransition.ANIMATE_PLACEHOLDER_SIZE,
    enter: EnterTransition = AppSharedTransition.containerEnter,
    exit: ExitTransition = AppSharedTransition.containerExit
): Modifier {
    if (!AppSharedTransition.ENABLED || key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalSharedTransitionAnimationScope.current ?: return this
    val base = this

    return with(sharedScope) {
        val csState = rememberSharedContentState(key = key)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "sharedBounds created key=$key, contentState=$csState")
        }
        base.sharedBounds(
            sharedContentState = csState,
            animatedVisibilityScope = animatedScope,
            enter = enter,
            exit = exit,
            boundsTransform = AppSharedTransition.boundsTransform,
            resizeMode = if (scaleToBounds) {
                SharedTransitionScope.ResizeMode.scaleToBounds()
            } else {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            },
            placeholderSize = placeholderSize(animatePlaceholderSize),
            zIndexInOverlay = zIndexInOverlay
        )
    }
}

private fun placeholderSize(animated: Boolean): SharedTransitionScope.PlaceholderSize =
    if (animated) {
        SharedTransitionScope.PlaceholderSize.AnimatedSize
    } else {
        SharedTransitionScope.PlaceholderSize.ContentSize
    }
