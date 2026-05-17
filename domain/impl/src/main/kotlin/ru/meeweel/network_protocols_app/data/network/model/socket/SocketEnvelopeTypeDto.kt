package ru.meeweel.network_protocols_app.data.network.model.socket

import kotlinx.serialization.Serializable

@Serializable
enum class SocketEnvelopeTypeDto {
    CLIENT_COMMAND,
    SERVER_EVENT,
}
