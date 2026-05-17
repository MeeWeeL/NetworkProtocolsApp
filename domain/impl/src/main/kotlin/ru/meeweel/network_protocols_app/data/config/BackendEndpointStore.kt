package ru.meeweel.network_protocols_app.data.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.meeweel.network_protocols_app.model.BackendEndpointConfig
import androidx.core.content.edit

class BackendEndpointStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<BackendEndpointConfig> = _config.asStateFlow()

    fun updateHost(host: String) {
        val normalizedHost = host.trim().ifBlank { DEFAULT_HOST }
        preferences.edit {
            putString(KEY_HOST, normalizedHost)
        }
        _config.value = _config.value.copy(host = normalizedHost)
    }

    fun updateMeasuredRuns(measuredRuns: Int) {
        val normalizedMeasuredRuns = measuredRuns.coerceIn(MIN_MEASURED_RUNS, MAX_MEASURED_RUNS)
        preferences.edit {
            putInt(KEY_MEASURED_RUNS, normalizedMeasuredRuns)
        }
        _config.value = _config.value.copy(measuredRuns = normalizedMeasuredRuns)
    }

    fun updateReusePersistentConnections(reusePersistentConnections: Boolean) {
        preferences.edit {
            putBoolean(KEY_REUSE_PERSISTENT_CONNECTIONS, reusePersistentConnections)
        }
        _config.value = _config.value.copy(reusePersistentConnections = reusePersistentConnections)
    }

    private fun loadConfig(): BackendEndpointConfig {
        val storedHost = preferences.getString(KEY_HOST, null)?.trim().orEmpty()
        val resolvedHost = when (storedHost) {
            "",
            LEGACY_EMULATOR_HOST,
            -> DEFAULT_HOST

            else -> storedHost
        }
        if (resolvedHost != storedHost) {
            preferences.edit {
                putString(KEY_HOST, resolvedHost)
            }
        }
        val resolvedMeasuredRuns = preferences.getInt(KEY_MEASURED_RUNS, DEFAULT_MEASURED_RUNS)
            .coerceIn(MIN_MEASURED_RUNS, MAX_MEASURED_RUNS)
        val reusePersistentConnections = preferences.getBoolean(
            KEY_REUSE_PERSISTENT_CONNECTIONS,
            DEFAULT_REUSE_PERSISTENT_CONNECTIONS,
        )
        return BackendEndpointConfig(
            host = resolvedHost,
            measuredRuns = resolvedMeasuredRuns,
            reusePersistentConnections = reusePersistentConnections,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "network_protocols_app"
        const val KEY_HOST = "backend_host"
        const val KEY_MEASURED_RUNS = "measured_runs"
        const val KEY_REUSE_PERSISTENT_CONNECTIONS = "reuse_persistent_connections"
        const val DEFAULT_HOST = "192.168.1.140"
        const val LEGACY_EMULATOR_HOST = "10.0.2.2"
        const val DEFAULT_MEASURED_RUNS = 1000
        const val DEFAULT_REUSE_PERSISTENT_CONNECTIONS = true
        const val MIN_MEASURED_RUNS = 1
        const val MAX_MEASURED_RUNS = 10_000
    }
}
