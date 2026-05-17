package ru.meeweel.network_protocols_app.model.energy

enum class EnergyBlockKind(
    val externalName: String,
) {
    Idle(externalName = "idle"),
    Workload(externalName = "workload"),
    ;

    companion object {
        fun fromExternal(value: String?): EnergyBlockKind? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { kind ->
                kind.externalName == normalized || kind.name.lowercase() == normalized
            }
        }
    }
}
