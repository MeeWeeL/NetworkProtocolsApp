package ru.meeweel.network_protocols_app.screen.by_protocols

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
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.TestRunStatus
import ru.meeweel.network_protocols_app.screen.by_protocols.ProtocolPageContract.State

@Composable
fun ByProtocolsScreen(
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
        ProtocolPage(protocol = ProtocolType.entries[page])
    },
) {
    val pagerState = rememberPagerState(pageCount = { ProtocolType.entries.size })

    Scaffold(
        containerColor = NpTheme.colorScheme.background,
        topBar = {
            NpTopBar(
                title = "По протоколам",
                onClickBack = onClickBack,
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            key = { page -> ProtocolType.entries[page].name },
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
internal fun ByProtocolsScreenPreview() {
    NpTheme {
        Content(
            onClickBack = {},
            pageContent = { page ->
                val protocol = ProtocolType.entries[page]
                ProtocolPageContent(
                    state = protocolPreviewState(protocol),
                    onClickFullTest = {},
                    onClickScenario = {},
                )
            },
        )
    }
}

private fun protocolPreviewState(protocol: ProtocolType): State {
    val progress = when (protocol) {
        ProtocolType.REST -> TestRunProgress(
            title = "REST",
            stateLabel = "Тест не запущен",
            details = "Готов к запуску S1-S6 в режимах h_req и h_series.",
            currentStep = 0,
            totalSteps = 12,
            status = TestRunStatus.Idle,
        )

        ProtocolType.SOAP -> TestRunProgress(
            title = "SOAP",
            stateLabel = "Идет выполнение",
            details = "S2 Большой объект • h_req • 214 мс клиент • 17 мс сервер",
            currentStep = 3,
            totalSteps = 12,
            status = TestRunStatus.Running,
        )

        ProtocolType.GRAPHQL -> TestRunProgress(
            title = "GraphQL",
            stateLabel = "Тест завершен",
            details = "Сценарии S1-S8 выполнены в обоих режимах соединения.",
            currentStep = 16,
            totalSteps = 16,
            status = TestRunStatus.Completed,
        )

        ProtocolType.WEBSOCKET -> TestRunProgress(
            title = "WebSocket",
            stateLabel = "Идет выполнение",
            details = "S8 Большой поток • h_series • 5 событий получено",
            currentStep = 15,
            totalSteps = 18,
            status = TestRunStatus.Running,
        )

        ProtocolType.GRPC -> TestRunProgress(
            title = "gRPC",
            stateLabel = "Ошибка выполнения",
            details = "Не удалось завершить S9: служебный сигнал не подтвержден.",
            currentStep = 17,
            totalSteps = 18,
            status = TestRunStatus.Error,
        )
    }

    return State(
        protocol = protocol,
        progress = progress,
    )
}
