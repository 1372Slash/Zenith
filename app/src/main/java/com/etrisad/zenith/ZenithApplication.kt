package com.etrisad.zenith

import android.app.Application
import android.content.res.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.widget.AppStreakWidget
import com.etrisad.zenith.ui.widget.GlobalStreakWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ZenithApplication : Application(), ImageLoaderFactory {

    private var lastUiMode: Int = 0

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }

    val shieldRepository: ShieldRepository by lazy {
        val database = ZenithDatabase.getDatabase(this)
        ShieldRepository(
            database.shieldDao(),
            database.scheduleDao(),
            database.dailyUsageDao(),
            database.hourlyUsageDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        lastUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        com.etrisad.zenith.service.UsageSyncWorker.enqueue(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.1)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newUiMode != lastUiMode) {
            lastUiMode = newUiMode
            // Trigger widget update when theme changes
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Small delay to ensure resources are updated
                    kotlinx.coroutines.delay(300)
                    AppStreakWidget().updateAll(this@ZenithApplication)
                    GlobalStreakWidget().updateAll(this@ZenithApplication)
                } catch (_: Exception) {
                }
            }
        }
    }
}
