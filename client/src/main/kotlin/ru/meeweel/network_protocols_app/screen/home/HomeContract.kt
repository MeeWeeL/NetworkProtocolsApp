package ru.meeweel.network_protocols_app.screen.home

import ru.meeweel.network_protocols_app.core.base.view_model.UiAction
import ru.meeweel.network_protocols_app.core.base.view_model.UiIntent
import ru.meeweel.network_protocols_app.core.base.view_model.UiState
import ru.meeweel.network_protocols_app.model.BackendEndpointConfig
import ru.meeweel.network_protocols_app.model.BackendHealthState
import ru.meeweel.network_protocols_app.model.BackendHealthStatus

object HomeContract {
    data class State(
        val host: String = BackendEndpointConfig().host,
        val measuredRuns: String = BackendEndpointConfig().measuredRuns.toString(),
        val healthTitle: String = BackendHealthState.idle().title,
        val healthDetails: String = BackendHealthState.idle().details,
        val healthStatus: BackendHealthStatus = BackendHealthState.idle().status,
    ) : UiState

    sealed interface Intent : UiIntent {
        data object OnClickFullTest : Intent
        data object OnClickByProtocols : Intent
        data object OnClickByScenarios : Intent
        data class OnHostChanged(
            val host: String,
        ) : Intent
        data class OnMeasuredRunsChanged(
            val measuredRuns: String,
        ) : Intent
        data object OnClickCheckConnection : Intent
    }

    sealed interface Action : UiAction {
        data object NavigateToFullTest : Action
        data object NavigateToByProtocols : Action
        data object NavigateToByScenarios : Action
    }
}
