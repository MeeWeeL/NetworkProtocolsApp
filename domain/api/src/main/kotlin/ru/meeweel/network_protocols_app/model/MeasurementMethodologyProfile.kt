package ru.meeweel.network_protocols_app.model

/**
 * Паспорт одной измерительной серии.
 *
 * Профиль фиксирует число прогревочных и измеряемых запусков, частоту
 * ресурсной телеметрии и режим жизненного цикла соединения:
 * - false означает h_req: открываем соединение заново для каждого обращения;
 * - true означает h_series: один раз готовим канал и используем его в серии.
 */
data class MeasurementMethodologyProfile(
    val calibrationChecks: Int,
    val warmUpRuns: Int,
    val measuredRuns: Int,
    val maxReconnectAttempts: Int,
    val resourceSamplingStride: Int,
    val reusePersistentConnections: Boolean,
) {
    val connectionModeCode: String
        get() = if (reusePersistentConnections) "h_series" else "h_req"

    val connectionModeLabel: String
        get() = if (reusePersistentConnections) {
            "соединение на серию (h_series)"
        } else {
            "соединение на каждый запрос (h_req)"
        }

    val methodologyLabel: String
        get() = buildString {
            append("Калибровка таймеров ")
            append(calibrationChecks)
            append(" проверок, прогрев ")
            append(warmUpRuns)
            append(", измеряемых повторов ")
            append(measuredRuns)
            append(", переподключений до ")
            append(maxReconnectAttempts)
            append(", телеметрия каждые ")
            append(resourceSamplingStride)
            append(" измерений")
            append(", режим ")
            append(connectionModeLabel)
        }

    companion object {
        /**
         * Базовый профиль для обычного полного теста.
         *
         * Прогревочные обращения не входят в итоговую статистику. В отчет
         * попадает только измеряемая часть серии.
         */
        fun defaultProfile(): MeasurementMethodologyProfile = MeasurementMethodologyProfile(
            calibrationChecks = 32,
            warmUpRuns = 3,
            measuredRuns = 1000,
            maxReconnectAttempts = 3,
            resourceSamplingStride = 25,
            reusePersistentConnections = true,
        )

        /**
         * Для каждой пары протокол-сценарий всегда создаются две серии:
         * h_req и h_series. Их нельзя усреднять, потому что они отвечают
         * на разные инженерные вопросы.
         */
        fun fullCoverageProfiles(measuredRuns: Int): List<MeasurementMethodologyProfile> {
            val baseProfile = defaultProfile().copy(measuredRuns = measuredRuns)
            return listOf(
                baseProfile.copy(reusePersistentConnections = false),
                baseProfile.copy(reusePersistentConnections = true),
            )
        }
    }
}
