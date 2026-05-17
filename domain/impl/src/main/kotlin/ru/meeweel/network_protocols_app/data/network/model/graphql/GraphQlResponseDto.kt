package ru.meeweel.network_protocols_app.data.network.model.graphql

import kotlinx.serialization.Serializable

@Serializable
data class GraphQlResponseDto(
    val data: GraphQlDataDto? = null,
    val errors: List<GraphQlErrorDto> = emptyList(),
)
