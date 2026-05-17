package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamEventDto(
    val eventId: String,
    val eventType: String,
    val documentId: String,
    val emittedAtEpochMs: Long,
    val revision: Int,
    val priority: String,
    val preview: LargeReadPreviewDto,
    val changedFields: List<String> = emptyList(),
    val relatedItems: List<LargeReadPreviewDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val summary: StreamEventSummaryDto,
)
