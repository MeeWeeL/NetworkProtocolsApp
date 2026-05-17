package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PageReadFacetDto(
    val name: String,
    val title: String,
    val buckets: List<PageReadFacetBucketDto> = emptyList(),
)
