package com.etrisad.zenith

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = ZenithDatabase.getDatabase(this)
        val shieldRepository = ShieldRepository(database.shieldDao(), database.scheduleDao(), database.dailyUsageDao())
        val userPreferencesRepository = UserPreferencesRepository(this)

        // Schedule daily usage sync
        DailyUsageWorker.schedule(applicationContext)

        // Secara otomatis aktifkan dynamic color jika perangkat mendukung (Android 12+)
        lifecycleScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            // Jika preferensi belum pernah disetel atau perangkat baru didukung
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Opsional: Anda bisa memaksa 'true' hanya jika user belum pernah mengubahnya
                // Namun sesuai permintaan, kita lgsg apply jika support.
                userPreferencesRepository.setDynamicColor(true)
            } else {
                userPreferencesRepository.setDynamicColor(false)
            }
        }
        
        val homeViewModelFactory = HomeViewModelFactory(applicationContext, shieldRepository)
        val homeViewModel = ViewModelProvider(this, homeViewModelFactory)[HomeViewModel::class.java]

        val focusViewModelFactory = FocusViewModelFactory(applicationContext, shieldRepository)
        val focusViewModel = ViewModelProvider(this, focusViewModelFactory)[FocusViewModel::class.java]

        // Start the monitoring service if needed, 
        // though Accessibility Service will handle the main interception.
        try {
            val serviceIntent = Intent(this, AppUsageMonitorService::class.java)
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            // Handle cases where foreground service might fail to start
        }

        setContent {
            val userPreferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
                initial = com.etrisad.zenith.data.preferences.UserPreferences()
            )

            val darkTheme = when (userPreferences.themeConfig) {
                com.etrisad.zenith.data.preferences.ThemeConfig.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ZenithTheme(
                darkTheme = darkTheme,
                dynamicColor = userPreferences.dynamicColor
            ) {
                val windowSizeClass = calculateWindowSizeClass(this)
                MainScreen(
                    homeViewModel = homeViewModel,
                    focusViewModel = focusViewModel,
                    userPreferencesRepository = userPreferencesRepository,
                    windowSizeClass = windowSizeClass
                )
            }
        }
    }
}
