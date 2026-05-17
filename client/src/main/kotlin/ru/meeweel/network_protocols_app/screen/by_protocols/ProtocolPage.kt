package ru.meeweel.network_protocols_app.screen.by_protocols

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
import ru.meeweel.network_protocols_app.screen.by_protocols.ProtocolPageContract.Intent
import ru.meeweel.network_protocols_app.screen.by_protocols.ProtocolPageContract.State
import ru.meeweel.network_protocols_app.screen.common.BenchmarkPageScreen

@Composable
fun ProtocolPage(
    protocol: ProtocolType,
) {
    val viewModel = koinViewModel<ProtocolPageViewModel>(
        key = "protocol-page-${protocol.name}",
        parameters = { parametersOf(protocol) },
    )
    val state by viewModel.state.collectAsState()

    ProtocolPageContent(
        state = state,
        onClickFullTest = { viewModel.intent(Intent.OnClickFullTest) },
        onClickScenario = { scenario -> viewModel.intent(Intent.OnClickScenario(scenario)) },
    )
}

@Composable
internal fun ProtocolPageContent(
    state: State,
    onClickFullTest: () -> Unit,
    onClickScenario: (ScenarioType) -> Unit,
) {
    val supportedScenarios = ProtocolScenarioMatrix.supportedScenarios(state.protocol)

    BenchmarkPageScreen(
        title = state.protocol.title,
        subtitle = "Выбери отдельный сценарий или запусти весь набор по протоколу.",
        progress = state.progress,
        actions = supportedScenarios.map { "${it.code} ${it.shortTitle}" },
        onClickAction = { index -> onClickScenario(supportedScenarios[index]) },
        onClickFullTest = onClickFullTest,
    )
}

@Preview(
    name = "Protocol Page",
    showBackground = true,
    backgroundColor = 0xFFF5F2EA,
    widthDp = 412,
    heightDp = 915,
)
@Composable
internal fun ProtocolPagePreview() {
    NpTheme {
        ProtocolPageContent(
            state = State(
                protocol = ProtocolType.WEBSOCKET,
                progress = TestRunProgress(
                    title = "WebSocket",
                    stateLabel = "Идет выполнение",
                    details = "S8 Большой поток • h_series • 5 событий получено",
                    currentStep = 15,
                    totalSteps = 18,
                    status = TestRunStatus.Running,
                ),
            ),
            onClickFullTest = {},
            onClickScenario = {},
        )
    }
}
