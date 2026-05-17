package ru.meeweel.network_protocols_app.screen.by_protocols

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.meeweel.network_protocols_app.core.base.view_model.NpViewModel
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.runner.ExperimentRunner
import ru.meeweel.network_protocols_app.screen.by_protocols.ProtocolPageContract.Action
import ru.meeweel.network_protocols_app.screen.by_protocols.ProtocolPageContract.Intent
import ru.meeweel.network_protocols_app.screen.by_protocols.ProtocolPageContract.State

class ProtocolPageViewModel(
    private val protocol: ProtocolType,
    private val runner: ExperimentRunner,
) : NpViewModel<State, Intent, Action>() {

    init {
        runner.protocolProgress
            .map { it.getValue(protocol) }
            .onEach { progress ->
                produce { copy(progress = progress) }
            }
            .launchIn(this)
    }

    override fun createInitialState(): State = State(
        protocol = protocol,
        progress = runner.protocolProgress.value.getValue(protocol),
    )

    override fun consumeIntent(intent: Intent) {
        when (intent) {
            Intent.OnClickFullTest -> runner.startProtocolSuite(protocol)
            is Intent.OnClickScenario -> runner.startProtocolScenario(protocol, intent.scenario)
        }
    }
}
