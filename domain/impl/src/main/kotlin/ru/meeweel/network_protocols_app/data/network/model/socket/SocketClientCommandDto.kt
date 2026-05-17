package ru.meeweel.network_protocols_app.data.network.model.socket

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_app.data.network.model.ScenarioRequestDto
import ru.meeweel.network_protocols_app.model.ScenarioType

@Serializable
data class SocketClientCommandDto(
    val command: SocketCommandTypeDto,
    val scenario: ScenarioType? = null,
    val request: ScenarioRequestDto? = null,
)
