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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = ZenithDatabase.getDatabase(this)
        val shieldRepository = ShieldRepository(database.shieldDao(), database.scheduleDao())
        val userPreferencesRepository = UserPreferencesRepository(this)
        
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
                initial = com.etrisad.zenith.data.preferences.UserPreferences(
                    com.etrisad.zenith.data.preferences.ThemeConfig.FOLLOW_SYSTEM,
                    true,
                    false,
                    0,
                    60,
                    30
                )
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
                MainScreen(homeViewModel, focusViewModel, userPreferencesRepository)
            }
        }
    }
}
