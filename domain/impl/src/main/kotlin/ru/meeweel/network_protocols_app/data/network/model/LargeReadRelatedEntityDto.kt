package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadRelatedEntityDto(
    val entityId: String,
    val relationType: String,
    val title: String,
    val status: String,
    val priority: String,
)
