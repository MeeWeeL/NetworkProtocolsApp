package ru.meeweel.network_protocols_app.runner

import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.meeweel.network_protocols_app.data.network.ExperimentBackendClient
import ru.meeweel.network_protocols_app.data.telemetry.DeviceTelemetryCollector
import ru.meeweel.network_protocols_app.model.BackendEndpointConfig
import ru.meeweel.network_protocols_app.model.BackendHealthStatus
import ru.meeweel.network_protocols_app.model.DeviceSeriesMetrics
import ru.meeweel.network_protocols_app.model.MeasurementApplicability
import ru.meeweel.network_protocols_app.model.MeasurementMethodologyProfile
import ru.meeweel.network_protocols_app.model.MeasurementSeriesResult
import ru.meeweel.network_protocols_app.model.ProtocolScenarioMatrix
import ru.meeweel.network_protocols_app.model.ProtocolType
import ru.meeweel.network_protocols_app.model.ScenarioExecutionResult
import ru.meeweel.network_protocols_app.model.SeriesConnectionMetrics
import ru.meeweel.network_protocols_app.model.ScenarioType
import ru.meeweel.network_protocols_app.model.TestRunProgress
import ru.meeweel.network_protocols_app.model.TestRunStatus
import ru.meeweel.network_protocols_app.model.energy.EnergyBlockKind
import ru.meeweel.network_protocols_app.model.energy.EnergyBlockRequest
import ru.meeweel.network_protocols_app.service.BackendEndpointService
import java.util.Locale
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Главный "дирижер" измерений на Android-стороне.
 *
 * Он не знает деталей REST, SOAP, GraphQL, WebSocket и gRPC. Его задача проще:
 * взять план из разрешенных пар протокол-сценарий, выполнить серии измерений,
 * собрать телеметрию устройства и превратить все это в отчет для диплома.
 */
class BackendExperimentRunner(
    private val endpointService: BackendEndpointService,
    private val backendClient: ExperimentBackendClient,
    private val scope: CoroutineScope,
    private val deviceTelemetryCollector: DeviceTelemetryCollector,
) : ExperimentRunner {
    private val protocols = ProtocolType.entries
    private val scenarios = ScenarioType.entries

    private val _fullTestProgress = MutableStateFlow(
        TestRunProgress.idle(
            title = "Полный тест",
            details = "Запусти серию измерений по всем поддерживаемым сочетаниям протоколов и сценариев.",
        ),
    )
    override val fullTestProgress: StateFlow<TestRunProgress> = _fullTestProgress.asStateFlow()

    private val _protocolProgress = MutableStateFlow(
        protocols.associateWith(::idleProtocolProgress),
    )
    override val protocolProgress: StateFlow<Map<ProtocolType, TestRunProgress>> =
        _protocolProgress.asStateFlow()

    private val _scenarioProgress = MutableStateFlow(
        scenarios.associateWith(::idleScenarioProgress),
    )
    override val scenarioProgress: StateFlow<Map<ScenarioType, TestRunProgress>> =
        _scenarioProgress.asStateFlow()

    private var fullTestJob: Job? = null
    private var energyBlockJob: Job? = null
    private val protocolJobs = mutableMapOf<ProtocolType, Job>()
    private val scenarioJobs = mutableMapOf<ScenarioType, Job>()
    private val fullReportNotes = mutableListOf<String>()
    private val fullCompletedSeries = mutableListOf<MeasurementSeriesResult>()
    private val protocolReportNotes = protocols.associateWith { mutableListOf<String>() }.toMutableMap()
    private val protocolCompletedSeries = protocols.associateWith { mutableListOf<MeasurementSeriesResult>() }.toMutableMap()
    private val scenarioReportNotes = scenarios.associateWith { mutableListOf<String>() }.toMutableMap()
    private val scenarioCompletedSeries = scenarios.associateWith { mutableListOf<MeasurementSeriesResult>() }.toMutableMap()

    override fun startFullTest() {
        energyBlockJob?.cancel()
        fullTestJob?.cancel()
        fullTestJob = scope.launch {
            val endpoint = endpointService.config.value
            val methodologies = currentMethodologies()
            val requestsPerSeries = methodologies.first().requestCountPerSeries()
            clearAllReportData()
            val executionSeed = SystemClock.elapsedRealtimeNanos()
            // Полный тест строится только из разрешенных методикой пар.
            // Затем порядок перемешивается: так один протокол не получает
            // постоянное преимущество просто потому, что всегда запускается первым.
            val executionPlan = ProtocolType.entries
                .flatMap { protocol ->
                    ProtocolScenarioMatrix.supportedScenarios(protocol).map { scenario ->
                        protocol to scenario
                    }
                }
                .flatMap { (protocol, scenario) ->
                    methodologies.map { methodology ->
                        Triple(protocol, scenario, methodology)
                    }
                }
                .shuffled(Random(executionSeed))
            val etaPlan = executionPlan.toEtaPlan()
            val etaTotalUnits = etaPlan.etaTotalUnits(requestsPerSeries)
            val protocolEtaPlans = protocols.associateWith { protocol ->
                executionPlan.filter { (planProtocol, _, _) -> planProtocol == protocol }.toEtaPlan()
            }
            val protocolEtaTotals = protocolEtaPlans.mapValues { (_, plan) ->
                plan.etaTotalUnits(requestsPerSeries)
            }
            val scenarioEtaPlans = scenarios.associateWith { scenario ->
                executionPlan.filter { (_, planScenario, _) -> planScenario == scenario }.toEtaPlan()
            }
            val scenarioEtaTotals = scenarioEtaPlans.mapValues { (_, plan) ->
                plan.etaTotalUnits(requestsPerSeries)
            }
            val protocolSteps = protocols.associateWith { 0 }.toMutableMap()
            val protocolTotals = protocols.associateWith { protocol ->
                ProtocolScenarioMatrix.supportedScenarios(protocol).size * methodologies.size
            }
            val scenarioSteps = scenarios.associateWith { 0 }.toMutableMap()
            val scenarioTotals = scenarios.associateWith { scenario ->
                ProtocolScenarioMatrix.supportedProtocols(scenario).size * methodologies.size
            }
            val totalSteps = executionPlan.size
            val totalRequests = totalSteps * requestsPerSeries

            appendFullNote(fullExecutionOrderEntry(executionSeed, executionPlan))
            protocols.forEach { protocol ->
                appendProtocolNote(
                    protocol = protocol,
                    entry = protocolExecutionOrderEntry(
                        seed = executionSeed,
                        protocol = protocol,
                        plan = executionPlan,
                    ),
                )
            }
            scenarios.forEach { scenario ->
                appendScenarioNote(
                    scenario = scenario,
                    entry = scenarioExecutionOrderEntry(
                        seed = executionSeed,
                        scenario = scenario,
                        plan = executionPlan,
                    ),
                )
            }

            updateFull(
                runningProgress(
                    title = "Полный тест",
                    details = "Подготавливаются измерения для ${endpoint.host}:${endpoint.httpPort}. Режимы h_req и h_series будут выполнены автоматически. Случайный порядок серий, seed=$executionSeed.",
                    currentStep = 0,
                    totalSteps = totalSteps,
                    currentRequest = 0,
                    totalRequests = totalRequests,
                    etaCompletedUnits = 0L,
                    etaTotalUnits = etaTotalUnits,
                ),
            )

            protocols.forEach { protocol ->
                updateProtocol(
                    protocol = protocol,
                    progress = runningProgress(
                        title = protocol.title,
                        details = "Полный тест запущен. Оба режима соединения: h_req и h_series. Случайный порядок серий, seed=$executionSeed.",
                        currentStep = 0,
                        totalSteps = protocolTotals.getValue(protocol),
                        currentRequest = 0,
                        totalRequests = protocolTotals.getValue(protocol) * requestsPerSeries,
                        etaCompletedUnits = 0L,
                        etaTotalUnits = protocolEtaTotals.getValue(protocol),
                    ),
                )
            }
            scenarios.forEach { scenario ->
                updateScenario(
                    scenario = scenario,
                    progress = runningProgress(
                        title = "${scenario.code} ${scenario.title}",
                        details = "Полный тест запущен. Оба режима соединения: h_req и h_series. Случайный порядок серий, seed=$executionSeed.",
                        currentStep = 0,
                        totalSteps = scenarioTotals.getValue(scenario),
                        currentRequest = 0,
                        totalRequests = scenarioTotals.getValue(scenario) * requestsPerSeries,
                        etaCompletedUnits = 0L,
                        etaTotalUnits = scenarioEtaTotals.getValue(scenario),
                    ),
                )
            }

            executionPlan.forEachIndexed { index, (protocol, scenario, methodology) ->
                val seriesLabel = seriesDescriptor(protocol, scenario, methodology)
                val result = measureSeries(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    onSeriesProgress = { executedRequests ->
                        val completedProtocolSeries = protocolSteps.getValue(protocol)
                        val completedScenarioSeries = scenarioSteps.getValue(scenario)
                        updateFull(
                            runningProgress(
                                title = "Полный тест",
                                details = "$seriesLabel • запрос $executedRequests из $requestsPerSeries",
                                currentStep = index + 1,
                                totalSteps = totalSteps,
                                currentRequest = index * requestsPerSeries + executedRequests,
                                totalRequests = totalRequests,
                                etaCompletedUnits = etaPlan.etaCompletedUnits(
                                    completedSeries = index,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = etaTotalUnits,
                            ),
                        )
                        updateProtocol(
                            protocol = protocol,
                            progress = runningProgress(
                                title = protocol.title,
                                details = "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = completedProtocolSeries + 1,
                                totalSteps = protocolTotals.getValue(protocol),
                                currentRequest = completedProtocolSeries * requestsPerSeries + executedRequests,
                                totalRequests = protocolTotals.getValue(protocol) * requestsPerSeries,
                                etaCompletedUnits = protocolEtaPlans.getValue(protocol).etaCompletedUnits(
                                    completedSeries = completedProtocolSeries,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = protocolEtaTotals.getValue(protocol),
                            ),
                        )
                        updateScenario(
                            scenario = scenario,
                            progress = runningProgress(
                                title = "${scenario.code} ${scenario.title}",
                                details = "${protocol.title} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = completedScenarioSeries + 1,
                                totalSteps = scenarioTotals.getValue(scenario),
                                currentRequest = completedScenarioSeries * requestsPerSeries + executedRequests,
                                totalRequests = scenarioTotals.getValue(scenario) * requestsPerSeries,
                                etaCompletedUnits = scenarioEtaPlans.getValue(scenario).etaCompletedUnits(
                                    completedSeries = completedScenarioSeries,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = scenarioEtaTotals.getValue(scenario),
                            ),
                        )
                    },
                )
                    .getOrElse { error ->
                        failFullRun(
                            protocol = protocol,
                            scenario = scenario,
                            methodology = methodology,
                            reason = error.userMessage(),
                            currentStep = index,
                            totalSteps = totalSteps,
                            protocolTotalSteps = protocolTotals.getValue(protocol),
                            scenarioTotalSteps = scenarioTotals.getValue(scenario),
                        )
                        return@launch
                    }

                protocolSteps[protocol] = protocolSteps.getValue(protocol) + 1
                scenarioSteps[scenario] = scenarioSteps.getValue(scenario) + 1
                appendFullCompletedSeries(result)
                appendProtocolCompletedSeries(protocol, result)
                appendScenarioCompletedSeries(scenario, result)

                updateProtocol(
                    protocol = protocol,
                    progress = if (protocolSteps.getValue(protocol) == protocolTotals.getValue(protocol)) {
                        completedProgress(
                            title = protocol.title,
                            details = "Все сценарии и оба режима соединения по протоколу ${protocol.title} завершены.",
                            totalSteps = protocolTotals.getValue(protocol),
                            totalRequests = protocolTotals.getValue(protocol) * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = protocol.title,
                            details = result.progressDetails,
                            currentStep = protocolSteps.getValue(protocol),
                            totalSteps = protocolTotals.getValue(protocol),
                            currentRequest = protocolSteps.getValue(protocol) * requestsPerSeries,
                            totalRequests = protocolTotals.getValue(protocol) * requestsPerSeries,
                            etaCompletedUnits = protocolEtaPlans.getValue(protocol).etaCompletedUnits(
                                completedSeries = protocolSteps.getValue(protocol),
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = protocolEtaTotals.getValue(protocol),
                        )
                    },
                )

                updateScenario(
                    scenario = scenario,
                    progress = if (scenarioSteps.getValue(scenario) == scenarioTotals.getValue(scenario)) {
                        completedProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = "Сценарий проверен на всех протоколах в режимах h_req и h_series.",
                            totalSteps = scenarioTotals.getValue(scenario),
                            totalRequests = scenarioTotals.getValue(scenario) * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = result.summaryLine,
                            currentStep = scenarioSteps.getValue(scenario),
                            totalSteps = scenarioTotals.getValue(scenario),
                            currentRequest = scenarioSteps.getValue(scenario) * requestsPerSeries,
                            totalRequests = scenarioTotals.getValue(scenario) * requestsPerSeries,
                            etaCompletedUnits = scenarioEtaPlans.getValue(scenario).etaCompletedUnits(
                                completedSeries = scenarioSteps.getValue(scenario),
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = scenarioEtaTotals.getValue(scenario),
                        )
                    },
                )

                updateFull(
                    runningProgress(
                        title = "Полный тест",
                        details = result.summaryLine,
                        currentStep = index + 1,
                        totalSteps = totalSteps,
                        currentRequest = (index + 1) * requestsPerSeries,
                        totalRequests = totalRequests,
                        etaCompletedUnits = etaPlan.etaCompletedUnits(
                            completedSeries = index + 1,
                            currentSeriesRequests = 0,
                            requestsPerSeries = requestsPerSeries,
                        ),
                        etaTotalUnits = etaTotalUnits,
                    ),
                )
            }

            updateFull(
                completedProgress(
                    title = "Полный тест",
                    details = "Проверены все поддерживаемые сочетания протоколов, сценариев и режимов h_req/h_series. Случайный порядок серий, seed=$executionSeed.",
                    totalSteps = totalSteps,
                    totalRequests = totalRequests,
                ),
            )
        }
    }

    override fun startProtocolSuite(protocol: ProtocolType) {
        energyBlockJob?.cancel()
        protocolJobs[protocol]?.cancel()
        protocolJobs[protocol] = scope.launch {
            val endpoint = endpointService.config.value
            val methodologies = currentMethodologies()
            val requestsPerSeries = methodologies.first().requestCountPerSeries()
            val executionSeed = SystemClock.elapsedRealtimeNanos()
            val executionPlan = ProtocolScenarioMatrix
                .supportedScenarios(protocol)
                .flatMap { scenario ->
                    methodologies.map { methodology ->
                        scenario to methodology
                    }
                }
                .shuffled(Random(executionSeed))
            val etaPlan = executionPlan
            val etaTotalUnits = etaPlan.etaTotalUnits(requestsPerSeries)
            val seenScenarios = mutableSetOf<ScenarioType>()
            clearProtocolReportData(protocol)
            appendProtocolNote(
                protocol = protocol,
                entry = protocolSuiteOrderEntry(
                    seed = executionSeed,
                    protocol = protocol,
                    scenarios = executionPlan,
                ),
            )

            updateProtocol(
                protocol = protocol,
                progress = runningProgress(
                    title = protocol.title,
                    details = "Запущен полный тест по поддерживаемым сценариям в режимах h_req и h_series. Случайный порядок, seed=$executionSeed.",
                    currentStep = 0,
                    totalSteps = executionPlan.size,
                    currentRequest = 0,
                    totalRequests = executionPlan.size * requestsPerSeries,
                    etaCompletedUnits = 0L,
                    etaTotalUnits = etaTotalUnits,
                ),
            )

            executionPlan.forEachIndexed { index, (scenario, methodology) ->
                if (seenScenarios.add(scenario)) {
                    clearScenarioReportData(scenario)
                }
                val result = measureSeries(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    onSeriesProgress = { executedRequests ->
                        updateProtocol(
                            protocol = protocol,
                            progress = runningProgress(
                                title = protocol.title,
                                details = "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = index + 1,
                                totalSteps = executionPlan.size,
                                currentRequest = index * requestsPerSeries + executedRequests,
                                totalRequests = executionPlan.size * requestsPerSeries,
                                etaCompletedUnits = etaPlan.etaCompletedUnits(
                                    completedSeries = index,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = etaTotalUnits,
                            ),
                        )
                    },
                )
                    .getOrElse { error ->
                        failProtocolRun(
                            protocol = protocol,
                            scenario = scenario,
                            methodology = methodology,
                            reason = error.userMessage(),
                            currentStep = index,
                            totalSteps = executionPlan.size,
                        )
                        return@launch
                    }

                appendProtocolCompletedSeries(protocol, result)
                appendScenarioCompletedSeries(scenario, result)

                updateProtocol(
                    protocol = protocol,
                    progress = if (index + 1 == executionPlan.size) {
                        completedProgress(
                            title = protocol.title,
                            details = "Все сценарии по протоколу ${protocol.title} завершены в режимах h_req и h_series.",
                            totalSteps = executionPlan.size,
                            totalRequests = executionPlan.size * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = protocol.title,
                            details = result.progressDetails,
                            currentStep = index + 1,
                            totalSteps = executionPlan.size,
                            currentRequest = (index + 1) * requestsPerSeries,
                            totalRequests = executionPlan.size * requestsPerSeries,
                            etaCompletedUnits = etaPlan.etaCompletedUnits(
                                completedSeries = index + 1,
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = etaTotalUnits,
                        )
                    },
                )

                updateScenario(
                    scenario = scenario,
                    progress = completedProgress(
                        title = "${scenario.code} ${scenario.title}",
                        details = result.summaryLine,
                        totalSteps = scenarioCompletedCount(scenario),
                        totalRequests = scenarioCompletedCount(scenario) * requestsPerSeries,
                    ),
                )
            }
        }
    }

    override fun startProtocolScenario(
        protocol: ProtocolType,
        scenario: ScenarioType,
    ) {
        energyBlockJob?.cancel()
        if (!ProtocolScenarioMatrix.supports(protocol, scenario)) {
            clearProtocolReportData(protocol)
            appendProtocolNote(
                protocol = protocol,
                entry = "${scenario.code} ${scenario.shortTitle} не поддерживается для ${protocol.title}.",
            )
            updateProtocol(
                protocol = protocol,
                progress = TestRunProgress.error(
                    title = protocol.title,
                    details = "${scenario.code} ${scenario.shortTitle} не поддерживается для ${protocol.title}.",
                    totalSteps = 1,
                ),
            )
            return
        }

        protocolJobs[protocol]?.cancel()
        protocolJobs[protocol] = scope.launch {
            val endpoint = endpointService.config.value
            val methodologies = currentMethodologies()
            val requestsPerSeries = methodologies.first().requestCountPerSeries()
            val etaPlan = methodologies.map { methodology -> scenario to methodology }
            val etaTotalUnits = etaPlan.etaTotalUnits(requestsPerSeries)
            clearProtocolReportData(protocol)
            clearScenarioReportData(scenario)

            updateProtocol(
                protocol = protocol,
                progress = runningProgress(
                    title = protocol.title,
                    details = "${scenario.code} ${scenario.title} • будут выполнены h_req и h_series",
                    currentStep = 0,
                    totalSteps = methodologies.size,
                    currentRequest = 0,
                    totalRequests = methodologies.size * requestsPerSeries,
                    etaCompletedUnits = 0L,
                    etaTotalUnits = etaTotalUnits,
                ),
            )

            methodologies.forEachIndexed { index, methodology ->
                val result = measureSeries(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    onSeriesProgress = { executedRequests ->
                        updateProtocol(
                            protocol = protocol,
                            progress = runningProgress(
                                title = protocol.title,
                                details = "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = index + 1,
                                totalSteps = methodologies.size,
                                currentRequest = index * requestsPerSeries + executedRequests,
                                totalRequests = methodologies.size * requestsPerSeries,
                                etaCompletedUnits = etaPlan.etaCompletedUnits(
                                    completedSeries = index,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = etaTotalUnits,
                            ),
                        )
                        updateScenario(
                            scenario = scenario,
                            progress = runningProgress(
                                title = "${scenario.code} ${scenario.title}",
                                details = "${protocol.title} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = index + 1,
                                totalSteps = methodologies.size,
                                currentRequest = index * requestsPerSeries + executedRequests,
                                totalRequests = methodologies.size * requestsPerSeries,
                                etaCompletedUnits = etaPlan.etaCompletedUnits(
                                    completedSeries = index,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = etaTotalUnits,
                            ),
                        )
                    },
                )
                    .getOrElse { error ->
                        failProtocolRun(
                            protocol = protocol,
                            scenario = scenario,
                            methodology = methodology,
                            reason = error.userMessage(),
                            currentStep = index,
                            totalSteps = methodologies.size,
                        )
                        return@launch
                    }

                appendProtocolCompletedSeries(protocol, result)
                appendScenarioCompletedSeries(scenario, result)

                val details = if (index + 1 == methodologies.size) {
                    "Выполнены оба режима соединения для ${scenario.code} ${scenario.shortTitle}."
                } else {
                    result.progressDetails
                }
                updateProtocol(
                    protocol = protocol,
                    progress = if (index + 1 == methodologies.size) {
                        completedProgress(
                            title = protocol.title,
                            details = details,
                            totalSteps = methodologies.size,
                            totalRequests = methodologies.size * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = protocol.title,
                            details = details,
                            currentStep = index + 1,
                            totalSteps = methodologies.size,
                            currentRequest = (index + 1) * requestsPerSeries,
                            totalRequests = methodologies.size * requestsPerSeries,
                            etaCompletedUnits = etaPlan.etaCompletedUnits(
                                completedSeries = index + 1,
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = etaTotalUnits,
                        )
                    },
                )
                updateScenario(
                    scenario = scenario,
                    progress = if (index + 1 == methodologies.size) {
                        completedProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = "Выполнены оба режима соединения для ${protocol.title}.",
                            totalSteps = methodologies.size,
                            totalRequests = methodologies.size * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = result.summaryLine,
                            currentStep = index + 1,
                            totalSteps = methodologies.size,
                            currentRequest = (index + 1) * requestsPerSeries,
                            totalRequests = methodologies.size * requestsPerSeries,
                            etaCompletedUnits = etaPlan.etaCompletedUnits(
                                completedSeries = index + 1,
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = etaTotalUnits,
                        )
                    },
                )
            }
        }
    }

    override fun startScenarioSuite(scenario: ScenarioType) {
        energyBlockJob?.cancel()
        scenarioJobs[scenario]?.cancel()
        scenarioJobs[scenario] = scope.launch {
            val endpoint = endpointService.config.value
            val methodologies = currentMethodologies()
            val requestsPerSeries = methodologies.first().requestCountPerSeries()
            val executionSeed = SystemClock.elapsedRealtimeNanos()
            val executionPlan = ProtocolScenarioMatrix
                .supportedProtocols(scenario)
                .flatMap { protocol ->
                    methodologies.map { methodology ->
                        protocol to methodology
                    }
                }
                .shuffled(Random(executionSeed))
            val etaPlan = executionPlan.map { (_, methodology) -> scenario to methodology }
            val etaTotalUnits = etaPlan.etaTotalUnits(requestsPerSeries)
            val seenProtocols = mutableSetOf<ProtocolType>()
            clearScenarioReportData(scenario)
            appendScenarioNote(
                scenario = scenario,
                entry = scenarioSuiteOrderEntry(
                    seed = executionSeed,
                    scenario = scenario,
                    protocols = executionPlan,
                ),
            )

            updateScenario(
                scenario = scenario,
                progress = runningProgress(
                    title = "${scenario.code} ${scenario.title}",
                    details = "Запущен тест сценария по поддерживаемым протоколам в режимах h_req и h_series. Случайный порядок, seed=$executionSeed.",
                    currentStep = 0,
                    totalSteps = executionPlan.size,
                    currentRequest = 0,
                    totalRequests = executionPlan.size * requestsPerSeries,
                    etaCompletedUnits = 0L,
                    etaTotalUnits = etaTotalUnits,
                ),
            )

            executionPlan.forEachIndexed { index, (protocol, methodology) ->
                if (seenProtocols.add(protocol)) {
                    clearProtocolReportData(protocol)
                }
                val result = measureSeries(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    onSeriesProgress = { executedRequests ->
                        updateScenario(
                            scenario = scenario,
                            progress = runningProgress(
                                title = "${scenario.code} ${scenario.title}",
                                details = "${protocol.title} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = index + 1,
                                totalSteps = executionPlan.size,
                                currentRequest = index * requestsPerSeries + executedRequests,
                                totalRequests = executionPlan.size * requestsPerSeries,
                                etaCompletedUnits = etaPlan.etaCompletedUnits(
                                    completedSeries = index,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = etaTotalUnits,
                            ),
                        )
                    },
                )
                    .getOrElse { error ->
                        failScenarioRun(
                            scenario = scenario,
                            protocol = protocol,
                            methodology = methodology,
                            reason = error.userMessage(),
                            currentStep = index,
                            totalSteps = executionPlan.size,
                        )
                        return@launch
                    }

                appendScenarioCompletedSeries(scenario, result)
                appendProtocolCompletedSeries(protocol, result)

                updateScenario(
                    scenario = scenario,
                    progress = if (index + 1 == executionPlan.size) {
                        completedProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = "Сценарий проверен на всех протоколах в режимах h_req и h_series.",
                            totalSteps = executionPlan.size,
                            totalRequests = executionPlan.size * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = result.summaryLine,
                            currentStep = index + 1,
                            totalSteps = executionPlan.size,
                            currentRequest = (index + 1) * requestsPerSeries,
                            totalRequests = executionPlan.size * requestsPerSeries,
                            etaCompletedUnits = etaPlan.etaCompletedUnits(
                                completedSeries = index + 1,
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = etaTotalUnits,
                        )
                    },
                )

                updateProtocol(
                    protocol = protocol,
                    progress = completedProgress(
                        title = protocol.title,
                        details = result.progressDetails,
                        totalSteps = protocolCompletedCount(protocol),
                        totalRequests = protocolCompletedCount(protocol) * requestsPerSeries,
                    ),
                )
            }
        }
    }

    override fun startScenarioProtocol(
        scenario: ScenarioType,
        protocol: ProtocolType,
    ) {
        energyBlockJob?.cancel()
        if (!ProtocolScenarioMatrix.supports(protocol, scenario)) {
            clearScenarioReportData(scenario)
            appendScenarioNote(
                scenario = scenario,
                entry = "${protocol.title} не поддерживает этот сценарий.",
            )
            updateScenario(
                scenario = scenario,
                progress = TestRunProgress.error(
                    title = "${scenario.code} ${scenario.title}",
                    details = "${protocol.title} не поддерживает этот сценарий.",
                    totalSteps = 1,
                ),
            )
            return
        }

        scenarioJobs[scenario]?.cancel()
        scenarioJobs[scenario] = scope.launch {
            val endpoint = endpointService.config.value
            val methodologies = currentMethodologies()
            val requestsPerSeries = methodologies.first().requestCountPerSeries()
            val etaPlan = methodologies.map { methodology -> scenario to methodology }
            val etaTotalUnits = etaPlan.etaTotalUnits(requestsPerSeries)
            clearScenarioReportData(scenario)
            clearProtocolReportData(protocol)

            updateScenario(
                scenario = scenario,
                progress = runningProgress(
                    title = "${scenario.code} ${scenario.title}",
                    details = "${protocol.title} • будут выполнены h_req и h_series",
                    currentStep = 0,
                    totalSteps = methodologies.size,
                    currentRequest = 0,
                    totalRequests = methodologies.size * requestsPerSeries,
                    etaCompletedUnits = 0L,
                    etaTotalUnits = etaTotalUnits,
                ),
            )

            methodologies.forEachIndexed { index, methodology ->
                val result = measureSeries(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    onSeriesProgress = { executedRequests ->
                        updateScenario(
                            scenario = scenario,
                            progress = runningProgress(
                                title = "${scenario.code} ${scenario.title}",
                                details = "${protocol.title} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = index + 1,
                                totalSteps = methodologies.size,
                                currentRequest = index * requestsPerSeries + executedRequests,
                                totalRequests = methodologies.size * requestsPerSeries,
                                etaCompletedUnits = etaPlan.etaCompletedUnits(
                                    completedSeries = index,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = etaTotalUnits,
                            ),
                        )
                        updateProtocol(
                            protocol = protocol,
                            progress = runningProgress(
                                title = protocol.title,
                                details = "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode} • запрос $executedRequests из $requestsPerSeries",
                                currentStep = index + 1,
                                totalSteps = methodologies.size,
                                currentRequest = index * requestsPerSeries + executedRequests,
                                totalRequests = methodologies.size * requestsPerSeries,
                                etaCompletedUnits = etaPlan.etaCompletedUnits(
                                    completedSeries = index,
                                    currentSeriesRequests = executedRequests,
                                    requestsPerSeries = requestsPerSeries,
                                ),
                                etaTotalUnits = etaTotalUnits,
                            ),
                        )
                    },
                )
                    .getOrElse { error ->
                        failScenarioRun(
                            scenario = scenario,
                            protocol = protocol,
                            methodology = methodology,
                            reason = error.userMessage(),
                            currentStep = index,
                            totalSteps = methodologies.size,
                        )
                        return@launch
                    }

                appendScenarioCompletedSeries(scenario, result)
                appendProtocolCompletedSeries(protocol, result)

                val details = if (index + 1 == methodologies.size) {
                    "Выполнены оба режима соединения для ${protocol.title}."
                } else {
                    result.summaryLine
                }
                updateScenario(
                    scenario = scenario,
                    progress = if (index + 1 == methodologies.size) {
                        completedProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = details,
                            totalSteps = methodologies.size,
                            totalRequests = methodologies.size * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = "${scenario.code} ${scenario.title}",
                            details = details,
                            currentStep = index + 1,
                            totalSteps = methodologies.size,
                            currentRequest = (index + 1) * requestsPerSeries,
                            totalRequests = methodologies.size * requestsPerSeries,
                            etaCompletedUnits = etaPlan.etaCompletedUnits(
                                completedSeries = index + 1,
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = etaTotalUnits,
                        )
                    },
                )
                updateProtocol(
                    protocol = protocol,
                    progress = if (index + 1 == methodologies.size) {
                        completedProgress(
                            title = protocol.title,
                            details = "Выполнены оба режима соединения для ${scenario.code} ${scenario.shortTitle}.",
                            totalSteps = methodologies.size,
                            totalRequests = methodologies.size * requestsPerSeries,
                        )
                    } else {
                        runningProgress(
                            title = protocol.title,
                            details = result.progressDetails,
                            currentStep = index + 1,
                            totalSteps = methodologies.size,
                            currentRequest = (index + 1) * requestsPerSeries,
                            totalRequests = methodologies.size * requestsPerSeries,
                            etaCompletedUnits = etaPlan.etaCompletedUnits(
                                completedSeries = index + 1,
                                currentSeriesRequests = 0,
                                requestsPerSeries = requestsPerSeries,
                            ),
                            etaTotalUnits = etaTotalUnits,
                        )
                    },
                )
            }
        }
    }

    override fun startEnergyBlock(request: EnergyBlockRequest) {
        cancelInteractiveRuns()
        energyBlockJob?.cancel()
        energyBlockJob = scope.launch {
            clearAllReportData()
            // Маркеры связывают нагрузочный блок приложения с ADB-контуром,
            // который сохраняет logcat и системный отчет batterystats.
            Log.i(ENERGY_LOG_TAG, "$ENERGY_MARKER_STARTED ${request.startedMarkerFields()}")
            updateFull(
                runningProgress(
                    title = "Тест энергопотребления",
                    details = request.startDetails(),
                    currentStep = 0,
                    totalSteps = request.normalizedDurationSeconds.toInt(),
                    etaCompletedUnits = 0L,
                    etaTotalUnits = request.normalizedDurationSeconds * 1_000L,
                ),
            )

            runCatching {
                when (request.kind) {
                    EnergyBlockKind.Idle -> runEnergyIdleBlock(request)
                    EnergyBlockKind.Workload -> runEnergyWorkloadBlock(request)
                }
            }.onSuccess { summary ->
                appendFullNote(summary.reportNote())
                updateFull(
                    completedProgress(
                        title = "Тест энергопотребления",
                        details = summary.uiDetails(),
                        totalSteps = request.normalizedDurationSeconds.toInt(),
                    ),
                )
                Log.i(ENERGY_LOG_TAG, "$ENERGY_MARKER_DONE ${summary.markerFields()}")
            }.onFailure { error ->
                val reason = error.userMessage()
                appendFullNote("Блок измерения энергопотребления ${request.blockId}: ошибка - $reason")
                updateFull(
                    TestRunProgress.error(
                        title = "Тест энергопотребления",
                        details = reason,
                        totalSteps = request.normalizedDurationSeconds.toInt(),
                    ),
                )
                Log.e(ENERGY_LOG_TAG, "$ENERGY_MARKER_FAILED ${request.failedMarkerFields(reason)}")
            }
        }
    }

    private fun cancelInteractiveRuns() {
        fullTestJob?.cancel()
        protocolJobs.values.forEach { job -> job.cancel() }
        scenarioJobs.values.forEach { job -> job.cancel() }
    }

    private suspend fun runEnergyIdleBlock(request: EnergyBlockRequest): EnergyBlockSummary {
        // Холостой блок ничего не отправляет в сеть. Он нужен как фон:
        // сколько само устройство и приложение "едят" за фиксированное время.
        val durationMs = request.normalizedDurationSeconds * 1_000L
        val startedAt = SystemClock.elapsedRealtime()
        val telemetrySession = deviceTelemetryCollector.startSession()
        var sampleIndex = 0
        while (SystemClock.elapsedRealtime() - startedAt < durationMs) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            updateEnergyProgress(
                request = request,
                elapsedMs = elapsedMs,
                attempts = 0,
                successes = 0,
                failures = 0,
            )
            delay(minOf(1_000L, (durationMs - elapsedMs).coerceAtLeast(1L)))
            sampleIndex += 1
            if (sampleIndex % ENERGY_IDLE_SAMPLE_STRIDE == 0) {
                telemetrySession.sample()
            }
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        val deviceMetrics = telemetrySession.finish()
        updateEnergyProgress(
            request = request,
            elapsedMs = elapsedMs,
            attempts = 0,
            successes = 0,
            failures = 0,
        )
        return EnergyBlockSummary(
            request = request,
            elapsedMs = elapsedMs,
            attempts = 0,
            successes = 0,
            failures = 0,
            responseCount = 0,
            clientDurationMicrosTotal = 0L,
            serverDurationMicrosTotal = 0L,
            deviceMetrics = deviceMetrics,
        )
    }

    private suspend fun runEnergyWorkloadBlock(request: EnergyBlockRequest): EnergyBlockSummary {
        val protocol = request.protocol
            ?: throw IllegalArgumentException("Для рабочего блока измерения энергопотребления не задан протокол.")
        val scenario = request.scenario
            ?: throw IllegalArgumentException("Для рабочего блока измерения энергопотребления не задан сценарий.")
        if (!ProtocolScenarioMatrix.supports(protocol, scenario)) {
            throw IllegalArgumentException("${scenario.code} ${scenario.shortTitle} не поддерживается для ${protocol.title}.")
        }

        val endpoint = request.resolveEndpoint(endpointService.config.value)
        // В energy-блоке мы не считаем "100 повторов". Вместо этого приложение
        // много раз выполняет одну операцию до истечения таймера, а расход
        // заряда потом берется из системного отчета Android.
        val methodology = MeasurementMethodologyProfile.defaultProfile().copy(
            warmUpRuns = 0,
            measuredRuns = 1,
            resourceSamplingStride = ENERGY_WORKLOAD_SAMPLE_STRIDE,
            reusePersistentConnections = request.connectionMode.reusePersistentConnections,
        )
        ensureSeriesPreconditions(endpoint, methodology)
        val executionContext = backendClient.openSeriesExecutionContext(
            endpointConfig = endpoint,
            protocol = protocol,
            scenario = scenario,
            methodology = methodology,
        )
        try {
            executionContext.prepare()
            val durationMs = request.normalizedDurationSeconds * 1_000L
            val startedAt = SystemClock.elapsedRealtime()
            val deadline = startedAt + durationMs
            val telemetrySession = deviceTelemetryCollector.startSession()
            var attempts = 0
            var successes = 0
            var failures = 0
            var responseCount = 0
            var clientDurationMicrosTotal = 0L
            var serverDurationMicrosTotal = 0L
            var consecutiveFailures = 0
            var nextProgressAt = startedAt

            do {
                val result = executeSingle(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    context = executionContext,
                    refreshHealthOnFailure = false,
                )
                attempts += 1
                result.onSuccess { execution ->
                    successes += 1
                    consecutiveFailures = 0
                    responseCount += execution.responseCount
                    clientDurationMicrosTotal += execution.clientDurationMicros
                    serverDurationMicrosTotal += execution.serverDurationMicros
                }.onFailure {
                    failures += 1
                    consecutiveFailures += 1
                }
                if (attempts % ENERGY_WORKLOAD_SAMPLE_STRIDE == 0) {
                    telemetrySession.sample()
                }
                val now = SystemClock.elapsedRealtime()
                if (now >= nextProgressAt) {
                    updateEnergyProgress(
                        request = request,
                        elapsedMs = now - startedAt,
                        attempts = attempts,
                        successes = successes,
                        failures = failures,
                    )
                    nextProgressAt = now + ENERGY_PROGRESS_PERIOD_MS
                }
                if (successes == 0 && consecutiveFailures >= ENERGY_MAX_CONSECUTIVE_FAILURES) {
                    throw IllegalStateException("Блок измерения энергопотребления остановлен: $consecutiveFailures ошибок подряд.")
                }
            } while (SystemClock.elapsedRealtime() < deadline)

            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val deviceMetrics = telemetrySession.finish()
            if (successes == 0) {
                throw IllegalStateException("Блок измерения энергопотребления не получил ни одного успешного ответа.")
            }
            updateEnergyProgress(
                request = request,
                elapsedMs = elapsedMs,
                attempts = attempts,
                successes = successes,
                failures = failures,
            )
            return EnergyBlockSummary(
                request = request,
                elapsedMs = elapsedMs,
                attempts = attempts,
                successes = successes,
                failures = failures,
                responseCount = responseCount,
                clientDurationMicrosTotal = clientDurationMicrosTotal,
                serverDurationMicrosTotal = serverDurationMicrosTotal,
                deviceMetrics = deviceMetrics,
            )
        } finally {
            executionContext.close()
        }
    }

    private fun updateEnergyProgress(
        request: EnergyBlockRequest,
        elapsedMs: Long,
        attempts: Int,
        successes: Int,
        failures: Int,
    ) {
        val durationMs = request.normalizedDurationSeconds * 1_000L
        val elapsedSeconds = ceil(elapsedMs.coerceAtLeast(0L).toDouble() / 1_000.0)
            .toInt()
            .coerceIn(0, request.normalizedDurationSeconds.toInt())
        updateFull(
            runningProgress(
                title = "Тест энергопотребления",
                details = request.runningDetails(attempts, successes, failures),
                currentStep = elapsedSeconds,
                totalSteps = request.normalizedDurationSeconds.toInt(),
                etaCompletedUnits = elapsedMs.coerceIn(0L, durationMs),
                etaTotalUnits = durationMs,
            ),
        )
    }

    private suspend fun measureSeries(
        endpoint: BackendEndpointConfig,
        protocol: ProtocolType,
        scenario: ScenarioType,
        methodology: MeasurementMethodologyProfile,
        onSeriesProgress: (Int) -> Unit = {},
    ): Result<MeasurementSeriesResult> {
        return try {
            Result.success(runMeasurementSeries(endpoint, protocol, scenario, methodology, onSeriesProgress))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            runCatching { endpointService.refreshHealth() }
            Result.failure(error)
        }
    }

    private suspend fun runMeasurementSeries(
        endpoint: BackendEndpointConfig,
        protocol: ProtocolType,
        scenario: ScenarioType,
        methodology: MeasurementMethodologyProfile,
        onSeriesProgress: (Int) -> Unit,
    ): MeasurementSeriesResult {
        ensureSeriesPreconditions(endpoint, methodology)
        // Контекст серии держит подготовленные соединения для h_series.
        // Для h_req он почти пустой: отдельное соединение будет создаваться
        // внутри каждого измеряемого обращения.
        val executionContext = backendClient.openSeriesExecutionContext(
            endpointConfig = endpoint,
            protocol = protocol,
            scenario = scenario,
            methodology = methodology,
        )
        try {
            val connectionSetupMs = executionContext.prepare()
            var executedRequests = 0
            onSeriesProgress(executedRequests)
            val warmUpErrors = mutableListOf<String>()
            // Прогрев нужен, чтобы первый холодный запуск не портил статистику.
            // Его ошибки мы сохраняем в заметки, но успешные прогревы не входят
            // в медиану, p95 и другие итоговые показатели.
            repeat(methodology.warmUpRuns) { index ->
                executeSingle(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    context = executionContext,
                    refreshHealthOnFailure = false,
                ).exceptionOrNull()?.let { error ->
                    warmUpErrors += "Прогрев ${index + 1}/${methodology.warmUpRuns}: ${error.userMessage()}"
                }
                executedRequests += 1
                onSeriesProgress(executedRequests)
            }

            val successfulRuns = mutableListOf<ScenarioExecutionResult>()
            val errors = mutableListOf<String>()
            val telemetrySession = deviceTelemetryCollector.startSession()
            val seriesStartedAt = SystemClock.elapsedRealtime()
            // Измеряемая часть серии: эти обращения попадают в отчет и
            // используются для медианы, хвостов и пропускной способности.
            repeat(methodology.measuredRuns) { index ->
                executeSingle(
                    endpoint = endpoint,
                    protocol = protocol,
                    scenario = scenario,
                    methodology = methodology,
                    context = executionContext,
                    refreshHealthOnFailure = false,
                ).onSuccess(successfulRuns::add)
                    .onFailure { error ->
                        errors += "Измерение ${index + 1}/${methodology.measuredRuns}: ${error.userMessage()}"
                    }
                executedRequests += 1
                onSeriesProgress(executedRequests)
                if ((index + 1) % methodology.resourceSamplingStride == 0) {
                    telemetrySession.sample()
                }
            }
            val seriesElapsedMs = SystemClock.elapsedRealtime() - seriesStartedAt
            val deviceMetrics = telemetrySession.finish()

            if (successfulRuns.isEmpty()) {
                val seriesErrors = (warmUpErrors + errors).takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "; ")
                    ?: "Нет пригодных измерений в серии."
                throw IllegalStateException(seriesErrors)
            }

            if (errors.isNotEmpty()) {
                runCatching { endpointService.refreshHealth() }
            }

            val result = successfulRuns.toMeasurementSeriesResult(
                protocol = protocol,
                scenario = scenario,
                methodology = methodology,
                seriesElapsedMs = seriesElapsedMs + connectionSetupMs,
                connectionSetupMs = connectionSetupMs.takeIf { it > 0L },
                failedRuns = errors.size,
                deviceMetrics = deviceMetrics,
                errorMessages = (warmUpErrors + errors).distinct(),
            )

            if (result.applicability == MeasurementApplicability.Insufficient) {
                throw IllegalStateException(
                    "Недостаточно пригодных измерений: ${result.successfulRuns} из ${methodology.measuredRuns}.",
                )
            }

            return result
        } finally {
            executionContext.close()
        }
    }

    private suspend fun ensureSeriesPreconditions(
        endpoint: BackendEndpointConfig,
        methodology: MeasurementMethodologyProfile,
    ) {
        // Перед серией проверяем две базовые вещи: backend отвечает, а клиентский
        // таймер идет вперед. Без этого красивые цифры в отчете были бы мусором.
        val health = backendClient.checkHealth(endpoint)
        if (health.status != BackendHealthStatus.Available) {
            throw IllegalStateException(health.details)
        }
        calibrateClientTimer(methodology)
    }

    private fun calibrateClientTimer(methodology: MeasurementMethodologyProfile) {
        var previous = SystemClock.elapsedRealtimeNanos()
        repeat(methodology.calibrationChecks - 1) {
            val current = SystemClock.elapsedRealtimeNanos()
            if (current < previous) {
                throw IllegalStateException("Калибровка таймера не пройдена: нарушен порядок клиентского таймера.")
            }
            previous = current
        }
    }

    private suspend fun executeSingle(
        endpoint: BackendEndpointConfig,
        protocol: ProtocolType,
        scenario: ScenarioType,
        methodology: MeasurementMethodologyProfile,
        context: ExperimentBackendClient.SeriesExecutionContext,
        refreshHealthOnFailure: Boolean,
    ): Result<ScenarioExecutionResult> {
        return try {
            Result.success(backendClient.execute(endpoint, protocol, scenario, methodology, context))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (refreshHealthOnFailure) {
                runCatching { endpointService.refreshHealth() }
            }
            Result.failure(error)
        }
    }

    private fun failFullRun(
        protocol: ProtocolType,
        scenario: ScenarioType,
        methodology: MeasurementMethodologyProfile,
        reason: String,
        currentStep: Int,
        totalSteps: Int,
        protocolTotalSteps: Int,
        scenarioTotalSteps: Int,
    ) {
        appendFullNote("${seriesDescriptor(protocol, scenario, methodology)} • Ошибка: $reason")
        appendProtocolNote(protocol, "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode} • Ошибка: $reason")
        appendScenarioNote(scenario, "${protocol.title} • ${methodology.connectionModeCode} • Ошибка: $reason")
        updateFull(
            TestRunProgress.error(
                title = "Полный тест",
                details = "Не удалось выполнить ${seriesDescriptor(protocol, scenario, methodology)}: $reason",
                currentStep = currentStep,
                totalSteps = totalSteps,
            ),
        )
        updateProtocol(
            protocol = protocol,
            progress = TestRunProgress.error(
                title = protocol.title,
                details = "Прогон остановлен на ${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode}: $reason",
                currentStep = 0,
                totalSteps = protocolTotalSteps,
            ),
        )
        updateScenario(
            scenario = scenario,
            progress = TestRunProgress.error(
                title = "${scenario.code} ${scenario.title}",
                details = "${protocol.title} • ${methodology.connectionModeCode}: $reason",
                totalSteps = scenarioTotalSteps,
            ),
        )
    }

    private fun failProtocolRun(
        protocol: ProtocolType,
        scenario: ScenarioType,
        methodology: MeasurementMethodologyProfile,
        reason: String,
        currentStep: Int,
        totalSteps: Int,
    ) {
        appendProtocolNote(protocol, "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode} • Ошибка: $reason")
        appendScenarioNote(scenario, "${protocol.title} • ${methodology.connectionModeCode} • Ошибка: $reason")
        updateProtocol(
            protocol = protocol,
            progress = TestRunProgress.error(
                title = protocol.title,
                details = "Не удалось выполнить ${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode}: $reason",
                currentStep = currentStep,
                totalSteps = totalSteps,
            ),
        )
        updateScenario(
            scenario = scenario,
            progress = TestRunProgress.error(
                title = "${scenario.code} ${scenario.title}",
                details = "${protocol.title} • ${methodology.connectionModeCode}: $reason",
                totalSteps = scenarioCompletedCount(scenario).coerceAtLeast(1),
            ),
        )
    }

    private fun failScenarioRun(
        scenario: ScenarioType,
        protocol: ProtocolType,
        methodology: MeasurementMethodologyProfile,
        reason: String,
        currentStep: Int,
        totalSteps: Int,
    ) {
        appendScenarioNote(scenario, "${protocol.title} • ${methodology.connectionModeCode} • Ошибка: $reason")
        appendProtocolNote(protocol, "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode} • Ошибка: $reason")
        updateScenario(
            scenario = scenario,
            progress = TestRunProgress.error(
                title = "${scenario.code} ${scenario.title}",
                details = "Не удалось выполнить сценарий на ${protocol.title} • ${methodology.connectionModeCode}: $reason",
                currentStep = currentStep,
                totalSteps = totalSteps,
            ),
        )
        updateProtocol(
            protocol = protocol,
            progress = TestRunProgress.error(
                title = protocol.title,
                details = "${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode}: $reason",
                totalSteps = protocolCompletedCount(protocol).coerceAtLeast(1),
            ),
        )
    }

    private fun idleProtocolProgress(protocol: ProtocolType): TestRunProgress =
        TestRunProgress.idle(
            title = protocol.title,
            details = "Выбери поддерживаемый сценарий или запусти серию измерений по полной методике.",
        )

    private fun idleScenarioProgress(scenario: ScenarioType): TestRunProgress =
        TestRunProgress.idle(
            title = "${scenario.code} ${scenario.title}",
            details = "Выбери поддерживаемый протокол или запусти серию измерений по полной методике.",
        )

    private fun runningProgress(
        title: String,
        details: String,
        currentStep: Int,
        totalSteps: Int,
        currentRequest: Int = 0,
        totalRequests: Int = 0,
        etaCompletedUnits: Long? = null,
        etaTotalUnits: Long? = null,
    ): TestRunProgress {
        return TestRunProgress(
            title = title,
            stateLabel = "Идет выполнение",
            details = details,
            currentStep = currentStep,
            totalSteps = totalSteps,
            status = TestRunStatus.Running,
            currentRequest = currentRequest,
            totalRequests = totalRequests,
            etaCompletedUnits = etaCompletedUnits,
            etaTotalUnits = etaTotalUnits,
        )
    }

    private fun completedProgress(
        title: String,
        details: String,
        totalSteps: Int,
        totalRequests: Int = 0,
    ): TestRunProgress {
        return TestRunProgress(
            title = title,
            stateLabel = "Тест завершен",
            details = details,
            currentStep = totalSteps,
            totalSteps = totalSteps,
            status = TestRunStatus.Completed,
            currentRequest = totalRequests,
            totalRequests = totalRequests,
        )
    }

    private fun updateFull(progress: TestRunProgress) {
        _fullTestProgress.value = progress
            .withTiming(previous = _fullTestProgress.value)
            .withReportData(
                reportNotes = fullReportNotes,
                completedSeries = fullCompletedSeries,
            )
    }

    private fun updateProtocol(
        protocol: ProtocolType,
        progress: TestRunProgress,
    ) {
        _protocolProgress.update { current ->
            current + (
                protocol to progress
                    .withTiming(previous = current[protocol])
                    .withReportData(
                        reportNotes = protocolReportNotes.getValue(protocol),
                        completedSeries = protocolCompletedSeries.getValue(protocol),
                    )
            )
        }
    }

    private fun updateScenario(
        scenario: ScenarioType,
        progress: TestRunProgress,
    ) {
        _scenarioProgress.update { current ->
            current + (
                scenario to progress
                    .withTiming(previous = current[scenario])
                    .withReportData(
                        reportNotes = scenarioReportNotes.getValue(scenario),
                        completedSeries = scenarioCompletedSeries.getValue(scenario),
                    )
            )
        }
    }

    private fun clearAllReportData() {
        fullReportNotes.clear()
        fullCompletedSeries.clear()
        protocols.forEach(::clearProtocolReportData)
        scenarios.forEach(::clearScenarioReportData)
    }

    private fun clearProtocolReportData(protocol: ProtocolType) {
        protocolReportNotes.getValue(protocol).clear()
        protocolCompletedSeries.getValue(protocol).clear()
    }

    private fun clearScenarioReportData(scenario: ScenarioType) {
        scenarioReportNotes.getValue(scenario).clear()
        scenarioCompletedSeries.getValue(scenario).clear()
    }

    private fun appendFullNote(entry: String) {
        fullReportNotes += entry
    }

    private fun appendProtocolNote(
        protocol: ProtocolType,
        entry: String,
    ) {
        protocolReportNotes.getValue(protocol) += entry
    }

    private fun appendScenarioNote(
        scenario: ScenarioType,
        entry: String,
    ) {
        scenarioReportNotes.getValue(scenario) += entry
    }

    private fun appendFullCompletedSeries(result: MeasurementSeriesResult) {
        fullCompletedSeries += result
    }

    private fun appendProtocolCompletedSeries(
        protocol: ProtocolType,
        result: MeasurementSeriesResult,
    ) {
        protocolCompletedSeries.getValue(protocol) += result
    }

    private fun appendScenarioCompletedSeries(
        scenario: ScenarioType,
        result: MeasurementSeriesResult,
    ) {
        scenarioCompletedSeries.getValue(scenario) += result
    }

    private fun protocolCompletedCount(protocol: ProtocolType): Int {
        return protocolCompletedSeries.getValue(protocol).size
    }

    private fun scenarioCompletedCount(scenario: ScenarioType): Int {
        return scenarioCompletedSeries.getValue(scenario).size
    }

    private fun currentMethodologies(): List<MeasurementMethodologyProfile> {
        val config = endpointService.config.value
        return MeasurementMethodologyProfile.fullCoverageProfiles(
            measuredRuns = config.measuredRuns,
        )
    }

    private data class EnergyBlockSummary(
        val request: EnergyBlockRequest,
        val elapsedMs: Long,
        val attempts: Int,
        val successes: Int,
        val failures: Int,
        val responseCount: Int,
        val clientDurationMicrosTotal: Long,
        val serverDurationMicrosTotal: Long,
        val deviceMetrics: DeviceSeriesMetrics,
    ) {
        private val successRate: Double
            get() = if (attempts > 0) successes.toDouble() / attempts.toDouble() else 1.0

        private val throughputOpsPerSec: Double
            get() = if (elapsedMs > 0L) successes / (elapsedMs / 1_000.0) else 0.0

        private val clientMeanMs: Double?
            get() = successes.takeIf { it > 0 }?.let {
                clientDurationMicrosTotal.toDouble() / it.toDouble() / 1_000.0
            }

        private val serverMeanMs: Double?
            get() = successes.takeIf { it > 0 }?.let {
                serverDurationMicrosTotal.toDouble() / it.toDouble() / 1_000.0
            }

        fun uiDetails(): String {
            return when (request.kind) {
                EnergyBlockKind.Idle -> "Холостой блок ${request.blockId} завершен за ${elapsedMs} мс."
                EnergyBlockKind.Workload -> {
                    "${request.blockTitle()} завершен: успешно $successes из $attempts, " +
                        "пропускная способность ${throughputOpsPerSec.formatForUi()} операций/с."
                }
            }
        }

        fun reportNote(): String {
            return buildString {
                appendLine("Блок измерения энергопотребления ${request.blockId}")
                appendLine("Тип: ${request.kind.externalName}")
                request.protocol?.let { protocol -> appendLine("Протокол: ${protocol.title}") }
                request.scenario?.let { scenario -> appendLine("Сценарий: ${scenario.code} ${scenario.shortTitle}") }
                appendLine("Режим соединения: ${request.connectionMode.code}")
                appendLine("Плановая длительность: ${request.normalizedDurationSeconds} с")
                appendLine("Фактическая длительность: ${elapsedMs} мс")
                appendLine("Попыток: $attempts")
                appendLine("Успешных операций: $successes")
                appendLine("Ошибок: $failures")
                appendLine("Доля успешных операций: ${(successRate * 100.0).formatForUi()} %")
                appendLine("Ответов/событий: $responseCount")
                appendLine("Пропускная способность: ${throughputOpsPerSec.formatForUi()} операций/с")
                appendLine("Средняя клиентская задержка: ${clientMeanMs.formatNullableForUi()} мс")
                appendLine("Среднее серверное время: ${serverMeanMs.formatNullableForUi()} мс")
                appendLine("Процессорное время: ${deviceMetrics.cpuTimeDeltaMs.logValue()} мс")
                appendLine("PSS-память, пик: ${deviceMetrics.pssPeakKb.logValue()} КБ")
                appendLine("Расход заряда по BatteryManager: ${deviceMetrics.chargeConsumedUah.logValue()} мкА·ч")
                appendLine("Расход энергии по BatteryManager: ${deviceMetrics.energyConsumedNwh.logValue()} нВт·ч")
                append("Изменение процента батареи: ${deviceMetrics.batteryPctDelta.logValue()} %")
            }
        }

        fun markerFields(): String {
            return request.commonMarkerFields() +
                " status=success" +
                " elapsedMs=$elapsedMs" +
                " attempts=$attempts" +
                " successes=$successes" +
                " failures=$failures" +
                " responseCount=$responseCount" +
                " throughputOpsPerSec=${throughputOpsPerSec.formatForLog()}" +
                " clientMeanMs=${clientMeanMs.formatNullableForLog()}" +
                " serverMeanMs=${serverMeanMs.formatNullableForLog()}" +
                " cpuMs=${deviceMetrics.cpuTimeDeltaMs.logValue()}" +
                " pssPeakKb=${deviceMetrics.pssPeakKb.logValue()}" +
                " chargeUah=${deviceMetrics.chargeConsumedUah.logValue()}" +
                " energyNwh=${deviceMetrics.energyConsumedNwh.logValue()}" +
                " batteryPct=${deviceMetrics.batteryPctDelta.logValue()}"
        }
    }
}

private fun fullExecutionOrderEntry(
    seed: Long,
    plan: List<Triple<ProtocolType, ScenarioType, MeasurementMethodologyProfile>>,
): String {
    return buildString {
        appendLine("Порядок выполнения (seed=$seed):")
        plan.forEachIndexed { index, (protocol, scenario, methodology) ->
            append(index + 1)
            append(". ")
            appendLine(seriesDescriptor(protocol, scenario, methodology))
        }
    }.trimEnd()
}

private fun protocolExecutionOrderEntry(
    seed: Long,
    protocol: ProtocolType,
    plan: List<Triple<ProtocolType, ScenarioType, MeasurementMethodologyProfile>>,
): String {
    val scenarios = plan
        .filter { (currentProtocol, _, _) -> currentProtocol == protocol }
        .map { (_, scenario, methodology) -> scenario to methodology }
    return protocolSuiteOrderEntry(seed = seed, protocol = protocol, scenarios = scenarios)
}

private fun scenarioExecutionOrderEntry(
    seed: Long,
    scenario: ScenarioType,
    plan: List<Triple<ProtocolType, ScenarioType, MeasurementMethodologyProfile>>,
): String {
    val protocols = plan
        .filter { (_, currentScenario, _) -> currentScenario == scenario }
        .map { (protocol, _, methodology) -> protocol to methodology }
    return scenarioSuiteOrderEntry(seed = seed, scenario = scenario, protocols = protocols)
}

private fun protocolSuiteOrderEntry(
    seed: Long,
    protocol: ProtocolType,
    scenarios: List<Pair<ScenarioType, MeasurementMethodologyProfile>>,
): String {
    return buildString {
        append("Случайный порядок сценариев для ")
        append(protocol.title)
        append(" (seed=")
        append(seed)
        append("): ")
        append(scenarios.joinToString(separator = " -> ") { (scenario, methodology) ->
            "${scenario.code} ${scenario.shortTitle} [${methodology.connectionModeCode}]"
        })
    }
}

private fun scenarioSuiteOrderEntry(
    seed: Long,
    scenario: ScenarioType,
    protocols: List<Pair<ProtocolType, MeasurementMethodologyProfile>>,
): String {
    return buildString {
        append("Случайный порядок протоколов для ")
        append(scenario.code)
        append(' ')
        append(scenario.shortTitle)
        append(" (seed=")
        append(seed)
        append("): ")
        append(protocols.joinToString(separator = " -> ") { (protocol, methodology) ->
            "${protocol.title} [${methodology.connectionModeCode}]"
        })
    }
}

private fun seriesDescriptor(
    protocol: ProtocolType,
    scenario: ScenarioType,
    methodology: MeasurementMethodologyProfile,
): String {
    return "${protocol.title} • ${scenario.code} ${scenario.shortTitle} • ${methodology.connectionModeCode}"
}

private fun Throwable.userMessage(): String {
    return message?.trim()?.takeIf { it.isNotEmpty() }
        ?: this::class.simpleName
        ?: "Неизвестная ошибка"
}

private fun List<Triple<ProtocolType, ScenarioType, MeasurementMethodologyProfile>>.toEtaPlan():
    List<Pair<ScenarioType, MeasurementMethodologyProfile>> {
    return map { (_, scenario, methodology) -> scenario to methodology }
}

private fun List<Pair<ScenarioType, MeasurementMethodologyProfile>>.etaTotalUnits(
    requestsPerSeries: Int,
): Long {
    return sumOf { (scenario, methodology) ->
        scenario.estimatedSeriesDurationMs(methodology, requestsPerSeries)
    }.coerceAtLeast(1L)
}

private fun List<Pair<ScenarioType, MeasurementMethodologyProfile>>.etaCompletedUnits(
    completedSeries: Int,
    currentSeriesRequests: Int,
    requestsPerSeries: Int,
): Long {
    val completedSeriesCount = completedSeries.coerceIn(0, size)
    val completedUnits = take(completedSeriesCount).sumOf { (scenario, methodology) ->
        scenario.estimatedSeriesDurationMs(methodology, requestsPerSeries)
    }
    if (completedSeriesCount >= size || requestsPerSeries <= 0) {
        return completedUnits
    }
    val (scenario, methodology) = this[completedSeriesCount]
    val currentSeriesUnits = scenario.estimatedSeriesDurationMs(methodology, requestsPerSeries)
    val currentProgressUnits = currentSeriesUnits *
        currentSeriesRequests.coerceIn(0, requestsPerSeries).toLong() /
        requestsPerSeries.toLong()
    return completedUnits + currentProgressUnits
}

private fun ScenarioType.estimatedSeriesDurationMs(
    methodology: MeasurementMethodologyProfile,
    requestsPerSeries: Int,
): Long {
    val baseMs = estimatedSingleRunDurationMs() * requestsPerSeries.toLong()
    return if (methodology.reusePersistentConnections) {
        baseMs
    } else {
        baseMs * connectionSetupMultiplierPercent() / 100L
    }
}

private fun ScenarioType.estimatedSingleRunDurationMs(): Long {
    return when (this) {
        ScenarioType.S1_SHORT_READ -> 60L
        ScenarioType.S2_LARGE_READ -> 140L
        ScenarioType.S3_PARTIAL_LARGE_READ -> 70L
        ScenarioType.S4_PAGE_READ -> 110L
        ScenarioType.S5_SMALL_WRITE_ACK -> 70L
        ScenarioType.S6_LARGE_WRITE_ACK -> 130L
        ScenarioType.S7_EVENT_STREAM -> (eventCount - 1).coerceAtLeast(0) * 50L + 150L
        ScenarioType.S8_HEAVY_EVENT_STREAM -> (eventCount - 1).coerceAtLeast(0) * 75L + 250L
        ScenarioType.S9_LONG_SESSION -> (eventCount - 1).coerceAtLeast(0) * 100L + 300L
    }
}

private fun ScenarioType.connectionSetupMultiplierPercent(): Long {
    return when (this) {
        ScenarioType.S7_EVENT_STREAM,
        ScenarioType.S8_HEAVY_EVENT_STREAM,
        ScenarioType.S9_LONG_SESSION,
        -> 125L
        else -> 110L
    }
}

private fun TestRunProgress.withReportData(
    reportNotes: List<String>,
    completedSeries: List<MeasurementSeriesResult>,
): TestRunProgress {
    return copy(
        reportNotes = reportNotes.toList(),
        completedSeries = completedSeries.toList(),
    )
}

private fun TestRunProgress.withTiming(previous: TestRunProgress?): TestRunProgress {
    val nowEpochMs = System.currentTimeMillis()
    if (status != TestRunStatus.Running) {
        val startedAt = previous?.startedAtEpochMs ?: startedAtEpochMs
        val finishedAt = when {
            status == TestRunStatus.Idle -> null
            startedAt == null -> null
            previous?.status == TestRunStatus.Running -> nowEpochMs
            previous?.finishedAtEpochMs != null -> previous.finishedAtEpochMs
            else -> nowEpochMs
        }
        return copy(
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = finishedAt,
            elapsedMs = startedAt?.let { start ->
                finishedAt?.let { finish -> (finish - start).coerceAtLeast(0L) }
            },
            estimatedRemainingMs = null,
        )
    }
    val startedAt = previous
        ?.takeIf { it.status == TestRunStatus.Running }
        ?.startedAtEpochMs
        ?: nowEpochMs
    val weightedTotalUnits = etaTotalUnits?.takeIf { it > 0L }
    val weightedCompletedUnits = etaCompletedUnits
        ?.takeIf { weightedTotalUnits != null }
        ?.coerceIn(0L, weightedTotalUnits ?: 0L)
    val totalUnits = weightedTotalUnits ?: when {
        totalRequests > 0 -> totalRequests.toLong()
        totalSteps > 0 -> totalSteps.toLong()
        else -> 0L
    }
    val completedUnits = weightedCompletedUnits ?: when {
        totalRequests > 0 -> currentRequest.coerceIn(0, totalRequests).toLong()
        totalSteps > 0 -> currentStep.coerceIn(0, totalSteps).toLong()
        else -> 0L
    }
    val estimatedRemainingMs = if (completedUnits > 0L && completedUnits < totalUnits) {
        val elapsedMs = (nowEpochMs - startedAt).coerceAtLeast(0L)
        (elapsedMs * (totalUnits - completedUnits)) / completedUnits
    } else {
        null
    }
    return copy(
        startedAtEpochMs = startedAt,
        finishedAtEpochMs = null,
        elapsedMs = null,
        estimatedRemainingMs = estimatedRemainingMs,
    )
}

private fun MeasurementMethodologyProfile.requestCountPerSeries(): Int {
    return warmUpRuns + measuredRuns
}

private fun EnergyBlockRequest.resolveEndpoint(current: BackendEndpointConfig): BackendEndpointConfig {
    return current.copy(
        host = backendHost?.trim()?.takeIf(String::isNotEmpty) ?: current.host,
        httpPort = httpPort ?: current.httpPort,
        grpcPort = grpcPort ?: current.grpcPort,
    )
}

private fun EnergyBlockRequest.startedMarkerFields(): String {
    return commonMarkerFields() +
        " status=started" +
        " durationSeconds=$normalizedDurationSeconds" +
        " host=${backendHost.logValue()}" +
        " httpPort=${httpPort.logValue()}" +
        " grpcPort=${grpcPort.logValue()}"
}

private fun EnergyBlockRequest.failedMarkerFields(reason: String): String {
    return commonMarkerFields() +
        " status=failed" +
        " reason=${reason.toLogToken()}"
}

private fun EnergyBlockRequest.commonMarkerFields(): String {
    return "blockId=${blockId.toLogToken()}" +
        " kind=${kind.externalName}" +
        " protocol=${protocol?.name ?: "IDLE"}" +
        " scenario=${scenario?.code ?: "IDLE"}" +
        " mode=${connectionMode.code}"
}

private fun EnergyBlockRequest.startDetails(): String {
    return when (kind) {
        EnergyBlockKind.Idle -> "Запущен холостой блок ${blockId.toLogToken()} на $normalizedDurationSeconds с."
        EnergyBlockKind.Workload -> "Запущен ${blockTitle()} на $normalizedDurationSeconds с."
    }
}

private fun EnergyBlockRequest.runningDetails(
    attempts: Int,
    successes: Int,
    failures: Int,
): String {
    return when (kind) {
        EnergyBlockKind.Idle -> "Холостой блок ${blockId.toLogToken()}: сетевые запросы не выполняются."
        EnergyBlockKind.Workload -> "${blockTitle()}: попыток $attempts, успешно $successes, ошибок $failures."
    }
}

private fun EnergyBlockRequest.blockTitle(): String {
    val protocolTitle = protocol?.title ?: "без протокола"
    val scenarioTitle = scenario?.let { "${it.code} ${it.shortTitle}" } ?: "без сценария"
    return "$protocolTitle • $scenarioTitle • ${connectionMode.code}"
}

private fun Double.formatForUi(): String =
    String.format(Locale.US, "%.2f", this)

private fun Double.formatForLog(): String =
    String.format(Locale.US, "%.4f", this)

private fun Double?.formatNullableForUi(): String =
    this?.formatForUi() ?: "н/д"

private fun Double?.formatNullableForLog(): String =
    this?.formatForLog() ?: "na"

private fun Number?.logValue(): String =
    this?.toString() ?: "na"

private fun String?.logValue(): String =
    this?.toLogToken() ?: "na"

private fun String.toLogToken(): String =
    replace(Regex("[^A-Za-z0-9_.:-]"), "_").take(180)

private const val ENERGY_LOG_TAG = "EnergyBenchmark"
private const val ENERGY_MARKER_STARTED = "ENERGY_BLOCK_STARTED"
private const val ENERGY_MARKER_DONE = "ENERGY_BLOCK_DONE"
private const val ENERGY_MARKER_FAILED = "ENERGY_BLOCK_FAILED"
private const val ENERGY_PROGRESS_PERIOD_MS = 1_000L
private const val ENERGY_IDLE_SAMPLE_STRIDE = 5
private const val ENERGY_WORKLOAD_SAMPLE_STRIDE = 100
private const val ENERGY_MAX_CONSECUTIVE_FAILURES = 10

private fun List<ScenarioExecutionResult>.toMeasurementSeriesResult(
    protocol: ProtocolType,
    scenario: ScenarioType,
    methodology: MeasurementMethodologyProfile,
    seriesElapsedMs: Long,
    connectionSetupMs: Long?,
    failedRuns: Int,
    deviceMetrics: DeviceSeriesMetrics,
    errorMessages: List<String>,
): MeasurementSeriesResult {
    val clientDurations = map(ScenarioExecutionResult::clientDurationMsPrecise)
    val serverDurations = map(ScenarioExecutionResult::serverDurationMsPrecise)
    val networkDurations = map(ScenarioExecutionResult::networkPlusClientDurationMsPrecise)
    val connectionMetrics = aggregateConnectionMetrics(
        seriesElapsedMs = seriesElapsedMs,
        connectionSetupMs = connectionSetupMs,
    )
    val applicability = when {
        failedRuns == 0 -> MeasurementApplicability.Full
        size >= maxOf(5, ceil(methodology.measuredRuns * 0.8).toInt()) -> MeasurementApplicability.Limited
        else -> MeasurementApplicability.Insufficient
    }

    return MeasurementSeriesResult(
        protocol = protocol,
        scenario = scenario,
        methodology = methodology,
        successfulRuns = size,
        failedRuns = failedRuns,
        seriesElapsedMs = seriesElapsedMs,
        connectionSetupMs = connectionSetupMs,
        clientMedianMs = clientDurations.medianOrNull(),
        clientMeanMs = clientDurations.averageOrNull(),
        clientP95Ms = clientDurations.percentileOrNull(0.95),
        clientP99Ms = clientDurations.percentileOrNull(0.99),
        serverMedianMs = serverDurations.medianOrNull(),
        serverMeanMs = serverDurations.averageOrNull(),
        serverP95Ms = serverDurations.percentileOrNull(0.95),
        serverP99Ms = serverDurations.percentileOrNull(0.99),
        networkMedianMs = networkDurations.medianOrNull(),
        networkP95Ms = networkDurations.percentileOrNull(0.95),
        throughputOpsPerSec = seriesElapsedMs.takeIf { it > 0L }?.let { size / (it / 1000.0) },
        errorRate = failedRuns.toDouble() / methodology.measuredRuns.toDouble(),
        applicability = applicability,
        connectionMetrics = connectionMetrics,
        deviceMetrics = deviceMetrics,
        requestResults = toList(),
        errorMessages = errorMessages,
    )
}

private fun List<ScenarioExecutionResult>.aggregateConnectionMetrics(
    seriesElapsedMs: Long,
    connectionSetupMs: Long?,
): SeriesConnectionMetrics? {
    val telemetry = mapNotNull(ScenarioExecutionResult::connectionTelemetry)
    if (telemetry.isEmpty()) return null

    val expectedEventsTotal = telemetry.sumOf { it.expectedEvents }
    val receivedEventsTotal = telemetry.sumOf { it.receivedEvents }
    val reconnectCount = telemetry.sumOf { it.reconnectCount }
    val unexpectedCloseCount = telemetry.sumOf { it.unexpectedCloseCount }
    val recoveryDurations = telemetry.flatMap { it.recoveryDurationsMs }.map(Long::toDouble)
    val timeToFirst = telemetry.mapNotNull { it.timeToFirstEventMs }.map(Long::toDouble)
    val streamCompletion = telemetry.mapNotNull { it.streamCompletionMs }.map(Long::toDouble)
    val interEventGaps = telemetry.flatMap { it.interEventGapsMs }.map(Long::toDouble)
    val heartbeatRtts = telemetry.flatMap { it.heartbeatRttsMs }.map(Long::toDouble)
    val heartbeatSent = telemetry.sumOf { it.heartbeatSent }
    val heartbeatAcknowledged = telemetry.sumOf { it.heartbeatAcknowledged }
    val lostEvents = (expectedEventsTotal - receivedEventsTotal).coerceAtLeast(0)
    val lossRate = when {
        expectedEventsTotal <= 0 -> 0.0
        else -> lostEvents.toDouble() / expectedEventsTotal.toDouble()
    }
    val heartbeatLossRate = heartbeatSent.takeIf { it > 0 }?.let { sent ->
        ((sent - heartbeatAcknowledged).coerceAtLeast(0)).toDouble() / sent.toDouble()
    }
    val reconnectsPerRun = reconnectCount.toDouble() / size.toDouble()
    val reconnectsPerMinute = seriesElapsedMs.takeIf { it > 0L }?.let {
        reconnectCount.toDouble() / (it / 60_000.0)
    }
    val stabilityIndex = calculateStabilityIndex(
        lossRate = lossRate,
        reconnectsPerRun = reconnectsPerRun,
        unexpectedCloseRate = unexpectedCloseCount.toDouble() / size.toDouble(),
        heartbeatLossRate = heartbeatLossRate ?: 0.0,
    )

    return SeriesConnectionMetrics(
        expectedEventsTotal = expectedEventsTotal,
        receivedEventsTotal = receivedEventsTotal,
        setupMedianMs = connectionSetupMs?.toDouble(),
        setupP95Ms = connectionSetupMs?.toDouble(),
        lossRate = lossRate,
        reconnectCount = reconnectCount,
        reconnectsPerRun = reconnectsPerRun,
        reconnectsPerMinute = reconnectsPerMinute,
        unexpectedCloseCount = unexpectedCloseCount,
        recoveryMedianMs = recoveryDurations.medianOrNull(),
        recoveryP95Ms = recoveryDurations.percentileOrNull(0.95),
        heartbeatSent = heartbeatSent,
        heartbeatAcknowledged = heartbeatAcknowledged,
        heartbeatLossRate = heartbeatLossRate,
        timeToFirstMedianMs = timeToFirst.medianOrNull(),
        timeToFirstP95Ms = timeToFirst.percentileOrNull(0.95),
        streamCompletionMedianMs = streamCompletion.medianOrNull(),
        streamCompletionP95Ms = streamCompletion.percentileOrNull(0.95),
        interEventGapMedianMs = interEventGaps.medianOrNull(),
        interEventGapP95Ms = interEventGaps.percentileOrNull(0.95),
        heartbeatRttMedianMs = heartbeatRtts.medianOrNull(),
        heartbeatRttP95Ms = heartbeatRtts.percentileOrNull(0.95),
        stabilityIndex = stabilityIndex,
    )
}

private fun calculateStabilityIndex(
    lossRate: Double,
    reconnectsPerRun: Double,
    unexpectedCloseRate: Double,
    heartbeatLossRate: Double,
): Double {
    val deliveryScore = (1.0 - lossRate).coerceIn(0.0, 1.0)
    val heartbeatScore = (1.0 - heartbeatLossRate).coerceIn(0.0, 1.0)
    val reconnectScore = (1.0 / (1.0 + reconnectsPerRun)).coerceIn(0.0, 1.0)
    val closeScore = (1.0 / (1.0 + unexpectedCloseRate)).coerceIn(0.0, 1.0)
    return deliveryScore * heartbeatScore * reconnectScore * closeScore
}

private fun List<Double>.medianOrNull(): Double? {
    return percentileOrNull(0.5)
}

private fun List<Double>.percentileOrNull(
    percentile: Double,
): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val rank = ceil(percentile.coerceIn(0.0, 1.0) * sorted.size).toInt().coerceAtLeast(1)
    return sorted[rank - 1]
}

private fun List<Double>.averageOrNull(): Double? {
    return takeIf { it.isNotEmpty() }?.average()
}
