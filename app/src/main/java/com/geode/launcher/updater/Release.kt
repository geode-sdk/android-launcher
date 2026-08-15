package com.geode.launcher.updater

import com.geode.launcher.utils.LaunchUtils
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    val url: String,
    val id: Int,
    val name: String,
    val size: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val browserDownloadUrl: String,
    val digest: String?,
)

@Serializable
data class Release(
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
data class LoaderPlatformDownload(
    val url: String,
    val hash: String?,
)

@Serializable
data class LoaderDownload(
    val win: LoaderPlatformDownload,
    val mac: LoaderPlatformDownload,
    val android32: LoaderPlatformDownload,
    val android64: LoaderPlatformDownload,
    val ios: LoaderPlatformDownload,
    val resources: LoaderPlatformDownload,
)

@Serializable
data class LoaderVersion(
    val tag: String,
    val version: String,
    val createdAt: Instant,
    val commitHash: String,
    val prerelease: Boolean,
    val downloads: LoaderDownload,
)

@Serializable
data class LoaderPayload<T>(
    val payload: T?,
    val error: String
)

data class DownloadableAsset(
    val url: String,
    val filename: String,
    val size: Long? = null,
    val hash: String? = null,
)

private fun mapDownload(download: Asset): DownloadableAsset {
    val hash = if (download.digest?.startsWith("sha256:") == true)
        download.digest.removePrefix("sha256:")
    else null

    return DownloadableAsset(
        url = download.browserDownloadUrl,
        filename = download.name,
        size = download.size.toLong(),
        hash = hash,
    )
}

abstract class Downloadable {
    /**
     * Human-readable representation of the current version.
     */
    abstract fun getDescription(): String

    /**
     * Unique value that refers to the current version, used for comparison.
     */
    abstract fun getDescriptor(): Long

    /**
     * Release download link for the current platform
     */
    abstract fun getDownload(): DownloadableAsset?

    /**
     * Resources download link for current platform.
     */
    abstract fun getResourcesDownload(): DownloadableAsset?
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
        return mapDownload(download)
    }

    override fun getResourcesDownload(): DownloadableAsset? {
        val download = release.assets.find {
            it.name == "resources.zip"
        } ?: return null

        return mapDownload(download)
    }
}

class DownloadableLauncherRelease(val release: Release) : Downloadable() {
    override fun getDescription(): String {
        return release.tagName
    }

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
            asset.name.endsWith("apk") && asset.name.contains("android32") == use32BitPlatform
        }
    }

    override fun getDownload(): DownloadableAsset? {
        val download = getGitHubDownload() ?: return null
        return mapDownload(download)
    }

    override fun getResourcesDownload(): DownloadableAsset? {
        return null
    }
}

class DownloadableLoaderRelease(private val version: LoaderVersion) : Downloadable() {
    override fun getDescription(): String {
        return version.tag
    }

    override fun getDescriptor(): Long {
        return version.createdAt.epochSeconds
    }

    override fun getDownload(): DownloadableAsset {
        val data = if (LaunchUtils.is64bit) version.downloads.android64 else version.downloads.android32
        val filename = "geode-${version.tag}-${LaunchUtils.platformName}.zip"
        return DownloadableAsset(
            url = data.url,
            filename = filename,
            hash = data.hash,
        )
    }

    override fun getResourcesDownload(): DownloadableAsset {
        val data = version.downloads.resources
        val filename = "resources.zip"
        return DownloadableAsset(
            url = data.url,
            filename = filename,
            hash = data.hash,
        )
    }
}
