package ru.meeweel.network_protocols_app.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType(
    val title: String,
) {
    REST(title = "REST"),
    SOAP(title = "SOAP"),
    GRAPHQL(title = "GraphQL"),
    GRPC(title = "gRPC"),
    WEBSOCKET(title = "WebSocket"),
}
