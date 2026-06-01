package com.etrisad.zenith.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.ViewModelProvider
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.ui.components.focus.AppPickerBottomSheet
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.FocusViewModelFactory
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.MutablePreferences

class AppStreakWidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val app = application as ZenithApplication
        val focusViewModelFactory = FocusViewModelFactory(
            applicationContext,
            app.shieldRepository,
            app.userPreferencesRepository
        )
        val focusViewModel = ViewModelProvider(this, focusViewModelFactory)[FocusViewModel::class.java]

        setContent {
            val userPreferences by app.userPreferencesRepository.userPreferencesFlow.collectAsState(
                initial = com.etrisad.zenith.data.preferences.UserPreferences()
            )
            
            val uiState by focusViewModel.uiState.collectAsState()

            ZenithTheme(
                darkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                dynamicColor = userPreferences.dynamicColor,
                fontOption = userPreferences.fontOption,
                expressiveColors = userPreferences.expressiveColors,
                gsFlexSettings = userPreferences.gsFlexSettings
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppPickerBottomSheet(
                        uiState = uiState,
                        title = "Select App for Streak",
                        onDismiss = { finish() },
                        onAppSelected = { appInfo ->
                            saveWidgetSelection(appInfo.packageName)
                        },
                        onSearchQueryChange = { query ->
                            focusViewModel.onSearchQueryChange(query)
                        }
                    )
                }
            }
        }
    }

    private fun saveWidgetSelection(packageName: String) {
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@AppStreakWidgetConfigurationActivity)
                    .getGlanceIdBy(appWidgetId)
                
                updateAppWidgetState(this@AppStreakWidgetConfigurationActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    val mutable = prefs.toMutablePreferences()
                    mutable[AppStreakWidget.SELECTED_PACKAGE_KEY] = packageName
                    mutable
                }
                
                AppStreakWidget().update(this@AppStreakWidgetConfigurationActivity, glanceId)

                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
            } catch (_: Exception) {
            } finally {
                finish()
            }
        }
    }
}
