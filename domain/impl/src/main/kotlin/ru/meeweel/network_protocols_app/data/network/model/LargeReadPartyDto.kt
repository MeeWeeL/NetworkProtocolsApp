package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadPartyDto(
    val partyId: String,
    val displayName: String,
    val role: String,
    val organization: String,
    val segment: String,
    val rating: Double,
)
