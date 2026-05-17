package ru.meeweel.network_protocols_app.screen.full_test

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.meeweel.network_protocols_app.core.base.view_model.NpViewModel
import ru.meeweel.network_protocols_app.runner.ExperimentRunner
import ru.meeweel.network_protocols_app.screen.full_test.FullTestContract.Action
import ru.meeweel.network_protocols_app.screen.full_test.FullTestContract.Intent
import ru.meeweel.network_protocols_app.screen.full_test.FullTestContract.State

class FullTestViewModel(
    private val runner: ExperimentRunner,
) : NpViewModel<State, Intent, Action>() {

    init {
        runner.fullTestProgress
            .onEach { progress ->
                produce { copy(progress = progress) }
            }
            .launchIn(this)
    }

    override fun createInitialState(): State = State(
        progress = runner.fullTestProgress.value,
    )

    override fun consumeIntent(intent: Intent) {
        when (intent) {
            Intent.OnClickStart -> runner.startFullTest()
        }
    }
}
