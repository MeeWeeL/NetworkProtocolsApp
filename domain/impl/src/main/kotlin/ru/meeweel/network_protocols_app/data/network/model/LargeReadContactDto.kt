package ru.meeweel.network_protocols_app.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadContactDto(
    val kind: String,
    val label: String,
    val value: String,
    val preferred: Boolean,
    val availability: String,
)
