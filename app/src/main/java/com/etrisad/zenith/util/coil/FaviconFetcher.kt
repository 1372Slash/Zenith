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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class FaviconData(val domain: String)

class FaviconFetcher(
    private val data: FaviconData,
    private val context: Context
) : Fetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun fetch(): FetchResult? {
        val drawable = fetchFavicon(data.domain) ?: return null
        return DrawableResult(
            drawable = drawable,
            isSampled = false,
            dataSource = DataSource.NETWORK
        )
    }

    private fun fetchFavicon(domain: String): Drawable? {
        val urls = listOf(
            "https://www.google.com/s2/favicons?domain=$domain&sz=64",
            "https://$domain/favicon.ico"
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

    class Factory(private val context: Context) : Fetcher.Factory<FaviconData> {
        override fun create(data: FaviconData, options: Options, imageLoader: ImageLoader): Fetcher {
            return FaviconFetcher(data, context)
        }
    }
}

class FaviconMapper : Mapper<String, FaviconData> {
    override fun map(data: String, options: Options): FaviconData? {
        if (!data.startsWith("favicon://")) return null
        val domain = data.substringAfter("favicon://")
        return if (domain.isNotEmpty()) FaviconData(domain) else null
    }
}
