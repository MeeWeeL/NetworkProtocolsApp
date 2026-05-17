package ru.meeweel.network_protocols_app.model

data class BackendHealthState(
    val status: BackendHealthStatus,
    val title: String,
    val details: String,
) {
    companion object {
        fun idle(): BackendHealthState = BackendHealthState(
            status = BackendHealthStatus.Idle,
            title = "Стенд не проверен",
            details = "Укажи хост backend и запусти проверку соединения.",
        )
    }
}
