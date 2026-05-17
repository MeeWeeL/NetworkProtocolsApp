package ru.meeweel.network_protocols_app.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.koin.androidx.compose.koinViewModel
import ru.meeweel.network_protocols_app.core.base.view_model.ActionCollectorEffect
import ru.meeweel.network_protocols_app.core.designsystem.component.NpButton
import ru.meeweel.network_protocols_app.core.designsystem.component.NpButtonType
import ru.meeweel.network_protocols_app.core.designsystem.component.NpInfoCard
import ru.meeweel.network_protocols_app.core.designsystem.component.NpTextField
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme
import ru.meeweel.network_protocols_app.navigation.NetworkProtocolsDestination
import ru.meeweel.network_protocols_app.model.BackendHealthStatus
import ru.meeweel.network_protocols_app.screen.home.HomeContract.Action
import ru.meeweel.network_protocols_app.screen.home.HomeContract.Intent
import ru.meeweel.network_protocols_app.screen.home.HomeContract.State

@Composable
fun HomeScreen(
    navController: NavHostController,
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsState()

    Content(
        state = state,
        onClickFullTest = { viewModel.intent(Intent.OnClickFullTest) },
        onClickByProtocols = { viewModel.intent(Intent.OnClickByProtocols) },
        onClickByScenarios = { viewModel.intent(Intent.OnClickByScenarios) },
        onHostChanged = { viewModel.intent(Intent.OnHostChanged(it)) },
        onMeasuredRunsChanged = { viewModel.intent(Intent.OnMeasuredRunsChanged(it)) },
        onClickCheckConnection = { viewModel.intent(Intent.OnClickCheckConnection) },
    )

    ActionCollectorEffect(viewModel.action) { action ->
        when (action) {
            Action.NavigateToByProtocols -> navController.navigate(NetworkProtocolsDestination.ByProtocols)
            Action.NavigateToByScenarios -> navController.navigate(NetworkProtocolsDestination.ByScenarios)
            Action.NavigateToFullTest -> navController.navigate(NetworkProtocolsDestination.FullTest)
        }
    }
}

@Composable
private fun Content(
    state: State,
    onClickFullTest: () -> Unit,
    onClickByProtocols: () -> Unit,
    onClickByScenarios: () -> Unit,
    onHostChanged: (String) -> Unit,
    onMeasuredRunsChanged: (String) -> Unit,
    onClickCheckConnection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NpInfoCard(
            title = state.healthTitle,
            body = state.healthDetails,
            caption = "Хост ${state.host} • HTTP 8080 • gRPC 9090",
        )
        NpTextField(
            value = state.host,
            onValueChange = onHostChanged,
            label = "Хост backend",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NpButton(
                text = "192.168.1.140",
                onClick = { onHostChanged("192.168.1.140") },
                modifier = Modifier.weight(1f),
                type = NpButtonType.Secondary,
            )
            NpButton(
                text = "10.0.2.2",
                onClick = { onHostChanged("10.0.2.2") },
                modifier = Modifier.weight(1f),
                type = NpButtonType.Secondary,
            )
        }
        NpTextField(
            value = state.measuredRuns,
            onValueChange = onMeasuredRunsChanged,
            label = "Количество измеряемых запусков",
        )
        NpInfoCard(
            title = "Режимы соединения",
            body = "Полный тест автоматически проверяет оба режима: h_req и h_series. Ручное переключение больше не требуется.",
            caption = "Сравнение строится как протокол × сценарий × режим соединения.",
        )
        NpButton(
            text = "Проверить стенд",
            onClick = onClickCheckConnection,
            type = NpButtonType.Secondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NpButton(
                text = "Полный тест",
                onClick = onClickFullTest,
            )
            NpButton(
                text = "По протоколам",
                onClick = onClickByProtocols,
            )
            NpButton(
                text = "По сценариям",
                onClick = onClickByScenarios,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview(
    name = "Home Screen",
    showBackground = true,
    backgroundColor = 0xFFF5F2EA,
    widthDp = 412,
    heightDp = 915,
)
@Composable
internal fun HomeScreenPreview() {
    NpTheme {
        Content(
            state = State(
                host = "192.168.1.140",
                measuredRuns = "1000",
                healthTitle = "Стенд доступен",
                healthDetails = "HTTP 8080, gRPC 9090 • REST, SOAP, GraphQL, WebSocket, gRPC",
                healthStatus = BackendHealthStatus.Available,
            ),
            onClickFullTest = {},
            onClickByProtocols = {},
            onClickByScenarios = {},
            onHostChanged = {},
            onMeasuredRunsChanged = {},
            onClickCheckConnection = {},
        )
    }
}
