package com.huanlab.engine.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.huanlab.engine.shared.ThermalLevel
import java.io.File
import java.util.concurrent.atomic.AtomicReference

data class MemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedRatio: Float
        get() = if (totalBytes <= 0L) 0f else {
            (((totalBytes - availableBytes).toDouble() / totalBytes.toDouble()) * 100.0)
                .toFloat().coerceIn(0f, 100f)
        }
}

data class RefreshRateSnapshot(
    val requestedHz: Metric<Float>,
    val actualHz: Metric<Float>,
    val supportedHz: List<Float>,
)

data class DeviceSnapshot(
    val cpuPercent: Metric<Float>,
    val memory: Metric<MemorySnapshot>,
    val thermal: Metric<ThermalLevel>,
    val refreshRate: RefreshRateSnapshot,
    val sampledAtElapsedMillis: Long,
)

/**
 * 真实设备采样器。
 *
 * CPU 采用跨调用窗口：保留上一次 /proc/stat 的 ticks，用两次采样差值计算；
 * 窗口过短（<200ms）或过长（>10s，说明轮询中断）时返回 Unknown，而不是硬编码 0%。
 */
class DeviceMonitor(private val context: Context) {

    private val appContext = context.applicationContext
    private val lastCpu = AtomicReference<CpuTicks?>(null)
    private val lastCpuAt = AtomicReference(0L)

    fun sample(): DeviceSnapshot {
        val now = SystemClock.elapsedRealtime()
        return DeviceSnapshot(
            cpuPercent = sampleCpu(now),
            memory = sampleMemory(),
            thermal = sampleThermal(),
            refreshRate = sampleRefreshRate(),
            sampledAtElapsedMillis = now,
        )
    }

    private fun sampleCpu(now: Long): Metric<Float> {
        val current = readCpuTicks() ?: return Metric.Unknown("无法读取 /proc/stat")
        val previous = lastCpu.getAndSet(current)
        val previousAt = lastCpuAt.getAndSet(now)
        if (previous == null) {
            return Metric.Unknown("采样窗口不足（首次采样）")
        }
        val window = now - previousAt
        if (window < MIN_WINDOW_MS) {
            return Metric.Unknown("采样窗口过短（${window}ms）")
        }
        if (window > MAX_WINDOW_MS) {
            return Metric.Unknown("采样窗口中断（${window}ms）")
        }
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0L) {
            return Metric.Unknown("CPU ticks 未增长")
        }
        val busy = (totalDelta - idleDelta).coerceAtLeast(0L)
        return Metric.Known(
            ((busy.toDouble() / totalDelta.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f),
        )
    }

    private fun sampleMemory(): Metric<MemorySnapshot> = runCatching {
        var total = 0L
        var available = 0L
        File("/proc/meminfo").forEachLine { line ->
            when {
                line.startsWith("MemTotal:") -> total = parseKb(line)
                line.startsWith("MemAvailable:") -> available = parseKb(line)
            }
        }
        if (total <= 0L) {
            Metric.Unknown("MemTotal 读取失败")
        } else if (available <= 0L || available > total) {
            Metric.Unknown("MemAvailable 异常（$available / $total）")
        } else {
            Metric.Known(MemorySnapshot(total, available))
        }
    }.getOrElse { Metric.Unknown("读取 /proc/meminfo 失败: ${it.javaClass.simpleName}") }

    private fun sampleThermal(): Metric<ThermalLevel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Metric.Unknown("系统版本过低，无 ThermalStatus API")
        }
        return runCatching {
            val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            Metric.Known(ThermalLevel.fromStatus(power.currentThermalStatus))
        }.getOrElse { Metric.Unknown("读取 ThermalStatus 失败") }
    }

    private fun sampleRefreshRate(): RefreshRateSnapshot {
        val requested = runCatching {
            val value = Settings.System.getFloat(
                appContext.contentResolver,
                "peak_refresh_rate",
                -1f,
            )
            if (value > 0f) Metric.Known(value) else Metric.Unknown("系统未设置 peak_refresh_rate")
        }.getOrElse { Metric.Unknown("读取 peak_refresh_rate 失败") }

        val display = runCatching {
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay
        }.getOrNull()
            ?: return RefreshRateSnapshot(requested, Metric.Unknown("无法获取 Display"), emptyList())

        val actual = runCatching {
            Metric.Known(display.mode.refreshRate)
        }.getOrElse { Metric.Unknown("读取 Display.Mode 失败") }

        val supported = runCatching {
            display.supportedModes.map { it.refreshRate }.distinct().sorted()
        }.getOrDefault(emptyList())

        return RefreshRateSnapshot(requested, actual, supported)
    }

    private fun parseKb(line: String): Long {
        val digits = line.filter { it.isDigit() }
        return digits.toLongOrNull()?.times(1024L) ?: 0L
    }

    private data class CpuTicks(val total: Long, val idle: Long)

    private fun readCpuTicks(): CpuTicks? {
        val line = runCatching {
            File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
        }.getOrNull() ?: return null
        val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return null
        val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
        return CpuTicks(total = parts.sum(), idle = idle)
    }

    private companion object {
        const val MIN_WINDOW_MS = 200L
        const val MAX_WINDOW_MS = 10_000L
    }
}
