package ru.meeweel.network_protocols_app.screen.full_test

import ru.meeweel.network_protocols_app.core.base.view_model.UiAction
import ru.meeweel.network_protocols_app.core.base.view_model.UiIntent
import ru.meeweel.network_protocols_app.core.base.view_model.UiState
import ru.meeweel.network_protocols_app.model.TestRunProgress

object FullTestContract {
    data class State(
        val progress: TestRunProgress,
    ) : UiState

    sealed interface Intent : UiIntent {
        data object OnClickStart : Intent
    }

    sealed interface Action : UiAction
}
