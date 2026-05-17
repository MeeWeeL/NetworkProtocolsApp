package ru.meeweel.network_protocols_app.screen.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.meeweel.network_protocols_app.core.designsystem.component.NpButton
import ru.meeweel.network_protocols_app.core.designsystem.component.NpChipButton
import ru.meeweel.network_protocols_app.core.designsystem.component.NpProgressCard
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.TestRunStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkPageScreen(
    title: String,
    subtitle: String,
    progress: TestRunProgress,
    actions: List<String>,
    onClickAction: (Int) -> Unit,
    onClickFullTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reportUi = progress.toBenchmarkReportUi()
    val firstCompletedSeries = progress.completedSeries.firstOrNull()
    val exportReusePersistentConnections = progress.completedSeries
        .map { it.methodology.reusePersistentConnections }
        .distinct()
        .singleOrNull()
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = NpTheme.typography.screenTitle,
            color = NpTheme.colorScheme.textPrimary,
        )
        Text(
            text = subtitle,
            style = NpTheme.typography.body,
            color = NpTheme.colorScheme.textSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            NpProgressCard(
                modifier = Modifier.fillMaxSize(),
                title = progress.title,
                stateLabel = progress.stateLabel,
                details = progress.details,
                progress = progress.progressFraction,
                progressLabel = progress.progressLabel,
                elapsedTimeLabel = progress.elapsedLabel,
                summary = reportUi.summary,
                reportNotes = reportUi.notes,
                reportItems = reportUi.items,
                reportActionsEnabled = progress.status != TestRunStatus.Running &&
                    (reportUi.items.isNotEmpty() || reportUi.notes.isNotEmpty()),
                exportMeasuredRuns = firstCompletedSeries?.methodology?.measuredRuns,
                exportReusePersistentConnections = exportReusePersistentConnections,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEachIndexed { index, label ->
                NpChipButton(
                    text = label,
                    onClick = { onClickAction(index) },
                )
            }
        }
        NpButton(
            text = "Полный тест",
            onClick = onClickFullTest,
        )
    }
}

@Composable
@NpPreview
private fun PreviewBenchmarkPageScreen() {
    NpTheme {
        BenchmarkPageScreen(
            title = "WebSocket",
            subtitle = "Выбери отдельный сценарий или запусти весь набор по протоколу.",
            progress = TestRunProgress(
                title = "WebSocket",
                stateLabel = "Идет выполнение",
                details = "S8 Большой поток • 5 событий получено • h_series",
                currentStep = 7,
                totalSteps = 9,
                status = TestRunStatus.Running,
            ),
            actions = listOf(
                "S1 Короткий ответ",
                "S2 Большой объект",
                "S3 Частичное чтение",
                "S4 Страница списка",
                "S5 Малая запись",
                "S6 Большая запись",
                "S7 Малый поток",
                "S8 Большой поток",
                "S9 Сессия",
            ),
            onClickAction = {},
            onClickFullTest = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
