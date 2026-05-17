package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadParameterDto(
    val key: String,
    val title: String,
    val valueType: String,
    val value: String,
    val unit: String? = null,
    val required: Boolean,
    val source: String,
)
