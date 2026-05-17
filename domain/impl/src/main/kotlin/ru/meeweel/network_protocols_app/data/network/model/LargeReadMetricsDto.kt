package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadMetricsDto(
    val summaryScore: Double,
    val riskScore: Double,
    val completenessPct: Double,
    val freshnessHours: Double,
    val responseItems: Int,
    val attachmentBytes: Long,
    val warnings: Int,
)
