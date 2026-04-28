package com.etrisad.zenith.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InterceptOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        @Volatile
        var isShowing = false
            private set
        private var overlayView: ComposeView? = null
        private var lifecycleOwner: MyLifecycleOwner? = null
        private var viewModelStore: ViewModelStore? = null
        @Volatile
        private var currentPackage: String? = null

        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller"
        )
    }

    fun showOverlay(
        packageName: String,
        appName: String,
        shield: com.etrisad.zenith.data.local.entity.ShieldEntity?,
        totalUsageToday: Long,
        totalGlobalUsageToday: Long,
        delayDurationSeconds: Int = 0,
        onAllowUse: (Int, Boolean) -> Unit,
        onCloseApp: () -> Unit,
        onGoalDismiss: () -> Unit
    ) {
        if (isShowing && currentPackage == packageName) return
        if (isShowing) {
            hideOverlay()
        }

        isShowing = true
        currentPackage = packageName

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
                            totalGlobalUsageToday = totalGlobalUsageToday,
                            delayDurationSeconds = delayDurationSeconds,
                            onAllowUse = { minutes, isEmergency ->
                                onAllowUse(minutes, isEmergency)
                                hideOverlay()
                            },
                            onCloseApp = {
                                onCloseApp()
                                hideOverlay()
                            },
                            onGoalDismiss = {
                                onGoalDismiss()
                                hideOverlay()
                            }
                        )
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner, vStore)
    }

    fun showScheduleOverlay(
        packageName: String,
        appName: String,
        schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity,
        totalGlobalUsageToday: Long,
        onAllowUse: (Int, Boolean) -> Unit,
        onCloseApp: () -> Unit
    ) {
        if (isShowing) return
        isShowing = true
        currentPackage = packageName

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
                        ScheduleOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            schedule = schedule,
                            totalGlobalUsageToday = totalGlobalUsageToday,
                            onAllowUse = { minutes, isEmergency ->
                                onAllowUse(minutes, isEmergency)
                                hideOverlay()
                            },
                            onCloseApp = {
                                onCloseApp()
                                hideOverlay()
                            }
                        )
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner, vStore)
    }

    private fun setupAndAddView(composeView: ComposeView, lOwner: MyLifecycleOwner, vStore: ViewModelStore) {
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
            isShowing = false
            currentPackage = null
        }
    }

    suspend fun checkAndHide(newPackage: String) {
        if (!isShowing) return
        
        val target = currentPackage ?: return

        if (newPackage == target ||
            newPackage == context.packageName || 
            newPackage in SYSTEM_UI_PACKAGES ||
            newPackage.contains("launcher", ignoreCase = true) ||
            newPackage.contains("home", ignoreCase = true)) {
            return
        }

        withContext(Dispatchers.Main) {
            hideOverlay()
        }
    }

    fun hideOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideOverlay() }
            return
        }

        val view = overlayView
        if (view != null) {
            try {
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                
                view.disposeComposition()
                windowManager.removeViewImmediate(view)
                
                viewModelStore?.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                overlayView = null
                lifecycleOwner = null
                viewModelStore = null
                currentPackage = null
                isShowing = false
            }
        } else {
            isShowing = false
            currentPackage = null
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
