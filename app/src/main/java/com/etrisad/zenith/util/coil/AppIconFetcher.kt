package com.etrisad.zenith.util.coil

import android.content.Context
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.map.Mapper
import coil.request.Options
data class AppIconData(val packageName: String)

class AppIconFetcher(
    private val data: AppIconData,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val icon = try {
            context.packageManager.getApplicationIcon(data.packageName)
        } catch (e: Exception) {
            null
        } ?: return null

        return DrawableResult(
            drawable = icon,
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconData> {
        override fun create(data: AppIconData, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(data, context)
        }
    }
}

/**
 * Mengubah string "app-icon://pkg" menjadi object AppIconData secara otomatis.
 */
class AppIconMapper : Mapper<String, AppIconData> {
    override fun map(data: String, options: Options): AppIconData? {
        if (!data.startsWith("app-icon://")) return null
        val pkg = data.substringAfter("app-icon://")
        return if (pkg.isNotEmpty()) AppIconData(pkg) else null
    }
}
