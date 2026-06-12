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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ZenithApplication : Application(), ImageLoaderFactory {

    private var lastUiMode: Int = 0
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }

    val shieldRepository: ShieldRepository by lazy {
        try {
            val database = ZenithDatabase.getDatabase(this)
            ShieldRepository(
                database.shieldDao(),
                database.scheduleDao(),
                database.dailyUsageDao(),
                database.hourlyUsageDao()
            )
        } catch (e: Exception) {
            android.util.Log.e("ZenithApp", "Failed to initialize DB, trying destructive rebuild", e)
            ZenithDatabase.closeDatabase()
            val database = ZenithDatabase.getDatabase(this)
            ShieldRepository(
                database.shieldDao(),
                database.scheduleDao(),
                database.dailyUsageDao(),
                database.hourlyUsageDao()
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        lastUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        com.etrisad.zenith.service.UsageSyncWorker.enqueue(this)
        com.etrisad.zenith.worker.StreakRefreshWorker.enqueue(this)

        applicationScope.launch {
            try {
                kotlinx.coroutines.delay(1000)
                AppStreakWidget().updateAll(this@ZenithApplication)
                GlobalStreakWidget().updateAll(this@ZenithApplication)
            } catch (_: Exception) {}
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(com.etrisad.zenith.util.coil.AppIconMapper())
                add(com.etrisad.zenith.util.coil.AppIconFetcher.Factory(this@ZenithApplication))
            }
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
            applicationScope.launch {
                try {
                    kotlinx.coroutines.delay(300)
                    AppStreakWidget().updateAll(this@ZenithApplication)
                    GlobalStreakWidget().updateAll(this@ZenithApplication)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}