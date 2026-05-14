package com.etrisad.zenith.service

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InterceptOverlayManager(
    private val context: Context,
    private val preferencesRepository: com.etrisad.zenith.data.preferences.UserPreferencesRepository
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "InterceptOverlayManager"
        @Volatile
        var isShowing = false
        private var overlayView: ComposeView? = null
        private var lifecycleOwner: MyLifecycleOwner? = null
        private var viewModelStore: ViewModelStore? = null
        @Volatile
        private var currentPackage: String? = null
        private var audioFocusRequest: AudioFocusRequest? = null
        private var focusRequestJob: kotlinx.coroutines.Job? = null
        private val afChangeListener = AudioManager.OnAudioFocusChangeListener { }

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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showOverlay(packageName, appName, shield, totalUsageToday, totalGlobalUsageToday, delayDurationSeconds, onAllowUse, onCloseApp, onGoalDismiss)
            }
            return
        }

        if (isShowing && currentPackage == packageName && overlayView != null) {
            overlayView?.setContent {
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
            return
        }

        if (isShowing || overlayView != null) {
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
            setViewTreeLifecycleOwner(lOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = vStore
            })
            setViewTreeSavedStateRegistryOwner(lOwner)
            
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
        setupAndAddView(composeView, lOwner)
    }

    fun showScheduleOverlay(
        packageName: String,
        appName: String,
        schedule: com.etrisad.zenith.data.local.entity.ScheduleEntity,
        totalGlobalUsageToday: Long,
        onAllowUse: (Int, Boolean) -> Unit,
        onCloseApp: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showScheduleOverlay(packageName, appName, schedule, totalGlobalUsageToday, onAllowUse, onCloseApp)
            }
            return
        }
        if (isShowing && currentPackage == packageName && overlayView != null) {
            overlayView?.setContent {
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
            return
        }
        if (isShowing || overlayView != null) hideOverlay()
        
        isShowing = true
        currentPackage = packageName

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = vStore
            })
            setViewTreeSavedStateRegistryOwner(lOwner)

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
        setupAndAddView(composeView, lOwner)
    }

    fun showBedtimeOverlay(
        packageName: String,
        appName: String,
        onCloseApp: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showBedtimeOverlay(packageName, appName, onCloseApp)
            }
            return
        }
        if (isShowing && currentPackage == packageName && overlayView != null) return
        if (isShowing || overlayView != null) hideOverlay()
        
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
                        BedtimeOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            onCloseApp = {
                                onCloseApp()
                                hideOverlay()
                            }
                        )
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner)
    }

    fun showWindDownOverlay(
        packageName: String,
        appName: String,
        sessionUsed: Boolean,
        onAllowUse: (Int) -> Unit,
        onCloseApp: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showWindDownOverlay(packageName, appName, sessionUsed, onAllowUse, onCloseApp)
            }
            return
        }
        if (isShowing && currentPackage == packageName && overlayView != null) {
            overlayView?.setContent {
                ZenithTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WindDownOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            sessionUsed = sessionUsed,
                            onAllowUse = { minutes ->
                                onAllowUse(minutes)
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
            return
        }
        if (isShowing || overlayView != null) hideOverlay()

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
                        WindDownOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            sessionUsed = sessionUsed,
                            onAllowUse = { minutes ->
                                onAllowUse(minutes)
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
        setupAndAddView(composeView, lOwner)
    }

    private fun setupAndAddView(composeView: ComposeView, lOwner: MyLifecycleOwner) {
        val vStore = viewModelStore ?: return

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
            requestMediaPause()
            windowManager.addView(composeView, params)
            overlayView = composeView
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            Log.d(TAG, "Overlay added to window for $currentPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay for $currentPackage", e)
            isShowing = false
            currentPackage = null
        }
    }

    fun checkAndHide(newPackage: String) {
        if (!isShowing) return
        
        val target = currentPackage ?: return

        if (newPackage != target && newPackage != context.packageName && !SYSTEM_UI_PACKAGES.contains(newPackage)) {
            hideOverlay()
        }
    }

    fun hideOverlay() {
        isShowing = false
        val target = currentPackage
        currentPackage = null
        
        resumeMedia()

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideOverlay() }
            return
        }

        val view = overlayView
        Log.d(TAG, "Hiding overlay for $target")
        if (view != null) {
            try {
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                
                view.disposeComposition()
                windowManager.removeViewImmediate(view)
                Log.d(TAG, "Overlay removed for $target")
                
                viewModelStore?.clear()
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay for $target", e)
            } finally {
                overlayView = null
                lifecycleOwner = null
                viewModelStore = null
            }
        }
    }

    private fun requestMediaPause() {
        val pkg = currentPackage ?: return
        if (!shouldRequestMediaFocus(pkg)) return

        focusRequestJob?.cancel()
        focusRequestJob = managerScope.launch {
            val enabled = preferencesRepository.userPreferencesFlow.first().interceptAudioFocusEnabled
            if (!enabled) return@launch

            if (!isShowing || currentPackage != pkg) return@launch

            val focusType = if (isVideoApp(pkg)) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            } else {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(focusType)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .build()
                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    afChangeListener,
                    AudioManager.STREAM_MUSIC,
                    focusType
                )
            }
        }
    }

    private fun isVideoApp(packageName: String): Boolean {
        val strictVideoApps = setOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "tv.twitch.android.app",
            "com.ss.android.ugc.aweme",
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.facebook.katana"
        )
        if (strictVideoApps.contains(packageName)) return true

        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun shouldRequestMediaFocus(packageName: String): Boolean {
        val mediaApps = setOf(
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme",
            "com.instagram.android",
            "com.facebook.katana",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "tv.twitch.android.app",
            "com.twitter.android",
            "com.snapchat.android",
            "com.google.android.apps.youtube.music",
            "com.spotify.music"
        )
        if (mediaApps.contains(packageName)) return true

        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO ||
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL ||
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun resumeMedia() {
        focusRequestJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(afChangeListener)
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
