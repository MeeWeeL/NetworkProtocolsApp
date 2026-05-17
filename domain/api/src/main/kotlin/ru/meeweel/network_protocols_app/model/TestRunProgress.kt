package ru.meeweel.network_protocols_app.model

data class TestRunProgress(
    val title: String,
    val stateLabel: String,
    val details: String,
    val currentStep: Int,
    val totalSteps: Int,
    val status: TestRunStatus,
    val reportNotes: List<String> = emptyList(),
    val completedSeries: List<MeasurementSeriesResult> = emptyList(),
    val currentRequest: Int = 0,
    val totalRequests: Int = 0,
    val startedAtEpochMs: Long? = null,
    val finishedAtEpochMs: Long? = null,
    val elapsedMs: Long? = null,
    val estimatedRemainingMs: Long? = null,
    val etaCompletedUnits: Long? = null,
    val etaTotalUnits: Long? = null,
) {
    val progressFraction: Float
        get() = when {
            totalRequests > 0 -> (currentRequest.toFloat() / totalRequests.toFloat()).coerceIn(0f, 1f)
            totalSteps <= 0 -> 0f
            else -> (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
        }

    val progressLabel: String
        get() {
            val base = when {
                totalRequests > 0 && totalSteps > 0 ->
                    "Запрос ${currentRequest.coerceIn(0, totalRequests)} из $totalRequests • серия ${displayStep()} из $totalSteps"
                totalRequests > 0 ->
                    "Запрос ${currentRequest.coerceIn(0, totalRequests)} из $totalRequests"
                totalSteps > 0 ->
                    "Серия ${displayStep()} из $totalSteps"
                else -> "Ожидание запуска"
            }
            val eta = estimatedRemainingLabel
            return if (status == TestRunStatus.Running && eta != null) {
                "$base • осталось $eta"
            } else {
                base
            }
        }

    val estimatedRemainingLabel: String?
        get() = estimatedRemainingMs
            ?.takeIf { it > 0L }
            ?.toDurationLabel()

    val elapsedLabel: String?
        get() = elapsedMs
            ?.takeIf { it >= 0L }
            ?.toDurationLabel()

    private fun displayStep(): Int {
        if (totalSteps <= 0) return 0
        val normalized = currentStep.coerceIn(0, totalSteps)
        return when (status) {
            TestRunStatus.Running -> normalized.coerceAtLeast(1)
            else -> normalized
        }
    }

    private fun Long.toDurationLabel(): String {
        val totalSeconds = ((this + 999L) / 1000L).coerceAtLeast(1L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> {
                if (minutes > 0L) "$hours ч $minutes мин" else "$hours ч"
            }
            minutes >= 10L -> "$minutes мин"
            minutes > 0L -> "$minutes мин $seconds с"
            else -> "$seconds с"
        }
    }

    companion object {
        fun idle(
            title: String,
            details: String,
        ): TestRunProgress = TestRunProgress(
            title = title,
            stateLabel = "Тест не запущен",
            details = details,
            currentStep = 0,
            totalSteps = 0,
            status = TestRunStatus.Idle,
        )

        fun error(
            title: String,
            details: String,
            currentStep: Int = 0,
            totalSteps: Int = 0,
        ): TestRunProgress = TestRunProgress(
            title = title,
            stateLabel = "Ошибка выполнения",
            details = details,
            currentStep = currentStep,
            totalSteps = totalSteps,
            status = TestRunStatus.Error,
        )
    }
}
