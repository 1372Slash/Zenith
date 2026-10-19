package com.etrisad.zenith

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.etrisad.zenith.crash.SafeMode
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.repository.ShieldRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.AppUsageMonitorService
import com.etrisad.zenith.ui.screens.MainScreen
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.FocusViewModelFactory
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.HomeViewModelFactory

import com.etrisad.zenith.service.DailyUsageWorker
import android.os.Build
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var initialPackageName by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        initialPackageName = intent.getStringExtra("package_name")

        val app = application as ZenithApplication
        val shieldRepository = app.shieldRepository
        val userPreferencesRepository = app.userPreferencesRepository
        val safeMode = SafeMode.isEnabled(this)

        if (!safeMode) {
            DailyUsageWorker.schedule(applicationContext)
        }

        lifecycleScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val shouldBeDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            
            if (prefs.dynamicColor != shouldBeDynamic) {
                userPreferencesRepository.setDynamicColor(shouldBeDynamic)
            }
        }
        
        val homeViewModelFactory = HomeViewModelFactory(applicationContext, shieldRepository, userPreferencesRepository)
        val homeViewModel = ViewModelProvider(this, homeViewModelFactory)[HomeViewModel::class.java]

        val focusViewModelFactory = FocusViewModelFactory(applicationContext, shieldRepository, userPreferencesRepository)
        val focusViewModel = ViewModelProvider(this, focusViewModelFactory)[FocusViewModel::class.java]

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    homeViewModel.setActive(true)
                    focusViewModel.setActive(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    homeViewModel.setActive(false)
                    focusViewModel.setActive(false)
                }
                else -> {}
            }
        })

        if (!safeMode) {
            AppUsageMonitorService.startMonitorService(this, fromForeground = true)
        }

        setContent {
            val appearance by userPreferencesRepository.appearanceFlow.collectAsState(
                initial = com.etrisad.zenith.data.preferences.UserPreferencesRepository.AppearancePrefs()
            )

            val darkTheme = when (appearance.themeConfig) {
                com.etrisad.zenith.data.preferences.ThemeConfig.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ZenithTheme(
                darkTheme = darkTheme,
                dynamicColor = appearance.dynamicColor,
                fontOption = appearance.fontOption,
                expressiveColors = appearance.expressiveColors,
                gsFlexSettings = appearance.gsFlexSettings
            ) {
                val windowSizeClass = calculateWindowSizeClass(this)
                MainScreen(
                    homeViewModel = homeViewModel,
                    focusViewModel = focusViewModel,
                    shieldRepository = shieldRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    windowSizeClass = windowSizeClass,
                    initialPackageName = initialPackageName,
                    onInitialPackageHandled = { initialPackageName = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!SafeMode.isEnabled(this)) {
            AppUsageMonitorService.requestCameraTypeUpgrade(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pkg = intent.getStringExtra("package_name")
        if (pkg != null) {
            initialPackageName = pkg
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val uiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val uiModeStr = when (uiMode) {
            Configuration.UI_MODE_NIGHT_YES -> "DARK"
            Configuration.UI_MODE_NIGHT_NO -> "LIGHT"
            else -> "UNDEFINED"
        }
        com.etrisad.zenith.data.local.database.DbLogBuffer.d("ZenithDB", "CONFIG_CHANGED: uiMode=$uiModeStr orientation=${newConfig.orientation} locale=${newConfig.locale} fontScale=${newConfig.fontScale} density=${newConfig.densityDpi}")
    }
}
