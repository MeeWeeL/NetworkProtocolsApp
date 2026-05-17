package ru.meeweel.network_protocols_app.model

enum class MeasurementApplicability(
    val title: String,
) {
    Full(title = "полная"),
    Limited(title = "ограниченная"),
    Insufficient(title = "недостаточная"),
}
