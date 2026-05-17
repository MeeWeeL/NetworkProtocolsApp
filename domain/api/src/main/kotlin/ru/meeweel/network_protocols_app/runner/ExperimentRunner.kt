package ru.meeweel.network_protocols_app.runner

import kotlinx.coroutines.flow.StateFlow
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.energy.EnergyBlockRequest

interface ExperimentRunner {
    val fullTestProgress: StateFlow<TestRunProgress>
    val protocolProgress: StateFlow<Map<ProtocolType, TestRunProgress>>
    val scenarioProgress: StateFlow<Map<ScenarioType, TestRunProgress>>

    fun startFullTest()
    fun startProtocolSuite(protocol: ProtocolType)
    fun startProtocolScenario(protocol: ProtocolType, scenario: ScenarioType)
    fun startScenarioSuite(scenario: ScenarioType)
    fun startScenarioProtocol(scenario: ScenarioType, protocol: ProtocolType)
    fun startEnergyBlock(request: EnergyBlockRequest)
}
