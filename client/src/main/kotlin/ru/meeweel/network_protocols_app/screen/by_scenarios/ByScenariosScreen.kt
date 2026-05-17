package ru.meeweel.network_protocols_app.screen.by_scenarios

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.component.NpTopBar
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.TestRunStatus
import ru.meeweel.network_protocols_app.screen.by_scenarios.ScenarioPageContract.State

@Composable
fun ByScenariosScreen(
    navController: NavHostController,
) {
    Content(
        onClickBack = { navController.popBackStack() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Content(
    onClickBack: () -> Unit,
    pageContent: @Composable (Int) -> Unit = { page ->
        ScenarioPage(scenario = ScenarioType.entries[page])
    },
) {
    val pagerState = rememberPagerState(pageCount = { ScenarioType.entries.size })

    Scaffold(
        containerColor = NpTheme.colorScheme.background,
        topBar = {
            NpTopBar(
                title = "По сценариям",
                onClickBack = onClickBack,
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            key = { page -> ScenarioType.entries[page].name },
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 32.dp,
                bottom = 16.dp,
            ),
        ) { page ->
            pageContent(page)
        }
    }
}

@Composable
@NpPreview
internal fun ByScenariosScreenPreview() {
    NpTheme {
        Content(
            onClickBack = {},
            pageContent = { page ->
                val scenario = ScenarioType.entries[page]
                ScenarioPageContent(
                    state = scenarioPreviewState(scenario),
                    onClickFullTest = {},
                    onClickProtocol = {},
                )
            },
        )
    }
}

private fun scenarioPreviewState(scenario: ScenarioType): State {
    val progress = when (scenario) {
        ScenarioType.S1_SHORT_READ -> TestRunProgress(
            title = "S1 Короткий ответ",
            stateLabel = "Тест завершен",
            details = "${ProtocolType.REST.title} • 42 мс клиент • 5 мс сервер",
            currentStep = 5,
            totalSteps = 5,
            status = TestRunStatus.Completed,
        )

        ScenarioType.S2_LARGE_READ -> TestRunProgress(
            title = "S2 Полный большой объект",
            stateLabel = "Идет выполнение",
            details = "${ProtocolType.SOAP.title} • 211 мс клиент • 18 мс сервер",
            currentStep = 2,
            totalSteps = 5,
            status = TestRunStatus.Running,
        )

        ScenarioType.S3_PARTIAL_LARGE_READ -> TestRunProgress(
            title = "S3 Частичное чтение объекта",
            stateLabel = "Тест завершен",
            details = "${ProtocolType.GRAPHQL.title} • 38 мс клиент • 6 мс сервер",
            currentStep = 5,
            totalSteps = 5,
            status = TestRunStatus.Completed,
        )

        ScenarioType.S4_PAGE_READ -> TestRunProgress(
            title = "S4 Страница списка",
            stateLabel = "Тест завершен",
            details = "${ProtocolType.REST.title} • 84 мс клиент • 9 мс сервер",
            currentStep = 5,
            totalSteps = 5,
            status = TestRunStatus.Completed,
        )

        ScenarioType.S5_SMALL_WRITE_ACK -> TestRunProgress(
            title = "S5 Малая запись с подтверждением",
            stateLabel = "Тест завершен",
            details = "${ProtocolType.GRPC.title} • 72 мс клиент • 7 мс сервер",
            currentStep = 5,
            totalSteps = 5,
            status = TestRunStatus.Completed,
        )

        ScenarioType.S6_LARGE_WRITE_ACK -> TestRunProgress(
            title = "S6 Большая запись с подтверждением",
            stateLabel = "Идет выполнение",
            details = "${ProtocolType.GRPC.title} • 118 мс клиент • 11 мс сервер",
            currentStep = 3,
            totalSteps = 5,
            status = TestRunStatus.Running,
        )

        ScenarioType.S7_EVENT_STREAM -> TestRunProgress(
            title = "S7 Поток малых событий",
            stateLabel = "Идет выполнение",
            details = "${ProtocolType.WEBSOCKET.title} • 9 событий получено",
            currentStep = 4,
            totalSteps = 5,
            status = TestRunStatus.Running,
        )

        ScenarioType.S8_HEAVY_EVENT_STREAM -> TestRunProgress(
            title = "S8 Поток больших событий",
            stateLabel = "Идет выполнение",
            details = "${ProtocolType.GRPC.title} • 5 событий получено",
            currentStep = 2,
            totalSteps = 5,
            status = TestRunStatus.Running,
        )

        ScenarioType.S9_LONG_SESSION -> TestRunProgress(
            title = "S9 Длительная сессия",
            stateLabel = "Ошибка выполнения",
            details = "${ProtocolType.WEBSOCKET.title} • служебный сигнал не подтвержден",
            currentStep = 3,
            totalSteps = 5,
            status = TestRunStatus.Error,
        )
    }

    return State(
        scenario = scenario,
        progress = progress,
    )
}
