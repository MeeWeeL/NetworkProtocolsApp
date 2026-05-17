package ru.meeweel.network_protocols_app.data.telemetry

import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import ru.meeweel.network_protocols_app.model.DeviceSeriesMetrics

class DeviceTelemetryCollector(
    private val context: Context,
) {
    fun startSession(): Session {
        return Session(context = context)
    }

    class Session(
        context: Context,
    ) {
        private val batteryManager = context.getSystemService(BatteryManager::class.java)
        private val startSnapshot = captureSnapshot()
        private var peakSnapshot = startSnapshot
        private var sampleCount = 1

        fun sample() {
            val snapshot = captureSnapshot()
            peakSnapshot = peakSnapshot.maxBy(snapshot)
            sampleCount += 1
        }

        fun finish(): DeviceSeriesMetrics {
            val endSnapshot = captureSnapshot()
            peakSnapshot = peakSnapshot.maxBy(endSnapshot)
            return DeviceSeriesMetrics(
                sampleCount = sampleCount + 1,
                cpuTimeDeltaMs = endSnapshot.cpuTimeMs - startSnapshot.cpuTimeMs,
                javaHeapDeltaKb = (endSnapshot.javaHeapUsedBytes - startSnapshot.javaHeapUsedBytes) / 1024L,
                javaHeapPeakKb = peakSnapshot.javaHeapUsedBytes / 1024L,
                nativeHeapDeltaKb = (endSnapshot.nativeHeapAllocatedBytes - startSnapshot.nativeHeapAllocatedBytes) / 1024L,
                nativeHeapPeakKb = peakSnapshot.nativeHeapAllocatedBytes / 1024L,
                pssDeltaKb = (endSnapshot.totalPssKb - startSnapshot.totalPssKb).toLong(),
                pssPeakKb = peakSnapshot.totalPssKb.toLong(),
                chargeConsumedUah = startSnapshot.chargeCounterUah?.let { start ->
                    endSnapshot.chargeCounterUah?.let { end ->
                        (start - end).coerceAtLeast(0)
                    }
                },
                energyConsumedNwh = startSnapshot.energyCounterNwh?.let { start ->
                    endSnapshot.energyCounterNwh?.let { end ->
                        (start - end).coerceAtLeast(0L)
                    }
                },
                batteryPctDelta = startSnapshot.batteryPct?.let { start ->
                    endSnapshot.batteryPct?.let { end ->
                        (start - end).coerceAtLeast(0)
                    }
                },
            )
        }

        private fun captureSnapshot(): DeviceTelemetrySnapshot {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            val runtime = Runtime.getRuntime()
            return DeviceTelemetrySnapshot(
                cpuTimeMs = Process.getElapsedCpuTime(),
                javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
                totalPssKb = memoryInfo.totalPss,
                chargeCounterUah = batteryManager.intPropertyOrNull(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
                energyCounterNwh = batteryManager.longPropertyOrNull(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER),
                batteryPct = batteryManager.intPropertyOrNull(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            )
        }

        private data class DeviceTelemetrySnapshot(
            val cpuTimeMs: Long,
            val javaHeapUsedBytes: Long,
            val nativeHeapAllocatedBytes: Long,
            val totalPssKb: Int,
            val chargeCounterUah: Int?,
            val energyCounterNwh: Long?,
            val batteryPct: Int?,
        ) {
            fun maxBy(other: DeviceTelemetrySnapshot): DeviceTelemetrySnapshot {
                return DeviceTelemetrySnapshot(
                    cpuTimeMs = maxOf(cpuTimeMs, other.cpuTimeMs),
                    javaHeapUsedBytes = maxOf(javaHeapUsedBytes, other.javaHeapUsedBytes),
                    nativeHeapAllocatedBytes = maxOf(nativeHeapAllocatedBytes, other.nativeHeapAllocatedBytes),
                    totalPssKb = maxOf(totalPssKb, other.totalPssKb),
                    chargeCounterUah = chargeCounterUah ?: other.chargeCounterUah,
                    energyCounterNwh = energyCounterNwh ?: other.energyCounterNwh,
                    batteryPct = batteryPct ?: other.batteryPct,
                )
            }
        }
    }
}

private fun BatteryManager?.intPropertyOrNull(id: Int): Int? {
    val value = this?.getIntProperty(id) ?: Int.MIN_VALUE
    return value.takeUnless { it == Int.MIN_VALUE }
}

private fun BatteryManager?.longPropertyOrNull(id: Int): Long? {
    val value = this?.getLongProperty(id) ?: Long.MIN_VALUE
    return value.takeUnless { it == Long.MIN_VALUE }
}
