package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PageReadSummaryDto(
    val totalAmount: Double,
    val selectedCount: Int,
    val highPriorityCount: Int,
    val staleCount: Int,
    val warningCount: Int,
)
