package com.geode.launcher.api

import com.geode.launcher.utils.DownloadUtils.executeCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL

class ReleaseRepository(private val httpClient: OkHttpClient) {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com/repos/geode-sdk/geode"
        private const val GITHUB_API_HEADER = "X-GitHub-Api-Version"
        private const val GITHUB_API_VERSION = "2022-11-28"
    }

    suspend fun getLatestNightlyRelease(): Release? {
        val nightlyPath = "$GITHUB_API_BASE/releases/tags/nightly"
        val url = URL(nightlyPath)

        return getReleaseByUrl(url)
    }

    suspend fun getLatestRelease(): Release? {
        val releasePath = "$GITHUB_API_BASE/releases/latest"
        val url = URL(releasePath)

        return getReleaseByUrl(url)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getReleaseByUrl(url: URL): Release? {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader(GITHUB_API_HEADER, GITHUB_API_VERSION)
            .build()

        val call = httpClient.newCall(request)
        val response = call.executeCoroutine()

        return when (response.code) {
            200 -> {
                val format = Json {
                    namingStrategy = JsonNamingStrategy.SnakeCase
                    ignoreUnknownKeys = true
                }

                val release = format.decodeFromBufferedSource<Release>(
                    response.body!!.source()
                )

                release
            }
            404 -> {
                null
            }
            else -> {
                throw IOException("unknown response ${response.code}")
            }
        }
    }
}