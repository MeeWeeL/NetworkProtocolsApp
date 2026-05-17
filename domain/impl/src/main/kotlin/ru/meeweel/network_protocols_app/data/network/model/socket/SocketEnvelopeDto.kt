package ru.meeweel.network_protocols_app.data.network.model.socket

import kotlinx.serialization.Serializable

@Serializable
data class SocketEnvelopeDto(
    val type: SocketEnvelopeTypeDto,
    val command: SocketClientCommandDto? = null,
    val event: SocketServerEventDto? = null,
)
