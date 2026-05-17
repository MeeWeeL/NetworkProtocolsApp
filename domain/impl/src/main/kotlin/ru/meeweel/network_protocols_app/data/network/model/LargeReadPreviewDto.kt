package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadPreviewDto(
    val documentId: String,
    val title: String,
    val status: String,
    val primaryBadge: String,
    val summaryScore: Double,
)
