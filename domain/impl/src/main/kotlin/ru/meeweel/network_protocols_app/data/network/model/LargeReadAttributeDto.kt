package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadAttributeDto(
    val code: String,
    val name: String,
    val value: String,
    val unit: String? = null,
    val category: String,
    val searchable: Boolean,
)
