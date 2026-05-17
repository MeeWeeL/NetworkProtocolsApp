package ru.meeweel.network_protocols_app.screen.by_scenarios

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.meeweel.network_protocols_app.core.base.view_model.NpViewModel
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.runner.ExperimentRunner
import ru.meeweel.network_protocols_app.screen.by_scenarios.ScenarioPageContract.Action
import ru.meeweel.network_protocols_app.screen.by_scenarios.ScenarioPageContract.Intent
import ru.meeweel.network_protocols_app.screen.by_scenarios.ScenarioPageContract.State

class ScenarioPageViewModel(
    private val scenario: ScenarioType,
    private val runner: ExperimentRunner,
) : NpViewModel<State, Intent, Action>() {

    init {
        runner.scenarioProgress
            .map { it.getValue(scenario) }
            .onEach { progress ->
                produce { copy(progress = progress) }
            }
            .launchIn(this)
    }

    override fun createInitialState(): State = State(
        scenario = scenario,
        progress = runner.scenarioProgress.value.getValue(scenario),
    )

    override fun consumeIntent(intent: Intent) {
        when (intent) {
            Intent.OnClickFullTest -> runner.startScenarioSuite(scenario)
            is Intent.OnClickProtocol -> runner.startScenarioProtocol(scenario, intent.protocol)
        }
    }
}
