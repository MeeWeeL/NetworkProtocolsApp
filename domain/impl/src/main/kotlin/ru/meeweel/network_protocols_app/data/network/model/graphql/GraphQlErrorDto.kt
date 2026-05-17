package ru.meeweel.network_protocols_app.data.network.model.graphql

import kotlinx.serialization.Serializable

@Serializable
data class GraphQlErrorDto(
    val message: String,
    val code: String? = null,
)
