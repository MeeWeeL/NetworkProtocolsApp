package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadLineItemDto(
    val itemId: String,
    val sku: String,
    val title: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double,
    val availabilityStatus: String,
    val tags: List<String> = emptyList(),
)
