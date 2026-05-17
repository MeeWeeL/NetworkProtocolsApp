package ru.meeweel.network_protocols_app.core.designsystem.component

data class NpProgressReportItem(
    val title: String,
    val summary: String,
    val detailsProvider: () -> String,
    val fullDetailsProvider: (() -> String)? = null,
)
