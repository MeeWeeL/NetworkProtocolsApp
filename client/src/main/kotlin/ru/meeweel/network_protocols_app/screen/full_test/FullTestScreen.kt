package ru.meeweel.network_protocols_app.screen.full_test

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import org.koin.androidx.compose.koinViewModel
import ru.meeweel.network_protocols_app.core.designsystem.component.NpButton
import ru.meeweel.network_protocols_app.core.designsystem.component.NpProgressCard
import ru.meeweel.network_protocols_app.core.designsystem.component.NpTopBar
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.TestRunStatus
import ru.meeweel.network_protocols_app.screen.common.toBenchmarkReportUi
import ru.meeweel.network_protocols_app.screen.full_test.FullTestContract.Intent
import ru.meeweel.network_protocols_app.screen.full_test.FullTestContract.State

@Composable
fun FullTestScreen(
    navController: NavHostController,
) {
    val viewModel = koinViewModel<FullTestViewModel>()
    val state by viewModel.state.collectAsState()

    Content(
        state = state,
        onClickBack = { navController.popBackStack() },
        onClickStart = { viewModel.intent(Intent.OnClickStart) },
    )
}

@Composable
private fun Content(
    state: State,
    onClickBack: () -> Unit,
    onClickStart: () -> Unit,
) {
    val reportUi = state.progress.toBenchmarkReportUi()
    val firstCompletedSeries = state.progress.completedSeries.firstOrNull()
    val exportReusePersistentConnections = state.progress.completedSeries
        .map { it.methodology.reusePersistentConnections }
        .distinct()
        .singleOrNull()
    Scaffold(
        containerColor = NpTheme.colorScheme.background,
        topBar = {
            NpTopBar(
                title = "Полный тест",
                onClickBack = onClickBack,
            )
        },
        bottomBar = {
            NpButton(
                modifier = Modifier.padding(16.dp),
                text = "Запуск",
                onClick = onClickStart,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            NpProgressCard(
                modifier = Modifier.fillMaxSize(),
                title = state.progress.title,
                stateLabel = state.progress.stateLabel,
                details = state.progress.details,
                progress = state.progress.progressFraction,
                progressLabel = state.progress.progressLabel,
                elapsedTimeLabel = state.progress.elapsedLabel,
                summary = reportUi.summary,
                reportNotes = reportUi.notes,
                reportItems = reportUi.items,
                reportActionsEnabled = state.progress.status != TestRunStatus.Running &&
                    (reportUi.items.isNotEmpty() || reportUi.notes.isNotEmpty()),
                exportMeasuredRuns = firstCompletedSeries?.methodology?.measuredRuns,
                exportReusePersistentConnections = exportReusePersistentConnections,
            )
        }
    }
}

@Preview(
    name = "Full Test Screen",
    showBackground = true,
    backgroundColor = 0xFFF5F2EA,
    widthDp = 412,
    heightDp = 915,
)
@Composable
internal fun FullTestScreenPreview() {
    NpTheme {
        Content(
            state = State(
                progress = TestRunProgress(
                    title = "Полный тест",
                    stateLabel = "Идет выполнение",
                    details = "WebSocket • S8 Большой поток • h_series • 5 событий получено",
                    currentStep = 53,
                    totalSteps = 76,
                    status = TestRunStatus.Running,
                ),
            ),
            onClickBack = {},
            onClickStart = {},
        )
    }
}
