package ru.meeweel.network_protocols_app.screen.by_scenarios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ProtocolScenarioMatrix
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.TestRunStatus
import ru.meeweel.network_protocols_app.screen.by_scenarios.ScenarioPageContract.Intent
import ru.meeweel.network_protocols_app.screen.by_scenarios.ScenarioPageContract.State
import ru.meeweel.network_protocols_app.screen.common.BenchmarkPageScreen

@Composable
fun ScenarioPage(
    scenario: ScenarioType,
) {
    val viewModel = koinViewModel<ScenarioPageViewModel>(
        key = "scenario-page-${scenario.name}",
        parameters = { parametersOf(scenario) },
    )
    val state by viewModel.state.collectAsState()

    ScenarioPageContent(
        state = state,
        onClickFullTest = { viewModel.intent(Intent.OnClickFullTest) },
        onClickProtocol = { protocol -> viewModel.intent(Intent.OnClickProtocol(protocol)) },
    )
}

@Composable
internal fun ScenarioPageContent(
    state: State,
    onClickFullTest: () -> Unit,
    onClickProtocol: (ProtocolType) -> Unit,
) {
    val supportedProtocols = ProtocolScenarioMatrix.supportedProtocols(state.scenario)

    BenchmarkPageScreen(
        title = "${state.scenario.code} ${state.scenario.title}",
        subtitle = state.scenario.description,
        progress = state.progress,
        actions = supportedProtocols.map { it.title },
        onClickAction = { index -> onClickProtocol(supportedProtocols[index]) },
        onClickFullTest = onClickFullTest,
    )
}

@Preview(
    name = "Scenario Page",
    showBackground = true,
    backgroundColor = 0xFFF5F2EA,
    widthDp = 412,
    heightDp = 915,
)
@Composable
internal fun ScenarioPagePreview() {
    NpTheme {
        ScenarioPageContent(
            state = State(
                scenario = ScenarioType.S5_SMALL_WRITE_ACK,
                progress = TestRunProgress(
                    title = "S5 Малая запись с подтверждением",
                    stateLabel = "Тест завершен",
                    details = "gRPC • 72 мс клиент • 7 мс сервер",
                    currentStep = 5,
                    totalSteps = 5,
                    status = TestRunStatus.Completed,
                ),
            ),
            onClickFullTest = {},
            onClickProtocol = {},
        )
    }
}
