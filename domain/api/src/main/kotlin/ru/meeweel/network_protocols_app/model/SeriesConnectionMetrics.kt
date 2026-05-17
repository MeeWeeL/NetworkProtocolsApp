package ru.meeweel.network_protocols_app.model

data class SeriesConnectionMetrics(
    val expectedEventsTotal: Int,
    val receivedEventsTotal: Int,
    val setupMedianMs: Double?,
    val setupP95Ms: Double?,
    val lossRate: Double,
    val reconnectCount: Int,
    val reconnectsPerRun: Double,
    val reconnectsPerMinute: Double?,
    val unexpectedCloseCount: Int,
    val recoveryMedianMs: Double?,
    val recoveryP95Ms: Double?,
    val heartbeatSent: Int,
    val heartbeatAcknowledged: Int,
    val heartbeatLossRate: Double?,
    val timeToFirstMedianMs: Double?,
    val timeToFirstP95Ms: Double?,
    val streamCompletionMedianMs: Double?,
    val streamCompletionP95Ms: Double?,
    val interEventGapMedianMs: Double?,
    val interEventGapP95Ms: Double?,
    val heartbeatRttMedianMs: Double?,
    val heartbeatRttP95Ms: Double?,
    val stabilityIndex: Double,
) {
    val lostEventsTotal: Int
        get() = (expectedEventsTotal - receivedEventsTotal).coerceAtLeast(0)
}
