package com.huanlab.engine.service.sampling

import android.content.Context
import com.huanlab.engine.core.Engine
import com.huanlab.engine.service.DeviceMonitor
import com.huanlab.engine.service.EngineProvider
import com.huanlab.engine.service.metrics.CpuFrequencyMonitor
import com.huanlab.engine.service.metrics.CpuUsageMetric
import com.huanlab.engine.service.metrics.CpuUsageProvider
import com.huanlab.engine.service.metrics.CpuFrequencySnapshot
import com.huanlab.engine.service.metrics.ThermalMonitor
import com.huanlab.engine.service.metrics.ThermalSnapshot
import com.huanlab.engine.service.DeviceSnapshot
import com.huanlab.engine.service.ForegroundAppDetector
import com.huanlab.engine.service.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SamplingState(
    val snapshot: DeviceSnapshot? = null,
    val foreground: Metric<String> = Metric.Unknown("尚未采样"),
    val cpuFrequency: CpuFrequencySnapshot? = null,
    val cpuUsage: CpuUsageMetric? = null,
    val thermalSensors: ThermalSnapshot? = null,
    val running: Boolean = false,
)

/**
 * 统一采样协调器：CPU / 内存 / 热状态 / 刷新率 / 前台应用只有这一个真实来源。
 * 首页、监控页、悬浮窗、健康中心都订阅同一个 StateFlow，禁止各自再实现采样。
 */
class SamplingCoordinator(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitor = DeviceMonitor(context)
    private val foregroundDetector = ForegroundAppDetector(context)
    private val engine: Engine = EngineProvider.get(context)
    private val cpuFrequencyMonitor = CpuFrequencyMonitor(engine)
    private val cpuUsageProvider = CpuUsageProvider(engine)
    private val thermalMonitor = ThermalMonitor(context, engine)

    private val _state = MutableStateFlow(SamplingState())
    val state: StateFlow<SamplingState> = _state.asStateFlow()

    private var job: Job? = null
    private var refCount = 0
    private var tick = 0

    @Synchronized
    fun acquire() {
        refCount += 1
        if (job == null) {
            startLoop()
        }
    }

    @Synchronized
    fun release() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount == 0) {
            stopLoop()
        }
    }

    private fun startLoop() {
        _state.value = _state.value.copy(running = true)
        job = scope.launch {
            while (isActive) {
                val current = _state.value
                val snapshot = runCatching { monitor.sample() }.getOrNull()
                val cpuFrequency = if (tick % CPU_FREQ_EVERY_TICKS == 0) {
                    runCatching { cpuFrequencyMonitor.sample() }.getOrNull() ?: current.cpuFrequency
                } else {
                    current.cpuFrequency
                }
                val cpuUsage = if (tick % CPU_USAGE_EVERY_TICKS == 0) {
                    runCatching { cpuUsageProvider.sample() }
                        .getOrElse { com.huanlab.engine.service.metrics.CpuUsageMetric.unknown("采样异常") }
                } else {
                    current.cpuUsage
                }
                val thermal = if (tick % THERMAL_EVERY_TICKS == 0) {
                    runCatching { thermalMonitor.sample() }.getOrNull() ?: current.thermalSensors
                } else {
                    current.thermalSensors
                }
                val foreground = if (tick % FOREGROUND_EVERY_TICKS == 0) {
                    runCatching { foregroundDetector.currentForeground() }
                        .getOrElse { Metric.Unknown("前台识别异常") }
                } else {
                    _state.value.foreground
                }
                _state.value = SamplingState(
                    snapshot = snapshot ?: current.snapshot,
                    foreground = foreground,
                    cpuFrequency = cpuFrequency,
                    cpuUsage = cpuUsage,
                    thermalSensors = thermal,
                    running = true,
                )
                tick += 1
                delay(INTERVAL_MS)
            }
        }
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false)
    }

    private companion object {
        const val INTERVAL_MS = 2_000L
        const val FOREGROUND_EVERY_TICKS = 3
        const val CPU_FREQ_EVERY_TICKS = 2
        const val THERMAL_EVERY_TICKS = 5
        const val CPU_USAGE_EVERY_TICKS = 2
    }
}

object SamplingCoordinatorProvider {

    @Volatile
    private var instance: SamplingCoordinator? = null

    fun get(context: Context): SamplingCoordinator =
        instance ?: synchronized(this) {
            instance ?: SamplingCoordinator(context.applicationContext).also { instance = it }
        }
}
