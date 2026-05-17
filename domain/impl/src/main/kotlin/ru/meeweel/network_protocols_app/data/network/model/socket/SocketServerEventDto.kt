package ru.meeweel.network_protocols_app.data.network.model.socket

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_app.data.network.model.ErrorResponseDto
import ru.meeweel.network_protocols_app.data.network.model.ScenarioResponseDto
import ru.meeweel.network_protocols_app.model.ScenarioType

@Serializable
data class SocketServerEventDto(
    val name: String,
    val scenario: ScenarioType? = null,
    val response: ScenarioResponseDto? = null,
    val error: ErrorResponseDto? = null,
    val message: String? = null,
)
