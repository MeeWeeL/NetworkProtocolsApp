package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadDocumentDto(
    val documentId: String,
    val externalId: String,
    val revision: Int,
    val generatedAtEpochMs: Long,
    val locale: String,
    val currency: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val status: String,
    val owner: LargeReadPartyDto,
    val contacts: List<LargeReadContactDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
    val attributes: List<LargeReadAttributeDto> = emptyList(),
    val parameterGroups: List<LargeReadParameterGroupDto> = emptyList(),
    val lineItems: List<LargeReadLineItemDto> = emptyList(),
    val relatedEntities: List<LargeReadRelatedEntityDto> = emptyList(),
    val attachments: List<LargeReadAttachmentDto> = emptyList(),
    val timeline: List<LargeReadTimelineEntryDto> = emptyList(),
    val metrics: LargeReadMetricsDto,
    val notes: List<String> = emptyList(),
    val narrative: String,
)
