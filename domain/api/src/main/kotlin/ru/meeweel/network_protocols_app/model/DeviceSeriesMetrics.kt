package ru.meeweel.network_protocols_app.model

data class DeviceSeriesMetrics(
    val sampleCount: Int,
    val cpuTimeDeltaMs: Long?,
    val javaHeapDeltaKb: Long?,
    val javaHeapPeakKb: Long?,
    val nativeHeapDeltaKb: Long?,
    val nativeHeapPeakKb: Long?,
    val pssDeltaKb: Long?,
    val pssPeakKb: Long?,
    val chargeConsumedUah: Int?,
    val energyConsumedNwh: Long?,
    val batteryPctDelta: Int?,
) {
    val hasAnyData: Boolean
        get() = cpuTimeDeltaMs != null ||
            javaHeapDeltaKb != null ||
            javaHeapPeakKb != null ||
            nativeHeapDeltaKb != null ||
            nativeHeapPeakKb != null ||
            pssDeltaKb != null ||
            pssPeakKb != null ||
            chargeConsumedUah != null ||
            energyConsumedNwh != null ||
            batteryPctDelta != null
}
