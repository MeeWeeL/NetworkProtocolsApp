package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_app.model.FailureMode
import ru.meeweel.network_protocols_app.model.ScenarioType

@Serializable
data class ScenarioRequestDto(
    val requestId: String? = null,
    val correlationId: String? = null,
    val sessionId: String? = null,
    val scenario: ScenarioType? = null,
    val payloadSizeBytes: Int? = null,
    val eventCount: Int = 1,
    val qClass: String? = null,
    val loadProfile: String? = null,
    val failureMode: FailureMode = FailureMode.NONE,
    val metadata: Map<String, String> = emptyMap(),
    val payload: String? = null,
)
