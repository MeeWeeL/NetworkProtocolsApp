package ru.meeweel.network_protocols_app.model

import kotlin.math.roundToLong

/**
 * Итог одной измерительной серии.
 *
 * Простыми словами, это строка отчета по одной паре "протокол + сценарий +
 * режим соединения". Здесь уже собраны успешные запросы, ошибки, медиана,
 * p95/p99, пропускная способность и служебные метрики канала.
 */
data class MeasurementSeriesResult(
    val protocol: ProtocolType,
    val scenario: ScenarioType,
    val methodology: MeasurementMethodologyProfile,
    val successfulRuns: Int,
    val failedRuns: Int,
    val seriesElapsedMs: Long,
    val connectionSetupMs: Long? = null,
    val clientMedianMs: Double?,
    val clientMeanMs: Double?,
    val clientP95Ms: Double?,
    val clientP99Ms: Double?,
    val serverMedianMs: Double?,
    val serverMeanMs: Double?,
    val serverP95Ms: Double?,
    val serverP99Ms: Double?,
    val networkMedianMs: Double?,
    val networkP95Ms: Double?,
    val throughputOpsPerSec: Double?,
    val errorRate: Double,
    val applicability: MeasurementApplicability,
    val connectionMetrics: SeriesConnectionMetrics? = null,
    val deviceMetrics: DeviceSeriesMetrics? = null,
    val requestResults: List<ScenarioExecutionResult> = emptyList(),
    val errorMessages: List<String>,
) {
    val hasUsableMeasurements: Boolean
        get() = successfulRuns > 0

    val connectionModeCode: String
        get() = methodology.connectionModeCode

    val progressDetails: String
        get() = buildString {
            append(scenario.code)
            append(' ')
            append(scenario.shortTitle)
            append(" • ")
            append(connectionModeCode)
            append(" • медиана ")
            append(clientMedianMs.asMetricText())
            append(" • p95 ")
            append(clientP95Ms.asMetricText())
            append(" • ER ")
            append(errorRate.asPercentText())
            connectionMetrics?.let { metrics ->
                append(" • потери ")
                append(metrics.lossRate.asPercentText())
                if (metrics.reconnectCount > 0) {
                    append(" • переподключения ")
                    append(metrics.reconnectCount)
                }
            }
        }

    val summaryLine: String
        get() = buildString {
            append(protocol.title)
            append(" • ")
            append(scenario.code)
            append(' ')
            append(scenario.shortTitle)
            append(" • ")
            append(connectionModeCode)
            append(" • R=")
            append(methodology.measuredRuns)
            append(" • медиана ")
            append(clientMedianMs.asMetricText())
            append(" • p95 ")
            append(clientP95Ms.asMetricText())
            append(" • p99 ")
            append(clientP99Ms.asMetricText())
            append(" • ER ")
            append(errorRate.asPercentText())
            append(" • TP ")
            append(throughputOpsPerSec.asThroughputText())
            connectionMetrics?.let { metrics ->
                append(" • потери ")
                append(metrics.lossRate.asPercentText())
                if (metrics.reconnectCount > 0) {
                    append(" • переподключения ")
                    append(metrics.reconnectCount)
                }
            }
        }

    fun reportBlock(): String {
        return buildString {
            appendAggregateBlock(includeRequestResults = false)
        }
    }

    fun fullReportBlock(): String {
        return buildString {
            appendAggregateBlock(includeRequestResults = true)
        }
    }

    private fun StringBuilder.appendAggregateBlock(includeRequestResults: Boolean) {
        appendLine("${protocol.title} • ${scenario.code} ${scenario.title} • $connectionModeCode")
        appendLine("Методика: ${methodology.methodologyLabel}")
        appendLine("Статистическая применимость: ${applicability.title}")
        appendLine("Успешных измерений: $successfulRuns из ${methodology.measuredRuns}")
        appendLine("Ошибок: $failedRuns из ${methodology.measuredRuns}")
        appendLine("ER: ${errorRate.asPercentText()}")
        appendLine("TP: ${throughputOpsPerSec.asThroughputText()}")
        appendLine("Серия: ${seriesElapsedMs} мс")
        connectionSetupMs?.takeIf { it > 0L }?.let { setupMs ->
            appendLine("Подготовка соединения: ${setupMs} мс")
        }
        appendLine("Клиентская задержка: медиана ${clientMedianMs.asMetricText()}, среднее ${clientMeanMs.asMetricText()}, p95 ${clientP95Ms.asMetricText()}, p99 ${clientP99Ms.asMetricText()}")
        appendLine("Серверное время: медиана ${serverMedianMs.asMetricText()}, среднее ${serverMeanMs.asMetricText()}, p95 ${serverP95Ms.asMetricText()}, p99 ${serverP99Ms.asMetricText()}")
        append("Клиент + сеть: медиана ${networkMedianMs.asMetricText()}, p95 ${networkP95Ms.asMetricText()}")
        connectionMetrics?.let { metrics ->
            appendLine()
            appendLine()
            appendLine("Метрики канала:")
            appendLine("Подготовка соединения: медиана ${metrics.setupMedianMs.asMetricText()}, p95 ${metrics.setupP95Ms.asMetricText()}")
            appendLine("Доставлено событий: ${metrics.receivedEventsTotal} из ${metrics.expectedEventsTotal}")
            appendLine("Потери: ${metrics.lossRate.asPercentText()}")
            appendLine("Переподключения: ${metrics.reconnectCount} (${metrics.reconnectsPerRun.formatTo(3)} на запуск, ${metrics.reconnectsPerMinute.asReconnectRateText()})")
            appendLine("Неожиданные закрытия: ${metrics.unexpectedCloseCount}")
            appendLine("Восстановление: медиана ${metrics.recoveryMedianMs.asMetricText()}, p95 ${metrics.recoveryP95Ms.asMetricText()}")
            if (
                metrics.timeToFirstMedianMs != null ||
                metrics.streamCompletionMedianMs != null ||
                metrics.interEventGapMedianMs != null
            ) {
                appendLine("Старт потока: медиана ${metrics.timeToFirstMedianMs.asMetricText()}, p95 ${metrics.timeToFirstP95Ms.asMetricText()}")
                appendLine("Завершение потока: медиана ${metrics.streamCompletionMedianMs.asMetricText()}, p95 ${metrics.streamCompletionP95Ms.asMetricText()}")
                appendLine("Интервал между событиями: медиана ${metrics.interEventGapMedianMs.asMetricText()}, p95 ${metrics.interEventGapP95Ms.asMetricText()}")
            }
            if (metrics.heartbeatSent > 0 || metrics.heartbeatRttMedianMs != null) {
                appendLine("RTT служебного сигнала: медиана ${metrics.heartbeatRttMedianMs.asMetricText()}, p95 ${metrics.heartbeatRttP95Ms.asMetricText()}")
                appendLine("Сигналы активности: подтверждено ${metrics.heartbeatAcknowledged} из ${metrics.heartbeatSent}, потери ${metrics.heartbeatLossRate.asPercentText()}")
            }
            append("Индекс устойчивости: ${metrics.stabilityIndex.formatTo(3)}")
        }
        deviceMetrics?.takeIf(DeviceSeriesMetrics::hasAnyData)?.let { metrics ->
            appendLine()
            appendLine()
            appendLine("Метрики устройства:")
            appendLine("Замеров телеметрии: ${metrics.sampleCount}")
            appendLine("Процессорное время: ${metrics.cpuTimeDeltaMs.asLongMetricText()}")
            appendLine("Память JVM: изменение ${metrics.javaHeapDeltaKb.asKbText()}, пик ${metrics.javaHeapPeakKb.asKbText()}")
            appendLine("Нативная память: изменение ${metrics.nativeHeapDeltaKb.asKbText()}, пик ${metrics.nativeHeapPeakKb.asKbText()}")
            appendLine("PSS-память: изменение ${metrics.pssDeltaKb.asKbText()}, пик ${metrics.pssPeakKb.asKbText()}")
            appendLine("Расход заряда (ориентировочно): ${metrics.chargeConsumedUah.asUahText()}")
            appendLine("Расход энергии (ориентировочно): ${metrics.energyConsumedNwh.asNwhText()}")
            appendLine("Батарея (грубая оценка): ${metrics.batteryPctDelta.asBatteryPctText()}")
            append("Показатели расхода заряда: ориентировочные; для коротких серий не использовать как самостоятельный критерий.")
        }
        if (includeRequestResults && requestResults.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("Измерения по запросам:")
            requestResults.forEachIndexed { index, request ->
                appendLine(request.exportLine(index + 1))
            }
        }
        if (errorMessages.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("Ошибки серии:")
            errorMessages.forEachIndexed { index, message ->
                append(index + 1)
                append(". ")
                appendLine(message)
            }
        }
    }
}

private fun ScenarioExecutionResult.exportLine(index: Int): String {
    return buildString {
        append(index)
        append(". ид_запроса=")
        append(requestId)
        append("; ид_корреляции=")
        append(correlationId)
        append("; клиент=")
        append(clientDurationMsPrecise.asMetricText())
        append("; сервер=")
        append(serverDurationMsPrecise.asMetricText())
        append("; клиент_и_сеть=")
        append(networkPlusClientDurationMsPrecise.asMetricText())
        append("; ответов=")
        append(responseCount)
        append("; детали=")
        append(details)
        if (auditFields.isNotEmpty()) {
            auditFields.toSortedMap().forEach { (key, value) ->
                append("; ")
                append(key)
                append('=')
                append(value)
            }
        }
    }
}

private fun Double?.asMetricText(): String {
    return this?.let { "${it.formatTo(2)} мс" } ?: "н/д"
}

private fun Long?.asLongMetricText(): String {
    return this?.let { "$it мс" } ?: "н/д"
}

private fun Double?.asThroughputText(): String {
    return this?.let { "${it.formatTo(2)} оп/с" } ?: "н/д"
}

private fun Double?.asPercentText(): String {
    return this?.let { "${(it * 100.0).formatTo(1)} %" } ?: "н/д"
}

private fun Double?.asReconnectRateText(): String {
    return this?.let { "${it.formatTo(3)} переподкл./мин" } ?: "н/д"
}

private fun Long?.asKbText(): String {
    return this?.let { "$it КиБ" } ?: "н/д"
}

private fun Int?.asUahText(): String {
    return this?.let { "$it мкАч" } ?: "н/д"
}

private fun Long?.asNwhText(): String {
    return this?.let { "$it нВтч" } ?: "н/д"
}

private fun Int?.asBatteryPctText(): String {
    return this?.let { "$it %" } ?: "н/д"
}

private fun Double.formatTo(scale: Int): String {
    val factor = 10.0.pow(scale)
    val rounded = (this * factor).roundToLong() / factor
    return "%.${scale}f".format(rounded)
}

private fun Double.pow(scale: Int): Double {
    var value = 1.0
    repeat(scale) {
        value *= this
    }
    return value
}
