package ru.meeweel.network_protocols_app.screen.common

import ru.meeweel.network_protocols_app.core.designsystem.component.NpProgressReportItem
import ru.meeweel.network_protocols_app.model.MeasurementSeriesResult
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.TestRunStatus

internal data class BenchmarkReportUi(
    val summary: String?,
    val notes: List<String>,
    val items: List<NpProgressReportItem>,
)

internal fun TestRunProgress.toBenchmarkReportUi(): BenchmarkReportUi {
    val includeCompletedItems = status != TestRunStatus.Running
    return BenchmarkReportUi(
        summary = completedSeries.toWinnerSummary(),
        notes = reportNotes,
        items = if (includeCompletedItems) {
            completedSeries.map { result ->
                NpProgressReportItem(
                    title = "${result.protocol.title} • ${result.scenario.code} ${result.scenario.title} • ${result.connectionModeCode}",
                    summary = result.cardSummary(),
                    detailsProvider = { result.reportBlockBody() },
                    fullDetailsProvider = { result.fullReportBlockBody() },
                )
            }
        } else {
            emptyList()
        },
    )
}

private fun List<MeasurementSeriesResult>.toWinnerSummary(): String? {
    val comparableGroups = groupBy { result ->
        result.scenario to result.connectionModeCode
    }
        .filterValues { results -> results.map(MeasurementSeriesResult::protocol).distinct().size > 1 }
    if (comparableGroups.isEmpty()) return null
    val parts = ScenarioType.entries.flatMap { scenario ->
        listOf("h_req", "h_series").mapNotNull { mode ->
            val results = comparableGroups[scenario to mode] ?: return@mapNotNull null
            val winner = results.minWithOrNull(seriesComparator(scenario)) ?: return@mapNotNull null
            "${scenario.code} $mode — ${winner.protocol.title} (${winner.winnerReason()})"
        }
    }
    if (parts.isEmpty()) return null
    return buildString {
        append("Текущие лидеры по измеряемым характеристикам: ")
        append(parts.joinToString(separator = "; "))
        append('.')
    }
}

private fun seriesComparator(scenario: ScenarioType): Comparator<MeasurementSeriesResult> {
    return Comparator { left, right ->
        when (scenario) {
            ScenarioType.S1_SHORT_READ,
            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            -> compareScenarioChains(
                left = left,
                right = right,
                minimizeKeys = listOf(
                    { it.errorRate },
                    { it.clientP95Ms },
                    { it.clientMedianMs },
                ),
                maximizeKeys = listOf(
                    { it.throughputOpsPerSec },
                ),
            )

            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            -> compareScenarioChains(
                left = left,
                right = right,
                minimizeKeys = listOf(
                    { it.errorRate },
                    { it.clientP95Ms },
                    { it.clientMedianMs },
                ),
                maximizeKeys = listOf(
                    { it.throughputOpsPerSec },
                ),
                maximizeFirst = true,
            )

            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> compareScenarioChains(
                left = left,
                right = right,
                minimizeKeys = listOf(
                    { it.errorRate },
                    { it.connectionMetrics?.lossRate },
                    { it.connectionMetrics?.streamCompletionP95Ms },
                    { it.connectionMetrics?.timeToFirstP95Ms },
                    { it.connectionMetrics?.interEventGapP95Ms },
                    { it.clientP95Ms },
                    { it.clientMedianMs },
                ),
                maximizeKeys = listOf(
                    { it.connectionMetrics?.stabilityIndex },
                    { it.throughputOpsPerSec },
                ),
            )

            ScenarioType.S9_LONG_SESSION -> compareScenarioChains(
                left = left,
                right = right,
                minimizeKeys = listOf(
                    { it.errorRate },
                    { it.connectionMetrics?.heartbeatLossRate },
                    { it.connectionMetrics?.lossRate },
                    { it.connectionMetrics?.heartbeatRttP95Ms },
                    { it.clientP95Ms },
                    { it.clientMedianMs },
                ),
                maximizeKeys = listOf(
                    { it.connectionMetrics?.stabilityIndex },
                    { it.throughputOpsPerSec },
                ),
            )
        }
    }
}

private fun compareScenarioChains(
    left: MeasurementSeriesResult,
    right: MeasurementSeriesResult,
    minimizeKeys: List<(MeasurementSeriesResult) -> Double?>,
    maximizeKeys: List<(MeasurementSeriesResult) -> Double?>,
    maximizeFirst: Boolean = false,
): Int {
    if (maximizeFirst) {
        maximizeKeys.forEach { selector ->
            compareDescending(selector(left), selector(right)).takeIf { it != 0 }?.let { return it }
        }
    }
    minimizeKeys.forEach { selector ->
        compareAscending(selector(left), selector(right)).takeIf { it != 0 }?.let { return it }
    }
    maximizeKeys.forEach { selector ->
        compareDescending(selector(left), selector(right)).takeIf { it != 0 }?.let { return it }
    }
    return 0
}

private fun compareAscending(
    left: Double?,
    right: Double?,
): Int {
    val normalizedLeft = left ?: Double.POSITIVE_INFINITY
    val normalizedRight = right ?: Double.POSITIVE_INFINITY
    return normalizedLeft.compareTo(normalizedRight)
}

private fun compareDescending(
    left: Double?,
    right: Double?,
): Int {
    val normalizedLeft = left ?: Double.NEGATIVE_INFINITY
    val normalizedRight = right ?: Double.NEGATIVE_INFINITY
    return normalizedRight.compareTo(normalizedLeft)
}

private fun MeasurementSeriesResult.cardSummary(): String {
    return buildString {
        append(connectionModeCode)
        append(" • ")
        append("R=")
        append(methodology.measuredRuns)
        append(" • медиана ")
        append(clientMedianMs.asMetricText())
        append(" • p95 ")
        append(clientP95Ms.asMetricText())
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
}

private fun MeasurementSeriesResult.reportBlockBody(): String {
    return reportBlock()
        .lineSequence()
        .drop(1)
        .joinToString(separator = "\n")
}

private fun MeasurementSeriesResult.fullReportBlockBody(): String {
    return fullReportBlock()
        .lineSequence()
        .drop(1)
        .joinToString(separator = "\n")
}

private fun MeasurementSeriesResult.winnerReason(): String {
    return when (scenario) {
        ScenarioType.S1_SHORT_READ,
        ScenarioType.S2_LARGE_READ,
        ScenarioType.S3_PARTIAL_LARGE_READ,
        ScenarioType.S4_PAGE_READ,
        -> "p95 ${clientP95Ms.asMetricText()}"

        ScenarioType.S5_SMALL_WRITE_ACK,
        ScenarioType.S6_LARGE_WRITE_ACK,
        ->
            "TP ${throughputOpsPerSec.asThroughputText()}"

        ScenarioType.S7_EVENT_STREAM,
        ScenarioType.S8_HEAVY_EVENT_STREAM,
        -> "завершение потока p95 ${connectionMetrics?.streamCompletionP95Ms.asMetricText()}"

        ScenarioType.S9_LONG_SESSION ->
            "сигнал p95 ${connectionMetrics?.heartbeatRttP95Ms.asMetricText()}"
    }
}

private fun Double?.asMetricText(): String {
    return this?.let { "${"%.2f".format(it)} мс" } ?: "н/д"
}

private fun Double?.asPercentText(): String {
    return this?.let { "${"%.1f".format(it * 100.0)} %" } ?: "н/д"
}

private fun Double?.asThroughputText(): String {
    return this?.let { "${"%.2f".format(it)} оп/с" } ?: "н/д"
}
