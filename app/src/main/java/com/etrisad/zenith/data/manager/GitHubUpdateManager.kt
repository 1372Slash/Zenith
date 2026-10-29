package com.etrisad.zenith.data.manager

import android.content.Context
import com.etrisad.zenith.BuildConfig
import com.etrisad.zenith.data.remote.GitHubService
import com.etrisad.zenith.data.remote.model.GitHubRelease
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class GitHubUpdateManager(private val context: Context) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service = retrofit.create(GitHubService::class.java)

    suspend fun fetchLatestRelease(): GitHubRelease? {
        return try {
            val response = service.getLatestRelease()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchAllReleases(): List<GitHubRelease>? {
        return try {
            val response = service.getAllReleases()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun checkForUpdates(): UpdateResult {
        return try {
            val response = service.getLatestRelease()
            if (response.isSuccessful) {
                val release = response.body()
                if (release != null) {
                    val currentVersion = BuildConfig.VERSION_NAME
                    val latestVersion = release.tagName.removePrefix("v")
                    
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        UpdateResult.NewUpdate(release)
                    } else {
                        UpdateResult.NoUpdate
                    }
                } else {
                    UpdateResult.Error("Empty response body")
                }
            } else {
                UpdateResult.Error("API Error: ${response.code()}")
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Network error")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    sealed class UpdateResult {
        data class NewUpdate(val release: GitHubRelease) : UpdateResult()
        data object NoUpdate : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }
}
