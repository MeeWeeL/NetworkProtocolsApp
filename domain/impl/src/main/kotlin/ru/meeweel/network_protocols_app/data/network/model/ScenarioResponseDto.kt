package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType

@Serializable
data class ScenarioResponseDto(
    val requestId: String,
    val correlationId: String,
    val sessionId: String? = null,
    val scenario: ScenarioType,
    val transport: ProtocolType,
    val canonicalOperation: String,
    val status: String,
    val payloadSizeBytes: Int,
    val payloadChecksum: String,
    val sequence: Int? = null,
    val acceptedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val serverProcessingTimeMs: Long,
    val serverProcessingTimeMicros: Long? = null,
    val payload: String? = null,
    val document: LargeReadDocumentDto? = null,
    val preview: LargeReadPreviewDto? = null,
    val page: PageReadPageDto? = null,
    val streamEvent: StreamEventDto? = null,
    val metadata: Map<String, String> = emptyMap(),
)
