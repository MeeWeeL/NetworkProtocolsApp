package ru.meeweel.network_protocols_app.data.network.model.graphql

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_app.data.network.model.ScenarioResponseDto

@Serializable
data class GraphQlDataDto(
    val scenario: ScenarioResponseDto? = null,
    val executeScenario: ScenarioResponseDto? = null,
    val subscribeScenario: List<ScenarioResponseDto>? = null,
)
