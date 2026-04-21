package com.etrisad.zenith

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.repository.ShieldRepository

class ZenithApplication : Application(), ImageLoaderFactory {

    val shieldRepository: ShieldRepository by lazy {
        val database = ZenithDatabase.getDatabase(this)
        ShieldRepository(database.shieldDao(), database.scheduleDao())
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Batasi memory cache Coil hanya 10% dari total RAM aplikasi atau max 15MB
                    // Ini krusial karena icon app seringkali kecil tapi Coil bisa boros cache
                    .maxSizePercent(0.1)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            // Kurangi overhead dengan mematikan logging atau tracing jika tidak perlu
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Agresif membersihkan cache saat sistem butuh RAM
        if (level >= TRIM_MEMORY_MODERATE) {
            // Gunakan singleton image loader, jangan buat baru
            coil.Coil.imageLoader(this).memoryCache?.clear()
            // Jangan panggil System.gc() terlalu sering karena memicu CPU spike
        }
    }
}
