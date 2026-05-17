package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadAttachmentDto(
    val attachmentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val checksum: String,
    val sourceSystem: String,
)
