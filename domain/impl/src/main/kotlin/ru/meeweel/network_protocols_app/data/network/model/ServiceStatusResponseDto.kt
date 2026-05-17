package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ServiceStatusResponseDto(
    val status: String,
    val service: String? = null,
    val transport: List<String> = emptyList(),
    val message: String? = null,
)
