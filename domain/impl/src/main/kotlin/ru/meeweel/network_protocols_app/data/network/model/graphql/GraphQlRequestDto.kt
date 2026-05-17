package ru.meeweel.network_protocols_app.data.network.model.graphql

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GraphQlRequestDto(
    val query: String,
    val operationName: String? = null,
    val variables: JsonObject = JsonObject(emptyMap()),
)
