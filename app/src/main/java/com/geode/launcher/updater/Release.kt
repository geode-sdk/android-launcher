package com.geode.launcher.updater

import com.geode.launcher.utils.LaunchUtils
import kotlinx.datetime.Instant
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
)

@Serializable
class Release(
    val url: String,
    val id: Int,
    val targetCommitish: String,
    val tagName: String,
    val createdAt: Instant,
    val publishedAt: Instant,
    val assets: List<Asset>,
    val htmlUrl: String
) {
    fun getDescription(): String {
        if (tagName == "nightly") {
            // get the commit from the assets
            // otherwise, a separate request is needed to get the hash (ew)
            val asset = assets.first()
            val commit = asset.name.substring(6..12)

            return "nightly-$commit"
        }

        return tagName
    }

    fun getDescriptor(): Long {
        return createdAt.epochSeconds
    }

    fun getGeodeDownload(): Asset? {
        // try to find an asset that matches the architecture first
        val platform = LaunchUtils.platformName

        val releaseSuffix = "$platform.zip"
        return assets.find {
            it.name.endsWith(releaseSuffix)
        }
    }

    fun getLauncherDownload(): Asset? {
        val platform = LaunchUtils.platformName
        val use32BitPlatform = platform == "android32"

        return assets.find { asset ->
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
}
