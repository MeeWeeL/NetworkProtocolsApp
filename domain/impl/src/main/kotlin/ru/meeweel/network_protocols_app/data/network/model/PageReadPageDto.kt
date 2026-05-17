package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PageReadPageDto(
    val pageNumber: Int,
    val pageSize: Int,
    val totalItems: Int,
    val nextCursor: String? = null,
    val sortBy: String,
    val appliedFilters: List<String> = emptyList(),
    val summary: PageReadSummaryDto,
    val facets: List<PageReadFacetDto> = emptyList(),
    val items: List<LargeReadPreviewDto> = emptyList(),
)
