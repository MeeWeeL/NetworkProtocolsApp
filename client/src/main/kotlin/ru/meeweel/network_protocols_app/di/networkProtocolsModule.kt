package ru.meeweel.network_protocols_app.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.screen.full_test.FullTestViewModel
import ru.meeweel.network_protocols_app.screen.home.HomeViewModel
import ru.meeweel.network_protocols_app.screen.by_protocols.ProtocolPageViewModel
import ru.meeweel.network_protocols_app.screen.by_scenarios.ScenarioPageViewModel

val networkProtocolsModule = module {
    viewModel { HomeViewModel(endpointService = get()) }
    viewModel { FullTestViewModel(runner = get()) }
    viewModel { (protocol: ProtocolType) ->
        ProtocolPageViewModel(
            protocol = protocol,
            runner = get(),
        )
    }
    viewModel { (scenario: ScenarioType) ->
        ScenarioPageViewModel(
            scenario = scenario,
            runner = get(),
        )
    }
}
