package ru.meeweel.network_protocols_app.screen.home

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.meeweel.network_protocols_app.core.base.view_model.NpViewModel
import ru.meeweel.network_protocols_app.service.BackendEndpointService
import ru.meeweel.network_protocols_app.screen.home.HomeContract.Action
import ru.meeweel.network_protocols_app.screen.home.HomeContract.Intent
import ru.meeweel.network_protocols_app.screen.home.HomeContract.State

class HomeViewModel(
    private val endpointService: BackendEndpointService,
) : NpViewModel<State, Intent, Action>() {

    init {
        endpointService.config
            .onEach { config ->
                produce {
                    copy(
                        host = config.host,
                        measuredRuns = config.measuredRuns.toString(),
                    )
                }
            }
            .launchIn(this)

        endpointService.health
            .onEach { health ->
                produce {
                    copy(
                        healthTitle = health.title,
                        healthDetails = health.details,
                        healthStatus = health.status,
                    )
                }
            }
            .launchIn(this)
    }

    override fun createInitialState(): State = State()

    override fun consumeIntent(intent: Intent) {
        when (intent) {
            Intent.OnClickByProtocols -> action(Action.NavigateToByProtocols)
            Intent.OnClickByScenarios -> action(Action.NavigateToByScenarios)
            Intent.OnClickFullTest -> action(Action.NavigateToFullTest)
            Intent.OnClickCheckConnection -> endpointService.refreshHealth()
            is Intent.OnHostChanged -> endpointService.updateHost(intent.host)
            is Intent.OnMeasuredRunsChanged -> {
                produce { copy(measuredRuns = intent.measuredRuns) }
                intent.measuredRuns.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let(endpointService::updateMeasuredRuns)
            }
        }
    }
}
