package com.huanlab.engine.service.sampling

import android.content.Context
import com.huanlab.engine.service.DeviceMonitor
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
                val snapshot = runCatching { monitor.sample() }.getOrNull()
                val foreground = if (tick % FOREGROUND_EVERY_TICKS == 0) {
                    runCatching { foregroundDetector.currentForeground() }
                        .getOrElse { Metric.Unknown("前台识别异常") }
                } else {
                    _state.value.foreground
                }
                _state.value = SamplingState(
                    snapshot = snapshot ?: _state.value.snapshot,
                    foreground = foreground,
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
