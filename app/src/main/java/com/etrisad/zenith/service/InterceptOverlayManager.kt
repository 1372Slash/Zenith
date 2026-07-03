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
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.etrisad.zenith.ui.components.overlay.BedtimeOverlayContent
import com.etrisad.zenith.ui.components.overlay.EyeCareOverlayContent
import com.etrisad.zenith.ui.components.overlay.InterceptOverlayContent
import com.etrisad.zenith.ui.components.overlay.ScheduleOverlayContent
import com.etrisad.zenith.ui.components.overlay.WindDownOverlayContent
import com.etrisad.zenith.ui.theme.GSFlexSettings
import com.etrisad.zenith.ui.theme.ZenithTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InterceptOverlayManager(
    private val context: Context,
    private val preferencesRepository: com.etrisad.zenith.data.preferences.UserPreferencesRepository
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayUsageState: androidx.compose.runtime.MutableState<Pair<Long, Long>>? = null
    private val sharedPrefs = MutableStateFlow<com.etrisad.zenith.data.preferences.UserPreferences?>(null)
    private var recreateOverlay: (() -> Unit)? = null

    init {
        managerScope.launch {
            preferencesRepository.userPreferencesFlow.collectLatest { sharedPrefs.value = it }
        }
    }

    companion object {
        private const val TAG = "InterceptOverlayManager"
        @Volatile
        var isShowing = false
        private var overlayView: ComposeView? = null
        private var lifecycleOwner: MyLifecycleOwner? = null
        private var viewModelStore: ViewModelStore? = null
        @Volatile
        var currentPackage: String? = null
        @Volatile
        var lastKickedPackage: String? = null
        @Volatile
        var lastKickTime: Long = 0L
        private var audioFocusRequest: AudioFocusRequest? = null
        private var focusRequestJob: kotlinx.coroutines.Job? = null
        private val afChangeListener = AudioManager.OnAudioFocusChangeListener { }

        val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.settings",
            "com.samsung.android.systemui",
            "com.miui.systemui",
            "com.huawei.systemui",
            "com.oppo.systemui",
            "com.coloros.safecenter",
            "com.vivo.systemui",
            "com.oneplus.twspackage",
            "com.android.incallui"
        )
        fun isSystemUiPackage(packageName: String): Boolean = packageName in SYSTEM_UI_PACKAGES
        private var keyboardPackages = emptySet<String>()
        private var lastKeyboardRefreshTime = 0L
    }

    private fun updateOverlayContent(
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
        overlayUsageState?.value = Pair(totalUsageToday, totalGlobalUsageToday)
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
        synchronized(this) {
            if (isShowing && currentPackage == packageName) return
            
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showOverlay(packageName, appName, shield, totalUsageToday, totalGlobalUsageToday, delayDurationSeconds, onAllowUse, onCloseApp, onGoalDismiss)
                }
                return
            }

            if (overlayView != null) {
                updateOverlayContent(packageName, appName, shield, totalUsageToday, totalGlobalUsageToday, delayDurationSeconds, onAllowUse, onCloseApp, onGoalDismiss)
                isShowing = true
                currentPackage = packageName
                return
            }

            isShowing = true
            currentPackage = packageName
        }

        recreateOverlay = {
            showOverlay(packageName, appName, shield, totalUsageToday, totalGlobalUsageToday, delayDurationSeconds, onAllowUse, onCloseApp, onGoalDismiss)
        }

        val usageState = androidx.compose.runtime.mutableStateOf(Pair(totalUsageToday, totalGlobalUsageToday))
        overlayUsageState = usageState

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
                val userPrefs by sharedPrefs.collectAsState(initial = null)
                val darkTheme = when (userPrefs?.themeConfig) {
                    com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                    com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = userPrefs?.fontOption ?: com.etrisad.zenith.data.preferences.FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings()
                ) {
                    val (usage, globalUsage) = usageState.value
                    Box(modifier = Modifier.fillMaxSize()) {
                        InterceptOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            shield = shield,
                            totalUsageToday = usage,
                            totalGlobalUsageToday = globalUsage,
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
        synchronized(this) {
            if (isShowing && currentPackage == packageName) return
            
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showScheduleOverlay(packageName, appName, schedule, totalGlobalUsageToday, onAllowUse, onCloseApp)
                }
                return
            }
            
            if (isShowing || overlayView != null) hideOverlay()
            
            isShowing = true
            currentPackage = packageName
        }

        recreateOverlay = {
            showScheduleOverlay(packageName, appName, schedule, totalGlobalUsageToday, onAllowUse, onCloseApp)
        }

        val usageState = androidx.compose.runtime.mutableStateOf(Pair(0L, totalGlobalUsageToday))
        overlayUsageState = usageState

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
                val userPrefs by sharedPrefs.collectAsState(initial = null)
                val darkTheme = when (userPrefs?.themeConfig) {
                    com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                    com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = userPrefs?.fontOption ?: com.etrisad.zenith.data.preferences.FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings()
                ) {
                    val (_, globalUsage) = usageState.value
                    Box(modifier = Modifier.fillMaxSize()) {
                        ScheduleOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            schedule = schedule,
                            totalGlobalUsageToday = globalUsage,
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
        synchronized(this) {
            if (isShowing && currentPackage == packageName) return

            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showBedtimeOverlay(packageName, appName, onCloseApp)
                }
                return
            }
            
            if (isShowing || overlayView != null) hideOverlay()
            
            isShowing = true
            currentPackage = packageName
        }

        recreateOverlay = {
            showBedtimeOverlay(packageName, appName, onCloseApp)
        }

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setContent {
                val userPrefs by sharedPrefs.collectAsState(initial = null)
                val darkTheme = when (userPrefs?.themeConfig) {
                    com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                    com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = userPrefs?.fontOption ?: com.etrisad.zenith.data.preferences.FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings()
                ) {
                    val prefs = userPrefs
                    if (prefs != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            BedtimeOverlayContent(
                                packageName = packageName,
                                appName = appName,
                                userPreferences = prefs,
                                onCloseApp = {
                                    onCloseApp()
                                    hideOverlay()
                                }
                            )
                        }
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner)
    }

    fun showEyeCareRestOverlay(
        durationSeconds: Int,
        onRestComplete: () -> Unit
    ) {
        synchronized(this) {
            if (isShowing) return

            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showEyeCareRestOverlay(durationSeconds, onRestComplete)
                }
                return
            }

            if (isShowing || overlayView != null) hideOverlay()

            isShowing = true
            currentPackage = context.packageName
        }

        recreateOverlay = {
            showEyeCareRestOverlay(durationSeconds, onRestComplete)
        }

        val vStore = ViewModelStore()
        viewModelStore = vStore

        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setContent {
                val userPrefs by sharedPrefs.collectAsState(initial = null)
                val prefs = userPrefs

                val darkTheme = when (prefs?.themeConfig) {
                    com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                    com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                if (prefs != null) {
                    com.etrisad.zenith.ui.theme.ZenithTheme(
                        darkTheme = darkTheme,
                        fontOption = prefs.fontOption,
                        dynamicColor = prefs.dynamicColor,
                        expressiveColors = prefs.expressiveColors,
                        gsFlexSettings = prefs.gsFlexSettings
                    ) {
                        EyeCareOverlayContent(
                            restSeconds = durationSeconds,
                            onRestComplete = {
                                onRestComplete()
                                hideOverlay()
                            },
                            userPrefs = prefs,
                            isLandscape = isLandscape
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
        synchronized(this) {
            if (isShowing && currentPackage == packageName) return

            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showWindDownOverlay(packageName, appName, sessionUsed, onAllowUse, onCloseApp)
                }
                return
            }

            if (isShowing || overlayView != null) hideOverlay()

            isShowing = true
            currentPackage = packageName
        }

        recreateOverlay = {
            showWindDownOverlay(packageName, appName, sessionUsed, onAllowUse, onCloseApp)
        }

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setContent {
                val userPrefs by sharedPrefs.collectAsState(initial = null)
                val darkTheme = when (userPrefs?.themeConfig) {
                    com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                    com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = userPrefs?.fontOption ?: com.etrisad.zenith.data.preferences.FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WindDownOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            sessionUsed = sessionUsed,
                            userPreferences = userPrefs ?: com.etrisad.zenith.data.preferences.UserPreferences(),
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

    private var lastOverlayError: String? = null
    private var lastOverlayErrorTime = 0L

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            requestMediaPause()
            
            synchronized(this) {
                if (!isShowing) {
                    composeView.disposeComposition()
                    return
                }
                composeView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewDetachedFromWindow(v: android.view.View) {
                        val savedRecreate: (() -> Unit)?
                        val savedPkg: String?
                        synchronized(this@InterceptOverlayManager) {
                            if (v == overlayView) {
                                savedPkg = currentPackage
                                savedRecreate = if (isShowing) recreateOverlay else null
                                isShowing = false
                                currentPackage = null
                                overlayView = null
                                lifecycleOwner = null
                                viewModelStore = null
                                overlayUsageState = null
                            } else {
                                savedPkg = null
                                savedRecreate = null
                            }
                        }
                        if (savedRecreate != null && savedPkg != null) {
                            mainHandler.postDelayed({
                                if (!InterceptOverlayManager.isShowing) {
                                    savedRecreate()
                                }
                            }, 300)
                        }
                    }
                    override fun onViewAttachedToWindow(v: android.view.View) {}
                })
                windowManager.addView(composeView, params)
                overlayView = composeView
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                composeView.post {
                    try {
                        composeView.windowInsetsController?.setSystemBarsAppearance(
                            0,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    } catch (_: Exception) {}
                }
            }

            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            val currentTime = System.currentTimeMillis()
            if (e.message != lastOverlayError || currentTime - lastOverlayErrorTime > 30000) {
                Log.e(TAG, "Error adding overlay for $currentPackage: ${e.message}", e)
                lastOverlayError = e.message
                lastOverlayErrorTime = currentTime
            }
            synchronized(this) {
                isShowing = false
                currentPackage = null
                overlayView = null
            }
        }
    }

    private fun isKeyboardApp(packageName: String): Boolean {
        val currentTime = System.currentTimeMillis()
        if (keyboardPackages.isEmpty() || currentTime - lastKeyboardRefreshTime > 600000) {
            try {
                val imm = context.getSystemService(InputMethodManager::class.java)
                keyboardPackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
                lastKeyboardRefreshTime = currentTime
            } catch (_: Exception) {}
        }
        return packageName in keyboardPackages
    }

    fun checkAndHide(newPackage: String) {
        if (!isShowing) return
        
        val target = currentPackage ?: return

        if (newPackage == target || newPackage == context.packageName) return
        if (SYSTEM_UI_PACKAGES.contains(newPackage) || isKeyboardApp(newPackage)) return

        hideOverlay()
    }

    fun hideOverlay() {
        val viewToRemove: ComposeView?
        val lOwnerToDestroy: MyLifecycleOwner?
        val vStoreToClear: ViewModelStore?

        synchronized(this) {
            if (!isShowing && overlayView == null) return

            isShowing = false
            currentPackage = null

            resumeMedia()

            viewToRemove = overlayView
            lOwnerToDestroy = lifecycleOwner
            vStoreToClear = viewModelStore
            overlayView = null
            lifecycleOwner = null
            viewModelStore = null
            overlayUsageState = null
            recreateOverlay = null
        }

        if (viewToRemove == null) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                removeOverlayViewImmediate(viewToRemove, lOwnerToDestroy, vStoreToClear)
            }
            return
        }

        removeOverlayViewImmediate(viewToRemove, lOwnerToDestroy, vStoreToClear)
    }

    private fun removeOverlayViewImmediate(
        view: ComposeView,
        lOwner: MyLifecycleOwner?,
        vStore: ViewModelStore?
    ) {
        try {
            lOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            view.disposeComposition()
            windowManager.removeViewImmediate(view)
            vStore?.clear()
        } catch (e: Exception) {
            val currentTime = System.currentTimeMillis()
            if (e.message != lastOverlayError || currentTime - lastOverlayErrorTime > 30000) {
                Log.e(TAG, "Error removing overlay: ${e.message}", e)
                lastOverlayError = e.message
                lastOverlayErrorTime = currentTime
            }
        }
    }

    private fun requestMediaPause() {
        val pkg = currentPackage ?: return
        if (!shouldRequestMediaFocus(pkg)) return

        focusRequestJob?.cancel()
        focusRequestJob = managerScope.launch {
            val enabled = sharedPrefs.value?.interceptAudioFocusEnabled ?: false
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

    fun resetState() {
        val viewToRemove: ComposeView?
        val lOwnerToDestroy: MyLifecycleOwner?
        val vStoreToClear: ViewModelStore?

        synchronized(this) {
            if (!isShowing && overlayView == null) return

            viewToRemove = overlayView
            lOwnerToDestroy = lifecycleOwner
            vStoreToClear = viewModelStore
            isShowing = false
            currentPackage = null
            overlayView = null
            lifecycleOwner = null
            viewModelStore = null
            overlayUsageState = null
            recreateOverlay = null
        }

        if (viewToRemove != null) {
            try {
                lOwnerToDestroy?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lOwnerToDestroy?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lOwnerToDestroy?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                viewToRemove.disposeComposition()
                windowManager.removeViewImmediate(viewToRemove)
                vStoreToClear?.clear()
            } catch (_: Exception) {
            }
        }
    }

    fun destroy() {
        hideOverlay()
        managerScope.cancel()
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
