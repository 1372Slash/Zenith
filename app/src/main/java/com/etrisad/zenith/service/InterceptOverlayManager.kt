package com.etrisad.zenith.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.etrisad.zenith.ui.theme.ZenithTheme

class InterceptOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var viewModelStore: ViewModelStore? = null

    companion object {
        var isShowing = false
    }

    fun showOverlay(
        packageName: String,
        appName: String,
        shield: com.etrisad.zenith.data.local.entity.ShieldEntity?,
        totalUsageToday: Long,
        onAllowUse: (Int, Boolean) -> Unit,
        onCloseApp: () -> Unit,
        onGoalDismiss: () -> Unit
    ) {
        if (overlayView != null) return
        isShowing = true

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setContent {
                ZenithTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        InterceptOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            shield = shield,
                            totalUsageToday = totalUsageToday,
                            onAllowUse = { minutes, isEmergency ->
                                hideOverlay()
                                onAllowUse(minutes, isEmergency)
                            },
                            onCloseApp = {
                                hideOverlay()
                                onCloseApp()
                            },
                            onGoalDismiss = {
                                hideOverlay()
                                onGoalDismiss()
                            }
                        )
                    }
                }
            }
        }

        // Force layout to go behind system bars so we can color them ourselves
        @Suppress("DEPRECATION")
        composeView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        composeView.setViewTreeLifecycleOwner(lOwner)
        composeView.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = vStore
        })
        composeView.setViewTreeSavedStateRegistryOwner(lOwner)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideOverlay() {
        overlayView?.let {
            try {
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                
                windowManager.removeView(it)
                it.disposeComposition()
                
                viewModelStore?.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                overlayView = null
                lifecycleOwner = null
                viewModelStore = null
                isShowing = false
            }
        }
    }

    private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        fun performRestore(savedState: Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }
    }
}
