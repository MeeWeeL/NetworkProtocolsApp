package ru.meeweel.network_protocols_app.model.energy

import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType

data class EnergyBlockRequest(
    val blockId: String,
    val kind: EnergyBlockKind,
    val protocol: ProtocolType?,
    val scenario: ScenarioType?,
    val connectionMode: EnergyConnectionMode,
    val durationSeconds: Long,
    val backendHost: String?,
    val httpPort: Int?,
    val grpcPort: Int?,
) {
    val normalizedDurationSeconds: Long
        get() = durationSeconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)

    companion object {
        const val MIN_DURATION_SECONDS = 30L
        const val MAX_DURATION_SECONDS = 3_600L
    }
}
