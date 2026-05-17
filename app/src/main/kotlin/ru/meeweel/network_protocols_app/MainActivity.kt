package ru.meeweel.network_protocols_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.core.context.GlobalContext
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.model.energy.EnergyBlockKind
import ru.meeweel.network_protocols_app.model.energy.EnergyBlockRequest
import ru.meeweel.network_protocols_app.model.energy.EnergyConnectionMode
import ru.meeweel.network_protocols_app.runner.ExperimentRunner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkProtocolsApp()
        }
        handleAutomationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutomationIntent(intent)
    }

    private fun handleAutomationIntent(intent: Intent?) {
        val request = intent?.toEnergyBlockRequest() ?: return
        GlobalContext.get()
            .get<ExperimentRunner>()
            .startEnergyBlock(request)
    }
}

private fun Intent.toEnergyBlockRequest(): EnergyBlockRequest? {
    val actionMatches = action == ACTION_ENERGY_BLOCK
    val modeMatches = stringExtra(EXTRA_RUN_MODE)?.equals(RUN_MODE_ENERGY, ignoreCase = true) == true
    if (!actionMatches && !modeMatches) return null

    val kind = EnergyBlockKind.fromExternal(
        stringExtra(EXTRA_ENERGY_KIND) ?: stringExtra(EXTRA_KIND),
    ) ?: EnergyBlockKind.Workload
    val protocol = protocolExtra(EXTRA_PROTOCOL)
    val scenario = scenarioExtra(EXTRA_SCENARIO)
    val connectionMode = EnergyConnectionMode.fromExternal(
        stringExtra(EXTRA_CONNECTION_MODE) ?: stringExtra(EXTRA_MODE),
    ) ?: EnergyConnectionMode.HSeries
    val blockId = stringExtra(EXTRA_BLOCK_ID)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "energy_${System.currentTimeMillis()}"

    return EnergyBlockRequest(
        blockId = blockId,
        kind = kind,
        protocol = protocol,
        scenario = scenario,
        connectionMode = connectionMode,
        durationSeconds = longExtra(EXTRA_DURATION_SECONDS, DEFAULT_DURATION_SECONDS),
        backendHost = stringExtra(EXTRA_BACKEND_HOST) ?: stringExtra(EXTRA_HOST),
        httpPort = intExtraOrNull(EXTRA_HTTP_PORT),
        grpcPort = intExtraOrNull(EXTRA_GRPC_PORT),
    )
}

private fun Intent.protocolExtra(key: String): ProtocolType? {
    val rawValue = stringExtra(key)?.trim().orEmpty()
    if (rawValue.isBlank()) return null
    return ProtocolType.entries.firstOrNull { protocol ->
        protocol.name.equals(rawValue, ignoreCase = true) ||
            protocol.title.equals(rawValue, ignoreCase = true)
    }
}

private fun Intent.scenarioExtra(key: String): ScenarioType? {
    val rawValue = stringExtra(key)?.trim().orEmpty()
    if (rawValue.isBlank()) return null
    return ScenarioType.entries.firstOrNull { scenario ->
        scenario.code.equals(rawValue, ignoreCase = true) ||
            scenario.name.equals(rawValue, ignoreCase = true)
    }
}

private fun Intent.stringExtra(key: String): String? =
    getStringExtra(key)

private fun Intent.longExtra(
    key: String,
    defaultValue: Long,
): Long {
    return if (hasExtra(key)) getIntExtra(key, defaultValue.toInt()).toLong() else defaultValue
}

private fun Intent.intExtraOrNull(key: String): Int? {
    return if (hasExtra(key)) getIntExtra(key, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE } else null
}

private const val ACTION_ENERGY_BLOCK = "ru.meeweel.network_protocols_app.ENERGY_BLOCK"
private const val RUN_MODE_ENERGY = "energy"
private const val DEFAULT_DURATION_SECONDS = 300L
private const val EXTRA_RUN_MODE = "runMode"
private const val EXTRA_ENERGY_KIND = "energyKind"
private const val EXTRA_KIND = "kind"
private const val EXTRA_PROTOCOL = "protocol"
private const val EXTRA_SCENARIO = "scenario"
private const val EXTRA_CONNECTION_MODE = "connectionMode"
private const val EXTRA_MODE = "mode"
private const val EXTRA_DURATION_SECONDS = "durationSeconds"
private const val EXTRA_BACKEND_HOST = "backendHost"
private const val EXTRA_HOST = "host"
private const val EXTRA_HTTP_PORT = "httpPort"
private const val EXTRA_GRPC_PORT = "grpcPort"
private const val EXTRA_BLOCK_ID = "blockId"
