package ru.meeweel.network_protocols_app.data.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.meeweel.network_protocols_app.data.network.ExperimentBackendClient
import ru.meeweel.network_protocols_app.model.BackendEndpointConfig
import ru.meeweel.network_protocols_app.model.BackendHealthState
import ru.meeweel.network_protocols_app.model.BackendHealthStatus
import ru.meeweel.network_protocols_app.service.BackendEndpointService

class BackendEndpointServiceImpl(
    private val store: BackendEndpointStore,
    private val backendClient: ExperimentBackendClient,
    private val scope: CoroutineScope,
) : BackendEndpointService {

    override val config: StateFlow<BackendEndpointConfig> = store.config

    private val _health = MutableStateFlow(BackendHealthState.idle())
    override val health: StateFlow<BackendHealthState> = _health.asStateFlow()

    init {
        refreshHealth()
    }

    override fun updateHost(host: String) {
        store.updateHost(host)
        refreshHealth()
    }

    override fun updateMeasuredRuns(measuredRuns: Int) {
        store.updateMeasuredRuns(measuredRuns)
    }

    override fun updateReusePersistentConnections(reusePersistentConnections: Boolean) {
        store.updateReusePersistentConnections(reusePersistentConnections)
    }

    override fun refreshHealth() {
        val endpointConfig = config.value
        scope.launch {
            _health.value = BackendHealthState(
                status = BackendHealthStatus.Checking,
                title = "Проверка соединения",
                details = "Проверяется ${endpointConfig.host}:${endpointConfig.httpPort} и gRPC ${endpointConfig.grpcPort}.",
            )
            _health.value = backendClient.checkHealth(endpointConfig)
        }
    }
}
