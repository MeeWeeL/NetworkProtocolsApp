package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val correlationId: String,
    val code: String,
    val message: String,
)
