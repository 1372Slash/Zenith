package com.etrisad.zenith.util.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.map.Mapper
import coil.request.Options
import com.etrisad.zenith.data.website.WebsiteRepository
import okhttp3.Request

data class AppIconData(val packageName: String)

class AppIconFetcher(
    private val data: AppIconData,
    private val context: Context
) : Fetcher {

    private val client = sharedFaviconHttpClient

    override suspend fun fetch(): FetchResult? {
        if (WebsiteRepository.isWebsitePackageName(data.packageName)) {
            val domain = WebsiteRepository.extractDomainFromPackageName(data.packageName)
            val favicon = fetchFavicon(domain)
            if (favicon != null) {
                return DrawableResult(
                    drawable = favicon,
                    isSampled = false,
                    dataSource = DataSource.NETWORK
                )
            }
            return null
        }

        val icon = try {
            context.packageManager.getApplicationIcon(data.packageName)
        } catch (e: Exception) {
            null
        } ?: return null

        return DrawableResult(
            drawable = icon,
            isSampled = false,
            dataSource = DataSource.MEMORY_CACHE
        )
    }

    private fun fetchFavicon(domain: String): Drawable? {
        val urls = listOf(
            "https:
            "https:
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body ?: continue
                    val bytes = body.bytes()
                    if (bytes.isNotEmpty()) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            return BitmapDrawable(context.resources, bitmap)
                        }
                    }
                }
                response.close()
            } catch (_: Exception) {}
        }
        return null
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconData> {
        override fun create(data: AppIconData, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(data, context)
        }
    }
}

class AppIconMapper : Mapper<String, AppIconData> {
    override fun map(data: String, options: Options): AppIconData? {
        if (!data.startsWith("app-icon:
        val pkg = data.substringAfter("app-icon:
        return if (pkg.isNotEmpty()) AppIconData(pkg) else null
    }
}
