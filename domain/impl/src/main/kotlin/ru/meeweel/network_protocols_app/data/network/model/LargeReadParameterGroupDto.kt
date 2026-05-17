package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadParameterGroupDto(
    val groupCode: String,
    val groupTitle: String,
    val editable: Boolean,
    val parameters: List<LargeReadParameterDto> = emptyList(),
)
