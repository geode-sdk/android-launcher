package com.geode.launcher.updater

import com.geode.launcher.utils.LaunchUtils
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class Asset @OptIn(ExperimentalTime::class) constructor(
    val url: String,
    val id: Int,
    val name: String,
    val size: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val browserDownloadUrl: String,
)

@Serializable
data class Release @OptIn(ExperimentalTime::class) constructor(
    val url: String,
    val id: Int,
    val targetCommitish: String,
    val tagName: String,
    val createdAt: Instant,
    val publishedAt: Instant,
    val assets: List<Asset>,
    val htmlUrl: String,
    val body: String?,
    val name: String?,
)

@Serializable
data class LoaderVersion @OptIn(ExperimentalTime::class) constructor(
    val tag: String,
    val version: String,
    val createdAt: Instant,
    val commitHash: String,
    val prerelease: Boolean
)

@Serializable
data class LoaderPayload<T>(
    val payload: T?,
    val error: String
)

data class DownloadableAsset(val url: String, val filename: String, val size: Long? = null)

abstract class Downloadable {
    abstract fun getDescription(): String
    abstract fun getDescriptor(): Long
    abstract fun getDownload(): DownloadableAsset?
}

class DownloadableGitHubLoaderRelease(private val release: Release) : Downloadable() {
    override fun getDescription(): String {
        if (release.tagName == "nightly") {
            // get the commit from the assets
            // otherwise, a separate request is needed to get the hash (ew)
            val asset = release.assets.first()
            val commit = asset.name.substring(6..12)

            return "nightly-$commit"
        }

        return release.tagName
    }

    @OptIn(ExperimentalTime::class)
    override fun getDescriptor(): Long {
        return release.createdAt.epochSeconds
    }

    private fun getGitHubDownload(): Asset? {
        // try to find an asset that matches the architecture first
        val platform = LaunchUtils.platformName

        val releaseSuffix = "$platform.zip"
        return release.assets.find {
            it.name.endsWith(releaseSuffix)
        }
    }

    override fun getDownload(): DownloadableAsset? {
        val download = getGitHubDownload() ?: return null
        return DownloadableAsset(
            url = download.browserDownloadUrl,
            filename = download.name,
            size = download.size.toLong()
        )
    }
}

class DownloadableLauncherRelease(val release: Release) : Downloadable() {
    override fun getDescription(): String {
        return release.tagName
    }

    @OptIn(ExperimentalTime::class)
    override fun getDescriptor(): Long {
        return release.createdAt.epochSeconds
    }

    private fun getGitHubDownload(): Asset? {
        val platform = LaunchUtils.platformName
        val use32BitPlatform = platform == "android32"

        return release.assets.find { asset ->
            /* you know it's good when you pull out the truth table
             * u32bp | contains | found
             * 1     | 1        | 1
             * 0     | 1        | 0
             * 1     | 0        | 0
             * 0     | 0        | 1
             * surprise! it's an xnor
             */

            (asset.name.contains("android32")) == use32BitPlatform
        }
    }

    override fun getDownload(): DownloadableAsset? {
        val download = getGitHubDownload() ?: return null
        return DownloadableAsset(
            url = download.browserDownloadUrl,
            filename = download.name,
            size = download.size.toLong()
        )
    }
}

class DownloadableLoaderRelease(private val version: LoaderVersion) : Downloadable() {
    override fun getDescription(): String {
        return version.tag
    }

    @OptIn(ExperimentalTime::class)
    override fun getDescriptor(): Long {
        return version.createdAt.epochSeconds
    }

    override fun getDownload(): DownloadableAsset? {
        val filename = "geode-${version.tag}-${LaunchUtils.platformName}.zip"
        return DownloadableAsset(
            url = "https://github.com/geode-sdk/geode/releases/download/${version.tag}/$filename",
            filename = filename
        )
    }
}
