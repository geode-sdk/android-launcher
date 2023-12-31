package com.geode.launcher.api

import kotlinx.serialization.Serializable

@Serializable
data class Release(
    val url: String,
    val id: Int,
    val targetCommitish: String
)
