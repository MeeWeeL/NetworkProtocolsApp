package ru.meeweel.network_protocols_app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import org.koin.core.context.GlobalContext
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme
import ru.meeweel.network_protocols_app.model.TestRunStatus
import ru.meeweel.network_protocols_app.navigation.NetworkProtocolsNavHost
import ru.meeweel.network_protocols_app.runner.ExperimentRunner

@Composable
fun NetworkProtocolsApp() {
    KeepScreenOnWhileBenchmarkRunning()
    NpTheme {
        Surface(
            color = NpTheme.colorScheme.background,
        ) {
            NetworkProtocolsNavHost()
        }
    }
}

@Composable
private fun KeepScreenOnWhileBenchmarkRunning() {
    val experimentRunner = remember { GlobalContext.get().get<ExperimentRunner>() }
    val fullTestProgress by experimentRunner.fullTestProgress.collectAsState()
    val protocolProgress by experimentRunner.protocolProgress.collectAsState()
    val scenarioProgress by experimentRunner.scenarioProgress.collectAsState()
    val keepScreenOn = fullTestProgress.status == TestRunStatus.Running ||
        protocolProgress.values.any { it.status == TestRunStatus.Running } ||
        scenarioProgress.values.any { it.status == TestRunStatus.Running }
    val view = LocalView.current

    SideEffect {
        view.keepScreenOn = keepScreenOn
    }

    DisposableEffect(view) {
        onDispose {
            view.keepScreenOn = false
        }
    }
}
