package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PageReadFacetBucketDto(
    val value: String,
    val count: Int,
    val selected: Boolean,
)
