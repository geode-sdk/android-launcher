package com.geode.launcher.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ReleaseRepository {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com/repos/geode-sdk/geode"
        private const val GITHUB_API_HEADER = "X-GitHub-Api-Version"
        private const val GITHUB_API_VERSION = "2022-11-28"
    }

    class HttpException(message: String) : IOException(message)

    private var foundNightlyRelease: Release? = null

    suspend fun getLatestNightlyRelease(isRefresh: Boolean = false): Release? {
        if (!isRefresh && foundNightlyRelease != null) {
            return foundNightlyRelease
        }

        val nightlyPath = "$GITHUB_API_BASE/releases/tags/nightly"
        val url = URL(nightlyPath)

        val release = getReleaseByUrl(url)
        foundNightlyRelease = release

        return release
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getReleaseByUrl(url: URL): Release? {
        return withContext(Dispatchers.IO) {
            val connection = url.openConnection() as HttpURLConnection
            connection.run {
                setRequestProperty("Accept", "application/json")
                setRequestProperty(GITHUB_API_HEADER, GITHUB_API_VERSION)

                val format = Json {
                    namingStrategy = JsonNamingStrategy.SnakeCase
                    ignoreUnknownKeys = true
                }

                when (responseCode) {
                    200 -> {
                        val release = format.decodeFromStream<Release>(inputStream)

                        release
                    }
                    404 -> {
                        null
                    }
                    else -> {
                        throw HttpException("response $responseCode")
                    }
                }
            }
        }
    }
}