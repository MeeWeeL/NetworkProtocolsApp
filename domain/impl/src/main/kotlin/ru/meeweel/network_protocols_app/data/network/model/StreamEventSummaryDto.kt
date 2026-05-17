package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamEventSummaryDto(
    val impactedItems: Int,
    val warningCount: Int,
    val scoreDelta: Double,
    val currentStatus: String,
)
