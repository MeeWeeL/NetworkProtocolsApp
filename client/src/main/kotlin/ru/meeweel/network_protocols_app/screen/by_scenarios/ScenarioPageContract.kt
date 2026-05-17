package ru.meeweel.network_protocols_app.screen.by_scenarios

import ru.meeweel.network_protocols_app.core.base.view_model.UiAction
import ru.meeweel.network_protocols_app.core.base.view_model.UiIntent
import ru.meeweel.network_protocols_app.core.base.view_model.UiState
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.model.TestRunProgress

object ScenarioPageContract {
    data class State(
        val scenario: ScenarioType,
        val progress: TestRunProgress,
    ) : UiState

    sealed interface Intent : UiIntent {
        data object OnClickFullTest : Intent
        data class OnClickProtocol(
            val protocol: ProtocolType,
        ) : Intent
    }

    sealed interface Action : UiAction
}
