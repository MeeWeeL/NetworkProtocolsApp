package ru.meeweel.network_protocols_app.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface NetworkProtocolsDestination {
    @Serializable
    data object Home : NetworkProtocolsDestination

    @Serializable
    data object FullTest : NetworkProtocolsDestination

    @Serializable
    data object ByProtocols : NetworkProtocolsDestination

    @Serializable
    data object ByScenarios : NetworkProtocolsDestination
}
