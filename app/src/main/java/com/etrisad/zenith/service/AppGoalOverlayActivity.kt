package com.etrisad.zenith.service

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.core.net.toUri
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.AppGoalOverlayContent
import com.etrisad.zenith.ui.theme.ZenithTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppGoalOverlayActivity : ComponentActivity() {

    private val activePackageNames = mutableStateListOf<String>()
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val packageList = intent.getStringArrayListExtra(EXTRA_PACKAGE_NAMES) ?: arrayListOf()
        if (packageList.isEmpty()) {
            finish()
            return
        }
        
        activePackageNames.addAll(packageList)
        playGoalSound(packageList.firstOrNull())

        (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(2000)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
            val userPreferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
                initial = UserPreferences()
            )

            val darkTheme = when (userPreferences.themeConfig) {
                ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                ThemeConfig.LIGHT -> false
                ThemeConfig.DARK -> true
            }

            ZenithTheme(
                darkTheme = darkTheme,
                dynamicColor = userPreferences.dynamicColor,
                fontOption = userPreferences.fontOption,
                expressiveColors = userPreferences.expressiveColors,
                gsFlexSettings = userPreferences.gsFlexSettings
            ) {
                AppGoalOverlayContent(
                    packageNames = activePackageNames,
                    onAnswer = { pkg ->
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                        finish()
                    },
                    onSnooze = {
                        val shieldRepo = (application as com.etrisad.zenith.ZenithApplication).shieldRepository
                        val packagesToSnooze = activePackageNames.toList()
                        lifecycleScope.launch(Dispatchers.IO) {
                            packagesToSnooze.forEach { pkg ->
                                val shield = shieldRepo.getShieldByPackageName(pkg)
                                if (shield != null) {
                                    val periodMillis = shield.goalReminderPeriodMinutes * 60 * 1000L
                                    val snoozeTime = 5 * 60 * 1000L
                                    val newTimestamp = System.currentTimeMillis() - periodMillis + snoozeTime
                                    shieldRepo.updateShield(shield.copy(lastGoalReminderTimestamp = newTimestamp))
                                }
                            }
                        }
                        finish()
                    },
                    onHangUp = {
                        finish()
                    }
                )
            }
        }
    }

    private fun playGoalSound(packageName: String?) {
        if (packageName == null) return
        val shieldRepo = (application as com.etrisad.zenith.ZenithApplication).shieldRepository
        lifecycleScope.launch(Dispatchers.IO) {
            val shield = shieldRepo.getShieldByPackageName(packageName)
            if (shield == null || (shield.isGoalCallerEnabled && shield.isGoalCallerSoundEnabled)) {
                val soundUri = if (shield?.goalCallerSoundUri != null) {
                    shield.goalCallerSoundUri.toUri()
                } else {
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                }

                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(this@AppGoalOverlayActivity, soundUri)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            isLooping = true
                            setOnPreparedListener { it.start() }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val packageList = intent.getStringArrayListExtra(EXTRA_PACKAGE_NAMES) ?: return
        packageList.forEach { pkg ->
            if (pkg !in activePackageNames) {
                activePackageNames.add(pkg)
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAMES = "extra_package_names"

        fun start(context: Context, packageNames: List<String>) {
            val intent = Intent(context, AppGoalOverlayActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_PACKAGE_NAMES, ArrayList(packageNames))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(intent)
        }
    }
}
