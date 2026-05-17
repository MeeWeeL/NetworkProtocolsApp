package ru.meeweel.network_protocols_app.model

/**
 * Простая таблица "что чем можно мерить".
 *
 * Не каждый протокол честно подходит для каждого сценария. Например, REST и SOAP
 * нормально проверять как запрос-ответ, но длительную сессию S9 через них пришлось
 * бы имитировать частым опросом сервера. Это уже был бы другой сценарий, поэтому
 * такие пары здесь не включаются.
 */
object ProtocolScenarioMatrix {
    private val supportedByProtocol = mapOf(
        ProtocolType.REST to listOf(
            ScenarioType.S1_SHORT_READ,
            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
        ),
        ProtocolType.SOAP to listOf(
            ScenarioType.S1_SHORT_READ,
            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
        ),
        ProtocolType.GRAPHQL to listOf(
            ScenarioType.S1_SHORT_READ,
            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
        ),
        ProtocolType.GRPC to listOf(
            ScenarioType.S1_SHORT_READ,
            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            ScenarioType.S9_LONG_SESSION,
        ),
        ProtocolType.WEBSOCKET to listOf(
            ScenarioType.S1_SHORT_READ,
            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            ScenarioType.S9_LONG_SESSION,
        ),
    )

    /**
     * Возвращает сценарии, которые разрешены для протокола в методике.
     * Именно из этого списка строится полный тест, поэтому случайные
     * "нечестные" пары в измерения не попадают.
     */
    fun supportedScenarios(protocol: ProtocolType): List<ScenarioType> =
        supportedByProtocol.getValue(protocol)

    /**
     * Обратный поиск: какие протоколы можно запускать для выбранного сценария.
     * Используется на экранах приложения и при подсчете полного плана теста.
     */
    fun supportedProtocols(scenario: ScenarioType): List<ProtocolType> =
        ProtocolType.entries.filter { protocol -> supports(protocol, scenario) }

    fun supports(
        protocol: ProtocolType,
        scenario: ScenarioType,
    ): Boolean = scenario in supportedScenarios(protocol)
}
