package ru.meeweel.network_protocols_app.model.energy

enum class EnergyConnectionMode(
    val code: String,
    val reusePersistentConnections: Boolean,
) {
    HReq(
        code = "h_req",
        reusePersistentConnections = false,
    ),
    HSeries(
        code = "h_series",
        reusePersistentConnections = true,
    ),
    ;

    companion object {
        fun fromExternal(value: String?): EnergyConnectionMode? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { mode ->
                mode.code == normalized || mode.name.lowercase() == normalized
            }
        }
    }
}
