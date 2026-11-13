package com.etrisad.zenith.util.coil

import android.content.Context
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
import java.util.concurrent.TimeUnit

data class FaviconData(val domain: String)

internal val sharedFaviconHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

internal object FaviconLoader {
    private val client = sharedFaviconHttpClient

    fun load(context: Context, domain: String): Drawable? {
        val normalizedDomain = domain.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
        if (normalizedDomain.isBlank() || !normalizedDomain.contains('.')) return null

        val candidates = listOf(
            "https://$normalizedDomain/favicon.ico",
            "https://$normalizedDomain/apple-touch-icon.png"
        )

        for (url in candidates) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) Zenith")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body ?: return@use
                    val bytes = body.bytes()
                    if (bytes.isEmpty()) return@use
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    return BitmapDrawable(context.resources, bitmap)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}

class FaviconFetcher(
    private val data: FaviconData,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val drawable = FaviconLoader.load(context, data.domain) ?: return null
        return DrawableResult(
            drawable = drawable,
            isSampled = false,
            dataSource = DataSource.NETWORK
        )
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
