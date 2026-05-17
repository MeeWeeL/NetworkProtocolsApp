package ru.meeweel.network_protocols_app.model

data class BackendEndpointConfig(
    val host: String = "192.168.1.140",
    val httpPort: Int = 8080,
    val grpcPort: Int = 9090,
    val measuredRuns: Int = 100,
    val reusePersistentConnections: Boolean = true,
) {
    val httpBaseUrl: String
        get() = "http://$host:$httpPort/"

    val webSocketUrl: String
        get() = "ws://$host:$httpPort/api/ws"

    val graphQlWebSocketUrl: String
        get() = "ws://$host:$httpPort/api/graphql/ws"
}
