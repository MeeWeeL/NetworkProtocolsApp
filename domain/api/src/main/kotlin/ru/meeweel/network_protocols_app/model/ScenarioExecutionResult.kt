package ru.meeweel.network_protocols_app.model

/**
 * Результат одного обращения к backend.
 *
 * Это самый маленький "кирпичик" статистики: клиент замерил время на своей
 * стороне, backend сообщил свое время обработки, а разница дает грубую оценку
 * клиентской обвязки и передачи по сети.
 */
data class ScenarioExecutionResult(
    val protocol: ProtocolType,
    val scenario: ScenarioType,
    val clientDurationMs: Long,
    val serverDurationMs: Long,
    val clientDurationMicros: Long = clientDurationMs * 1_000L,
    val serverDurationMicros: Long = serverDurationMs * 1_000L,
    val responseCount: Int,
    val requestId: String,
    val correlationId: String,
    val details: String,
    val connectionTelemetry: ScenarioConnectionTelemetry? = null,
    val auditFields: Map<String, String> = emptyMap(),
) {
    val networkPlusClientDurationMs: Long
        get() = (clientDurationMs - serverDurationMs).coerceAtLeast(0L)

    val clientDurationMsPrecise: Double
        get() = clientDurationMicros.toDouble() / 1_000.0

    val serverDurationMsPrecise: Double
        get() = serverDurationMicros.toDouble() / 1_000.0

    val networkPlusClientDurationMsPrecise: Double
        get() = (clientDurationMicros - serverDurationMicros)
            .coerceAtLeast(0L)
            .toDouble() / 1_000.0
}
