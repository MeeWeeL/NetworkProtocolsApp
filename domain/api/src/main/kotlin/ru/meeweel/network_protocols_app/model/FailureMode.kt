package ru.meeweel.network_protocols_app.model

import kotlinx.serialization.Serializable

@Serializable
enum class FailureMode {
    NONE,
    VALIDATION,
    TIMEOUT,
    UNAVAILABLE,
    BUSINESS_CONFLICT,
    INTERNAL,
}
