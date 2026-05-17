package ru.meeweel.network_protocols_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Список упражнений, которые выполняют все сравниваемые технологии.
 *
 * Сценарий - это не экран приложения один-в-один, а контролируемая задача:
 * коротко прочитать, прочитать большой объект, записать данные, принять поток
 * событий и так далее. Такой список нужен, чтобы сравнивать не "вообще REST
 * против gRPC", а конкретную операцию с понятным размером данных и метрикой.
 */
@Serializable
enum class ScenarioType(
    val code: String,
    val title: String,
    val shortTitle: String,
    val description: String,
    val qClass: String,
    val loadProfile: String,
    val payloadSizeBytes: Int,
    val eventCount: Int,
) {
    @SerialName("S1")
    S1_SHORT_READ(
        code = "S1",
        title = "Короткий ответ",
        shortTitle = "Короткий ответ",
        description = "Небольшой запрос и короткий ответ.",
        qClass = "Q1",
        loadProfile = "L1",
        payloadSizeBytes = 512,
        eventCount = 1,
    ),
    @SerialName("S2")
    S2_LARGE_READ(
        code = "S2",
        title = "Полный большой объект",
        shortTitle = "Большой объект",
        description = "Чтение полного структурированного объекта с большим числом полей и списков.",
        qClass = "Q2",
        loadProfile = "L1",
        payloadSizeBytes = 32_768,
        eventCount = 1,
    ),
    @SerialName("S3")
    S3_PARTIAL_LARGE_READ(
        code = "S3",
        title = "Частичное чтение объекта",
        shortTitle = "Частичное чтение",
        description = "Чтение того же объекта в виде компактной проекции для экрана, где нужны только несколько полей.",
        qClass = "Q2",
        loadProfile = "L1",
        payloadSizeBytes = 768,
        eventCount = 1,
    ),
    @SerialName("S4")
    S4_PAGE_READ(
        code = "S4",
        title = "Страница списка",
        shortTitle = "Страница списка",
        description = "Чтение страницы списка с курсором, агрегатной сводкой и фасетами.",
        qClass = "Q2",
        loadProfile = "L1",
        payloadSizeBytes = 12_288,
        eventCount = 1,
    ),
    @SerialName("S5")
    S5_SMALL_WRITE_ACK(
        code = "S5",
        title = "Малая запись с подтверждением",
        shortTitle = "Малая запись",
        description = "Передача небольшого набора данных на сервер с подтверждением приема.",
        qClass = "Q3",
        loadProfile = "L2",
        payloadSizeBytes = 512,
        eventCount = 1,
    ),
    @SerialName("S6")
    S6_LARGE_WRITE_ACK(
        code = "S6",
        title = "Большая запись с подтверждением",
        shortTitle = "Большая запись",
        description = "Передача большого набора данных на сервер с подтверждением приема.",
        qClass = "Q3",
        loadProfile = "L2",
        payloadSizeBytes = 8_192,
        eventCount = 1,
    ),
    @SerialName("S7")
    S7_EVENT_STREAM(
        code = "S7",
        title = "Поток малых событий",
        shortTitle = "Малый поток",
        description = "Подписка на поток компактных событий и прием обновлений в реальном времени.",
        qClass = "Q4",
        loadProfile = "L2",
        payloadSizeBytes = 1_024,
        eventCount = 5,
    ),
    @SerialName("S8")
    S8_HEAVY_EVENT_STREAM(
        code = "S8",
        title = "Поток больших событий",
        shortTitle = "Большой поток",
        description = "Подписка на поток структурированных событий с более тяжелой полезной нагрузкой.",
        qClass = "Q4",
        loadProfile = "L2",
        payloadSizeBytes = 8_192,
        eventCount = 5,
    ),
    @SerialName("S9")
    S9_LONG_SESSION(
        code = "S9",
        title = "Длительная сессия",
        shortTitle = "Сессия",
        description = "Длительное соединение с контролем служебных сигналов и устойчивости канала.",
        qClass = "Q5",
        loadProfile = "L1",
        payloadSizeBytes = 256,
        eventCount = 1,
    ),
}
