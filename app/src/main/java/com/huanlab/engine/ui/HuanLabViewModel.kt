package com.huanlab.engine.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huanlab.engine.action.EngineActions
import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport
import com.huanlab.engine.core.ShizukuPhase
import com.huanlab.engine.core.ShizukuUiState
import com.huanlab.engine.service.DeviceMonitor
import com.huanlab.engine.service.DeviceSnapshot
import com.huanlab.engine.service.EngineProvider
import com.huanlab.engine.service.Metric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val shizuku: ShizukuUiState = ShizukuUiState(ShizukuPhase.UNKNOWN, "初始化中"),
    val snapshot: DeviceSnapshot? = null,
    val performanceEnabled: Boolean = false,
    val refreshRateTarget: Float = 120f,
    val busy: Boolean = false,
    val events: List<String> = emptyList(),
) {
    val cpu: Metric<Float> get() = snapshot?.cpuPercent ?: Metric.Unknown("尚未采样")
    val memory get() = snapshot?.memory ?: Metric.Unknown("尚未采样")
    val thermal get() = snapshot?.thermal ?: Metric.Unknown("尚未采样")
}

class HuanLabViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: Engine = EngineProvider.get(application)
    private val monitor = DeviceMonitor(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { pollLoop() }
    }

    private suspend fun pollLoop() {
        while (true) {
            val result = withContext(Dispatchers.IO) {
                monitor.sample() to engine.shizukuState()
            }
            _state.update { it.copy(snapshot = result.first, shizuku = result.second) }
            delay(POLL_INTERVAL_MS)
        }
    }

    fun retryShizuku() {
        engine.refreshShizuku()
        pushEvent("已请求重新检测 Shizuku")
    }

    fun togglePerformance(enabled: Boolean) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val report = engine.execute(EngineActions.performanceMode(enabled))
            val success = report is ExecutionReport.Executed && report.allOk
            if (success) {
                _state.update { it.copy(performanceEnabled = enabled) }
            }
            pushEvent(if (enabled) "性能模式" else "均衡模式", report)
            _state.update { it.copy(busy = false) }
        }
    }

    fun previewRefreshRate(value: Float) {
        _state.update { it.copy(refreshRateTarget = value) }
    }

    fun applyRefreshRate(value: Float) {
        if (_state.value.busy) return
        val hz = value.toInt().coerceIn(EngineActions.MIN_HZ, EngineActions.MAX_HZ)
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val report = engine.execute(EngineActions.refreshRate(hz))
            val success = report is ExecutionReport.Executed && report.allOk
            _state.update {
                it.copy(
                    refreshRateTarget = if (success) hz.toFloat() else it.refreshRateTarget,
                    busy = false,
                )
            }
            pushEvent("刷新率 ${hz}Hz", report)
        }
    }

    fun whitelistSelf() {
        if (_state.value.busy) return
        val pkg = getApplication<Application>().packageName
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val report = engine.execute(EngineActions.deviceIdleWhitelist(pkg))
            pushEvent("加入 Doze 白名单", report)
            _state.update { it.copy(busy = false) }
        }
    }

    fun pushEvent(title: String, report: ExecutionReport) {
        val text = when (report) {
            is ExecutionReport.Executed -> {
                val failed = report.results.count { !it.ok }
                if (failed == 0) "$title：成功（${report.results.size} 条命令）"
                else "$title：${report.results.size - failed} 成功 / $failed 失败\n${report.summary}"
            }
            is ExecutionReport.Rejected -> "$title 未执行\n${report.summary}"
        }
        pushEvent(text)
    }

    fun pushEvent(text: String) {
        _state.update { current ->
            current.copy(events = (listOf(text) + current.events).take(8))
        }
    }

    override fun onCleared() {
        // Engine 为应用级共享实例，由进程生命周期管理。
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
    }
}
