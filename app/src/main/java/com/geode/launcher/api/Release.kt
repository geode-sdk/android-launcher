package com.geode.launcher.api

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
    val assets: List<Asset>
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

    fun getAndroidDownload(): Asset? {
        return assets.find {
            it.name.contains("android")
        }
    }
}
