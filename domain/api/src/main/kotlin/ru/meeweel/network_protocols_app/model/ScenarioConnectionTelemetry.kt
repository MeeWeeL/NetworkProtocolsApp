package ru.meeweel.network_protocols_app.model

data class ScenarioConnectionTelemetry(
    val expectedEvents: Int,
    val receivedEvents: Int,
    val reconnectCount: Int = 0,
    val unexpectedCloseCount: Int = 0,
    val recoveryDurationsMs: List<Long> = emptyList(),
    val heartbeatSent: Int = 0,
    val heartbeatAcknowledged: Int = 0,
    val timeToFirstEventMs: Long? = null,
    val streamCompletionMs: Long? = null,
    val interEventGapsMs: List<Long> = emptyList(),
    val heartbeatRttsMs: List<Long> = emptyList(),
) {
    val lostEvents: Int
        get() = (expectedEvents - receivedEvents).coerceAtLeast(0)

    val lossRate: Double
        get() = when {
            expectedEvents <= 0 -> 0.0
            else -> lostEvents.toDouble() / expectedEvents.toDouble()
        }

    val heartbeatLossRate: Double?
        get() = heartbeatSent.takeIf { it > 0 }?.let { sent ->
            ((sent - heartbeatAcknowledged).coerceAtLeast(0)).toDouble() / sent.toDouble()
        }
}
