package ru.meeweel.network_protocols_app.data.network.model.socket

import kotlinx.serialization.Serializable

@Serializable
enum class SocketCommandTypeDto {
    START_SCENARIO,
    HEARTBEAT,
    CLOSE_SESSION,
}
