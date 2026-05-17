package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadTimelineEntryDto(
    val eventCode: String,
    val title: String,
    val actor: String,
    val occurredAtEpochMs: Long,
    val status: String,
    val description: String,
)
