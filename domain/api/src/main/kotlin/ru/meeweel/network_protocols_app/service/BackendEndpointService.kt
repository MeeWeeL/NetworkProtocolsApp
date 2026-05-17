package ru.meeweel.network_protocols_app.service

import kotlinx.coroutines.flow.StateFlow
import ru.meeweel.network_protocols_app.model.BackendEndpointConfig
import ru.meeweel.network_protocols_app.model.BackendHealthState

interface BackendEndpointService {
    val config: StateFlow<BackendEndpointConfig>
    val health: StateFlow<BackendHealthState>

    fun updateHost(host: String)
    fun updateMeasuredRuns(measuredRuns: Int)
    fun updateReusePersistentConnections(reusePersistentConnections: Boolean)
    fun refreshHealth()
}
