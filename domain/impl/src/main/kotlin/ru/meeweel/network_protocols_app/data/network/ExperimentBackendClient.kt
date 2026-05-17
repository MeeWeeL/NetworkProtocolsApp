package ru.meeweel.network_protocols_app.data.network

import android.os.SystemClock
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.io.StringReader
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import ru.meeweel.network_protocols_app.data.network.model.ErrorResponseDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadAttachmentDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadAttributeDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadContactDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadDocumentDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadLineItemDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadMetricsDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadParameterDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadParameterGroupDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadPartyDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadPreviewDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadRelatedEntityDto
import ru.meeweel.network_protocols_app.data.network.model.LargeReadTimelineEntryDto
import ru.meeweel.network_protocols_app.data.network.model.PageReadFacetBucketDto
import ru.meeweel.network_protocols_app.data.network.model.PageReadFacetDto
import ru.meeweel.network_protocols_app.data.network.model.PageReadPageDto
import ru.meeweel.network_protocols_app.data.network.model.PageReadSummaryDto
import ru.meeweel.network_protocols_app.data.network.model.ScenarioRequestDto
import ru.meeweel.network_protocols_app.data.network.model.ScenarioResponseDto
import ru.meeweel.network_protocols_app.data.network.model.ServiceStatusResponseDto
import ru.meeweel.network_protocols_app.data.network.model.StreamEventDto
import ru.meeweel.network_protocols_app.data.network.model.StreamEventSummaryDto
import ru.meeweel.network_protocols_app.data.network.model.graphql.GraphQlRequestDto
import ru.meeweel.network_protocols_app.data.network.model.graphql.GraphQlResponseDto
import ru.meeweel.network_protocols_app.data.network.model.socket.SocketClientCommandDto
import ru.meeweel.network_protocols_app.data.network.model.socket.SocketCommandTypeDto
import ru.meeweel.network_protocols_app.data.network.model.socket.SocketEnvelopeDto
import ru.meeweel.network_protocols_app.data.network.model.socket.SocketEnvelopeTypeDto
import ru.meeweel.network_protocols_app.model.BackendEndpointConfig
import ru.meeweel.network_protocols_app.model.BackendHealthState
import ru.meeweel.network_protocols_app.model.BackendHealthStatus
import ru.meeweel.network_protocols_app.model.FailureMode
import ru.meeweel.network_protocols_app.model.MeasurementMethodologyProfile
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioExecutionResult
import ru.meeweel.network_protocols_app.model.ScenarioConnectionTelemetry
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_backend.grpc.ExperimentGrpcServiceGrpcKt
import ru.meeweel.network_protocols_backend.grpc.GrpcFailureMode
import ru.meeweel.network_protocols_backend.grpc.GrpcScenarioRequest
import ru.meeweel.network_protocols_backend.grpc.GrpcScenarioResponse
import ru.meeweel.network_protocols_backend.grpc.GrpcScenarioType
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DEFAULT_STREAM_EVENT_INTERVAL_MS = 50L
private const val DEFAULT_HEAVY_STREAM_EVENT_INTERVAL_MS = 75L
private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 100L

/**
 * Низкоуровневый клиент к экспериментальному backend.
 *
 * Если BackendExperimentRunner решает "что и сколько раз запускать", то этот
 * класс знает "как именно отправить один сценарий" через REST, SOAP, GraphQL,
 * WebSocket или gRPC и как вернуть результат в едином формате.
 */
class ExperimentBackendClient(
    private val json: Json,
    private val okHttpClient: OkHttpClient,
) {
    private val jsonContentType = "application/json".toMediaType()
    private val xmlContentType = "text/xml; charset=utf-8".toMediaType()
    private val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    class SeriesExecutionContext internal constructor(
        internal val grpcChannel: ManagedChannel?,
        internal val webSocketSession: WebSocketSeriesSession?,
        internal val graphQlSession: GraphQlSeriesSession?,
    ) {
        /**
         * Подготавливает постоянные соединения для режима h_series.
         *
         * Для gRPC это ожидание готового канала, для WebSocket/GraphQL -
         * открытие сокета. Время подготовки сохраняется отдельно, чтобы не
         * спутать стоимость старта канала с временем самой операции.
         */
        suspend fun prepare(): Long {
            val startedAt = SystemClock.elapsedRealtime()
            grpcChannel?.awaitReady()
            webSocketSession?.prepare()
            graphQlSession?.prepare()
            return (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        }

        fun close() {
            grpcChannel?.shutdownNow()
            webSocketSession?.close()
            graphQlSession?.close()
        }
    }

    internal inner class WebSocketSeriesSession(
        private val request: Request,
        private val methodology: MeasurementMethodologyProfile,
    ) {
        private var currentSocket: WebSocket? = null
        private var socketOpened = false
        private var closeRequested = false
        private var activeRun: ActiveWebSocketRun? = null
        private var prepareContinuation: CancellableContinuation<Unit>? = null

        suspend fun prepare() {
            if (socketOpened && currentSocket != null) return
            suspendCancellableCoroutine<Unit> { continuation ->
                if (socketOpened && currentSocket != null) {
                    continuation.resume(Unit)
                    return@suspendCancellableCoroutine
                }
                prepareContinuation = continuation
                continuation.invokeOnCancellation {
                    if (prepareContinuation === continuation) {
                        prepareContinuation = null
                    }
                }
                openSocketIfNeeded()
            }
        }

        suspend fun executeScenario(
            scenario: ScenarioType,
            spec: ScenarioRequestSpec,
        ): ScenarioExecutionResult {
            check(activeRun == null) { "WebSocket-серия не поддерживает параллельные измерения." }
            // Для WebSocket один открытый канал используется как труба.
            // В эту трубу отправляем команду "выполни сценарий", а затем
            // ждем ответ, события потока или служебные сигналы сессии.
            return suspendCancellableCoroutine { continuation ->
                val run = ActiveWebSocketRun(
                    scenario = scenario,
                    spec = spec,
                    continuation = continuation,
                    startedAtNanos = SystemClock.elapsedRealtimeNanos(),
                    expectedEvents = spec.eventCount.coerceAtLeast(1),
                )
                activeRun = run
                continuation.invokeOnCancellation {
                    if (activeRun === run) {
                        activeRun = null
                    }
                }
                if (socketOpened && currentSocket != null) {
                    sendScenarioStart(run)
                } else {
                    run.pendingStartOnOpen = true
                    openSocketIfNeeded()
                }
            }
        }

        fun close() {
            closeRequested = true
            failPrepare("Серия WebSocket завершена до открытия соединения.")
            activeRun?.let { run ->
                failRun(run, "Серия WebSocket завершена до получения ответа.")
            }
            currentSocket?.let { webSocket ->
                val closeEnvelope = SocketEnvelopeDto(
                    type = SocketEnvelopeTypeDto.CLIENT_COMMAND,
                    command = SocketClientCommandDto(command = SocketCommandTypeDto.CLOSE_SESSION),
                )
                runCatching { webSocket.send(json.encodeToString(closeEnvelope)) }
                webSocket.close(1000, "series-complete")
                webSocket.cancel()
            }
            currentSocket = null
            socketOpened = false
        }

        private fun openSocketIfNeeded() {
            if (currentSocket != null || closeRequested) return
            currentSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    var disconnectHandled = false

                    fun detachSocket(webSocket: WebSocket): Boolean {
                        if (disconnectHandled || currentSocket !== webSocket) return false
                        disconnectHandled = true
                        socketOpened = false
                        currentSocket = null
                        return true
                    }

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (webSocket !== currentSocket) return
                        socketOpened = true
                        prepareContinuation?.let { continuation ->
                            prepareContinuation = null
                            if (!continuation.isCompleted) {
                                continuation.resume(Unit)
                            }
                        }
                        val run = activeRun ?: return
                        if (!run.pendingStartOnOpen) return
                        run.disconnectStartedAtMs?.let { startedAt ->
                            run.recoveryDurationsMs += (SystemClock.elapsedRealtime() - startedAt)
                                .coerceAtLeast(0L)
                            run.disconnectStartedAtMs = null
                        }
                        run.pendingStartOnOpen = false
                        if (run.scenario == ScenarioType.S9_LONG_SESSION && run.responses.isNotEmpty()) {
                            scheduleNextHeartbeat(run)
                        } else {
                            sendScenarioStart(run)
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (webSocket !== currentSocket) return
                        val envelope = runCatching {
                            json.decodeFromString<SocketEnvelopeDto>(text)
                        }.getOrNull() ?: return
                        val event = envelope.event ?: return
                        val run = activeRun ?: return
                        when (event.name) {
                            "session-opened" -> Unit
                            "stream-event" -> {
                                event.response
                                    ?.takeIf { it.requestId == run.spec.requestId }
                                    ?.let(run::recordResponse)
                                if (run.responses.size >= run.expectedEvents) {
                                    completeRun(run)
                                }
                            }

                            "single-response" -> {
                                event.response
                                    ?.takeIf { it.requestId == run.spec.requestId }
                                    ?.let { response -> run.recordResponse(response, fallbackSequence = 1) }
                                if (run.responses.isNotEmpty()) {
                                    completeRun(run)
                                }
                            }

                            "session-heartbeat",
                            "heartbeat-ack",
                            -> {
                                event.response
                                    ?.takeIf { it.requestId == run.spec.requestId }
                                    ?.let { response ->
                                        run.recordResponse(
                                            response = response,
                                            fallbackSequence = run.responses.size + 1,
                                        )
                                    }
                                if (run.responses.size >= run.expectedEvents) {
                                    completeRun(run)
                                } else if (run.responses.isNotEmpty()) {
                                    scheduleNextHeartbeat(run)
                                }
                            }

                            "stream-complete",
                            "session-closing",
                            -> Unit

                            "error" -> {
                                val error = event.error ?: return
                                if (error.correlationId == run.spec.correlationId) {
                                    failRun(run, error.message.ifBlank { "Сбой выполнения WebSocket-сценария." })
                                }
                            }
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (!detachSocket(webSocket)) return
                        webSocket.close(code, reason)
                        if (!closeRequested) {
                            handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (!detachSocket(webSocket)) return
                        if (!closeRequested) {
                            handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        if (!detachSocket(webSocket)) return
                        if (!closeRequested) {
                            handleUnexpectedDisconnect(t.message ?: "Сбой соединения WebSocket.")
                        }
                    }
                },
            )
        }

        private fun handleUnexpectedDisconnect(reason: String) {
            val run = activeRun
            if (run == null) {
                failPrepare(reason)
                return
            }
            run.unexpectedCloseCount += 1
            run.heartbeatDispatchPending = false
            val supportsPartialCompletion = run.scenario in streamingAndSessionScenarios()
            if (run.reconnectCount < methodology.maxReconnectAttempts) {
                run.disconnectStartedAtMs = SystemClock.elapsedRealtime()
                run.reconnectCount += 1
                run.pendingStartOnOpen = true
                openSocketIfNeeded()
            } else if (supportsPartialCompletion && run.responses.isNotEmpty()) {
                completeRun(run)
            } else {
                failRun(run, reason)
            }
        }

        private fun sendScenarioStart(run: ActiveWebSocketRun) {
            val webSocket = currentSocket ?: run.run {
                failRun(this, "Соединение WebSocket недоступно.")
                return
            }
            val extraMetadata = if (run.scenario == ScenarioType.S9_LONG_SESSION) {
                run.buildNextHeartbeatMetadata()
            } else {
                emptyMap()
            }
            val payload = SocketEnvelopeDto(
                type = SocketEnvelopeTypeDto.CLIENT_COMMAND,
                command = SocketClientCommandDto(
                    command = SocketCommandTypeDto.START_SCENARIO,
                    scenario = run.scenario,
                    request = run.spec.toRequestDto(run.scenario, extraMetadata),
                ),
            )
            val sent = runCatching {
                webSocket.send(json.encodeToString(payload))
            }.getOrDefault(false)
            if (!sent) {
                if (currentSocket === webSocket) {
                    currentSocket = null
                }
                socketOpened = false
                webSocket.cancel()
                handleUnexpectedDisconnect("Не удалось отправить команду запуска WebSocket-сценария.")
                return
            }
            if (run.scenario == ScenarioType.S9_LONG_SESSION) {
                run.markHeartbeatSent(extraMetadata)
            }
        }

        private fun sendHeartbeat(run: ActiveWebSocketRun) {
            val webSocket = currentSocket ?: run.run {
                failRun(this, "Соединение WebSocket недоступно.")
                return
            }
            val extraMetadata = run.buildNextHeartbeatMetadata()
            val payload = SocketEnvelopeDto(
                type = SocketEnvelopeTypeDto.CLIENT_COMMAND,
                command = SocketClientCommandDto(
                    command = SocketCommandTypeDto.HEARTBEAT,
                    scenario = run.scenario,
                    request = run.spec.toRequestDto(run.scenario, extraMetadata),
                ),
            )
            val sent = runCatching {
                webSocket.send(json.encodeToString(payload))
            }.getOrDefault(false)
            if (!sent) {
                if (currentSocket === webSocket) {
                    currentSocket = null
                }
                socketOpened = false
                webSocket.cancel()
                handleUnexpectedDisconnect("Не удалось отправить служебный сигнал WebSocket-сценария.")
                return
            }
            run.markHeartbeatSent(extraMetadata)
        }

        private fun scheduleNextHeartbeat(run: ActiveWebSocketRun) {
            if (run.scenario != ScenarioType.S9_LONG_SESSION) return
            if (run.heartbeatDispatchPending || run.responses.size >= run.expectedEvents) return
            val webSocket = currentSocket ?: return
            run.heartbeatDispatchPending = true
            transportScope.launch {
                delay(DEFAULT_HEARTBEAT_INTERVAL_MS)
                if (activeRun !== run || closeRequested) {
                    run.heartbeatDispatchPending = false
                    return@launch
                }
                if (currentSocket !== webSocket) {
                    run.heartbeatDispatchPending = false
                    return@launch
                }
                sendHeartbeat(run)
            }
        }

        private fun completeRun(run: ActiveWebSocketRun) {
            if (activeRun !== run) return
            activeRun = null
            if (run.continuation.isCompleted) return
            if (run.responses.isEmpty()) {
                run.continuation.resumeWithException(
                    IllegalStateException("WebSocket не вернул ни одного события."),
                )
                return
            }
            run.continuation.resume(
                run.responses.values.toList().toExecutionResult(
                    protocol = ProtocolType.WEBSOCKET,
                    scenario = run.scenario,
                    clientDurationMicros = elapsedMicros(run.startedAtNanos),
                    connectionTelemetry = run.buildConnectionTelemetryOrNull(),
                ),
            )
        }

        private fun failRun(
            run: ActiveWebSocketRun,
            message: String,
        ) {
            if (activeRun === run) {
                activeRun = null
            }
            if (!run.continuation.isCompleted) {
                run.continuation.resumeWithException(IllegalStateException(message))
            }
        }

        private fun failPrepare(message: String) {
            val continuation = prepareContinuation ?: return
            prepareContinuation = null
            if (!continuation.isCompleted) {
                continuation.resumeWithException(IllegalStateException(message))
            }
        }
    }

    internal inner class GraphQlSeriesSession(
        private val request: Request,
        private val methodology: MeasurementMethodologyProfile,
    ) {
        private var currentSocket: WebSocket? = null
        private var socketOpened = false
        private var closeRequested = false
        private var activeRun: ActiveWebSocketRun? = null
        private var prepareContinuation: CancellableContinuation<Unit>? = null

        suspend fun prepare() {
            if (socketOpened && currentSocket != null) return
            suspendCancellableCoroutine<Unit> { continuation ->
                if (socketOpened && currentSocket != null) {
                    continuation.resume(Unit)
                    return@suspendCancellableCoroutine
                }
                prepareContinuation = continuation
                continuation.invokeOnCancellation {
                    if (prepareContinuation === continuation) {
                        prepareContinuation = null
                    }
                }
                openSocketIfNeeded()
            }
        }

        suspend fun executeScenario(
            scenario: ScenarioType,
            spec: ScenarioRequestSpec,
        ): ScenarioExecutionResult {
            check(scenario in graphQlStreamingScenarios()) {
                "GraphQL-серия с постоянным соединением поддерживает только потоковые сценарии."
            }
            check(activeRun == null) { "GraphQL-серия не поддерживает параллельные измерения." }
            // Для GraphQL-потока используется подписка поверх WebSocket.
            // Смысл для читателя простой: клиент один раз подписался, а дальше
            // backend сам присылает события, пока серия не набрала нужное число.
            return suspendCancellableCoroutine { continuation ->
                val run = ActiveWebSocketRun(
                    scenario = scenario,
                    spec = spec,
                    continuation = continuation,
                    startedAtNanos = SystemClock.elapsedRealtimeNanos(),
                    expectedEvents = spec.eventCount.coerceAtLeast(1),
                )
                activeRun = run
                continuation.invokeOnCancellation {
                    if (activeRun === run) {
                        activeRun = null
                    }
                }
                if (socketOpened && currentSocket != null) {
                    sendSubscription(run)
                } else {
                    run.pendingStartOnOpen = true
                    openSocketIfNeeded()
                }
            }
        }

        fun close() {
            closeRequested = true
            failPrepare("Серия GraphQL завершена до открытия соединения.")
            activeRun?.let { run ->
                failRun(run, "Серия GraphQL завершена до получения ответа.")
            }
            currentSocket?.close(1000, "series-complete")
            currentSocket?.cancel()
            currentSocket = null
            socketOpened = false
        }

        private fun openSocketIfNeeded() {
            if (currentSocket != null || closeRequested) return
            currentSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    var disconnectHandled = false

                    fun detachSocket(webSocket: WebSocket): Boolean {
                        if (disconnectHandled || currentSocket !== webSocket) return false
                        disconnectHandled = true
                        socketOpened = false
                        currentSocket = null
                        return true
                    }

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (webSocket !== currentSocket) return
                        socketOpened = true
                        prepareContinuation?.let { continuation ->
                            prepareContinuation = null
                            if (!continuation.isCompleted) {
                                continuation.resume(Unit)
                            }
                        }
                        val run = activeRun ?: return
                        if (!run.pendingStartOnOpen) return
                        run.disconnectStartedAtMs?.let { startedAt ->
                            run.recoveryDurationsMs += (SystemClock.elapsedRealtime() - startedAt)
                                .coerceAtLeast(0L)
                            run.disconnectStartedAtMs = null
                        }
                        run.pendingStartOnOpen = false
                        sendSubscription(run)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (webSocket !== currentSocket) return
                        val response = runCatching {
                            json.decodeFromString<GraphQlResponseDto>(text)
                        }.getOrElse { error ->
                            failRun(
                                activeRun ?: return,
                                error.message ?: "Некорректный ответ GraphQL-подписки.",
                            )
                            return
                        }
                        if (response.errors.isNotEmpty()) {
                            failRun(
                                activeRun ?: return,
                                response.errors.joinToString(separator = "; ") { it.message },
                            )
                            return
                        }
                        val run = activeRun ?: return
                        response.data?.subscribeScenario.orEmpty()
                            .asSequence()
                            .filter { item -> item.requestId == run.spec.requestId }
                            .forEach(run::recordResponse)
                        if (run.responses.size >= run.expectedEvents) {
                            completeRun(run)
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (!detachSocket(webSocket)) return
                        webSocket.close(code, reason)
                        if (!closeRequested) {
                            handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (!detachSocket(webSocket)) return
                        if (!closeRequested) {
                            handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        if (!detachSocket(webSocket)) return
                        if (!closeRequested) {
                            handleUnexpectedDisconnect(t.message ?: "Сбой GraphQL-подписки.")
                        }
                    }
                },
            )
        }

        private fun handleUnexpectedDisconnect(reason: String) {
            val run = activeRun
            if (run == null) {
                failPrepare(reason)
                return
            }
            run.unexpectedCloseCount += 1
            if (run.reconnectCount < methodology.maxReconnectAttempts) {
                run.disconnectStartedAtMs = SystemClock.elapsedRealtime()
                run.reconnectCount += 1
                run.pendingStartOnOpen = true
                openSocketIfNeeded()
            } else if (run.responses.isNotEmpty()) {
                completeRun(run)
            } else {
                failRun(run, reason)
            }
        }

        private fun sendSubscription(run: ActiveWebSocketRun) {
            val webSocket = currentSocket ?: run.run {
                failRun(this, "Соединение GraphQL-подписки недоступно.")
                return
            }
            val payload = buildGraphQlRequest(run.scenario, run.spec)
            val sent = runCatching {
                webSocket.send(json.encodeToString(payload))
            }.getOrDefault(false)
            if (!sent) {
                if (currentSocket === webSocket) {
                    currentSocket = null
                }
                socketOpened = false
                webSocket.cancel()
                handleUnexpectedDisconnect("Не удалось отправить GraphQL-подписку.")
            }
        }

        private fun completeRun(run: ActiveWebSocketRun) {
            if (activeRun !== run) return
            activeRun = null
            if (run.continuation.isCompleted) return
            if (run.responses.isEmpty()) {
                run.continuation.resumeWithException(
                    IllegalStateException("GraphQL-подписка не вернула ни одного события."),
                )
                return
            }
            run.continuation.resume(
                run.responses.values.toList().toExecutionResult(
                    protocol = ProtocolType.GRAPHQL,
                    scenario = run.scenario,
                    clientDurationMicros = elapsedMicros(run.startedAtNanos),
                    connectionTelemetry = run.buildConnectionTelemetryOrNull(),
                ),
            )
        }

        private fun failRun(
            run: ActiveWebSocketRun,
            message: String,
        ) {
            if (activeRun === run) {
                activeRun = null
            }
            if (!run.continuation.isCompleted) {
                run.continuation.resumeWithException(IllegalStateException(message))
            }
        }

        private fun failPrepare(message: String) {
            val continuation = prepareContinuation ?: return
            prepareContinuation = null
            if (!continuation.isCompleted) {
                continuation.resumeWithException(IllegalStateException(message))
            }
        }
    }

    private class ActiveWebSocketRun(
        val scenario: ScenarioType,
        val spec: ScenarioRequestSpec,
        val continuation: CancellableContinuation<ScenarioExecutionResult>,
        val startedAtNanos: Long,
        val expectedEvents: Int,
    ) {
        val responses = linkedMapOf<Int, ScenarioResponseDto>()
        val eventArrivalOffsetsMs = mutableListOf<Long>()
        val heartbeatRttsMs = mutableListOf<Long>()
        val recoveryDurationsMs = mutableListOf<Long>()
        val pendingHeartbeatSentAtNanos = linkedMapOf<String, Long>()
        var reconnectCount = 0
        var unexpectedCloseCount = 0
        var disconnectStartedAtMs: Long? = null
        var pendingStartOnOpen = false
        var heartbeatDispatchPending = false

        fun recordResponse(
            response: ScenarioResponseDto,
            fallbackSequence: Int? = null,
        ) {
            val normalizedSequence = response.sequence ?: fallbackSequence
            val normalizedResponse = when {
                normalizedSequence != null && normalizedSequence != response.sequence ->
                    response.copy(sequence = normalizedSequence)

                else -> response
            }
            val key = normalizedResponse.sequence ?: (responses.size + 1)
            if (responses.containsKey(key)) return
            responses[key] = normalizedResponse
            eventArrivalOffsetsMs += streamOffsetMs(startedAtNanos)
            if (scenario == ScenarioType.S9_LONG_SESSION) {
                heartbeatDispatchPending = false
                normalizedResponse.metadata["heartbeatNonce"]?.let { nonce ->
                    pendingHeartbeatSentAtNanos.remove(nonce)?.let { sentAtNanos ->
                        heartbeatRttsMs += elapsedMillisFrom(sentAtNanos)
                    }
                }
            }
        }

        fun buildConnectionTelemetryOrNull(): ScenarioConnectionTelemetry? {
            return when (scenario) {
                ScenarioType.S7_EVENT_STREAM,
                ScenarioType.S8_HEAVY_EVENT_STREAM,
                -> buildStreamConnectionTelemetry(
                    expectedEvents = expectedEvents,
                    receivedEvents = responses.size,
                    reconnectCount = reconnectCount,
                    unexpectedCloseCount = unexpectedCloseCount,
                    recoveryDurationsMs = recoveryDurationsMs.toList(),
                    eventArrivalOffsetsMs = eventArrivalOffsetsMs.toList(),
                )

                ScenarioType.S9_LONG_SESSION -> buildHeartbeatConnectionTelemetry(
                    expectedEvents = expectedEvents,
                    receivedEvents = responses.size,
                    reconnectCount = reconnectCount,
                    unexpectedCloseCount = unexpectedCloseCount,
                    recoveryDurationsMs = recoveryDurationsMs.toList(),
                    heartbeatRttsMs = heartbeatRttsMs.toList(),
                )

                else -> null
            }
        }

        fun buildNextHeartbeatMetadata(): Map<String, String> {
            val heartbeatIndex = responses.size + 1
            val heartbeatNonce = UUID.randomUUID().toString()
            return mapOf(
                "heartbeatIndex" to heartbeatIndex.toString(),
                "heartbeatNonce" to heartbeatNonce,
                "heartbeatIntervalMs" to DEFAULT_HEARTBEAT_INTERVAL_MS.toString(),
            )
        }

        fun markHeartbeatSent(metadata: Map<String, String>) {
            heartbeatDispatchPending = false
            metadata["heartbeatNonce"]?.let { nonce ->
                pendingHeartbeatSentAtNanos[nonce] = SystemClock.elapsedRealtimeNanos()
            }
        }
    }

    suspend fun checkHealth(
        endpointConfig: BackendEndpointConfig,
    ): BackendHealthState {
        return runCatching {
            val response = retrofit(endpointConfig)
                .create(HealthApi::class.java)
                .health()
            BackendHealthState(
                status = BackendHealthStatus.Available,
                title = response.service ?: "Стенд доступен",
                details = buildString {
                    append("HTTP ${endpointConfig.httpPort}, gRPC ${endpointConfig.grpcPort}")
                    if (response.transport.isNotEmpty()) {
                        append(" • ")
                        append(response.transport.joinToString())
                    }
                },
            )
        }.getOrElse { error ->
            BackendHealthState(
                status = BackendHealthStatus.Unavailable,
                title = "Стенд недоступен",
                details = error.message ?: "Не удалось установить соединение.",
            )
        }
    }

    suspend fun execute(
        endpointConfig: BackendEndpointConfig,
        protocol: ProtocolType,
        scenario: ScenarioType,
        methodology: MeasurementMethodologyProfile,
        context: SeriesExecutionContext? = null,
    ): ScenarioExecutionResult {
        val spec = buildScenarioSpec(scenario)
        return when (protocol) {
            ProtocolType.REST -> executeRest(endpointConfig, scenario, spec)
            ProtocolType.SOAP -> executeSoap(endpointConfig, scenario, spec)
            ProtocolType.GRAPHQL -> if (scenario in graphQlStreamingScenarios()) {
                context?.graphQlSession?.executeScenario(scenario, spec)
                    ?: executeGraphQl(endpointConfig, scenario, spec, methodology)
            } else {
                executeGraphQl(endpointConfig, scenario, spec, methodology)
            }
            ProtocolType.WEBSOCKET -> context?.webSocketSession?.executeScenario(
                scenario = scenario,
                spec = spec,
            ) ?: executeWebSocket(endpointConfig, scenario, spec, methodology)
            ProtocolType.GRPC -> executeGrpc(
                endpointConfig = endpointConfig,
                scenario = scenario,
                spec = spec,
                methodology = methodology,
                sharedChannel = context?.grpcChannel,
            )
        }
    }

    fun openSeriesExecutionContext(
        endpointConfig: BackendEndpointConfig,
        protocol: ProtocolType,
        scenario: ScenarioType,
        methodology: MeasurementMethodologyProfile,
    ): SeriesExecutionContext {
        if (!methodology.reusePersistentConnections) {
            return SeriesExecutionContext(
                grpcChannel = null,
                webSocketSession = null,
                graphQlSession = null,
            )
        }
        val grpcChannel = if (protocol == ProtocolType.GRPC) {
            grpcChannel(endpointConfig)
        } else {
            null
        }
        val webSocketSession = if (protocol == ProtocolType.WEBSOCKET) {
            WebSocketSeriesSession(
                request = Request.Builder()
                    .url(endpointConfig.webSocketUrl)
                    .build(),
                methodology = methodology,
            )
        } else {
            null
        }
        val graphQlSession = if (protocol == ProtocolType.GRAPHQL && scenario in graphQlStreamingScenarios()) {
            GraphQlSeriesSession(
                request = Request.Builder()
                    .url(endpointConfig.graphQlWebSocketUrl)
                    .build(),
                methodology = methodology,
            )
        } else {
            null
        }
        return SeriesExecutionContext(
            grpcChannel = grpcChannel,
            webSocketSession = webSocketSession,
            graphQlSession = graphQlSession,
        )
    }

    private suspend fun executeRest(
        endpointConfig: BackendEndpointConfig,
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
    ): ScenarioExecutionResult {
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val response = retrofit(endpointConfig)
            .create(RestApi::class.java)
            .execute(
                scenario = scenario.code,
                request = spec.toRequestDto(scenario),
            )
        val clientDurationMicros = elapsedMicros(startedAtNanos)
        return response.toExecutionResult(
            protocol = ProtocolType.REST,
            scenario = scenario,
            clientDurationMicros = clientDurationMicros,
            responseCount = 1,
        )
    }

    private suspend fun executeGraphQl(
        endpointConfig: BackendEndpointConfig,
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
        methodology: MeasurementMethodologyProfile,
    ): ScenarioExecutionResult {
        if (scenario in graphQlStreamingScenarios()) {
            return executeGraphQlSubscription(endpointConfig, scenario, spec, methodology)
        }
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val response = retrofit(endpointConfig)
            .create(GraphQlApi::class.java)
            .execute(
                buildGraphQlRequest(scenario, spec),
            )
        val clientDurationMicros = elapsedMicros(startedAtNanos)
        if (response.errors.isNotEmpty()) {
            error(response.errors.joinToString(separator = "; ") { it.message })
        }
        val scenarioResponse = when (scenario) {
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            -> response.data?.executeScenario?.let { listOf(it) }.orEmpty()

            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> response.data?.subscribeScenario.orEmpty()

            else -> response.data?.scenario?.let { listOf(it) }.orEmpty()
        }
        if (scenarioResponse.isEmpty()) {
            error("GraphQL вернул пустой ответ.")
        }
        val connectionTelemetry = when (scenario) {
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> buildStreamConnectionTelemetry(
                expectedEvents = spec.eventCount,
                receivedEvents = scenarioResponse.size,
                eventArrivalOffsetsMs = scenarioResponse.indices.map { streamOffsetMs(startedAtNanos) },
            )

            else -> null
        }
        return scenarioResponse.toExecutionResult(
            protocol = ProtocolType.GRAPHQL,
            scenario = scenario,
            clientDurationMicros = clientDurationMicros,
            connectionTelemetry = connectionTelemetry,
        )
    }

    private suspend fun executeGraphQlSubscription(
        endpointConfig: BackendEndpointConfig,
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
        methodology: MeasurementMethodologyProfile,
    ): ScenarioExecutionResult = suspendCancellableCoroutine { continuation ->
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val expectedEvents = spec.eventCount.coerceAtLeast(1)
        val responses = linkedMapOf<Int, ScenarioResponseDto>()
        val eventArrivalOffsetsMs = mutableListOf<Long>()
        val recoveryDurationsMs = mutableListOf<Long>()
        var pendingResult: ScenarioExecutionResult? = null
        var closeRequested = false
        var reconnectCount = 0
        var unexpectedCloseCount = 0
        var disconnectStartedAtMs: Long? = null

        val request = Request.Builder()
            .url(endpointConfig.graphQlWebSocketUrl)
            .build()
        val subscriptionPayload = json.encodeToString(buildGraphQlRequest(scenario, spec))

        var currentSocket: WebSocket? = null

        fun currentResponses(): List<ScenarioResponseDto> = responses.values.toList()

        fun recordResponse(response: ScenarioResponseDto) {
            val key = response.sequence ?: (responses.size + 1)
            if (responses.containsKey(key)) return
            val normalizedResponse = if (response.sequence == null) {
                response.copy(sequence = key)
            } else {
                response
            }
            responses[key] = normalizedResponse
            eventArrivalOffsetsMs += streamOffsetMs(startedAtNanos)
        }

        fun buildResult(): ScenarioExecutionResult {
            return currentResponses().toExecutionResult(
                protocol = ProtocolType.GRAPHQL,
                scenario = scenario,
                clientDurationMicros = elapsedMicros(startedAtNanos),
                connectionTelemetry = buildStreamConnectionTelemetry(
                    expectedEvents = expectedEvents,
                    receivedEvents = currentResponses().size,
                    reconnectCount = reconnectCount,
                    unexpectedCloseCount = unexpectedCloseCount,
                    recoveryDurationsMs = recoveryDurationsMs.toList(),
                    eventArrivalOffsetsMs = eventArrivalOffsetsMs.toList(),
                ),
            )
        }

        fun resumePendingResult() {
            val result = pendingResult ?: return
            if (continuation.isCompleted) return
            continuation.resume(result)
        }

        fun fail(message: String) {
            if (continuation.isCompleted) return
            currentSocket?.cancel()
            continuation.resumeWithException(IllegalStateException(message))
        }

        fun finishAfterComplete() {
            if (continuation.isCompleted || pendingResult != null) return
            if (responses.isEmpty()) {
                fail("GraphQL-подписка не вернула ни одного события.")
                return
            }
            pendingResult = buildResult()
            closeRequested = true
            val webSocket = currentSocket
            if (webSocket == null) {
                resumePendingResult()
            } else {
                webSocket.close(1000, "done")
            }
        }

        lateinit var connect: () -> Unit
        lateinit var reconnectOrFinishPartial: (String) -> Unit
        lateinit var handleUnexpectedDisconnect: (String) -> Unit

        reconnectOrFinishPartial = reconnect@{ reason ->
            if (currentResponses().isEmpty()) {
                if (reconnectCount >= methodology.maxReconnectAttempts) {
                    fail(reason)
                } else {
                    disconnectStartedAtMs = SystemClock.elapsedRealtime()
                    reconnectCount += 1
                    connect()
                }
            } else if (
                currentResponses().size < expectedEvents &&
                reconnectCount < methodology.maxReconnectAttempts
            ) {
                disconnectStartedAtMs = SystemClock.elapsedRealtime()
                reconnectCount += 1
                connect()
            } else {
                pendingResult = buildResult()
                resumePendingResult()
            }
        }

        handleUnexpectedDisconnect = disconnect@{ reason ->
            if (continuation.isCompleted) return@disconnect
            unexpectedCloseCount += 1
            currentSocket = null
            reconnectOrFinishPartial(reason)
        }

        connect = {
            closeRequested = false
            currentSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (webSocket !== currentSocket) return
                        disconnectStartedAtMs?.let { startedAt ->
                            recoveryDurationsMs += (SystemClock.elapsedRealtime() - startedAt)
                                .coerceAtLeast(0L)
                            disconnectStartedAtMs = null
                        }
                        webSocket.send(subscriptionPayload)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (webSocket !== currentSocket) return
                        val response = runCatching {
                            json.decodeFromString<GraphQlResponseDto>(text)
                        }.getOrElse { error ->
                            fail(error.message ?: "Некорректный ответ GraphQL-подписки.")
                            return
                        }
                        if (response.errors.isNotEmpty()) {
                            fail(response.errors.joinToString(separator = "; ") { it.message })
                            return
                        }
                        response.data?.subscribeScenario.orEmpty().forEach(::recordResponse)
                        if (currentResponses().size >= expectedEvents) {
                            finishAfterComplete()
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (webSocket !== currentSocket && pendingResult == null) return
                        currentSocket = null
                        webSocket.close(code, reason)
                        when {
                            pendingResult != null || closeRequested -> resumePendingResult()
                            else -> handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (webSocket !== currentSocket && pendingResult == null) return
                        currentSocket = null
                        when {
                            pendingResult != null || closeRequested -> resumePendingResult()
                            else -> handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        if (webSocket !== currentSocket && pendingResult == null) return
                        currentSocket = null
                        when {
                            pendingResult != null || closeRequested -> resumePendingResult()
                            else -> handleUnexpectedDisconnect(t.message ?: "Сбой GraphQL-подписки.")
                        }
                    }
                },
            )
        }

        connect()

        continuation.invokeOnCancellation {
            currentSocket?.cancel()
        }
    }

    private suspend fun executeSoap(
        endpointConfig: BackendEndpointConfig,
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
    ): ScenarioExecutionResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${endpointConfig.httpBaseUrl}api/soap")
            .post(buildSoapEnvelope(scenario, spec).toRequestBody(xmlContentType))
            .addHeader("Content-Type", "text/xml; charset=utf-8")
            .build()

        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val clientDurationMicros = elapsedMicros(startedAtNanos)
            if (!response.isSuccessful) {
                throw IllegalStateException(parseSoapFault(body))
            }
            parseSoapSuccess(body).toExecutionResult(
                protocol = ProtocolType.SOAP,
                scenario = scenario,
                clientDurationMicros = clientDurationMicros,
                responseCount = 1,
            )
        }
    }

    private suspend fun executeWebSocket(
        endpointConfig: BackendEndpointConfig,
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
        methodology: MeasurementMethodologyProfile,
    ): ScenarioExecutionResult = suspendCancellableCoroutine { continuation ->
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val expectedEvents = spec.eventCount.coerceAtLeast(1)
        val responses = linkedMapOf<Int, ScenarioResponseDto>()
        val eventArrivalOffsetsMs = mutableListOf<Long>()
        val heartbeatRttsMs = mutableListOf<Long>()
        val pendingHeartbeatSentAtNanos = linkedMapOf<String, Long>()
        val recoveryDurationsMs = mutableListOf<Long>()
        var pendingResult: ScenarioExecutionResult? = null
        var closeRequested = false
        var reconnectCount = 0
        var unexpectedCloseCount = 0
        var disconnectStartedAtMs: Long? = null
        var heartbeatDispatchPending = false

        val request = Request.Builder()
            .url(endpointConfig.webSocketUrl)
            .build()

        var currentSocket: WebSocket? = null

        fun currentResponses(): List<ScenarioResponseDto> = responses.values.toList()

        fun recordResponse(
            response: ScenarioResponseDto,
            fallbackSequence: Int? = null,
        ) {
            val normalizedSequence = response.sequence ?: fallbackSequence
            val normalizedResponse = when {
                normalizedSequence != null && normalizedSequence != response.sequence ->
                    response.copy(sequence = normalizedSequence)

                else -> response
            }
            val key = normalizedResponse.sequence ?: (responses.size + 1)
            if (responses.containsKey(key)) return
            responses[key] = normalizedResponse
            eventArrivalOffsetsMs += streamOffsetMs(startedAtNanos)
            if (scenario == ScenarioType.S9_LONG_SESSION) {
                heartbeatDispatchPending = false
                normalizedResponse.metadata["heartbeatNonce"]?.let { nonce ->
                    pendingHeartbeatSentAtNanos.remove(nonce)?.let { sentAtNanos ->
                        heartbeatRttsMs += elapsedMillisFrom(sentAtNanos)
                    }
                }
            }
        }

        fun buildConnectionTelemetryOrNull(): ScenarioConnectionTelemetry? {
            return when (scenario) {
                ScenarioType.S7_EVENT_STREAM,
                ScenarioType.S8_HEAVY_EVENT_STREAM,
                -> buildStreamConnectionTelemetry(
                    expectedEvents = expectedEvents,
                    receivedEvents = currentResponses().size,
                    reconnectCount = reconnectCount,
                    unexpectedCloseCount = unexpectedCloseCount,
                    recoveryDurationsMs = recoveryDurationsMs.toList(),
                    eventArrivalOffsetsMs = eventArrivalOffsetsMs.toList(),
                )

                ScenarioType.S9_LONG_SESSION -> buildHeartbeatConnectionTelemetry(
                    expectedEvents = expectedEvents,
                    receivedEvents = currentResponses().size,
                    reconnectCount = reconnectCount,
                    unexpectedCloseCount = unexpectedCloseCount,
                    recoveryDurationsMs = recoveryDurationsMs.toList(),
                    heartbeatRttsMs = heartbeatRttsMs.toList(),
                )

                else -> null
            }
        }

        fun buildResult(): ScenarioExecutionResult {
            return currentResponses().toExecutionResult(
                protocol = ProtocolType.WEBSOCKET,
                scenario = scenario,
                clientDurationMicros = elapsedMicros(startedAtNanos),
                connectionTelemetry = buildConnectionTelemetryOrNull(),
            )
        }

        fun resumePendingResult() {
            val result = pendingResult ?: return
            if (continuation.isCompleted) return
            continuation.resume(result)
        }

        fun requestSessionClose() {
            if (closeRequested) return
            closeRequested = true
            val webSocket = currentSocket
            if (webSocket == null) {
                resumePendingResult()
                return
            }
            val closeEnvelope = SocketEnvelopeDto(
                type = SocketEnvelopeTypeDto.CLIENT_COMMAND,
                command = SocketClientCommandDto(
                    command = SocketCommandTypeDto.CLOSE_SESSION,
                ),
            )
            val closeCommandSent = runCatching {
                webSocket.send(json.encodeToString(closeEnvelope))
            }.getOrDefault(false)
            if (!closeCommandSent) {
                webSocket.close(1000, "done")
            }
        }

        fun finishAfterClose() {
            if (continuation.isCompleted || pendingResult != null) return
            if (responses.isEmpty()) {
                continuation.resumeWithException(
                    IllegalStateException("WebSocket не вернул ни одного события."),
                )
                return
            }
            pendingResult = buildResult()
            requestSessionClose()
        }

        fun sendScenarioStart(webSocket: WebSocket) {
            val extraMetadata = if (scenario == ScenarioType.S9_LONG_SESSION) {
                buildHeartbeatStepMetadata(
                    heartbeatIndex = currentResponses().size + 1,
                    heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS,
                )
            } else {
                emptyMap()
            }
            val payload = SocketEnvelopeDto(
                type = SocketEnvelopeTypeDto.CLIENT_COMMAND,
                command = SocketClientCommandDto(
                    command = SocketCommandTypeDto.START_SCENARIO,
                    scenario = scenario,
                    request = spec.toRequestDto(scenario, extraMetadata),
                ),
            )
            check(webSocket.send(json.encodeToString(payload))) {
                "Не удалось отправить команду запуска WebSocket-сценария."
            }
            if (scenario == ScenarioType.S9_LONG_SESSION) {
                heartbeatDispatchPending = false
                extraMetadata["heartbeatNonce"]?.let { nonce ->
                    pendingHeartbeatSentAtNanos[nonce] = SystemClock.elapsedRealtimeNanos()
                }
            }
        }

        fun sendHeartbeat(webSocket: WebSocket) {
            val extraMetadata = buildHeartbeatStepMetadata(
                heartbeatIndex = currentResponses().size + 1,
                heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS,
            )
            val payload = SocketEnvelopeDto(
                type = SocketEnvelopeTypeDto.CLIENT_COMMAND,
                command = SocketClientCommandDto(
                    command = SocketCommandTypeDto.HEARTBEAT,
                    scenario = scenario,
                    request = spec.toRequestDto(scenario, extraMetadata),
                ),
            )
            check(webSocket.send(json.encodeToString(payload))) {
                "Не удалось отправить служебный сигнал WebSocket-сценария."
            }
            heartbeatDispatchPending = false
            extraMetadata["heartbeatNonce"]?.let { nonce ->
                pendingHeartbeatSentAtNanos[nonce] = SystemClock.elapsedRealtimeNanos()
            }
        }

        fun scheduleNextHeartbeat(webSocket: WebSocket) {
            if (scenario != ScenarioType.S9_LONG_SESSION) return
            if (heartbeatDispatchPending || currentResponses().size >= expectedEvents) return
            heartbeatDispatchPending = true
            transportScope.launch {
                delay(DEFAULT_HEARTBEAT_INTERVAL_MS)
                if (continuation.isCompleted || closeRequested) {
                    heartbeatDispatchPending = false
                    return@launch
                }
                if (currentSocket !== webSocket) {
                    heartbeatDispatchPending = false
                    return@launch
                }
                runCatching { sendHeartbeat(webSocket) }
                    .onFailure { error ->
                        heartbeatDispatchPending = false
                        currentSocket?.cancel()
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(
                                IllegalStateException(
                                    error.message ?: "Не удалось отправить служебный сигнал WebSocket-сценария.",
                                ),
                            )
                        }
                    }
            }
        }

        fun fail(message: String) {
            if (continuation.isCompleted) return
            currentSocket?.cancel()
            continuation.resumeWithException(IllegalStateException(message))
        }

        lateinit var connect: () -> Unit
        lateinit var reconnectOrFinishPartial: (String) -> Unit
        lateinit var handleUnexpectedDisconnect: (String) -> Unit

        reconnectOrFinishPartial = reconnect@{ reason ->
            if (currentResponses().isEmpty()) {
                fail(reason)
            } else if (
                currentResponses().size < expectedEvents &&
                reconnectCount < methodology.maxReconnectAttempts
            ) {
                disconnectStartedAtMs = SystemClock.elapsedRealtime()
                reconnectCount += 1
                connect()
            } else {
                pendingResult = buildResult()
                resumePendingResult()
            }
        }

        handleUnexpectedDisconnect = disconnect@{ reason ->
            if (continuation.isCompleted) return@disconnect
            unexpectedCloseCount += 1
            currentSocket = null
            reconnectOrFinishPartial(reason)
        }

        connect = {
            closeRequested = false
            currentSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (webSocket !== currentSocket) return
                        disconnectStartedAtMs?.let { startedAt ->
                            recoveryDurationsMs += (SystemClock.elapsedRealtime() - startedAt)
                                .coerceAtLeast(0L)
                            disconnectStartedAtMs = null
                        }
                        if (scenario == ScenarioType.S9_LONG_SESSION && currentResponses().isNotEmpty()) {
                            scheduleNextHeartbeat(webSocket)
                        } else {
                            sendScenarioStart(webSocket)
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (webSocket !== currentSocket) return
                        val envelope = runCatching {
                            json.decodeFromString<SocketEnvelopeDto>(text)
                        }.getOrNull() ?: return
                        val event = envelope.event ?: return
                        when (event.name) {
                            "session-opened" -> Unit

                            "stream-event" -> {
                                event.response?.let(::recordResponse)
                                if (currentResponses().size >= expectedEvents) {
                                    finishAfterClose()
                                }
                            }

                            "single-response" -> {
                                event.response?.let { recordResponse(it, fallbackSequence = 1) }
                                finishAfterClose()
                            }

                            "session-heartbeat",
                            "heartbeat-ack",
                            -> {
                                event.response?.let {
                                    recordResponse(
                                        response = it,
                                        fallbackSequence = currentResponses().size + 1,
                                    )
                                }
                                if (currentResponses().size >= expectedEvents) {
                                    finishAfterClose()
                                } else {
                                    scheduleNextHeartbeat(webSocket)
                                }
                            }

                            "stream-complete" -> finishAfterClose()
                            "session-closing" -> webSocket.close(1000, "done")
                            "error" -> fail(event.error?.message ?: "Сбой выполнения WebSocket-сценария.")
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (webSocket !== currentSocket && pendingResult == null) return
                        webSocket.close(code, reason)
                        when {
                            pendingResult != null || closeRequested -> resumePendingResult()
                            scenario in streamingAndSessionScenarios() ->
                                handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (webSocket !== currentSocket && pendingResult == null) return
                        when {
                            pendingResult != null || closeRequested -> resumePendingResult()
                            scenario in streamingAndSessionScenarios() ->
                                handleUnexpectedDisconnect(reason)
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        if (webSocket !== currentSocket && pendingResult == null) return
                        when {
                            pendingResult != null || closeRequested -> resumePendingResult()
                            scenario in streamingAndSessionScenarios() ->
                                handleUnexpectedDisconnect(t.message ?: "Сбой соединения WebSocket.")

                            else -> fail(t.message ?: "Сбой соединения WebSocket.")
                        }
                    }
                },
            )
        }

        connect()

        continuation.invokeOnCancellation {
            currentSocket?.cancel()
        }
    }

    private suspend fun executeGrpc(
        endpointConfig: BackendEndpointConfig,
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
        methodology: MeasurementMethodologyProfile,
        sharedChannel: ManagedChannel? = null,
    ): ScenarioExecutionResult {
        val request = spec.toGrpcRequest(scenario)
        return when (scenario) {
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> executeGrpcStreaming(
                endpointConfig = endpointConfig,
                scenario = scenario,
                request = request,
                spec = spec,
                methodology = methodology,
                sharedChannel = sharedChannel,
            )

            ScenarioType.S9_LONG_SESSION -> executeGrpcLongSession(
                endpointConfig = endpointConfig,
                spec = spec,
                methodology = methodology,
                sharedChannel = sharedChannel,
            )

            else -> {
                val channel = sharedChannel ?: grpcChannel(endpointConfig)
                try {
                    val startedAtNanos = SystemClock.elapsedRealtimeNanos()
                    val stub = ExperimentGrpcServiceGrpcKt.ExperimentGrpcServiceCoroutineStub(channel)
                    val response = stub.executeScenario(request)
                    listOf(response).toGrpcExecutionResult(
                        protocol = ProtocolType.GRPC,
                        scenario = scenario,
                        clientDurationMicros = elapsedMicros(startedAtNanos),
                    )
                } finally {
                    if (sharedChannel == null) {
                        channel.shutdownNow()
                    }
                }
            }
        }
    }

    private suspend fun executeGrpcStreaming(
        endpointConfig: BackendEndpointConfig,
        scenario: ScenarioType,
        request: GrpcScenarioRequest,
        spec: ScenarioRequestSpec,
        methodology: MeasurementMethodologyProfile,
        sharedChannel: ManagedChannel? = null,
    ): ScenarioExecutionResult {
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val expectedEvents = spec.eventCount.coerceAtLeast(1)
        val responses = linkedMapOf<Int, GrpcScenarioResponse>()
        val eventArrivalOffsetsMs = mutableListOf<Long>()
        val recoveryDurationsMs = mutableListOf<Long>()
        var reconnectCount = 0
        var unexpectedCloseCount = 0
        var disconnectStartedAtMs: Long? = null
        var lastFailureMessage: String? = null
        val ownsChannel = sharedChannel == null

        while (true) {
            // Потоковые сценарии могут оборваться. Мы не прячем этот факт:
            // считаем неожиданные закрытия, пробуем переподключиться и потом
            // кладем эти числа в connectionTelemetry.
            val channel = sharedChannel ?: grpcChannel(endpointConfig)
            try {
                val stub = ExperimentGrpcServiceGrpcKt.ExperimentGrpcServiceCoroutineStub(channel)
                stub.streamScenario(request).collect { response ->
                    disconnectStartedAtMs?.let { startedAt ->
                        recoveryDurationsMs += (SystemClock.elapsedRealtime() - startedAt)
                            .coerceAtLeast(0L)
                        disconnectStartedAtMs = null
                    }
                    val normalizedResponse = normalizeGrpcResponse(response, null)
                    val key = normalizedResponse.sequence.takeIf { it > 0 } ?: (responses.size + 1)
                    if (responses.containsKey(key)) return@collect
                    responses[key] = normalizedResponse
                    eventArrivalOffsetsMs += streamOffsetMs(startedAtNanos)
                }
                if (responses.size >= expectedEvents) {
                    break
                }
                unexpectedCloseCount += 1
                lastFailureMessage = "gRPC-поток завершился до доставки всех событий."
            } catch (error: Throwable) {
                unexpectedCloseCount += 1
                lastFailureMessage = error.message ?: "Сбой gRPC-потока."
            } finally {
                if (ownsChannel) {
                    channel.shutdownNow()
                }
            }

            if (responses.size >= expectedEvents) {
                break
            }
            if (responses.isEmpty() && reconnectCount >= methodology.maxReconnectAttempts) {
                throw IllegalStateException(lastFailureMessage ?: "gRPC вернул пустой ответ.")
            }
            if (reconnectCount >= methodology.maxReconnectAttempts) {
                break
            }
            disconnectStartedAtMs = SystemClock.elapsedRealtime()
            reconnectCount += 1
        }

        if (responses.isEmpty()) {
            error(lastFailureMessage ?: "gRPC вернул пустой ответ.")
        }

        val connectionTelemetry = buildStreamConnectionTelemetry(
            expectedEvents = expectedEvents,
            receivedEvents = responses.size,
            reconnectCount = reconnectCount,
            unexpectedCloseCount = unexpectedCloseCount,
            recoveryDurationsMs = recoveryDurationsMs.toList(),
            eventArrivalOffsetsMs = eventArrivalOffsetsMs.toList(),
        )

        return responses.values.toList().toGrpcExecutionResult(
            protocol = ProtocolType.GRPC,
            scenario = scenario,
            clientDurationMicros = elapsedMicros(startedAtNanos),
            connectionTelemetry = connectionTelemetry,
        )
    }

    private suspend fun executeGrpcLongSession(
        endpointConfig: BackendEndpointConfig,
        spec: ScenarioRequestSpec,
        methodology: MeasurementMethodologyProfile,
        sharedChannel: ManagedChannel? = null,
    ): ScenarioExecutionResult {
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val expectedHeartbeats = spec.eventCount.coerceAtLeast(1)
        val responses = mutableListOf<GrpcScenarioResponse>()
        val heartbeatRttsMs = mutableListOf<Long>()
        val recoveryDurationsMs = mutableListOf<Long>()
        var reconnectCount = 0
        var unexpectedCloseCount = 0
        var lastFailureMessage: String? = null
        val ownsBaseChannel = sharedChannel == null
        var currentChannel = sharedChannel ?: grpcChannel(endpointConfig)
        var stopSession = false

        for (heartbeatIndex in 0 until expectedHeartbeats) {
            if (stopSession) break
            if (heartbeatIndex > 0) {
                delay(DEFAULT_HEARTBEAT_INTERVAL_MS)
            }

            val heartbeatMetadata = buildHeartbeatStepMetadata(
                heartbeatIndex = heartbeatIndex + 1,
                heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS,
            )
            val request = spec.toGrpcRequest(
                scenario = ScenarioType.S9_LONG_SESSION,
                extraMetadata = heartbeatMetadata,
            )
            val sendStartedAtNanos = SystemClock.elapsedRealtimeNanos()

            try {
                val stub = ExperimentGrpcServiceGrpcKt.ExperimentGrpcServiceCoroutineStub(currentChannel)
                val response = stub.executeScenario(request)
                val ackNonce = response.metadataMap["heartbeatNonce"]
                if (ackNonce != heartbeatMetadata["heartbeatNonce"]) {
                    throw IllegalStateException("Служебный сигнал gRPC подтвердился с несовпадающим nonce.")
                }
                responses += response
                heartbeatRttsMs += elapsedMillisFrom(sendStartedAtNanos)
            } catch (error: Throwable) {
                unexpectedCloseCount += 1
                lastFailureMessage = error.message ?: "Сбой служебного сигнала gRPC."
                if (sharedChannel != null || reconnectCount >= methodology.maxReconnectAttempts) {
                    stopSession = true
                    continue
                }
                val reconnectStartedAtMs = SystemClock.elapsedRealtime()
                reconnectCount += 1
                currentChannel.shutdownNow()
                currentChannel = grpcChannel(endpointConfig)
                currentChannel.awaitReady()
                recoveryDurationsMs += (SystemClock.elapsedRealtime() - reconnectStartedAtMs)
                    .coerceAtLeast(0L)
            }
        }

        if (ownsBaseChannel) {
            currentChannel.shutdownNow()
        }
        if (responses.isEmpty()) {
            error(lastFailureMessage ?: "gRPC не вернул ни одного служебного сигнала.")
        }

        val connectionTelemetry = buildHeartbeatConnectionTelemetry(
            expectedEvents = expectedHeartbeats,
            receivedEvents = responses.size,
            reconnectCount = reconnectCount,
            unexpectedCloseCount = unexpectedCloseCount,
            recoveryDurationsMs = recoveryDurationsMs.toList(),
            heartbeatRttsMs = heartbeatRttsMs.toList(),
        )

        return responses.toGrpcExecutionResult(
            protocol = ProtocolType.GRPC,
            scenario = ScenarioType.S9_LONG_SESSION,
            clientDurationMicros = elapsedMicros(startedAtNanos),
            connectionTelemetry = connectionTelemetry,
        )
    }

    private fun retrofit(endpointConfig: BackendEndpointConfig): Retrofit {
        return Retrofit.Builder()
            .baseUrl(endpointConfig.httpBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(jsonContentType))
            .build()
    }

    private fun grpcChannel(endpointConfig: BackendEndpointConfig): ManagedChannel {
        return OkHttpChannelBuilder
            .forAddress(endpointConfig.host, endpointConfig.grpcPort)
            .usePlaintext()
            .build()
    }

    private fun buildGraphQlRequest(
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
    ): GraphQlRequestDto {
        return GraphQlRequestDto(
            query = buildGraphQlQuery(scenario),
            variables = spec.toGraphQlVariables(scenario),
        )
    }

    private fun buildGraphQlQuery(
        scenario: ScenarioType,
    ): String {
        val operation = when (scenario) {
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            -> "mutation"

            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> "subscription"

            else -> "query"
        }
        val field = when (scenario) {
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            -> "executeScenario"

            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> "subscribeScenario"

            else -> "scenario"
        }
        val structuredSelection = when (scenario) {
            ScenarioType.S2_LARGE_READ -> documentGraphQlSelection()
            ScenarioType.S3_PARTIAL_LARGE_READ -> previewGraphQlSelection()
            ScenarioType.S4_PAGE_READ -> pageGraphQlSelection()
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> streamEventGraphQlSelection()
            else -> ""
        }
        return """
            $operation {
              $field(
                scenario: ${'$'}scenario,
                requestId: ${'$'}requestId,
                correlationId: ${'$'}correlationId,
                sessionId: ${'$'}sessionId,
                payloadSizeBytes: ${'$'}payloadSizeBytes,
                eventCount: ${'$'}eventCount,
                qClass: ${'$'}qClass,
                loadProfile: ${'$'}loadProfile,
                metadata: ${'$'}metadata,
                payload: ${'$'}payload
              ) {
                requestId
                correlationId
                sessionId
                scenario
                transport
                canonicalOperation
                status
                payloadSizeBytes
                payloadChecksum
                sequence
                acceptedAtEpochMs
                completedAtEpochMs
                serverProcessingTimeMs
                serverProcessingTimeMicros
                payload
                $structuredSelection
                metadata
              }
            }
        """.trimIndent()
    }

    private fun buildSoapEnvelope(
        scenario: ScenarioType,
        spec: ScenarioRequestSpec,
    ): String {
        return """
            <soapenv:Envelope xmlns:soapenv="$SOAP_NS" xmlns:svc="$SERVICE_NS">
              <soapenv:Header>
                <svc:CorrelationId>${spec.correlationId}</svc:CorrelationId>
              </soapenv:Header>
              <soapenv:Body>
                <svc:ExecuteScenarioRequest>
                  <svc:Scenario>${scenario.code}</svc:Scenario>
                  <svc:RequestId>${spec.requestId}</svc:RequestId>
                  ${spec.sessionId?.let { "<svc:SessionId>$it</svc:SessionId>" } ?: ""}
                  ${spec.payloadSizeBytes?.let { "<svc:PayloadSizeBytes>$it</svc:PayloadSizeBytes>" } ?: ""}
                  <svc:EventCount>${spec.eventCount}</svc:EventCount>
                  <svc:QClass>${spec.qClass}</svc:QClass>
                  <svc:LoadProfile>${spec.loadProfile}</svc:LoadProfile>
                  ${metadataEnvelope(spec.requestMetadata(scenario))}
                  ${spec.payload?.let { "<svc:Payload>${xmlEscape(it)}</svc:Payload>" } ?: ""}
                </svc:ExecuteScenarioRequest>
              </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()
    }

    private fun parseSoapSuccess(xml: String): ScenarioResponseDto {
        val document = parseXml(xml)
        val responseElement = document.getElementsByTagNameNS(SERVICE_NS, "ExecuteScenarioResponse")
            .item(0) as? Element
            ?: error("В SOAP-ответе отсутствует тело ExecuteScenarioResponse.")
        return ScenarioResponseDto(
            requestId = responseElement.childText("RequestId") ?: error("В SOAP-ответе отсутствует RequestId."),
            correlationId = document.firstText("CorrelationId") ?: error("В SOAP-ответе отсутствует CorrelationId."),
            sessionId = responseElement.childText("SessionId"),
            scenario = scenarioFromCode(responseElement.childText("Scenario") ?: "S1"),
            transport = ProtocolType.valueOf(responseElement.childText("Transport") ?: ProtocolType.SOAP.name),
            canonicalOperation = responseElement.childText("CanonicalOperation").orEmpty(),
            status = responseElement.childText("Status").orEmpty(),
            payloadSizeBytes = responseElement.childText("PayloadSizeBytes")?.toIntOrNull() ?: 0,
            payloadChecksum = responseElement.childText("PayloadChecksum").orEmpty(),
            sequence = responseElement.childText("Sequence")?.toIntOrNull(),
            acceptedAtEpochMs = responseElement.childText("AcceptedAtEpochMs")?.toLongOrNull() ?: 0L,
            completedAtEpochMs = responseElement.childText("CompletedAtEpochMs")?.toLongOrNull() ?: 0L,
            serverProcessingTimeMs = responseElement.childText("ServerProcessingTimeMs")?.toLongOrNull() ?: 0L,
            serverProcessingTimeMicros = responseElement.childText("ServerProcessingTimeMicros")?.toLongOrNull(),
            payload = responseElement.childText("Payload"),
            document = responseElement.firstDirectChild("Document")?.toLargeReadDocumentDto(),
            preview = responseElement.firstDirectChild("Preview")?.toLargeReadPreviewDto(),
            page = responseElement.firstDirectChild("Page")?.toPageReadPageDto(),
            metadata = responseElement.metadataEntries(),
        )
    }

    private fun parseSoapFault(xml: String): String {
        val document = parseXml(xml)
        val faults = document.getElementsByTagName("faultstring")
        if (faults.length == 0) return "Сбой выполнения SOAP-запроса."
        return faults.item(0)?.textContent?.trim().orEmpty()
    }

    private fun parseXml(xml: String): Document {
        return secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
    }

    private fun buildScenarioSpec(scenario: ScenarioType): ScenarioRequestSpec {
        val requestId = UUID.randomUUID().toString()
        // Все протоколы получают один и тот же логический запрос.
        // Для сценариев записи заранее создаем строку нужного размера, чтобы
        // REST, SOAP, GraphQL, WebSocket и gRPC отправляли сопоставимое тело.
        val payload = if (scenario in writeScenarios()) {
            payloadOfExactSize(
                sizeBytes = scenario.payloadSizeBytes,
                seed = "${scenario.code.lowercase()}-$requestId",
            )
        } else {
            null
        }
        return ScenarioRequestSpec(
            requestId = requestId,
            correlationId = UUID.randomUUID().toString(),
            sessionId = if (scenario in streamingAndSessionScenarios()) {
                UUID.randomUUID().toString()
            } else {
                null
            },
            payloadSizeBytes = scenario.payloadSizeBytes,
            eventCount = scenario.eventCount,
            qClass = scenario.qClass,
            loadProfile = scenario.loadProfile,
            payload = payload,
        )
    }

    internal data class ScenarioRequestSpec(
        val requestId: String,
        val correlationId: String,
        val sessionId: String?,
        val payloadSizeBytes: Int?,
        val eventCount: Int,
        val qClass: String,
        val loadProfile: String,
        val payload: String?,
    ) {
        fun requestMetadata(
            scenario: ScenarioType,
            extraMetadata: Map<String, String> = emptyMap(),
        ): Map<String, String> {
            // Метаданные - это маленькая "наклейка" на запросе. По ней потом
            // проще понять, какой сценарий выполнялся и совпало ли отправленное
            // тело с тем, что подтвердил backend.
            val baseMetadata = linkedMapOf(
                "client" to "android",
                "runner" to "network-protocols-app",
                "scenarioCode" to scenario.code,
            )
            when (scenario) {
                ScenarioType.S5_SMALL_WRITE_ACK,
                ScenarioType.S6_LARGE_WRITE_ACK,
                -> {
                    val sentPayload = payload.orEmpty()
                    baseMetadata["payloadExpectedBytes"] = (payloadSizeBytes ?: sentPayload.utf8SizeBytes()).toString()
                    baseMetadata["payloadSentBytes"] = sentPayload.utf8SizeBytes().toString()
                    baseMetadata["payloadSentChecksum"] = sha256(sentPayload)
                }

                ScenarioType.S7_EVENT_STREAM,
                ScenarioType.S8_HEAVY_EVENT_STREAM,
                -> {
                    val intervalMs = if (scenario == ScenarioType.S8_HEAVY_EVENT_STREAM) {
                        DEFAULT_HEAVY_STREAM_EVENT_INTERVAL_MS
                    } else {
                        DEFAULT_STREAM_EVENT_INTERVAL_MS
                    }
                    baseMetadata["eventIntervalMs"] = intervalMs.toString()
                    baseMetadata["expectedEvents"] = eventCount.toString()
                }

                ScenarioType.S9_LONG_SESSION -> {
                    baseMetadata["heartbeatIntervalMs"] = DEFAULT_HEARTBEAT_INTERVAL_MS.toString()
                    baseMetadata["expectedHeartbeats"] = eventCount.toString()
                }

                else -> Unit
            }
            extraMetadata.forEach { (key, value) ->
                baseMetadata[key] = value
            }
            return baseMetadata
        }

        fun toRequestDto(
            scenario: ScenarioType,
            extraMetadata: Map<String, String> = emptyMap(),
        ): ScenarioRequestDto = ScenarioRequestDto(
            requestId = requestId,
            correlationId = correlationId,
            sessionId = sessionId,
            scenario = scenario,
            payloadSizeBytes = payloadSizeBytes,
            eventCount = eventCount,
            qClass = qClass,
            loadProfile = loadProfile,
            failureMode = FailureMode.NONE,
            metadata = requestMetadata(scenario, extraMetadata),
            payload = payload,
        )

        fun toGraphQlVariables(
            scenario: ScenarioType,
            extraMetadata: Map<String, String> = emptyMap(),
        ) = buildJsonObject {
            put("scenario", scenario.code)
            put("requestId", requestId)
            put("correlationId", correlationId)
            sessionId?.let { put("sessionId", it) }
            payloadSizeBytes?.let { put("payloadSizeBytes", it) }
            put("eventCount", eventCount)
            put("qClass", qClass)
            put("loadProfile", loadProfile)
            put("metadata", buildJsonObject {
                requestMetadata(scenario, extraMetadata).forEach { (key, value) ->
                    put(key, value)
                }
            })
            payload?.let { put("payload", it) }
        }

        fun toGrpcRequest(
            scenario: ScenarioType,
            extraMetadata: Map<String, String> = emptyMap(),
        ): GrpcScenarioRequest {
            return GrpcScenarioRequest.newBuilder()
                .setRequestId(requestId)
                .setCorrelationId(correlationId)
                .setScenario(scenario.toGrpcScenario())
                .setEventCount(eventCount)
                .setQClass(qClass)
                .setLoadProfile(loadProfile)
                .setFailureMode(GrpcFailureMode.GRPC_FAILURE_NONE)
                .putAllMetadata(requestMetadata(scenario, extraMetadata))
                .apply {
                    sessionId?.let(::setSessionId)
                    payloadSizeBytes?.let(::setPayloadSizeBytes)
                    payload?.let(::setPayload)
                }
                .build()
        }
    }

    private interface HealthApi {
        @GET("health")
        suspend fun health(): ServiceStatusResponseDto
    }

    private interface RestApi {
        @POST("api/rest/scenarios/{scenario}")
        suspend fun execute(
            @Path("scenario") scenario: String,
            @Body request: ScenarioRequestDto,
        ): ScenarioResponseDto
    }

    private interface GraphQlApi {
        @POST("api/graphql")
        suspend fun execute(
            @Body request: GraphQlRequestDto,
        ): GraphQlResponseDto
    }

    private companion object {
        const val SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
        const val SERVICE_NS = "urn:meeweel:network_protocols_backend"
    }
}

private fun List<ScenarioResponseDto>.toExecutionResult(
    protocol: ProtocolType,
    scenario: ScenarioType,
    clientDurationMicros: Long,
    connectionTelemetry: ScenarioConnectionTelemetry? = null,
): ScenarioExecutionResult {
    val last = last()
    val serverDurationMicros = sumOf(::serverProcessingMicros)
    return ScenarioExecutionResult(
        protocol = protocol,
        scenario = scenario,
        clientDurationMs = clientDurationMicros / 1_000L,
        serverDurationMs = serverDurationMicros / 1_000L,
        clientDurationMicros = clientDurationMicros,
        serverDurationMicros = serverDurationMicros,
        responseCount = size,
        requestId = last.requestId,
        correlationId = last.correlationId,
        details = buildExecutionDetails(
            scenario = scenario,
            clientDurationMicros = clientDurationMicros,
            serverDurationMicros = serverDurationMicros,
            connectionTelemetry = connectionTelemetry,
        ),
        connectionTelemetry = connectionTelemetry,
        auditFields = buildAuditFields(
            scenario = scenario,
            response = last,
            responseCount = size,
            connectionTelemetry = connectionTelemetry,
        ),
    )
}

private fun ScenarioResponseDto.toExecutionResult(
    protocol: ProtocolType,
    scenario: ScenarioType,
    clientDurationMicros: Long,
    responseCount: Int,
    connectionTelemetry: ScenarioConnectionTelemetry? = null,
): ScenarioExecutionResult {
    val serverDurationMicros = serverProcessingMicros(this)
    return ScenarioExecutionResult(
        protocol = protocol,
        scenario = scenario,
        clientDurationMs = clientDurationMicros / 1_000L,
        serverDurationMs = serverDurationMicros / 1_000L,
        clientDurationMicros = clientDurationMicros,
        serverDurationMicros = serverDurationMicros,
        responseCount = responseCount,
        requestId = requestId,
        correlationId = correlationId,
        details = buildExecutionDetails(
            scenario = scenario,
            clientDurationMicros = clientDurationMicros,
            serverDurationMicros = serverDurationMicros,
            connectionTelemetry = connectionTelemetry,
        ),
        connectionTelemetry = connectionTelemetry,
        auditFields = buildAuditFields(
            scenario = scenario,
            response = this,
            responseCount = responseCount,
            connectionTelemetry = connectionTelemetry,
        ),
    )
}

private fun List<GrpcScenarioResponse>.toGrpcExecutionResult(
    protocol: ProtocolType,
    scenario: ScenarioType,
    clientDurationMicros: Long,
    connectionTelemetry: ScenarioConnectionTelemetry? = null,
): ScenarioExecutionResult {
    val last = last()
    val serverDurationMicros = sumOf(::serverProcessingMicros)
    return ScenarioExecutionResult(
        protocol = protocol,
        scenario = scenario,
        clientDurationMs = clientDurationMicros / 1_000L,
        serverDurationMs = serverDurationMicros / 1_000L,
        clientDurationMicros = clientDurationMicros,
        serverDurationMicros = serverDurationMicros,
        responseCount = size,
        requestId = last.requestId,
        correlationId = last.correlationId,
        details = buildExecutionDetails(
            scenario = scenario,
            clientDurationMicros = clientDurationMicros,
            serverDurationMicros = serverDurationMicros,
            connectionTelemetry = connectionTelemetry,
        ),
        connectionTelemetry = connectionTelemetry,
        auditFields = buildGrpcAuditFields(
            scenario = scenario,
            response = last,
            responseCount = size,
            connectionTelemetry = connectionTelemetry,
        ),
    )
}

private fun normalizeGrpcResponse(
    response: GrpcScenarioResponse,
    fallbackSequence: Int?,
): GrpcScenarioResponse {
    return when {
        fallbackSequence != null && response.sequence == 0 -> response.toBuilder()
            .setSequence(fallbackSequence)
            .build()

        else -> response
    }
}

private fun serverProcessingMicros(response: ScenarioResponseDto): Long {
    return response.serverProcessingTimeMicros ?: response.serverProcessingTimeMs * 1_000L
}

private fun serverProcessingMicros(response: GrpcScenarioResponse): Long {
    return when {
        response.serverProcessingTimeMicros > 0L -> response.serverProcessingTimeMicros
        else -> response.serverProcessingTimeMs * 1_000L
    }
}

private fun elapsedMicros(startedAtNanos: Long): Long {
    return ((SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000L).coerceAtLeast(0L)
}

private fun streamOffsetMs(startedAtNanos: Long): Long {
    return (elapsedMicros(startedAtNanos) / 1_000L).coerceAtLeast(0L)
}

private fun elapsedMillisFrom(startedAtNanos: Long): Long {
    return ((SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
}

private fun buildStreamConnectionTelemetry(
    expectedEvents: Int,
    receivedEvents: Int,
    reconnectCount: Int = 0,
    unexpectedCloseCount: Int = 0,
    recoveryDurationsMs: List<Long> = emptyList(),
    eventArrivalOffsetsMs: List<Long>,
): ScenarioConnectionTelemetry {
    return ScenarioConnectionTelemetry(
        expectedEvents = expectedEvents,
        receivedEvents = receivedEvents,
        reconnectCount = reconnectCount,
        unexpectedCloseCount = unexpectedCloseCount,
        recoveryDurationsMs = recoveryDurationsMs,
        timeToFirstEventMs = eventArrivalOffsetsMs.firstOrNull(),
        streamCompletionMs = eventArrivalOffsetsMs.lastOrNull(),
        interEventGapsMs = eventArrivalOffsetsMs.zipWithNext { left, right ->
            (right - left).coerceAtLeast(0L)
        },
    )
}

private fun buildHeartbeatConnectionTelemetry(
    expectedEvents: Int,
    receivedEvents: Int,
    reconnectCount: Int = 0,
    unexpectedCloseCount: Int = 0,
    recoveryDurationsMs: List<Long> = emptyList(),
    heartbeatRttsMs: List<Long>,
): ScenarioConnectionTelemetry {
    return ScenarioConnectionTelemetry(
        expectedEvents = expectedEvents,
        receivedEvents = receivedEvents,
        reconnectCount = reconnectCount,
        unexpectedCloseCount = unexpectedCloseCount,
        recoveryDurationsMs = recoveryDurationsMs,
        heartbeatSent = expectedEvents,
        heartbeatAcknowledged = receivedEvents,
        heartbeatRttsMs = heartbeatRttsMs,
    )
}

private fun buildHeartbeatStepMetadata(
    heartbeatIndex: Int,
    heartbeatIntervalMs: Long,
): Map<String, String> {
    return mapOf(
        "heartbeatIndex" to heartbeatIndex.toString(),
        "heartbeatNonce" to UUID.randomUUID().toString(),
        "heartbeatIntervalMs" to heartbeatIntervalMs.toString(),
    )
}

private fun buildAuditFields(
    scenario: ScenarioType,
    response: ScenarioResponseDto,
    responseCount: Int,
    connectionTelemetry: ScenarioConnectionTelemetry?,
): Map<String, String> {
    return when (scenario) {
        ScenarioType.S5_SMALL_WRITE_ACK,
        ScenarioType.S6_LARGE_WRITE_ACK,
        -> linkedMapOf(
            "payload_expected_bytes" to (response.metadata["payloadExpectedBytes"] ?: response.payloadSizeBytes.toString()),
            "payload_sent_bytes" to (response.metadata["payloadSentBytes"] ?: response.payloadSizeBytes.toString()),
            "payload_accepted_bytes" to response.payloadSizeBytes.toString(),
            "payload_sent_checksum" to (response.metadata["payloadSentChecksum"] ?: response.payloadChecksum),
            "payload_accepted_checksum" to response.payloadChecksum,
        )

        ScenarioType.S7_EVENT_STREAM,
        ScenarioType.S8_HEAVY_EVENT_STREAM,
        -> linkedMapOf(
            "events_expected" to (response.metadata["expectedEvents"] ?: (connectionTelemetry?.expectedEvents ?: responseCount).toString()),
            "events_received" to (connectionTelemetry?.receivedEvents ?: responseCount).toString(),
            "event_interval_ms" to (response.metadata["eventIntervalMs"] ?: if (scenario == ScenarioType.S8_HEAVY_EVENT_STREAM) DEFAULT_HEAVY_STREAM_EVENT_INTERVAL_MS.toString() else DEFAULT_STREAM_EVENT_INTERVAL_MS.toString()),
            "time_to_first_ms" to (connectionTelemetry?.timeToFirstEventMs?.toString() ?: "n/a"),
            "stream_completion_ms" to (connectionTelemetry?.streamCompletionMs?.toString() ?: "n/a"),
        )

        ScenarioType.S9_LONG_SESSION -> linkedMapOf(
            "session_id" to (response.sessionId ?: "n/a"),
            "heartbeat_expected" to (response.metadata["expectedHeartbeats"] ?: (connectionTelemetry?.expectedEvents ?: responseCount).toString()),
            "heartbeat_acknowledged" to (connectionTelemetry?.receivedEvents ?: responseCount).toString(),
            "heartbeat_interval_ms" to (response.metadata["heartbeatIntervalMs"] ?: DEFAULT_HEARTBEAT_INTERVAL_MS.toString()),
            "heartbeat_last_index" to (response.metadata["heartbeatIndex"] ?: responseCount.toString()),
        )

        else -> emptyMap()
    }
}

private fun buildGrpcAuditFields(
    scenario: ScenarioType,
    response: GrpcScenarioResponse,
    responseCount: Int,
    connectionTelemetry: ScenarioConnectionTelemetry?,
): Map<String, String> {
    return when (scenario) {
        ScenarioType.S5_SMALL_WRITE_ACK,
        ScenarioType.S6_LARGE_WRITE_ACK,
        -> linkedMapOf(
            "payload_expected_bytes" to (response.metadataMap["payloadExpectedBytes"] ?: response.payloadSizeBytes.toString()),
            "payload_sent_bytes" to (response.metadataMap["payloadSentBytes"] ?: response.payloadSizeBytes.toString()),
            "payload_accepted_bytes" to response.payloadSizeBytes.toString(),
            "payload_sent_checksum" to (response.metadataMap["payloadSentChecksum"] ?: response.payloadChecksum),
            "payload_accepted_checksum" to response.payloadChecksum,
        )

        ScenarioType.S7_EVENT_STREAM,
        ScenarioType.S8_HEAVY_EVENT_STREAM,
        -> linkedMapOf(
            "events_expected" to (response.metadataMap["expectedEvents"] ?: (connectionTelemetry?.expectedEvents ?: responseCount).toString()),
            "events_received" to (connectionTelemetry?.receivedEvents ?: responseCount).toString(),
            "event_interval_ms" to (response.metadataMap["eventIntervalMs"] ?: if (scenario == ScenarioType.S8_HEAVY_EVENT_STREAM) DEFAULT_HEAVY_STREAM_EVENT_INTERVAL_MS.toString() else DEFAULT_STREAM_EVENT_INTERVAL_MS.toString()),
            "time_to_first_ms" to (connectionTelemetry?.timeToFirstEventMs?.toString() ?: "n/a"),
            "stream_completion_ms" to (connectionTelemetry?.streamCompletionMs?.toString() ?: "n/a"),
        )

        ScenarioType.S9_LONG_SESSION -> linkedMapOf(
            "session_id" to response.sessionId,
            "heartbeat_expected" to (response.metadataMap["expectedHeartbeats"] ?: (connectionTelemetry?.expectedEvents ?: responseCount).toString()),
            "heartbeat_acknowledged" to (connectionTelemetry?.receivedEvents ?: responseCount).toString(),
            "heartbeat_interval_ms" to (response.metadataMap["heartbeatIntervalMs"] ?: DEFAULT_HEARTBEAT_INTERVAL_MS.toString()),
            "heartbeat_last_index" to (response.metadataMap["heartbeatIndex"] ?: responseCount.toString()),
        )

        else -> emptyMap()
    }
}

private fun buildExecutionDetails(
    scenario: ScenarioType,
    clientDurationMicros: Long,
    serverDurationMicros: Long,
    connectionTelemetry: ScenarioConnectionTelemetry?,
): String {
    val clientMs = clientDurationMicros.toDouble() / 1_000.0
    val serverMs = serverDurationMicros.toDouble() / 1_000.0
    return buildString {
        append(scenario.code)
        append(' ')
        append(scenario.shortTitle)
        append(" • ")
        append(clientMs.formatTo(2))
        append(" мс клиент • ")
        append(serverMs.formatTo(2))
        append(" мс сервер")
        connectionTelemetry?.let { telemetry ->
            append(" • потери ")
            append((telemetry.lossRate * 100.0).formatTo(1))
            append(" %")
            if (telemetry.reconnectCount > 0) {
                append(" • переподключения ")
                append(telemetry.reconnectCount)
            }
            when (scenario) {
                ScenarioType.S7_EVENT_STREAM,
                ScenarioType.S8_HEAVY_EVENT_STREAM,
                -> {
                    telemetry.timeToFirstEventMs?.let {
                        append(" • первый ")
                        append(it)
                        append(" мс")
                    }
                    telemetry.streamCompletionMs?.let {
                        append(" • поток ")
                        append(it)
                        append(" мс")
                    }
                }

                ScenarioType.S9_LONG_SESSION -> {
                    telemetry.heartbeatRttsMs.percentileOrNull(0.95)?.let {
                        append(" • сигнал p95 ")
                        append(it.formatTo(2))
                        append(" мс")
                    }
                }

                else -> Unit
            }
        }
    }
}

private fun Double.formatTo(scale: Int): String {
    return "%.${scale}f".format(this)
}

private fun payloadOfExactSize(
    sizeBytes: Int,
    seed: String,
): String {
    val builder = StringBuilder(sizeBytes)
    var chunkIndex = 1
    while (builder.length < sizeBytes) {
        builder.append(seed)
        builder.append('-')
        builder.append(chunkIndex.toString().padStart(4, '0'))
        builder.append('|')
        chunkIndex += 1
    }
    return builder.substring(0, sizeBytes)
}

private fun String.utf8SizeBytes(): Int {
    return toByteArray(StandardCharsets.UTF_8).size
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun List<Long>.percentileOrNull(percentile: Double): Double? {
    if (isEmpty()) return null
    val rank = (size * percentile).toInt().coerceAtLeast(1)
    return sorted()[rank.coerceAtMost(size) - 1].toDouble()
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory {
    return DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        safelySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        safelySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        safelySetFeature("http://xml.org/sax/features/external-general-entities", false)
        safelySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        safelySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }
}

private fun DocumentBuilderFactory.safelySetFeature(
    name: String,
    value: Boolean,
) {
    runCatching { setFeature(name, value) }
}

private fun Document.firstText(localName: String): String? {
    val nodes = getElementsByTagNameNS("urn:meeweel:network_protocols_backend", localName)
    if (nodes.length == 0) return null
    return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
}

private fun Element.childText(localName: String): String? {
    val nodes = getElementsByTagNameNS("urn:meeweel:network_protocols_backend", localName)
    if (nodes.length == 0) return null
    return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
}

private fun Element.firstDirectChild(localName: String): Element? {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element &&
            child.localName == localName &&
            child.namespaceURI == "urn:meeweel:network_protocols_backend"
        ) {
            return child
        }
    }
    return null
}

private fun Element.directChildren(localName: String): List<Element> {
    val result = mutableListOf<Element>()
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element &&
            child.localName == localName &&
            child.namespaceURI == "urn:meeweel:network_protocols_backend"
        ) {
            result += child
        }
    }
    return result
}

private fun Element.metadataEntries(): Map<String, String> {
    val metadataElement = firstDirectChild("Metadata") ?: return emptyMap()
    val entries = linkedMapOf<String, String>()
    val nodes = metadataElement.getElementsByTagNameNS("urn:meeweel:network_protocols_backend", "Entry")
    for (index in 0 until nodes.length) {
        val node = nodes.item(index) as? Element ?: continue
        val key = node.getAttribute("key").trim()
        if (key.isNotBlank()) {
            entries[key] = node.textContent?.trim().orEmpty()
        }
    }
    return entries
}

private fun Element.toLargeReadDocumentDto(): LargeReadDocumentDto {
    return LargeReadDocumentDto(
        documentId = childText("DocumentId").orEmpty(),
        externalId = childText("ExternalId").orEmpty(),
        revision = childText("Revision")?.toIntOrNull() ?: 0,
        generatedAtEpochMs = childText("GeneratedAtEpochMs")?.toLongOrNull() ?: 0L,
        locale = childText("Locale").orEmpty(),
        currency = childText("Currency").orEmpty(),
        title = childText("Title").orEmpty(),
        subtitle = childText("Subtitle").orEmpty(),
        category = childText("Category").orEmpty(),
        status = childText("Status").orEmpty(),
        owner = firstDirectChild("Owner")?.toLargeReadPartyDto() ?: LargeReadPartyDto(
            partyId = "",
            displayName = "",
            role = "",
            organization = "",
            segment = "",
            rating = 0.0,
        ),
        contacts = firstDirectChild("Contacts")
            ?.directChildren("Contact")
            ?.map(Element::toLargeReadContactDto)
            .orEmpty(),
        tags = firstDirectChild("Tags")
            ?.directChildren("Tag")
            ?.mapNotNull { it.textContent?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty(),
        flags = firstDirectChild("Flags")
            ?.directChildren("Flag")
            ?.mapNotNull { it.textContent?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty(),
        attributes = firstDirectChild("Attributes")
            ?.directChildren("Attribute")
            ?.map(Element::toLargeReadAttributeDto)
            .orEmpty(),
        parameterGroups = firstDirectChild("ParameterGroups")
            ?.directChildren("ParameterGroup")
            ?.map(Element::toLargeReadParameterGroupDto)
            .orEmpty(),
        lineItems = firstDirectChild("LineItems")
            ?.directChildren("LineItem")
            ?.map(Element::toLargeReadLineItemDto)
            .orEmpty(),
        relatedEntities = firstDirectChild("RelatedEntities")
            ?.directChildren("RelatedEntity")
            ?.map(Element::toLargeReadRelatedEntityDto)
            .orEmpty(),
        attachments = firstDirectChild("Attachments")
            ?.directChildren("Attachment")
            ?.map(Element::toLargeReadAttachmentDto)
            .orEmpty(),
        timeline = firstDirectChild("Timeline")
            ?.directChildren("TimelineEntry")
            ?.map(Element::toLargeReadTimelineEntryDto)
            .orEmpty(),
        metrics = firstDirectChild("Metrics")?.toLargeReadMetricsDto() ?: LargeReadMetricsDto(
            summaryScore = 0.0,
            riskScore = 0.0,
            completenessPct = 0.0,
            freshnessHours = 0.0,
            responseItems = 0,
            attachmentBytes = 0L,
            warnings = 0,
        ),
        notes = firstDirectChild("Notes")
            ?.directChildren("Note")
            ?.mapNotNull { it.textContent?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty(),
        narrative = childText("Narrative").orEmpty(),
    )
}

private fun Element.toLargeReadPartyDto(): LargeReadPartyDto {
    return LargeReadPartyDto(
        partyId = childText("PartyId").orEmpty(),
        displayName = childText("DisplayName").orEmpty(),
        role = childText("Role").orEmpty(),
        organization = childText("Organization").orEmpty(),
        segment = childText("Segment").orEmpty(),
        rating = childText("Rating")?.toDoubleOrNull() ?: 0.0,
    )
}

private fun Element.toLargeReadContactDto(): LargeReadContactDto {
    return LargeReadContactDto(
        kind = childText("Kind").orEmpty(),
        label = childText("Label").orEmpty(),
        value = childText("Value").orEmpty(),
        preferred = childText("Preferred")?.toBooleanStrictOrNull() ?: false,
        availability = childText("Availability").orEmpty(),
    )
}

private fun Element.toLargeReadAttributeDto(): LargeReadAttributeDto {
    return LargeReadAttributeDto(
        code = childText("Code").orEmpty(),
        name = childText("Name").orEmpty(),
        value = childText("Value").orEmpty(),
        unit = childText("Unit"),
        category = childText("Category").orEmpty(),
        searchable = childText("Searchable")?.toBooleanStrictOrNull() ?: false,
    )
}

private fun Element.toLargeReadParameterGroupDto(): LargeReadParameterGroupDto {
    return LargeReadParameterGroupDto(
        groupCode = childText("GroupCode").orEmpty(),
        groupTitle = childText("GroupTitle").orEmpty(),
        editable = childText("Editable")?.toBooleanStrictOrNull() ?: false,
        parameters = firstDirectChild("Parameters")
            ?.directChildren("Parameter")
            ?.map(Element::toLargeReadParameterDto)
            .orEmpty(),
    )
}

private fun Element.toLargeReadParameterDto(): LargeReadParameterDto {
    return LargeReadParameterDto(
        key = childText("Key").orEmpty(),
        title = childText("Title").orEmpty(),
        valueType = childText("ValueType").orEmpty(),
        value = childText("Value").orEmpty(),
        unit = childText("Unit"),
        required = childText("Required")?.toBooleanStrictOrNull() ?: false,
        source = childText("Source").orEmpty(),
    )
}

private fun Element.toLargeReadLineItemDto(): LargeReadLineItemDto {
    return LargeReadLineItemDto(
        itemId = childText("ItemId").orEmpty(),
        sku = childText("Sku").orEmpty(),
        title = childText("Title").orEmpty(),
        category = childText("Category").orEmpty(),
        quantity = childText("Quantity")?.toIntOrNull() ?: 0,
        unit = childText("Unit").orEmpty(),
        unitPrice = childText("UnitPrice")?.toDoubleOrNull() ?: 0.0,
        totalPrice = childText("TotalPrice")?.toDoubleOrNull() ?: 0.0,
        availabilityStatus = childText("AvailabilityStatus").orEmpty(),
        tags = firstDirectChild("Tags")
            ?.directChildren("Tag")
            ?.mapNotNull { it.textContent?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty(),
    )
}

private fun Element.toLargeReadRelatedEntityDto(): LargeReadRelatedEntityDto {
    return LargeReadRelatedEntityDto(
        entityId = childText("EntityId").orEmpty(),
        relationType = childText("RelationType").orEmpty(),
        title = childText("Title").orEmpty(),
        status = childText("Status").orEmpty(),
        priority = childText("Priority").orEmpty(),
    )
}

private fun Element.toLargeReadAttachmentDto(): LargeReadAttachmentDto {
    return LargeReadAttachmentDto(
        attachmentId = childText("AttachmentId").orEmpty(),
        fileName = childText("FileName").orEmpty(),
        mimeType = childText("MimeType").orEmpty(),
        sizeBytes = childText("SizeBytes")?.toLongOrNull() ?: 0L,
        checksum = childText("Checksum").orEmpty(),
        sourceSystem = childText("SourceSystem").orEmpty(),
    )
}

private fun Element.toLargeReadTimelineEntryDto(): LargeReadTimelineEntryDto {
    return LargeReadTimelineEntryDto(
        eventCode = childText("EventCode").orEmpty(),
        title = childText("Title").orEmpty(),
        actor = childText("Actor").orEmpty(),
        occurredAtEpochMs = childText("OccurredAtEpochMs")?.toLongOrNull() ?: 0L,
        status = childText("Status").orEmpty(),
        description = childText("Description").orEmpty(),
    )
}

private fun Element.toLargeReadMetricsDto(): LargeReadMetricsDto {
    return LargeReadMetricsDto(
        summaryScore = childText("SummaryScore")?.toDoubleOrNull() ?: 0.0,
        riskScore = childText("RiskScore")?.toDoubleOrNull() ?: 0.0,
        completenessPct = childText("CompletenessPct")?.toDoubleOrNull() ?: 0.0,
        freshnessHours = childText("FreshnessHours")?.toDoubleOrNull() ?: 0.0,
        responseItems = childText("ResponseItems")?.toIntOrNull() ?: 0,
        attachmentBytes = childText("AttachmentBytes")?.toLongOrNull() ?: 0L,
        warnings = childText("Warnings")?.toIntOrNull() ?: 0,
    )
}

private fun Element.toLargeReadPreviewDto(): LargeReadPreviewDto {
    return LargeReadPreviewDto(
        documentId = childText("DocumentId").orEmpty(),
        title = childText("Title").orEmpty(),
        status = childText("Status").orEmpty(),
        primaryBadge = childText("PrimaryBadge").orEmpty(),
        summaryScore = childText("SummaryScore")?.toDoubleOrNull() ?: 0.0,
    )
}

private fun Element.toPageReadPageDto(): PageReadPageDto {
    return PageReadPageDto(
        pageNumber = childText("PageNumber")?.toIntOrNull() ?: 0,
        pageSize = childText("PageSize")?.toIntOrNull() ?: 0,
        totalItems = childText("TotalItems")?.toIntOrNull() ?: 0,
        nextCursor = childText("NextCursor"),
        sortBy = childText("SortBy").orEmpty(),
        appliedFilters = firstDirectChild("AppliedFilters")
            ?.directChildren("Filter")
            ?.mapNotNull { it.textContent?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty(),
        summary = firstDirectChild("Summary")?.toPageReadSummaryDto() ?: PageReadSummaryDto(
            totalAmount = 0.0,
            selectedCount = 0,
            highPriorityCount = 0,
            staleCount = 0,
            warningCount = 0,
        ),
        facets = firstDirectChild("Facets")
            ?.directChildren("Facet")
            ?.map(Element::toPageReadFacetDto)
            .orEmpty(),
        items = firstDirectChild("Items")
            ?.directChildren("Item")
            ?.map(Element::toLargeReadPreviewDto)
            .orEmpty(),
    )
}

private fun Element.toPageReadSummaryDto(): PageReadSummaryDto {
    return PageReadSummaryDto(
        totalAmount = childText("TotalAmount")?.toDoubleOrNull() ?: 0.0,
        selectedCount = childText("SelectedCount")?.toIntOrNull() ?: 0,
        highPriorityCount = childText("HighPriorityCount")?.toIntOrNull() ?: 0,
        staleCount = childText("StaleCount")?.toIntOrNull() ?: 0,
        warningCount = childText("WarningCount")?.toIntOrNull() ?: 0,
    )
}

private fun Element.toPageReadFacetDto(): PageReadFacetDto {
    return PageReadFacetDto(
        name = childText("Name").orEmpty(),
        title = childText("Title").orEmpty(),
        buckets = firstDirectChild("Buckets")
            ?.directChildren("Bucket")
            ?.map(Element::toPageReadFacetBucketDto)
            .orEmpty(),
    )
}

private fun Element.toPageReadFacetBucketDto(): PageReadFacetBucketDto {
    return PageReadFacetBucketDto(
        value = childText("Value").orEmpty(),
        count = childText("Count")?.toIntOrNull() ?: 0,
        selected = childText("Selected")?.toBooleanStrictOrNull() ?: false,
    )
}

private fun metadataEnvelope(metadata: Map<String, String>): String {
    if (metadata.isEmpty()) return ""
    val entries = metadata.entries.joinToString(separator = "") { (key, value) ->
        "<svc:Entry key=\"${xmlEscape(key)}\">${xmlEscape(value)}</svc:Entry>"
    }
    return "<svc:Metadata>$entries</svc:Metadata>"
}

private fun scenarioFromCode(code: String): ScenarioType {
    return ScenarioType.entries.firstOrNull { it.code == code.trim().uppercase() }
        ?: ScenarioType.S1_SHORT_READ
}

private fun ScenarioType.toGrpcScenario(): GrpcScenarioType {
    return when (this) {
        ScenarioType.S1_SHORT_READ -> GrpcScenarioType.S1_SHORT_READ
        ScenarioType.S2_LARGE_READ -> GrpcScenarioType.S2_LARGE_READ
        ScenarioType.S3_PARTIAL_LARGE_READ -> GrpcScenarioType.S3_PARTIAL_LARGE_READ
        ScenarioType.S4_PAGE_READ -> GrpcScenarioType.S4_PAGE_READ
        ScenarioType.S5_SMALL_WRITE_ACK -> GrpcScenarioType.S5_SMALL_WRITE_ACK
        ScenarioType.S6_LARGE_WRITE_ACK -> GrpcScenarioType.S6_LARGE_WRITE_ACK
        ScenarioType.S7_EVENT_STREAM -> GrpcScenarioType.S7_EVENT_STREAM
        ScenarioType.S8_HEAVY_EVENT_STREAM -> GrpcScenarioType.S8_HEAVY_EVENT_STREAM
        ScenarioType.S9_LONG_SESSION -> GrpcScenarioType.S9_LONG_SESSION
    }
}

private fun documentGraphQlSelection(): String {
    return """
        document {
          documentId
          externalId
          revision
          generatedAtEpochMs
          locale
          currency
          title
          subtitle
          category
          status
          owner {
            partyId
            displayName
            role
            organization
            segment
            rating
          }
          contacts {
            kind
            label
            value
            preferred
            availability
          }
          tags
          flags
          attributes {
            code
            name
            value
            unit
            category
            searchable
          }
          parameterGroups {
            groupCode
            groupTitle
            editable
            parameters {
              key
              title
              valueType
              value
              unit
              required
              source
            }
          }
          lineItems {
            itemId
            sku
            title
            category
            quantity
            unit
            unitPrice
            totalPrice
            availabilityStatus
            tags
          }
          relatedEntities {
            entityId
            relationType
            title
            status
            priority
          }
          attachments {
            attachmentId
            fileName
            mimeType
            sizeBytes
            checksum
            sourceSystem
          }
          timeline {
            eventCode
            title
            actor
            occurredAtEpochMs
            status
            description
          }
          metrics {
            summaryScore
            riskScore
            completenessPct
            freshnessHours
            responseItems
            attachmentBytes
            warnings
          }
          notes
          narrative
        }
    """.trimIndent()
}

private fun pageGraphQlSelection(): String {
    return """
        page {
          pageNumber
          pageSize
          totalItems
          nextCursor
          sortBy
          appliedFilters
          summary {
            totalAmount
            selectedCount
            highPriorityCount
            staleCount
            warningCount
          }
          facets {
            name
            title
            buckets {
              value
              count
              selected
            }
          }
          items {
            documentId
            title
            status
            primaryBadge
            summaryScore
          }
        }
    """.trimIndent()
}

private fun previewGraphQlSelection(): String {
    return """
        preview {
          documentId
          title
          status
          primaryBadge
          summaryScore
        }
    """.trimIndent()
}

private fun streamEventGraphQlSelection(): String {
    return """
        streamEvent {
          eventId
          eventType
          documentId
          emittedAtEpochMs
          revision
          priority
          preview {
            documentId
            title
            status
            primaryBadge
            summaryScore
          }
          changedFields
          relatedItems {
            documentId
            title
            status
            primaryBadge
            summaryScore
          }
          tags
          notes
          summary {
            impactedItems
            warningCount
            scoreDelta
            currentStatus
          }
        }
    """.trimIndent()
}

private fun writeScenarios(): Set<ScenarioType> {
    return setOf(
        ScenarioType.S5_SMALL_WRITE_ACK,
        ScenarioType.S6_LARGE_WRITE_ACK,
    )
}

private fun graphQlStreamingScenarios(): Set<ScenarioType> {
    return setOf(
        ScenarioType.S7_EVENT_STREAM,
        ScenarioType.S8_HEAVY_EVENT_STREAM,
    )
}

private fun streamingAndSessionScenarios(): Set<ScenarioType> {
    return graphQlStreamingScenarios() + ScenarioType.S9_LONG_SESSION
}

private fun xmlEscape(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private suspend fun ManagedChannel.awaitReady(timeoutMs: Long = 5_000L) {
    withTimeout(timeoutMs) {
        while (true) {
            when (val state = getState(true)) {
                ConnectivityState.READY -> return@withTimeout
                ConnectivityState.SHUTDOWN -> {
                    throw IllegalStateException("gRPC-канал завершен до перехода в READY.")
                }

                else -> suspendCancellableCoroutine<Unit> { continuation ->
                    notifyWhenStateChanged(state) {
                        if (!continuation.isCompleted) {
                            continuation.resume(Unit)
                        }
                    }
                }
            }
        }
    }
}
